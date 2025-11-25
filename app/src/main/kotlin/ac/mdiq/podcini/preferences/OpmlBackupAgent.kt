package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlReader
import ac.mdiq.podcini.preferences.OpmlTransporter.OpmlWriter
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import android.app.backup.BackupAgentHelper
import android.app.backup.BackupDataInputStream
import android.app.backup.BackupDataOutput
import android.app.backup.BackupHelper
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.preference.PreferenceManager
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Reader
import java.io.Writer
import java.math.BigInteger
import java.nio.charset.Charset
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import org.apache.commons.io.IOUtils
import org.xmlpull.v1.XmlPullParserException
import androidx.core.content.edit

class OpmlBackupAgent : BackupAgentHelper() {

    override fun onCreate() {
        val isAutoBackupOPML = getPref(AppPrefs.prefOPMLBackup, true)
        if (isAutoBackupOPML) {
            Logd(TAG, "Backup of OPML enabled in preferences")
            addHelper(OPML_BACKUP_KEY, OpmlBackupHelper(this))
        } else Logd(TAG, "Backup of OPML disabled in preferences")
    }

    private class OpmlBackupHelper(private val mContext: Context) : BackupHelper {
        private var mChecksum: ByteArray = byteArrayOf()

        override fun performBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput, newState: ParcelFileDescriptor) {
            Logd(TAG, "Performing backup")
            val byteStream = ByteArrayOutputStream()
            var digester: MessageDigest? = null
            var writer: Writer

            try {
                digester = MessageDigest.getInstance("MD5")
                writer = OutputStreamWriter(DigestOutputStream(byteStream, digester), Charset.forName("UTF-8"))
            } catch (e: NoSuchAlgorithmException) { writer = OutputStreamWriter(byteStream, Charset.forName("UTF-8")) }

            try {
                // Write OPML
                OpmlWriter().writeDocument(getFeedList(), writer, mContext)
                // Compare checksum of new and old file to see if we need to perform a backup at all
                if (digester != null) {
                    val newChecksum = digester.digest()
                    Logd(TAG, "New checksum: " + BigInteger(1, newChecksum).toString(16))
                    // Get the old checksum
                    if (oldState != null) {
                        val inState = FileInputStream(oldState.fileDescriptor)
                        val len = inState.read()
                        if (len != -1) {
                            val oldChecksum = ByteArray(len)
                            IOUtils.read(inState, oldChecksum, 0, len)
                            Logd(TAG, "Old checksum: " + BigInteger(1, oldChecksum).toString(16))
                            if (oldChecksum.contentEquals(newChecksum)) {
                                Logd(TAG, "Checksums are the same; won't backup")
                                return
                            }
                        }
                    }
                    writeNewStateDescription(newState, newChecksum)
                }
                Logd(TAG, "Backing up OPML")
                val bytes = byteStream.toByteArray()
                data.writeEntityHeader(OPML_ENTITY_KEY, bytes.size)
                data.writeEntityData(bytes, bytes.size)
                Logt(TAG, "OPML file backed up")
            } catch (e: IOException) { Logs(TAG, e, "Error during backup.")
            } finally { IOUtils.closeQuietly(writer) }
        }
        
        override fun restoreEntity(data: BackupDataInputStream) {
            Logd(TAG, "Backup restore")
            if (OPML_ENTITY_KEY != data.key) {
                Logd(TAG, "Unknown entity key: " + data.key)
                return
            }
            var digester: MessageDigest? = null
            var reader: Reader
            var linesRead = 0
            try {
                digester = MessageDigest.getInstance("MD5")
                reader = InputStreamReader(DigestInputStream(data, digester), Charset.forName("UTF-8"))
            } catch (e: NoSuchAlgorithmException) { reader = InputStreamReader(data, Charset.forName("UTF-8")) }
            var feedCount = 0
            try {
                mChecksum = digester?.digest() ?: byteArrayOf()
                BufferedReader(reader).use { bufferedReader ->
                    val tempFile = File(mContext.filesDir, "opml_restored.txt")
                    FileWriter(tempFile).use { fileWriter ->
                        while (true) {
                            val line = bufferedReader.readLine() ?: break
                            if (line.contains("<outline")) feedCount++
                            Logd(TAG, "restoreEntity: $linesRead $line")
                            linesRead++
                            fileWriter.write(line)
                            fileWriter.write(System.lineSeparator()) // Write a newline character
                        }
                    }
                }
            } catch (e: XmlPullParserException) { Logs(TAG, e, "Error while parsing the OPML file,")
            } catch (e: IOException) { Logs(TAG, e, "Failed to restore OPML backup.")
            } finally {
                if (linesRead > 0) {
                    Logd(TAG, "restoreEntity finally $feedCount")
                    val prefs = PreferenceManager.getDefaultSharedPreferences(mContext.applicationContext)
                    prefs.edit {
                        putBoolean(AppPrefs.prefOPMLRestore.name, true)
                        putInt(AppPrefs.prefOPMLFeedsToRestore.name, feedCount)
                    }
                }
                IOUtils.closeQuietly(reader)
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
                outState.write(checksum.size)
                outState.write(checksum)
                outState.flush()
                outState.close()
            } catch (e: IOException) { Logs(TAG, e, "Failed to write new state description.") }
        }

        companion object {
            private val TAG: String = OpmlBackupHelper::class.simpleName ?: "Anonymous"
            private const val OPML_ENTITY_KEY = "podcini-feeds.opml"
        }
    }

    companion object {
        private val TAG: String = OpmlBackupAgent::class.simpleName ?: "Anonymous"
        private const val OPML_BACKUP_KEY = "opml"

        fun performRestore(context: Context) {
            Logd(TAG, "performRestore")
            val tempFile = File(context.filesDir, "opml_restored.txt")
            if (tempFile.exists()) {
                val reader = FileReader(tempFile)
                val opmlElements = OpmlReader().readDocument(reader)
                for (opmlElem in opmlElements) {
                    val feed = Feed(opmlElem.xmlUrl, null, opmlElem.text)
                    feed.episodes.clear()
                    updateFeedFull(context, feed, false)
                }
                Logt(TAG, "${opmlElements.size} feeds were restored")
                runOnce(context)
            } else Loge(TAG, "No backup data found")
        }
    }
}
