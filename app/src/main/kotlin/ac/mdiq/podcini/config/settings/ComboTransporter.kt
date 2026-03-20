package ac.mdiq.podcini.config.settings

import ac.mdiq.podcini.storage.database.allFeeds
import ac.mdiq.podcini.storage.database.getEpisodes
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.utils.UnifiedFile
import ac.mdiq.podcini.storage.utils.clipsDir
import ac.mdiq.podcini.storage.utils.div
import ac.mdiq.podcini.storage.utils.generateFileName
import ac.mdiq.podcini.storage.utils.mediaDir
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import kotlinx.io.IOException

open class MediaFilesTransporter(val filesDirName: String) {
    private val TAG = "MediaFilesTransporter"

    var feed: Feed? = null
    private val nameFeedMap: MutableMap<String, Feed> = mutableMapOf()
    private val nameEpisodeMap: MutableMap<String, Long> = mutableMapOf()

    suspend fun fromMediaDirToUF(uf: UnifiedFile, move: Boolean = false, useSubDir: Boolean = true) {
        try {
            Logd(TAG, "exportToUri uri: ${uf.absPath}")
            if (!uf.exists()) throw IOException("Destination directory does not exist")
            val exportSubDir = if (useSubDir) {
                var subDir = uf / filesDirName
                if (!subDir.exists()) subDir = uf.createDirectory(filesDirName)
                subDir
            } else uf
            allFeeds.forEach { f -> if (!f.title.isNullOrEmpty()) nameFeedMap[generateFileName(f.title!!)] = f }
            mediaDir.listChildren().forEach { file -> copyRecursive(file, exportSubDir, move) }
        } catch (e: IOException) {
            Logs(TAG, e)
            throw e
        } finally { }
    }

    private suspend fun copyMediaFile(srcFile: UnifiedFile, destRootDir: UnifiedFile, move: Boolean, onlyUpdateDB: Boolean) {
        Logd(TAG, "copyMediaFile srcFile.name: ${srcFile.name}")
        val nameParts = srcFile.name.split(".")
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

    protected open suspend fun copyRecursive(srcFile: UnifiedFile, destRootDir: UnifiedFile, move: Boolean, onlyUpdateDB: Boolean = false) {
        Logd(TAG, "copyRecursive srcFile: ${srcFile.absPath}")
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
            if (files.isNotEmpty()) files.forEach { file -> copyRecursive(file, destdir, move) }
            if (!onlyUpdateDB && move) srcFile.delete()
        } else copyMediaFile(srcFile, destRootDir, move, onlyUpdateDB)
    }

    suspend fun fromUFToMediaDir(uf: UnifiedFile, move: Boolean = false, verify : Boolean = true) {
        try {
            if (!uf.exists()) throw IOException("Backup directory is not valid")
            Logd(TAG, "fromUFToMediaDir uf: ${uf.name} filesDirName: $filesDirName")
            if (verify && !uf.name.contains(filesDirName)) return
            val fileList = uf.listChildren()
            if (fileList.isNotEmpty()) {
                allFeeds.forEach { f -> if (!f.title.isNullOrEmpty()) nameFeedMap[generateFileName(f.title!!)] = f }
                fileList.forEach { file -> copyRecursive(file, mediaDir, move) }
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
    open suspend fun updateDB() {
        try {
            allFeeds.forEach { f -> if (!f.title.isNullOrEmpty()) nameFeedMap[generateFileName(f.title!!)] = f }
            val fileList = mediaDir.listChildren()
            fileList.forEach { file -> copyRecursive(file, mediaDir, move = false, onlyUpdateDB = true) }
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

class ClipsTransporter(val filesDirName: String) {
    private val TAG = "ClipsTransporter"

    suspend fun fromMediaDirToUF(uf: UnifiedFile, move: Boolean = false, useSubDir: Boolean = true) {
        try {
            Logd(TAG, "fromMediaDirToUF uf: ${uf.absPath}")
            if (!uf.exists()) throw IOException("Destination directory does not exist")
            val exportSubDir = if (useSubDir) {
                var subDir = uf / filesDirName
                if (!subDir.exists()) subDir = uf.createDirectory(filesDirName)
                subDir
            } else uf
            clipsDir.listChildren().forEach { file -> copyRecursive(file, exportSubDir, move) }
        } catch (e: IOException) {
            Logs(TAG, e)
            throw e
        } finally { }
    }

    suspend fun copyRecursive(srcFile: UnifiedFile, destRootDir: UnifiedFile, move: Boolean) {
        Logd(TAG, "copyRecursive srcFile: ${srcFile.absPath}")
        if (srcFile.isDirectory()) Loge(TAG, "srcFile should not be a directory: ${srcFile.absPath}")
        else {
            val destName = srcFile.name
            Logd(TAG, "copyMediaFile srcFile.name: $destName")
            val nameParts = destName.split(".")
            if (nameParts.size != 2) return
            val mainPart = nameParts[0]
            Logd(TAG, "copyRecursive mainPart: $mainPart")
            val parts = mainPart.split("_")
            if (parts.size < 3) return
            val eid = parts[1].toLong()
            val episode = realm.query(Episode::class).query("id == $eid").first().find() ?: return
            Logd(TAG, "copyRecursiveFD found episode: ${episode.title}")
            var destFile = destRootDir / destName
            if (!destFile.exists()) {
                Logd(TAG, "copyRecursiveDF copying file to: ${destFile.absPath}")
                destFile = destRootDir.createFile(episode.mimeType?:"", destName)
                srcFile.copyTo(destFile)
                if (move) srcFile.delete()
            }
        }
    }

    suspend fun fromUFToMediaDir(uf: UnifiedFile, move: Boolean = false, verify : Boolean = true) {
        try {
            if (!uf.exists()) throw IOException("Backup directory is not valid")
            Logd(TAG, "fromUFToMediaDir uf: ${uf.name} filesDirName: $filesDirName")
            if (verify && !uf.name.contains(filesDirName)) return
            val fileList = uf.listChildren()
            if (fileList.isNotEmpty()) fileList.forEach { file -> copyRecursive(file, clipsDir, move) }
        } catch (e: IOException) {
            Logs(TAG, e)
            throw e
        } finally { }
    }
}

class DatabaseTransporter {
    val TAG = "DatabaseTransporter"

    suspend fun exportToUri(uri: UnifiedFile) {
        try {
            val realmPath = realm.configuration.path
            Logd(TAG, "exportToStream realmPath: $realmPath")
            val currentDB = realmPath.toUF()
            if (currentDB.exists()) currentDB.copyTo(uri)
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
