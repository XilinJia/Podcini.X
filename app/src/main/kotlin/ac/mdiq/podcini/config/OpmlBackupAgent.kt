package ac.mdiq.podcini.config

import ac.mdiq.podcini.config.settings.OpmlTransporter
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.UnifiedFile
import ac.mdiq.podcini.storage.utils.div
import ac.mdiq.podcini.storage.utils.internalDir
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInputStream
import android.app.backup.BackupDataOutput
import android.app.backup.BackupHelper
import android.os.ParcelFileDescriptor
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.readLine
import okio.buffer
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.MessageDigest

class OpmlBackupAgent : BackupAgentHelper() {

    override fun onCreate() {
        val isAutoBackupOPML = appPrefs.OPMLBackup
        if (isAutoBackupOPML) {
            Logd(TAG, "Backup of OPML enabled in preferences")
            addHelper(OPML_BACKUP_KEY, OpmlBackupHelper())
        } else Logd(TAG, "Backup of OPML disabled in preferences")
    }

    private class OpmlBackupHelper() : BackupHelper {
        private var mChecksum: ByteArray = byteArrayOf()

        override fun performBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput, newState: ParcelFileDescriptor) {
            runOnIOScope {
                Logd(TAG, "Performing backup")
                val digester: MessageDigest = MessageDigest.getInstance("MD5")
                val file = getOpmlFile()

                try { // Write OPML
                    OpmlTransporter.OpmlWriter().writeDocument(getFeedList("NOT (downloadUrl BEGINSWITH '${Feed.Companion.PREFIX_LOCAL_FOLDER}')"), file) // Compare checksum of new and old file to see if we need to perform a backup at all
                    val newChecksum = digester.digest()
                    Logd(TAG, "New checksum: " + BigInteger(1, newChecksum).toString(16)) // Get the old checksum
                    if (oldState != null) {
                        val inputStream = FileInputStream(oldState.fileDescriptor)
                        val source = inputStream.asSource().buffered()
                        source.use { input ->
                            val len = try {
                                input.readByte().toInt()
                            } catch (e: Exception) {
                                -1
                            }
                            if (len > 0) {
                                val oldChecksum = input.readByteArray(len)
                                if (oldChecksum.contentEquals(mChecksum)) {
                                    Logd(TAG, "Checksums are same; skipping backup")
                                    return@runOnIOScope
                                }
                            }
                        }
                    }
                    writeNewStateDescription(newState, newChecksum)

                    Logd(TAG, "Backing up OPML")
                    val bytes = file.source().buffer().use { it.readByteArray() }
                    if (bytes.isNotEmpty()) {
                        data.writeEntityHeader(OPML_ENTITY_KEY, bytes.size)
                        data.writeEntityData(bytes, bytes.size)
                    }
                    data.writeEntityHeader(OPML_ENTITY_KEY, bytes.size)
                    data.writeEntityData(bytes, bytes.size)
                    Logt(TAG, "OPML file backed up")
                } catch (e: Exception) {
                    Logs(TAG, e, "Error during backup.")
                }
            }
        }

        override fun restoreEntity(data: BackupDataInputStream) {
            runOnIOScope {
                Logd(TAG, "Backup restore")
                if (OPML_ENTITY_KEY != data.key) {
                    Logd(TAG, "Unknown entity key: " + data.key)
                    return@runOnIOScope
                }
                val digester = MessageDigest.getInstance("MD5")
                var linesRead = 0
                var feedCount = 0

                val restoredFile = getOpmlFile()
                digester.reset()
                val source = data.asSource().buffered()

                try {
                    restoredFile.sink().buffer().use { sink ->
                        source.use { input ->
                            while (true) {
                                val line = input.readLine() ?: break
                                if (line.contains("<outline")) feedCount++
                                val lineWithNewline = "$line\n"
                                val bytes = lineWithNewline.encodeToByteArray()
                                digester.update(bytes)
                                sink.write(bytes)
                                linesRead++
                                Logd(TAG, "restoreEntity: $linesRead $line")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("zero bytes") == true) Logd(TAG, "Stream ended prematurely or returned zero bytes. Stopping.")
                    else Logs(TAG, e, "Failed to restore OPML backup.")
                } finally {
                    mChecksum = digester.digest() ?: byteArrayOf()
                    if (linesRead > 0) {
                        Logd(TAG, "restoreEntity finally $feedCount")
                        upsertBlk(appPrefs) {
                            it.OPMLRestored = true
                            it.OPMLFeedsToRestore = feedCount
                        }
                    }
                }
            }
        }
        override fun writeNewStateDescription(newState: ParcelFileDescriptor) {
            writeNewStateDescription(newState, mChecksum)
        }
        /**
         * Writes the new state description, which is the checksum of the OPML file.
         * @param newState
         * @param checksum
         */
        private fun writeNewStateDescription(newState: ParcelFileDescriptor, checksum: ByteArray?) {
            if (checksum == null) return
            try {
                val outState = FileOutputStream(newState.fileDescriptor)
                // TODO: this can get: java.io.IOException: write failed: EBADF (Bad file descriptor)
                outState.write(checksum.size)
                outState.write(checksum)
                outState.flush()
                outState.close()
            } catch (e: Exception) { Logs(TAG, e, "Failed to write new state description.") }
        }

        companion object {
            private val TAG: String = OpmlBackupHelper::class.simpleName ?: "Anonymous"
            private const val OPML_ENTITY_KEY = "podcini-feeds.opml"
        }
    }

    companion object {
        private val TAG: String = OpmlBackupAgent::class.simpleName ?: "Anonymous"
        private const val OPML_BACKUP_KEY = "opml"

        private fun getOpmlFile(): UnifiedFile {
            return internalDir / "opml_restored.txt"
        }

        fun performRestore() {
            runOnIOScope {
                Logd(TAG, "performRestore")
                val tempFile = getOpmlFile()
                if (tempFile.exists()) {
                    val opmlElements = OpmlTransporter.OpmlReader().readDocument(tempFile)
                    for (opmlElem in opmlElements) {
                        val feed = Feed(opmlElem.xmlUrl, null, opmlElem.text)
                        feed.episodes.clear()
                        updateFeedFull(feed, false)
                    }
                    Logt(TAG, "${opmlElements.size} feeds were restored")
                    FeedUpdateManager.runOnce()
                } else Loge(TAG, "No backup data found in ${tempFile.absPath}")
            }
        }
    }
}