package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.feed.parser.media.id3.Id3MetadataReader
import ac.mdiq.podcini.net.feed.parser.media.vorbis.VorbisCommentMetadataReader
import ac.mdiq.podcini.net.feed.parser.media.vorbis.VorbisCommentReaderException
import ac.mdiq.podcini.net.feed.parser.utils.getMimeType
import ac.mdiq.podcini.net.feed.parser.utils.parseDate
import ac.mdiq.podcini.storage.database.addDownloadStatus
import ac.mdiq.podcini.storage.database.getFeedDownloadLog
import ac.mdiq.podcini.storage.database.persistFeedLastUpdateFailed
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Episode.MediaMetadataRetrieverCompat
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.apache.commons.io.input.CountingInputStream

private const val TAG: String = "LocalFeedUpdater"

fun updateLocalFeed(feed: Feed, context: Context, progressCB: ((Int, Int)->Unit)? = null) {
    if (feed.downloadUrl.isNullOrEmpty()) return
    try {
        val uriString = feed.downloadUrl!!.replace(Feed.PREFIX_LOCAL_FOLDER, "")
        val documentFolder = DocumentFile.fromTreeUri(context, uriString.toUri()) ?: throw IOException("Unable to retrieve document tree. Try re-connecting the folder on the podcast info page.")
        if (!documentFolder.exists() || !documentFolder.canRead()) throw IOException("Cannot read local directory. Try re-connecting the folder on the podcast info page.")

        val folderUri = documentFolder.uri
        val allFiles = listFastFiles(context, folderUri)
        val mediaFiles: MutableList<FastDocumentFile> = mutableListOf()
        val mediaFileNames: MutableSet<String> = HashSet()
        for (file in allFiles) {
            val mimeType = getMimeType(file.type, file.uri.toString()) ?: continue
            val mediaType = MediaType.fromMimeType(mimeType)
            if (mediaType == MediaType.AUDIO || mediaType == MediaType.VIDEO) {
                mediaFiles.add(file)
                mediaFileNames.add(file.name)
                Logd(TAG, "tryUpdateFeed add to mediaFileNames ${file.name}")
            }
        }

        // add new files to feed and update item data
        val newItems = feed.episodes
        for (i in mediaFiles.indices) {
            Logd(TAG, "tryUpdateFeed mediaFiles ${mediaFiles[i].name}")
            val oldItem = feedContainsFile(feed, mediaFiles[i].name)
            val newItem = createEpisode(feed, mediaFiles[i], context)
            Logd(TAG, "tryUpdateFeed oldItem: ${oldItem?.title} url: ${oldItem?.downloadUrl}")
            Logd(TAG, "tryUpdateFeed newItem: ${newItem.title} url: ${newItem.downloadUrl}")
            oldItem?.updateFromOther(newItem) ?: newItems.add(newItem)
            progressCB?.invoke(i, mediaFiles.size)
        }
        // remove feed items without corresponding file
        val it = newItems.iterator()
        while (it.hasNext()) {
            val feedItem = it.next()
            if (!mediaFileNames.contains(feedItem.link)) {
                Logd(TAG, "tryUpdateFeed removing file ${feedItem.link} ${feedItem.title} ")
                it.remove()
            }
        }
        if (folderUri != null) feed.imageUrl = getImageUrl(allFiles, folderUri)
        feed.autoDownload = false
        feed.description = context.getString(R.string.local_feed_description)
        feed.author = context.getString(R.string.local_folder)
        updateFeedFull(feed, removeUnlistedItems = true)

        if (mustReportDownloadSuccessful(feed)) reportSuccess(feed)
    } catch (e: Exception) {
        Logs(TAG, e, "updateFeed failed")
        reportError(feed, e.message)
    }
}

private fun getImageUrl(files: List<FastDocumentFile>, folderUri: Uri): String {
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

private fun feedContainsFile(feed: Feed, filename: String): Episode? {
    val index = feed.episodes.indexOfFirst { it.link == filename }
    return if (index < 0) null else feed.episodes[index]
}

private fun createEpisode(feed: Feed, file: FastDocumentFile, context: Context): Episode {
    val item = Episode(0L, file.name, UUID.randomUUID().toString(), file.name, Date(file.lastModified), EpisodeState.UNPLAYED.code, feed)
    item.disableAutoDownload()
    val size = file.length
    Logd(TAG, "createEpisode file.uri: ${file.uri}")
    item.fillMedia(0, 0, size, file.type, file.uri.toString(), file.uri.toString(), false, null, 0, 0)
    val episodes = feed.episodes
    for (existingItem in episodes) {
        if (existingItem.downloadUrl == file.uri.toString() && file.length == existingItem.size) {
            // We found an old file that we already scanned. Re-use metadata.
            item.updateFromOther(existingItem)
            return item
        }
    }
    // Did not find existing item. Scan metadata.
    try { loadMetadata(item, file, context) } catch (e: Exception) {
        Logs(TAG, e, "loadMetadata failed")
        item.setDescriptionIfLonger(e.message) }
    return item
}

private fun loadMetadata(item: Episode, file: FastDocumentFile, context: Context) {
    MediaMetadataRetrieverCompat().use { mediaMetadataRetriever ->
        mediaMetadataRetriever.setDataSource(context, file.uri)
        val dateStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
        if (!dateStr.isNullOrEmpty() && "19040101T000000.000Z" != dateStr) {
            try {
                val simpleDateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.getDefault())
                item.pubDate = simpleDateFormat.parse(dateStr)?.time ?: 0L
            } catch (e: ParseException) {
                Logs(TAG, e, "loadMetadata failed")
                val date = parseDate(dateStr)
                if (date != null) item.pubDate = date.time
            }
        }
        val title = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        if (!title.isNullOrEmpty()) item.title = title
        val durationStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        item.duration = (durationStr!!.toLong().toInt())
        item.hasEmbeddedPicture = (mediaMetadataRetriever.embeddedPicture != null)
        try {
            context.contentResolver.openInputStream(file.uri).use { inputStream ->
                val reader = Id3MetadataReader(CountingInputStream(BufferedInputStream(inputStream)))
                reader.readInputStream()
                item.setDescriptionIfLonger(reader.comment)
            }
        } catch (e: Throwable) {
            Logs(TAG, e, "Unable to parse ID3 of " + file.uri + ": ")
            try {
                context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                    val reader = VorbisCommentMetadataReader(inputStream)
                    reader.readInputStream()
                    item.setDescriptionIfLonger(reader.description)
                }
            } catch (e2: IOException) { Logs(TAG, e2, "Unable to parse vorbis comments of " + file.uri + ": ")
            } catch (e2: VorbisCommentReaderException) { Logs(TAG, e2, "Unable to parse vorbis comments of " + file.uri + ": ") }
        }
    }
}

private fun reportError(feed: Feed, reasonDetailed: String?) {
    val status = DownloadResult(feed.id, feed.title?:"", DownloadError.ERROR_IO_ERROR, false, reasonDetailed?:"")
    addDownloadStatus(status)
    persistFeedLastUpdateFailed(feed, true)
}

/**
 * Reports a successful download status.
 */
private fun reportSuccess(feed: Feed) {
    val status = DownloadResult(feed.id, feed.title?:"", DownloadError.SUCCESS, true, "")
    addDownloadStatus(status)
    persistFeedLastUpdateFailed(feed, false)
}

/**
 * Answers if reporting success is needed for the given feed.
 */
private fun mustReportDownloadSuccessful(feed: Feed): Boolean {
    val downloadResults = getFeedDownloadLog(feed.id).toMutableList()
    // report success if never reported before
    if (downloadResults.isEmpty()) return true
    downloadResults.sortWith { downloadStatus1: DownloadResult, downloadStatus2: DownloadResult ->
        downloadStatus1.getCompletionDate().compareTo(downloadStatus2.getCompletionDate())
    }
    val lastDownloadResult = downloadResults[downloadResults.size - 1]
    // report success if the last update was not successful
    // (avoid logging success again if the last update was ok)
    return !lastDownloadResult.isSuccessful
}

/**
 * Android's DocumentFile is slow because every single method call queries the ContentResolver.
 * This queries the ContentResolver a single time with all the information.
 */
data class FastDocumentFile(val name: String, val type: String, val uri: Uri, val length: Long, val lastModified: Long)

fun listFastFiles(context: Context, folderUri: Uri?): List<FastDocumentFile> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getDocumentId(folderUri))
    val cursor = context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
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
