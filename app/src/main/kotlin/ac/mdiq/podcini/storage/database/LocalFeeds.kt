package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.DownloadResult.Companion.logDownloadResult
import ac.mdiq.podcini.storage.model.DownloadResult.Companion.getFeedDownloadLogs
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Volume
import ac.mdiq.podcini.storage.parser.Id3MetadataReader
import ac.mdiq.podcini.storage.parser.VorbisCommentMetadataReader
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
import ac.mdiq.podcini.storage.utils.toSafeUri
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.utils.LogFor
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.LogtFor
import ac.mdiq.podcini.utils.parsePatternTimestampToMillis
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.io.IOException
import okio.ByteString.Companion.encodeUtf8
import okio.buffer
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
                    Logd(TAG,"addLocalFolder Found files in folder: ${directory.toAndroidUri()}")
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
                    } else Logt(TAG, "addLocalFolder local feed already exists: $title $uri")
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
                        Logd(TAG, "addLocalFolder Created volume: ${v.name} $parentId")
                    } else v = realm.copyFromRealm(vExist)
                    for (subDir in subDirsInThisDir) traverseDirectory(subDir, v.id)
                }
            }

            traverseDirectory(file)
            if (volumes.isNotEmpty()) realm.write { for (v in volumes) copyToRealm(v) }
            if (feeds.isNotEmpty()) gearbox.feedUpdater(feeds, doItAnyway = true).startRefresh()
            Logt(TAG, "addLocalFolder Imported ${feeds.size} local feeds in ${volumes.size} volumes")
        } catch (e: Throwable) { Logs(TAG, e, e.localizedMessage?: "No messaage") }
    }
}

@OptIn(ExperimentalUuidApi::class)
suspend fun updateLocalFeed(feed: Feed, progressCB: ((Int, Int)->Unit)? = null) {
    data class DocFile(val name: String, val type: String, val uri: Uri, val length: Long, val lastModified: Long)

    fun getImageUrl(files: List<DocFile>, folderUri: Uri): String {
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
    fun createEpisode(feed: Feed, file: DocFile): Episode {
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
            val format = file.uri.toUF().source().buffer().peek().use { source ->
                val header4 = source.readByteString(4)
                val header8 = if (source.request(8)) source.readByteString(8) else header4
                when {
                    header4.startsWith("ID3".encodeUtf8()) -> "MP3"
                    header4.startsWith("OggS".encodeUtf8()) -> "OGG"
                    header4.startsWith("fLaC".encodeUtf8()) -> "FLAC"
                    header8.substring(4, 8).string(Charsets.UTF_8) == "ftyp" -> "M4A"
                    header4.startsWith("RIFF".encodeUtf8()) -> "WAV"
                    else -> "UNKNOWN"
                }
            }
            CountingSource(file.uri.toUF().source()).use { source ->
                when(format) {
                    "MP3" -> {
                        val reader = Id3MetadataReader(source)
                        reader.readSource()
                        item.setDescriptionIfLonger(reader.comment)
                    }
                    "OGG", "FLAC" -> {
                        val reader = VorbisCommentMetadataReader(source)
                        reader.readSource()
                        item.setDescriptionIfLonger(reader.description)
                    }
                    else -> Logt(TAG, "Unhandled file type: $format ${file.uri}")
                }
            }
            MediaMetadataRetrieverCompat().use { mmr ->
                val NULL_DATE_PLACEHOLDER = "19040101T000000.000Z"
                try {
                    getAppContext().contentResolver.openFileDescriptor(file.uri, "r")?.use { pfd ->
                        mmr.setDataSource(pfd.fileDescriptor)
                        val dateStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                        if (!dateStr.isNullOrEmpty() && dateStr != NULL_DATE_PLACEHOLDER) {
                            try {
                                item.pubDate = parsePatternTimestampToMillis("yyyyMMdd'T'HHmmss", dateStr) ?: 0L
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
                } catch (e: Throwable) { Logs(TAG, "MediaMetadataRetrieverCompat failure: ${e.message}")}
            }
        } catch (e: Exception) {
            Logs(TAG, e, "loadMetadata failed: ${file.uri}")
            item.setDescriptionIfLonger(e.message) }
        return item
    }
    val allFiles = mutableListOf<DocFile>()
    fun traverseAll(uri: Uri, docId: String? = null) {
        Logd(TAG, "traverseAll uri: $uri docId: $docId")
        val authority = uri.authority ?: return
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val startDocId = when {
            docId != null -> docId
            DocumentsContract.isDocumentUri(getAppContext(), uri) -> DocumentsContract.getDocumentId(uri)
            else -> treeId
        }
        val treeRootUri = DocumentsContract.buildTreeDocumentUri(authority, treeId)
        val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeRootUri, startDocId)

        getAppContext().contentResolver.query(childUri, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val uri = DocumentsContract.buildDocumentUriUsingTree(uri, id)
                val name = cursor.getString(1)
                val size = cursor.getLong(2)
                val lastModified = cursor.getLong(3)
                val mime = cursor.getString(4)
                Logd(TAG, "traverseAll doc: $name mime: $mime")
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) traverseAll(uri, id)
                else allFiles.add(DocFile(name, mime, uri, size, lastModified))
            }
        }
    }

    if (feed.downloadUrl.isNullOrEmpty()) {
        LogFor(TAG, feed, false, "downloadUrl is null or empty")
        return
    }
    val uriString = feed.downloadUrl!!.replace(Feed.PREFIX_LOCAL_FOLDER, "")
    val documentFolder = uriString.toUF()
    if (!documentFolder.exists()) throw IOException("Cannot read local directory. Try re-connecting the folder on the podcast info page.")

    val folderUri = uriString.toSafeUri()
    traverseAll(folderUri)

    val mediaFiles: MutableList<DocFile> = mutableListOf()
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
    Logd(TAG, "updateLocalFeed newItems: ${newItems.size}")

    // remove feed items without corresponding file
    val it = newItems.iterator()
    while (it.hasNext()) {
        val item = it.next()
        if (!mediaFileNames.contains(item.link)) {
            LogtFor(TAG, item.id, "updateLocalFeed removing episode without file: ${item.link}")
            it.remove()
        }
    }
    Logd(TAG, "updateLocalFeed newItems 1: ${newItems.size}")
    feed.imageUrl = getImageUrl(allFiles, folderUri)
    feed.episodes.addAll(newItems)
    updateFeedFull(feed, removeUnlistedItems = true)

    fun mustReportDownloadSuccessful(feed: Feed): Boolean {
        val downloadResults = getFeedDownloadLogs(feed.id).toMutableList()
        if (downloadResults.isEmpty()) return true
        downloadResults.sortWith { ds1: DownloadResult, ds2: DownloadResult -> (ds1.completionTime - ds2.completionTime).toInt() }
        val lastDownloadResult = downloadResults[downloadResults.size - 1]
        return !lastDownloadResult.isSuccessful
    }

    if (mustReportDownloadSuccessful(feed)) {
        logDownloadResult(DownloadResult(feed, DownloadError.SUCCESS, true, ""))
        upsert(feed) { it.lastUpdateFailed = false }
    }
}
