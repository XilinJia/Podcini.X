package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.DownloadResult.Companion.addDownloadStatus
import ac.mdiq.podcini.storage.model.DownloadResult.Companion.getFeedDownloadLog
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Volume
import ac.mdiq.podcini.storage.parser.Id3MetadataReader
import ac.mdiq.podcini.storage.parser.VorbisCommentMetadataReader
import ac.mdiq.podcini.storage.parser.VorbisCommentReaderException
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.utils.CountingSource
import ac.mdiq.podcini.storage.utils.MediaMetadataRetrieverCompat
import ac.mdiq.podcini.storage.utils.UnifiedFile
import ac.mdiq.podcini.storage.utils.getMimeType
import ac.mdiq.podcini.storage.utils.parseDate
import ac.mdiq.podcini.storage.utils.persistedTrees
import ac.mdiq.podcini.storage.utils.toAndroidUri
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.parsePatternTimestampToMillis
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import kotlinx.io.IOException
import kotlin.collections.contains
import kotlin.text.toIntOrNull
import kotlin.use
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "LocalFeeds"

fun addLocalFolder(uri: Uri) {
    val context = getAppContext()
    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    runOnIOScope {
        try {
            persistedTrees.add(uri)
            val file = uri.toUF()
            val feeds = mutableListOf<Feed>()
            val volumes = mutableListOf<Volume>()

            suspend fun traverseDirectory(directory: UnifiedFile?, parentId: Long = -1) {
                if (directory == null || !directory.isDirectory()) return

                val content = directory.listChildren()
                val filesInThisDir = content.filter { !it.isDirectory() }

                if (filesInThisDir.isNotEmpty()) {
                    Logd(TAG,"Found files in folder: ${directory.toAndroidUri()}")
                    val uri = directory.toAndroidUri()
                    val title = directory.name
                    val dirFeed = Feed(Feed.PREFIX_LOCAL_FOLDER + uri.toString(), null, title)
                    val fExist = feedByIdentityOrID(dirFeed)
                    if (fExist == null) {
                        dirFeed.volumeId = parentId
                        dirFeed.episodeSortOrder = EpisodeSortOrder.EPISODE_TITLE_ASC
                        dirFeed.keepUpdated = false
                        dirFeed.autoDownload = false
                        dirFeed.description = context.getString(R.string.local_feed_description)
                        dirFeed.author = context.getString(R.string.local_folder)
                        addNewFeed(dirFeed)
                        feeds.add(dirFeed)
                    } else Logt(TAG, "local feed already exists: $title $uri")
                }

                val subDirsInThisDir = content.filter { it.isDirectory() }
                if (subDirsInThisDir.isNotEmpty()) {
                    val v: Volume
                    val vExist = realm.query(Volume::class).query("uriString == $0", directory.absPath).first().find()
                    if (vExist == null) {
                        v = Volume()
                        v.id = getId()
                        v.name = directory.name
                        v.uriString = directory.absPath
                        v.parentId = parentId
                        v.isLocal = true
                        volumes.add(v)
                        Logd(TAG, "Created volume: ${v.name} $parentId")
                    } else v = realm.copyFromRealm(vExist)

                    for (subDir in subDirsInThisDir) traverseDirectory(subDir, v.id)
                }
            }

            traverseDirectory(file)
            if (volumes.isNotEmpty()) realm.write { for (v in volumes) copyToRealm(v) }
            if (feeds.isNotEmpty()) gearbox.feedUpdater(feeds, doItAnyway = true).startRefresh()
            Logt(TAG, "Imported ${feeds.size} local feeds in ${volumes.size} volumes")
        } catch (e: Throwable) { Logs(TAG, e, e.localizedMessage?: "No messaage") }
    }
}

@OptIn(ExperimentalUuidApi::class)
suspend fun updateLocalFeed(feed: Feed, progressCB: ((Int, Int)->Unit)? = null) {
    /**
     * Android's DocumentFile is slow because every single method call queries the ContentResolver.
     * This queries the ContentResolver a single time with all the information.
     */
    data class FastDocumentFile(val name: String, val type: String, val uri: Uri, val length: Long, val lastModified: Long)

    fun getImageUrl(files: List<FastDocumentFile>, folderUri: Uri): String {
        val PREFERRED_FEED_IMAGE_FILENAMES: Array<String> = arrayOf("folder.jpg", "Folder.jpg", "folder.png", "Folder.png")
        for (iconLocation in PREFERRED_FEED_IMAGE_FILENAMES) {
            for (file in files) if (iconLocation == file.name) return file.uri.toString()
        }
        for (file in files) {
            val mime = file.type
            if (mime.startsWith("image/jpeg") || mime.startsWith("image/png")) return file.uri.toString()
        }
        return Feed.PREFIX_GENERATIVE_COVER + folderUri
    }
    fun createEpisode(feed: Feed, file: FastDocumentFile): Episode {
        val item = Episode(0L, file.name, Uuid.random().toString(), file.name, file.lastModified, EpisodeState.UNPLAYED.code, feed)
        item.isAutoDownloadEnabled = false
        val size = file.length
        Logd(TAG, "createEpisode file.uri: ${file.uri}")
        item.fillMedia(0, 0, size, file.type, file.uri.toString(), file.uri.toString(), false, 0L, 0, 0)
        val episodes = feed.episodes
        for (existingItem in episodes) {
            if (existingItem.downloadUrl == file.uri.toString() && existingItem.size == file.length) {
                // We found an old file that we already scanned. Re-use metadata.
                item.updateFromOther(existingItem)
                return item
            }
        }
        // Did not find existing item. Scan metadata.
        try {
            MediaMetadataRetrieverCompat().use { mmr ->
                val NULL_DATE_PLACEHOLDER = "19040101T000000.000Z"
                mmr.setDataSource(getAppContext(), file.uri)
                val dateStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                if (!dateStr.isNullOrEmpty() && dateStr != NULL_DATE_PLACEHOLDER) {
                    try {
                        item.pubDate = parsePatternTimestampToMillis("yyyyMMdd'T'HHmmss", dateStr)?: 0L
                    } catch (e: Throwable) {
                        Logs(TAG, e, "loadMetadata failed")
                        val date = parseDate(dateStr)
                        if (date != null) item.pubDate = date.toEpochMilliseconds()
                    }
                }
                val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (!title.isNullOrEmpty()) item.title = title
                val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (!durationStr.isNullOrBlank()) item.duration = durationStr.toIntOrNull() ?: 30000
                item.hasEmbeddedPicture = (mmr.embeddedPicture != null)
            }
            try {
                val source = CountingSource(file.uri.toUF().source())
                val reader = Id3MetadataReader(source)
                reader.readSource()
                item.setDescriptionIfLonger(reader.comment)
            } catch (e: Throwable) {
                Logs(TAG, e, "Unable to parse ID3 of " + file.uri + ": ")
                try {
                    val source = CountingSource(file.uri.toUF().source())
                    val reader = VorbisCommentMetadataReader(source)
                    reader.readSource()
                    item.setDescriptionIfLonger(reader.description)
                } catch (e2: IOException) { Logs(TAG, e2, "Unable to parse vorbis comments of " + file.uri + ": ")
                } catch (e2: VorbisCommentReaderException) { Logs(TAG, e2, "Unable to parse vorbis comments of " + file.uri + ": ") }
            }
        } catch (e: Exception) {
            Logs(TAG, e, "loadMetadata failed")
            item.setDescriptionIfLonger(e.message) }
        return item
    }
    fun listFastFiles(folderUri: Uri?): List<FastDocumentFile> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getDocumentId(folderUri))
        val cursor = getAppContext().contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
        val list = mutableListOf<FastDocumentFile>()
        while (cursor!!.moveToNext()) {
            val id = cursor.getString(0)
            val uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id)
            val name = cursor.getString(1)
            val size = cursor.getLong(2)
            val lastModified = cursor.getLong(3)
            val mimeType = cursor.getString(4)
            list.add(FastDocumentFile(name, mimeType, uri, size, lastModified))
        }
        cursor.close()
        return list
    }

    if (feed.downloadUrl.isNullOrEmpty()) return
    val uriString = feed.downloadUrl!!.replace(Feed.PREFIX_LOCAL_FOLDER, "")
    val documentFolder = uriString.toUF()
    if (!documentFolder.exists()) throw IOException("Cannot read local directory. Try re-connecting the folder on the podcast info page.")

    val folderUri = uriString.toUri()
    val allFiles = listFastFiles(folderUri)
    val mediaFiles: MutableList<FastDocumentFile> = mutableListOf()
    val mediaFileNames: MutableSet<String> = HashSet()
    for (file in allFiles) {
        val mimeType = getMimeType(file.type, file.uri.toString()) ?: continue
        val mediaType = MediaType.fromMimeType(mimeType)
        if (mediaType == MediaType.AUDIO || mediaType == MediaType.VIDEO) {
            mediaFiles.add(file)
            mediaFileNames.add(file.name)
            Logd(TAG, "updateLocalFeed add to mediaFileNames ${file.name}")
        }
    }

    // add new files to feed and update item data
    val newItems = mutableListOf<Episode>()
    for (i in mediaFiles.indices) {
        Logd(TAG, "updateLocalFeed mediaFiles ${mediaFiles[i].name}")
        val oldItem = realm.query(Episode::class).query("feedId == ${feed.id} AND link == $0", mediaFiles[i].name).first().find()
        val newItem = createEpisode(feed, mediaFiles[i])
        Logd(TAG, "updateLocalFeed oldItem: ${oldItem?.title} url: ${oldItem?.downloadUrl}")
        Logd(TAG, "updateLocalFeed newItem: ${newItem.title} url: ${newItem.downloadUrl}")
        if (oldItem != null) upsertBlk(oldItem) { it.updateFromOther(newItem) }
        else newItems.add(newItem)
        progressCB?.invoke(i, mediaFiles.size)
    }
    // remove feed items without corresponding file
    val it = newItems.iterator()
    while (it.hasNext()) {
        val item = it.next()
        if (!mediaFileNames.contains(item.link)) {
            Logt(TAG, "updateLocalFeed removing episode without file: ${item.link} ${item.title} ")
            it.remove()
        }
    }
    feed.imageUrl = getImageUrl(allFiles, folderUri)
    feed.episodes.addAll(newItems)
    updateFeedFull(feed, removeUnlistedItems = true)

    fun mustReportDownloadSuccessful(feed: Feed): Boolean {
        val downloadResults = getFeedDownloadLog(feed.id).toMutableList()
        if (downloadResults.isEmpty()) return true
        downloadResults.sortWith { ds1: DownloadResult, ds2: DownloadResult -> (ds1.completionTime - ds2.completionTime).toInt() }
        val lastDownloadResult = downloadResults[downloadResults.size - 1]
        return !lastDownloadResult.isSuccessful
    }

    if (mustReportDownloadSuccessful(feed)) {
        val status = DownloadResult(feed, DownloadError.SUCCESS, true, "")
        addDownloadStatus(status)
        upsert(feed) { it.lastUpdateFailed = false }
    }
}
