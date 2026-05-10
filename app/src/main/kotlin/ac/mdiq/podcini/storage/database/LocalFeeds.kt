package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.DownloadResult.Companion.getFeedDownloadLogs
import ac.mdiq.podcini.storage.model.DownloadResult.Companion.logDownloadResult
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Volume
import ac.mdiq.podcini.storage.model.allVolumes
import ac.mdiq.podcini.storage.parser.Id3MetadataReader
import ac.mdiq.podcini.storage.parser.VorbisCommentMetadataReader
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.utils.CountingSource
import ac.mdiq.podcini.storage.utils.MediaFormat
import ac.mdiq.podcini.storage.utils.MediaMetadataRetrieverCompat
import ac.mdiq.podcini.storage.utils.UnifiedFile
import ac.mdiq.podcini.storage.utils.getMimeType
import ac.mdiq.podcini.storage.utils.parseDate
import ac.mdiq.podcini.storage.utils.peekFileFormat
import ac.mdiq.podcini.storage.utils.toAndroidUri
import ac.mdiq.podcini.storage.utils.toSafeUri
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.utils.LogFor
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.LogsFor
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.LogtFor
import ac.mdiq.podcini.utils.parsePatternTimestampToMillis
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.io.IOException
import okio.buffer
import kotlin.use
import kotlin.uuid.ExperimentalUuidApi

private const val TAG = "LocalFeeds"

suspend fun loadLocalFolder(uri: Uri, feedsExist: List<Feed> = listOf()) {
    try {
        for (f in feedsExist) {
            Logd(TAG, "loadLocalFolder check for feed: ${f.title}")
            if (f.downloadUrl.isNullOrBlank()) {
                LogFor(TAG, f, false, "downloadUrl is null or empty")
                continue
            }
            if (f.downloadUrl!!.startsWith("podcini_local:")) Logd(TAG, "loadLocalFolder invalid url: ${f.title} ${f.downloadUrl}")
            val folder = f.downloadUrl!!.toUF()
            if (!folder.exists()) {
                Logt(TAG, "loadLocalFolder feed folder not exists, deleting: ${f.title}")
                deleteFeed(f.id)
            }
        }

        val file = uri.toUF()
        val feeds = mutableListOf<Feed>()
        val volumes = mutableListOf<Volume>()

        val context = getAppContext()

        suspend fun traverseDirectory(directory: UnifiedFile?, parentId: Long = -1) {
            if (directory == null || !directory.isDirectory()) return

            val content = directory.listChildren()
            val filesInThisDir = content.filter { !it.isDirectory() }

            if (filesInThisDir.isNotEmpty()) {
                Logd(TAG,"loadLocalFolder Found files in folder: ${directory.toAndroidUri()}")
                val uri = directory.toAndroidUri()
                val title = directory.name
                val dirFeed = Feed(uri.toString(), null, title)
                dirFeed.isLocal = true
                dirFeed.volumeId = parentId
                val fExist = feedByIdentityOrID(dirFeed)
                if (fExist == null) {
                    dirFeed.episodeSortOrder = EpisodeSortOrder.EPISODE_TITLE_ASC
                    dirFeed.keepUpdated = false
                    dirFeed.autoDownload = false
                    dirFeed.description = context.getString(R.string.local_feed_description)
                    dirFeed.author = context.getString(R.string.local_folder)
                    addNewFeed(dirFeed)
                    feeds.add(dirFeed)
                } else Logt(TAG, "loadLocalFolder local feed already exists: $title $uri")
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
                    Logd(TAG, "loadLocalFolder Created volume: ${v.name} $parentId")
                } else v = realm.copyFromRealm(vExist)
                for (subDir in subDirsInThisDir) traverseDirectory(subDir, v.id)
            }
        }

        traverseDirectory(file)

        if (volumes.isNotEmpty()) {
            allVolumes += volumes
            realm.write { for (v in volumes) copyToRealm(v) }
        }
        if (feeds.isNotEmpty()) gearbox.feedUpdater(feeds, doItAnyway = true).startRefresh()
        Logt(TAG, "loadLocalFolder Imported ${feeds.size} local feeds in ${volumes.size} volumes")
        for (f in allFeeds) Logd(TAG, "loadLocalFolder feed: ${f.id} ${f.title} episodesCount: ${f.episodesCount}")
    } catch (e: Throwable) { Logs(TAG, e, e.localizedMessage?: "No messaage") }
}

@OptIn(ExperimentalUuidApi::class)
suspend fun updateLocalFeed(feed: Feed, progressCB: ((Int, Int)->Unit)? = null) {
    data class DocFile(val name: String, val type: String, val uri: Uri, val length: Long, val lastModified: Long)

    fun getImageUrl(files: List<DocFile>, folderUri: Uri): String {
        for (iconLocation in arrayOf("folder.jpg", "Folder.jpg", "folder.png", "Folder.png")) {
            for (file in files) if (iconLocation == file.name) return file.uri.toString()
        }
        for (file in files) {
            val mime = file.type
            if (mime.startsWith("image/jpeg") || mime.startsWith("image/png")) return file.uri.toString()
        }
        return folderUri.toString()
    }
    fun createEpisode(feed: Feed, file: DocFile): Episode {
        val item = Episode(0L, file.name, null, file.name, file.lastModified, EpisodeState.UNPLAYED.code, feed)
        item.isAutoDownloadEnabled = false
        val size = file.length
        Logd(TAG, "createEpisode file.uri: ${file.uri}")
        item.fillMedia(0, 0, size, file.type, file.uri.toString(), file.uri.toString(), false, 0L, 0, 0)
        val episodes = feed.episodes
        for (existingItem in episodes) {
            if (existingItem.downloadUrl == file.uri.toString() && existingItem.size == file.length) {
                item.updateFromOther(existingItem)
                return item
            }
        }
        try {
            val fileSource = file.uri.toUF().source().buffer()
            val format = peekFileFormat(fileSource)
            CountingSource(fileSource).use { source ->
                when(format) {
                    MediaFormat.MP3 -> {
                        val reader = Id3MetadataReader(source)
                        reader.readSource()
                        item.setDescriptionIfLonger(reader.comment)
                    }
                    MediaFormat.OGG, MediaFormat.FLAC -> {
                        val reader = VorbisCommentMetadataReader(source)
                        reader.readSource()
                        item.setDescriptionIfLonger(reader.description)
                    }
                    else -> LogtFor(TAG, item.id, "Unhandled file type: $format ${file.uri}")
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
                                LogsFor(TAG, item.id, "MediaMetadataRetrieverCompat failed: ${e.message}")
                                val date = parseDate(dateStr)
                                if (date != null) item.pubDate = date.toEpochMilliseconds()
                            }
                        }
                        val title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        if (!title.isNullOrEmpty()) item.title = title
                        val durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        if (!durationStr.isNullOrBlank()) item.duration = durationStr.toIntOrNull() ?: 30000
                        item.hasEmbeddedPicture = mmr.embeddedPicture != null
                    }
                } catch (e: Throwable) { LogsFor(TAG, item.id, "MediaMetadataRetrieverCompat failure: ${e.message}") }
            }
        } catch (e: Exception) {
            LogsFor(TAG, item.id, "loadMetadata failed: ${file.uri} ${e.message}")
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
    val uriString = feed.downloadUrl!!
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

    val newItems = mutableListOf<Episode>()
    for (i in mediaFiles.indices) {
        Logd(TAG, "updateLocalFeed mediaFiles ${mediaFiles[i].name}")
        val oldItem = realm.query(Episode::class).query("feedId == ${feed.id} AND link == $0", mediaFiles[i].name).first().find()
        val newItem = createEpisode(feed, mediaFiles[i])
        Logd(TAG, "updateLocalFeed oldItem: ${oldItem?.title} url: ${oldItem?.downloadUrl}")
        Logd(TAG, "updateLocalFeed newItem: ${newItem.title} url: ${newItem.downloadUrl}")
        if (oldItem != null) upsertBlk(oldItem) { it.updateFromOther(newItem) }
        newItems.add(newItem)
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
    getFeed(feed.id)?.let { f->
        if (mustReportDownloadSuccessful(f)) {
            logDownloadResult(DownloadResult(f, DownloadError.SUCCESS, true, ""))
            upsert(f) { it.lastUpdateFailed = false }
        }
    }
}
