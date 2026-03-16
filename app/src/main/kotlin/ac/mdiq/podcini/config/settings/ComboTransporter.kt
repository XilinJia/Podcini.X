package ac.mdiq.podcini.config.settings

import ac.mdiq.podcini.storage.database.getEpisodes
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.utils.UnifiedFile
import ac.mdiq.podcini.storage.utils.div
import ac.mdiq.podcini.storage.utils.generateFileName
import ac.mdiq.podcini.storage.utils.mediaDir
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import android.net.Uri
import kotlinx.io.IOException

class MediaFilesTransporter(val mediaFilesDirName: String) {
    val TAG = "MediaFilesTransporter"

    var feed: Feed? = null
    private val nameFeedMap: MutableMap<String, Feed> = mutableMapOf()
    private val nameEpisodeMap: MutableMap<String, Long> = mutableMapOf()

    suspend fun fromMediaDirToUri(uf: UnifiedFile, move: Boolean = false, useSubDir: Boolean = true) {
        try {
            Logd(TAG, "exportToUri uri: $uf")
            val chosenDir = uf
            if (!chosenDir.exists()) throw IOException("Destination directory does not exist")
            val exportSubDir = if (useSubDir) {
                var subDir = chosenDir / mediaFilesDirName
                if (!subDir.exists()) subDir = chosenDir.createDirectory(mediaFilesDirName)
                subDir
            } else chosenDir
            val feeds = getFeedList()
            feeds.forEach { f -> if (!f.title.isNullOrEmpty()) nameFeedMap[generateFileName(f.title!!)] = f }
            mediaDir.listChildren().forEach { file -> copyRecursive(file, mediaDir, exportSubDir, move) }
        } catch (e: IOException) {
            Logs(TAG, e)
            throw e
        } finally { }
    }

    private suspend fun copyRecursive(srcFile: UnifiedFile, srcRootDir: UnifiedFile, destRootDir: UnifiedFile, move: Boolean, onlyUpdateDB: Boolean = false) {
        Logd(TAG, "copyRecursive srcFile: ${srcFile.absPath} ${srcRootDir.absPath}")
        val relativePath = srcFile.name
        if (srcFile.isDirectory()) {
            feed = nameFeedMap[relativePath] ?: return
            Logd(TAG, "copyRecursiveFD found feed: ${feed?.title}")
            nameEpisodeMap.clear()
            val episodes = getEpisodes(EpisodeFilter("feedId == ${feed!!.id}"), null, copy = false)   // TODO: can run out of memory?
            episodes.forEach { e -> if (!e.title.isNullOrEmpty()) nameEpisodeMap[generateFileName(e.title!!)] = e.id }
//            nameEpisodeMap.keys.forEach { Logd(TAG, "key: $it") }
            var destdir = destRootDir / relativePath
            if (!destdir.exists()) destdir = destRootDir.createDirectory(relativePath)
            val files = srcFile.listChildren()
            if (files.isNotEmpty()) files.forEach { file -> copyRecursive(file, srcFile, destdir, move) }
            if (!onlyUpdateDB && move) srcFile.delete()
        } else {
            val nameParts = relativePath.split(".")
            if (nameParts.size < 3) return
            val ext = nameParts[nameParts.size-1]
            val title = nameParts.dropLast(2).joinToString(".")
            Logd(TAG, "copyRecursiveFD file title: $title")
            val eid = nameEpisodeMap[title] ?: return
            val episode = realm.query(Episode::class).query("id == $eid").first().find() ?: return
            Logd(TAG, "copyRecursiveFD found episode: ${episode.title}")
            val destName = "$title.${episode.id}.$ext"
            var destFile = destRootDir / destName
            if (!destFile.exists()) {
                Logd(TAG, "copyRecursiveDF copying file to: ${destFile.absPath}")
                if (!onlyUpdateDB) {
                    destFile = destRootDir.createFile(episode.mimeType?:"", destName)
                    srcFile.copyTo(destFile)
                    if (move) srcFile.delete()
                }
                upsert(episode) {
                    it.fileUrl = destFile.absPath
                    Logd(TAG, "copyRecursiveDF fileUrl: ${it.fileUrl}")
                    it.downloaded = true
                }
            }
        }
    }

    suspend fun fromUriToMediaDir(uf: UnifiedFile, move: Boolean = false, verify : Boolean = true) {
        try {
            val exportedDir = uf
            if (!exportedDir.exists()) throw IOException("Backup directory is not valid")
            if (verify && !exportedDir.name.contains(mediaFilesDirName)) return
            val fileList = exportedDir.listChildren()
            if (fileList.isNotEmpty()) {
                val feeds = getFeedList()
                feeds.forEach { f -> if (!f.title.isNullOrEmpty()) nameFeedMap[generateFileName(f.title!!)] = f }
                fileList.forEach { file -> copyRecursive(file, exportedDir, mediaDir, move) }
            }
        } catch (e: IOException) {
            Logs(TAG, e)
            throw e
        } finally {
            nameFeedMap.clear()
            nameEpisodeMap.clear()
            feed = null
        }
    }
    suspend fun updateDB() {
        try {
            val feeds = getFeedList()
            feeds.forEach { f -> if (!f.title.isNullOrEmpty()) nameFeedMap[generateFileName(f.title!!)] = f }
            val fileList = mediaDir.listChildren()
            fileList.forEach { file -> copyRecursive(file, mediaDir, mediaDir, move = false, onlyUpdateDB = true) }
        } catch (e: IOException) {
            Logs(TAG, e)
            throw e
        } finally {
            nameFeedMap.clear()
            nameEpisodeMap.clear()
            feed = null
        }
    }
}

class DatabaseTransporter {
    val TAG = "DatabaseTransporter"

    suspend fun exportToUri(uri: UnifiedFile) {
        try {
            val outfile = uri
            val realmPath = realm.configuration.path
            Logd(TAG, "exportToStream realmPath: $realmPath")
            val currentDB = realmPath.toUF()
            if (currentDB.exists()) currentDB.copyTo(outfile)
            else throw IOException("Can not access current database")
        } catch (e: Exception) {
            Logs(TAG, e)
            throw e
        }
    }

    suspend fun importBackup(sourceFile: UnifiedFile) {
        try {
            val currentDB = realm.configuration.path.toUF()
            currentDB.delete()
            sourceFile.copyTo(currentDB)
        } catch (e: Exception) {
            Logs(TAG, e)
            throw e
        }
    }
}
