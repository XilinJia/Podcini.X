package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.net.feed.parser.media.id3.ChapterReader
import ac.mdiq.podcini.net.feed.parser.media.id3.ID3ReaderException
import ac.mdiq.podcini.net.feed.parser.media.vorbis.VorbisCommentChapterReader
import ac.mdiq.podcini.net.feed.parser.media.vorbis.VorbisCommentReaderException
import ac.mdiq.podcini.net.utils.NetworkUtils.isImageDownloadAllowed
import ac.mdiq.podcini.storage.database.feedsMap
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Feed.Companion.TAG_SEPARATOR
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.ChapterStartTimeComparator
import ac.mdiq.podcini.storage.utils.MEDIA_DOWNLOADPATH
import ac.mdiq.podcini.storage.utils.customMediaUriString
import ac.mdiq.podcini.storage.utils.generateFileName
import ac.mdiq.podcini.storage.utils.getDataFolder
import ac.mdiq.podcini.storage.utils.getMimeType
import ac.mdiq.podcini.storage.utils.loadChaptersFromUrl
import ac.mdiq.podcini.storage.utils.merge
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.fullDateTimeString
import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.URLUtil.guessFileName
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.github.xilinjia.krdb.ext.realmListOf
import io.github.xilinjia.krdb.ext.realmSetOf
import io.github.xilinjia.krdb.types.RealmList
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.RealmSet
import io.github.xilinjia.krdb.types.annotations.Ignore
import io.github.xilinjia.krdb.types.annotations.Index
import io.github.xilinjia.krdb.types.annotations.PrimaryKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Request.Builder
import org.apache.commons.io.FilenameUtils.EXTENSION_SEPARATOR
import org.apache.commons.io.FilenameUtils.getExtension
import org.apache.commons.io.input.CountingInputStream
import org.apache.commons.lang3.builder.ToStringBuilder
import org.apache.commons.lang3.builder.ToStringStyle
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.util.Date
import kotlin.math.max

private const val smartMarkAsPlayedPercent: Int = 95

@Stable
class Episode : RealmObject {
    @PrimaryKey
    var id: Long = 0L   // increments from Date().time * 100 at time of creation

    /**
     * The id/guid that can be found in the rss/atom feed. Might not be set, especially in youtube feeds
     */
    @Index
    var identifier: String? = null

    var title: String? = null

    var shortDescription: String? = null

    var description: String? = null

    var transcript: String? = null

    var link: String? = null

    var pubDate: Long = 0

//    @Ignore
//    var feed: Feed? = null
//        get() {
//            if (field == null && feedId != null) field = getFeed(feedId!!)
//            return field
//        }

    @Index
    var feedId: Long? = null

    @Ignore
    var feed: Feed? = null
        get() = if (feedId != null) feedsMap[feedId!!] else null

    // parent in these refers to the original parent of the content (shared)
    var parentTitle: String? = null

    var parentURL: String? = null

    var related: RealmSet<Episode> = realmSetOf()

    var podcastIndexChapterUrl: String? = null

    @set:JvmName("setPlayStateProperty")
    var playState: Int
        private set

    @Ignore
    var playStateSetDate: Date? = null
        get() = field ?: Date(playStateSetTime)
        set(value) {
            field = value?.clone() as? Date
            this.playStateSetTime = value?.time ?: 0L
        }
    var playStateSetTime: Long = 0L

    var paymentLink: String? = null

    /**
     * Returns the image of this item, as specified in the feed.
     * To load the image that can be displayed to the user, use [.getImageLocation],
     * which also considers embedded pictures or the feed picture if no other picture is present.
     */
    var imageUrl: String? = null

    var isAutoDownloadEnabled: Boolean = true

    @Ignore
    val tagsAsString: String
        get() = tags.joinToString(TAG_SEPARATOR)
    var tags: RealmSet<String> = realmSetOf()

    var clips: RealmSet<String> = realmSetOf()

    var marks: RealmSet<Long> = realmSetOf()

    var chapters: RealmList<Chapter> = realmListOf()

    var chaptersLoaded: Boolean = false

    var rating: Int = Rating.UNRATED.code

    // info from youtube
    var viewCount: Int = 0

    var likeCount: Int = 0

    @Ignore
    var isSUPER: Boolean = (rating == Rating.SUPER.code)
        private set

    var comment: String = ""
        private set

    var commentTime: Long = 0L
        private set

    var todos: RealmList<Todo> = realmListOf()

    @Ignore
    val isNew: Boolean
        get() = playState == EpisodeState.NEW.code

    @Ignore
    val isInProgress: Boolean
        get() = position > 0

    /**
     * Returns the value that uniquely identifies this FeedItem. If the
     * itemIdentifier attribute is not null, it will be returned. Else it will
     * try to return the title. If the title is not given, it will use the link
     * of the entry.
     */
    @Ignore
    val identifyingValue: String?
        get() = when {
            !identifier.isNullOrEmpty() -> identifier
            !title.isNullOrEmpty() -> title
            downloadUrl != null -> downloadUrl
            else -> link
        }

    @Ignore
    val isRemote = mutableStateOf(false)

    var fileUrl: String? = null

    var downloadUrl: String? = null

    var downloaded: Boolean = false

    var downloadTime: Long = 0

    var duration: Int = 0    // in milliseconds

    @set:JvmName("setPositionProperty")
    var position: Int = 0 // Current position in file, in milliseconds

    @Ignore
    var lastPlayedDate: Date? = null
        get() = field ?: Date(lastPlayedTime)
        set(value) {
            field = value?.clone() as? Date
            this.lastPlayedTime = value?.time ?: 0
        }
    var lastPlayedTime: Long = 0 // Last time this media was played (in ms)

    var startPosition: Int = -1
    var playedDurationWhenStarted: Int = 0
    var playedDuration: Int = 0 // How many ms of this file have been played

    var timeSpentOnStart: Long = 0 // How many ms of this file have been played in actual time
    var startTime: Long = 0 // time in ms when start playing
    var timeSpent: Long = 0 // How many ms of this file have been played in actual time

    // File size in Byte
    var size: Long = 0L

    var mimeType: String? = ""

    var origFeedTitle: String? = null
    var origFeeddownloadUrl: String? = null
    var origFeedlink: String? = null

    @Ignore
    var playbackCompletionDate: Date? = null
        get() = field ?: Date(playbackCompletionTime)
        set(value) {
            field = value?.clone() as? Date
            this.playbackCompletionTime = value?.time ?: 0
        }
    var playbackCompletionTime: Long = 0

    // if null: unknown, will be checked
    // TODO: what to do with this? can be expensive
    @Ignore
    var hasEmbeddedPicture: Boolean? = null

    @Ignore
    var forceVideo by mutableStateOf(false)

    @Ignore
    var effectUrl = ""

    @Ignore
    var effectMimeType = ""

    @Ignore
    var dueDate: Date? = null
        get() = field ?: Date(repeatTime)
        set(value) {
            field = value?.clone() as? Date
            this.repeatTime = value?.time ?: 0L
        }
    var repeatTime: Long = 0L

    @Ignore
    val isWorthy: Boolean
        get() = (playState != EpisodeState.IGNORED.code && comment != "")
                || rating >= Rating.GOOD.code
                || playState == EpisodeState.AGAIN.code
                || playState == EpisodeState.FOREVER.code
                || playState == EpisodeState.SOON.code
                || playState == EpisodeState.LATER.code
                || clips.isNotEmpty()
                || marks.isNotEmpty()

    constructor() {
        this.playState = EpisodeState.NEW.code
        playStateSetTime = System.currentTimeMillis()
    }

    // used only in LocalFeedUpdater
    constructor(id: Long, title: String?, itemIdentifier: String?, link: String?, pubDate: Date?, state: Int, feed: Feed?) {
        this.id = id
        this.title = title
        this.identifier = itemIdentifier
        this.link = link
        this.pubDate = pubDate?.time ?: 0
        this.playState = state
        playStateSetTime = System.currentTimeMillis()
        if (feed != null) this.feedId = feed.id
        this.feed = feed
    }

    fun updateFromOther(other: Episode, includingState: Boolean = false) {
//        Logd(TAG, "updateFromOther ${other.viewCount} $title")
        if (other.imageUrl != null) this.imageUrl = other.imageUrl
        if (other.title != null) title = other.title
        if (other.description != null) description = other.description
        if (other.link != null) link = other.link
        if (other.pubDate != 0L && other.pubDate != pubDate) pubDate = other.pubDate

        this.downloadUrl = other.downloadUrl

        if (other.size > 0) size = other.size
        // Do not overwrite duration that we measured after downloading
        if (other.duration > 0 && duration <= 0) duration = other.duration
        if (other.mimeType != null) mimeType = other.mimeType

        if (other.paymentLink != null) paymentLink = other.paymentLink
        if (other.chapters.isNotEmpty()) {
            chapters.clear()
            chapters.addAll(other.chapters)
        }
        if (other.podcastIndexChapterUrl != null) podcastIndexChapterUrl = other.podcastIndexChapterUrl
        if (other.viewCount > 0) viewCount = other.viewCount
        if (other.likeCount > 0) likeCount = other.likeCount

        if (includingState) {
            this.rating = other.rating
            this.playState = other.playState
            this.playStateSetTime = other.playStateSetTime
            this.position = other.position
            this.playbackCompletionTime = other.playbackCompletionTime
            this.playedDuration = other.playedDuration
            this.hasEmbeddedPicture = other.hasEmbeddedPicture
            this.lastPlayedTime = other.lastPlayedTime
            this.isAutoDownloadEnabled = other.isAutoDownloadEnabled
        }
    }

    fun imageLocation(forceFeed: Boolean = false): String? {
        return when {
            forceFeed || feed?.useFeedImage() == true -> feed?.imageUrl
            else -> {
                when {
                    imageUrl != null -> imageUrl
                    feed != null -> feed!!.imageUrl
                    hasEmbeddedPicture() -> FILENAME_PREFIX_EMBEDDED_COVER + fileUrl
                    else -> null
                }
            }
        }
    }

    @JvmName("setPlayStateFunction")
    fun setPlayState(state: EpisodeState, resetPosition: Boolean = false) {
        playState = if (state != EpisodeState.UNSPECIFIED) state.code
        else {
            if (playState == EpisodeState.PLAYED.code) EpisodeState.UNPLAYED.code
            else EpisodeState.PLAYED.code
        }
        if (resetPosition || playState == EpisodeState.PLAYED.code || playState == EpisodeState.IGNORED.code) setPosition(0)
        if (state in listOf(EpisodeState.QUEUE, EpisodeState.SKIPPED, EpisodeState.PLAYED, EpisodeState.PASSED, EpisodeState.IGNORED)) isAutoDownloadEnabled = false
        playStateSetTime = System.currentTimeMillis()
    }

    fun isPlayed(): Boolean = playState >= EpisodeState.SKIPPED.code

    fun setPlayed(played: Boolean) {
        playState = if (played) EpisodeState.PLAYED.code else EpisodeState.UNPLAYED.code
        playStateSetTime = System.currentTimeMillis()
    }

    fun hasAlmostEnded(): Boolean = duration > 0 && position >= duration * smartMarkAsPlayedPercent * 0.01

    /**
     * Updates this item's description property if the given argument is longer than the already stored description
     * @param newDescription The new item description, content:encoded, itunes:description, etc.
     */
    fun setDescriptionIfLonger(newDescription: String?) {
        if (newDescription.isNullOrEmpty()) return
        when {
            this.description == null -> this.description = newDescription
            description!!.length < newDescription.length -> this.description = newDescription
        }
    }

    fun setTranscriptIfLonger(newTranscript: String?) {
        if (newTranscript.isNullOrEmpty()) return
        when {
            this.transcript == null -> this.transcript = newTranscript
            transcript!!.length < newTranscript.length -> this.transcript = newTranscript
        }
    }

    fun compileCommentText(): String = (if (comment.isBlank()) "" else comment.trimEnd('\n') + '\n') + fullDateTimeString(System.currentTimeMillis()) + ":\n"

    fun addComment(text: String, addition: Boolean = true) {
        if (addition) {
            comment = if (comment.isBlank()) "" else (comment + "\n")
            commentTime = System.currentTimeMillis()
            comment += fullDateTimeString(commentTime) + ":\n" + text
        } else comment = text
    }

    /**
     * Get the link for the feed item for the purpose of Share. It fallbacks to
     * use the feed's link if the named feed item has no link.
     */
    fun getLinkWithFallback(): String? {
        return when {
            !link.isNullOrBlank() -> link
            !feed?.link.isNullOrEmpty() -> feed!!.link
            else -> null
        }
    }

    fun disableAutoDownload() {
        this.isAutoDownloadEnabled = false
    }

    override fun toString(): String {
        return ToStringBuilder.reflectionToString(this, ToStringStyle.SHORT_PREFIX_STYLE)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Episode

        if (id != other.id) return false
        if (identifier != other.identifier) return false
        if (title != other.title) return false
//        if (description != other.description) return false
//        if (transcript != other.transcript) return false
        if (link != other.link) return false
        if (pubDate != other.pubDate) return false
        if (feedId != other.feedId) return false
        if (podcastIndexChapterUrl != other.podcastIndexChapterUrl) return false
        if (playState != other.playState) return false
//        if (paymentLink != other.paymentLink) return false
        if (imageUrl != other.imageUrl) return false
        if (isAutoDownloadEnabled != other.isAutoDownloadEnabled) return false
        if (tags != other.tags) return false
        if (chapters != other.chapters) return false
        if (rating != other.rating) return false
        if (comment != other.comment) return false
        if (isInProgress != other.isInProgress) return false

        if (fileUrl != other.fileUrl) return false
        if (downloadUrl != other.downloadUrl) return false
        if (downloaded != other.downloaded) return false
        if (downloadTime != other.downloadTime) return false
        if (duration != other.duration) return false
        if (position != other.position) return false
        if (lastPlayedTime != other.lastPlayedTime) return false
        if (playedDuration != other.playedDuration) return false
        if (size != other.size) return false
        if (mimeType != other.mimeType) return false
        if (playbackCompletionDate != other.playbackCompletionDate) return false
        if (playbackCompletionTime != other.playbackCompletionTime) return false
        if (startPosition != other.startPosition) return false
        if (playedDurationWhenStarted != other.playedDurationWhenStarted) return false
        if (hasEmbeddedPicture != other.hasEmbeddedPicture) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (identifier?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
//        result = 31 * result + (description?.hashCode() ?: 0)
//        result = 31 * result + (transcript?.hashCode() ?: 0)
        result = 31 * result + (link?.hashCode() ?: 0)
        result = 31 * result + pubDate.hashCode()
        result = 31 * result + (feedId?.hashCode() ?: 0)
        result = 31 * result + (podcastIndexChapterUrl?.hashCode() ?: 0)
        result = 31 * result + playState
//        result = 31 * result + (paymentLink?.hashCode() ?: 0)
        result = 31 * result + (imageUrl?.hashCode() ?: 0)
        result = 31 * result + isAutoDownloadEnabled.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + chapters.hashCode()
        result = 31 * result + rating.hashCode()
        result = 31 * result + comment.hashCode()
        result = 31 * result + isInProgress.hashCode()

        result = 31 * result + (fileUrl?.hashCode() ?: 0)
        result = 31 * result + (downloadUrl?.hashCode() ?: 0)
        result = 31 * result + downloaded.hashCode()
        result = 31 * result + downloadTime.hashCode()
        result = 31 * result + duration
        result = 31 * result + position
        result = 31 * result + lastPlayedTime.hashCode()
        result = 31 * result + playedDuration
        result = 31 * result + size.hashCode()
        result = 31 * result + (mimeType?.hashCode() ?: 0)
        result = 31 * result + (playbackCompletionDate?.hashCode() ?: 0)
        result = 31 * result + playbackCompletionTime.hashCode()
        result = 31 * result + startPosition
        result = 31 * result + playedDurationWhenStarted
        result = 31 * result + (hasEmbeddedPicture?.hashCode() ?: 0)

        return result
    }

    fun fillMedia(duration: Int, position: Int,
                  size: Long, mimeType: String?, fileUrl: String?, downloadUrl: String?,
                  downloaded: Boolean, playbackCompletionDate: Date?, playedDuration: Int,
                  lastPlayedTime: Long) {
        this.duration = duration
        this.position = position
        this.playedDuration = playedDuration
        this.playedDurationWhenStarted = playedDuration
        this.size = size
        this.mimeType = mimeType
        this.playbackCompletionDate =  playbackCompletionDate?.clone() as? Date
        this.playbackCompletionTime =  playbackCompletionDate?.time ?: 0
        this.lastPlayedTime = lastPlayedTime
        setfileUrlOrNull(fileUrl)
        this.downloadUrl = downloadUrl
        if (downloaded) setIsDownloaded()
        else this.downloaded = false
    }

    fun fillMedia(downloadUrl: String?, size: Long, mimeType: String?) {
        this.size = size
        this.mimeType = mimeType
        setfileUrlOrNull(null)
        this.downloadUrl = downloadUrl
//        Logd(TAG, "fillMedia downloadUrl: $downloadUrl")
    }

    // from EpisodeMedia

    /**
     * Uses mimetype to determine the type of media.
     */
    fun getMediaType(): MediaType {
        return MediaType.fromMimeType(mimeType)
    }

//    fun updateFromOther(other: Episode) {
//        this.downloadUrl = other.downloadUrl
//
//        if (other.size > 0) size = other.size
//        // Do not overwrite duration that we measured after downloading
//        if (other.duration > 0 && duration <= 0) duration = other.duration
//        if (other.mimeType != null) mimeType = other.mimeType
//    }

//    fun compareWithOther(other: Episode): Boolean {
//        if (downloadUrl != other.downloadUrl) return true
//
//        if (other.mimeType != null) {
//            if (mimeType == null || mimeType != other.mimeType) return true
//        }
//        if (other.size > 0 && other.size != size) return true
//        if (other.duration > 0 && duration <= 0) return true
//
//        return false
//    }

    fun setIsDownloaded() {
        downloaded = true
        downloadTime = Date().time
        if (isNew) setPlayed(false)
    }

    fun setfileUrlOrNull(url: String?) {
        fileUrl = url
        if (url.isNullOrBlank()) downloaded = false
    }

    fun setPosition(newPosition: Int) {
        this.position = newPosition
        if (newPosition > 0 && isNew) setPlayed(false)
    }

    fun fileSize(): Long? {
        if (fileUrl == null) return null
        val fileUri = fileUrl!!.toUri()
        return try {
            when (fileUri.scheme) {
                "file" -> {
                    val file = File(fileUri.path ?: return null)
                    if (file.exists()) file.length() else null
                }
                "content" -> {
                    getAppContext().contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (sizeIndex != -1) cursor.getLong(sizeIndex) else null
                        } else null
                    }
                }
                else -> {
                    Loge(TAG, "getFileSize: unsupported uri scheme: $fileUrl")
                    null
                }
            }
        } catch (e: Exception) {
            Logs(TAG, e, "getFileSize failed")
            null
        }
    }

    fun fileExists(): Boolean {
        if (fileUrl == null) {
            Loge(TAG, "fileUrl is null")
            return false
        }
        val fileuri = fileUrl!!.toUri()
        return when (fileuri.scheme) {
            "file" -> {
                val file = File(fileuri.path!!)
                try { file.exists()
                } catch (e: SecurityException) {
                    Loge(TAG, "file can not be accessed $fileuri: ${e.message}")
                    false
                }
            }
            "content" -> {
                try {
                    getAppContext().contentResolver.openFileDescriptor(fileuri, "r")?.close()
                    true
                } catch (e: FileNotFoundException) {
                    Loge(TAG, "file not exist $fileuri: ${e.message}")
                    false
                } catch (e: Exception) {
                    Loge(TAG, "Error checking file existence: ${e.message}")
                    false
                }
            }
            else -> {
                Loge(TAG, "Unsupported URI scheme: ${fileuri.scheme}")
                false
            }
        }
    }

    fun getMediaFileUriString(): String {
        val fileName = getMediafilename()
        Logd(TAG, "getMediaFileUriString: filename: $fileName customMediaUriString: $customMediaUriString")
        val subDirectoryName = generateFileName(feed?.title ?: "NoFeed")
        return if (customMediaUriString.isNotBlank()) {
            val customUri = customMediaUriString.toUri()
            val baseDir = DocumentFile.fromTreeUri(getAppContext(), customUri)
            if (baseDir == null || !baseDir.isDirectory) throw IllegalArgumentException("Invalid tree URI: $customMediaUriString")

            var subDirectory = baseDir.findFile(subDirectoryName)
            Logd(TAG, "getMediaFileUriString subDirectoryName: [$subDirectoryName] ${subDirectory.toString()}")
            if (subDirectory == null) subDirectory = baseDir.createDirectory(subDirectoryName)
            var fileUri: Uri? = null
            if (subDirectory != null) fileUri = subDirectory.findFile(fileName)?.uri ?: subDirectory.createFile(getMimeType(fileName), fileName)?.uri
            fileUri.toString()
        } else {
            val baseDir = getAppContext().getExternalFilesDir("media")
            val subDirectory = File(baseDir, subDirectoryName)
            if (!subDirectory.exists() && !subDirectory.mkdir()) throw IllegalStateException("Failed to create sub-directory: ${subDirectory.absolutePath}")
            val file = File(subDirectory, fileName)
            if (!file.exists() && !file.createNewFile()) throw IllegalStateException("Failed to create file: ${file.absolutePath}")
            Uri.fromFile(file).toString()
        }
    }

    fun getMediafilename(): String {
        var titleBaseFilename = ""
        if (title != null) titleBaseFilename = generateFileName(title!!)
        val urlBaseFilename = guessFileName(downloadUrl, null, mimeType)
        var baseFilename: String
        baseFilename = if (titleBaseFilename != "") titleBaseFilename else urlBaseFilename
        val filenameMaxLength = 220
        if (baseFilename.length > filenameMaxLength) baseFilename = baseFilename.take(filenameMaxLength)
        return (baseFilename + EXTENSION_SEPARATOR + id + EXTENSION_SEPARATOR + getExtension(urlBaseFilename))
    }

    // TODO: for TTS temp file
    fun getMediafilePath(): String {
        val title = feed?.title?:return ""
        val mediaPath = (MEDIA_DOWNLOADPATH + generateFileName(title))
        return getDataFolder(mediaPath).toString() + "/"
    }

    fun isDownloaded(): Boolean {
        val url = fileUrl ?: return false
        when {
            url.startsWith("content://") -> {
                val documentFile = DocumentFile.fromSingleUri(getAppContext(), url.toUri())
                return documentFile != null && documentFile.exists()
            }
            else -> {
                val path = url.toUri().path ?: return false
                return File(path).exists()
            }
        }
    }

    suspend fun fetchMediaSize(persist: Boolean = true, force: Boolean = false) : Long {
        return withContext(Dispatchers.IO) {
            if (!isImageDownloadAllowed) {
                Logt(TAG, "need unrestricted network or allow image on mobile for fetchMediaSize")
                return@withContext -1
            }

            var size_ = CHECKED_ON_SIZE_BUT_UNKNOWN.toLong()
            when {
                downloaded -> {
                    val url = fileUrl
                    if (!url.isNullOrEmpty()) size_ = fileSize() ?: 0
                }
                force || !isSizeSetUnknown() -> {
                    // only query the network if we haven't already checked
                    val url = downloadUrl
                    if (url.isNullOrEmpty()) return@withContext -1

                    val client = getHttpClient()
                    val httpReq: Builder = Builder().url(url).header("Accept-Encoding", "identity").head()
                    try {
                        val response = client.newCall(httpReq.build()).execute()
                        if (response.isSuccessful) {
                            val contentLength = response.header("Content-Length")?:"0"
                            try { size_ = contentLength.toInt().toLong() } catch (e: NumberFormatException) { Logs(TAG, e, "fetchMediaSize failed") }
                        }
                    } catch (e: Exception) {
                        Logs(TAG, e, "fetchMediaSize failed")
                        return@withContext -1  // better luck next time
                    }
                }
            }
            // they didn't tell us the size, but we don't want to keep querying on it
            if (persist) upsert(this@Episode) { it.size = if (size_ <= 0) CHECKED_ON_SIZE_BUT_UNKNOWN.toLong() else size_ }
            size_
        }
    }

    fun getClipFile(clipname: String): File {
        val context = getAppContext()
        val mediaFilesDir = context.getExternalFilesDir("media")?.apply { mkdirs() } ?: File(context.filesDir, "media").apply { mkdirs() }
        return File(mediaFilesDir, "recorded_${id}_$clipname")
    }

    fun isSizeSetUnknown(): Boolean = (CHECKED_ON_SIZE_BUT_UNKNOWN.toLong() == this.size)

    fun hasEmbeddedPicture(): Boolean {
        if (hasEmbeddedPicture == null) checkEmbeddedPicture()
        return hasEmbeddedPicture == true
    }

//    override fun writeToParcel(dest: Parcel, flags: Int) {
//        dest.writeString(id.toString())
//        dest.writeString(if (episode != null) episode!!.id.toString() else "")
//        dest.writeInt(duration)
//        dest.writeInt(position)
//        dest.writeLong(size)
//        dest.writeString(mimeType)
//        dest.writeString(fileUrl)
//        dest.writeString(downloadUrl)
//        dest.writeByte((if (downloaded) 1 else 0).toByte())
//        dest.writeLong(playbackCompletionDate?.time ?: 0)
//        dest.writeInt(playedDuration)
//        dest.writeLong(lastPlayedTime)
//    }

    fun getEpisodeTitle(): String = title ?: identifyingValue ?: "No title"

    /**
     * Returns true if a local file that can be played is available. getFileUrl MUST return a non-null string if this method returns true.
     */
    fun localFileAvailable(): Boolean = downloaded && !fileUrl.isNullOrBlank()

    /**
     * This method should be called every time playback starts on this object.
     * Position held by this EpisodeMedia should be set accurately before a call to this method is made.
     */
    fun setPlaybackStart() {
        Logd(TAG, "setPlaybackStart ${System.currentTimeMillis()} timeSpent: $timeSpent")
        startPosition = max(position, 0)
        playedDurationWhenStarted = playedDuration
        timeSpentOnStart = timeSpent
        startTime = System.currentTimeMillis()
    }

    fun setChapters(chapters_: List<Chapter>) {
        for (c in chapters_) c.episode = this
        chapters.clear()
        chapters.addAll(chapters_)
        chaptersLoaded = true
    }

    fun getCurrentChapterIndex(position: Int): Int {
        if (chapters.isEmpty()) return -1
        for (i in chapters.indices) if (chapters[i].start > position) return i - 1
        return chapters.size - 1
    }

    fun loadChapters(forceRefresh: Boolean) {
        if (chaptersLoaded && !forceRefresh) return
        var chaptersFromDatabase: List<Chapter>? = null
        var chaptersFromPodcastIndex: List<Chapter>? = null
        if (chapters.isNotEmpty()) chaptersFromDatabase = chapters
        if (!podcastIndexChapterUrl.isNullOrEmpty()) chaptersFromPodcastIndex = loadChaptersFromUrl(podcastIndexChapterUrl!!, forceRefresh)

        val chaptersFromMediaFile = loadChaptersFromMediaFile()
        val chaptersMergePhase1 = merge(chaptersFromDatabase, chaptersFromMediaFile)
        val chapters = merge(chaptersMergePhase1, chaptersFromPodcastIndex)
        Logd(TAG, "loadChapters chapters size: ${chapters?.size?:0} ${getEpisodeTitle()}")
        upsertBlk(this) {
            if (chapters == null) it.setChapters(listOf())    // Do not try loading again. There are no chapters.
            else it.setChapters(chapters)
        }
    }

    fun loadChaptersFromMediaFile(): List<Chapter> {
        try {
            openStream().use { inVal ->
                val chapters = readId3ChaptersFrom(inVal)
                if (chapters.isNotEmpty()) return chapters
            }
        } catch (e: IOException) { Logs(TAG, e, "Unable to load ID3 chapters: ")
        } catch (e: ID3ReaderException) { Logs(TAG, e, "Unable to load ID3 chapters: ") }

        try {
            openStream().use { inVal ->
                val chapters = readOggChaptersFromInputStream(inVal)
                if (chapters.isNotEmpty()) return chapters
            }
        } catch (e: IOException) { Logs(TAG, e, "Unable to load vorbis chapters: ")
        } catch (e: VorbisCommentReaderException) { Logs(TAG, e, "Unable to load vorbis chapters: ") }
        return listOf()
    }

    @Throws(IOException::class)
    private fun openStream(): CountingInputStream {
        val context = getAppContext()
        if (localFileAvailable()) {
            if (fileUrl.isNullOrBlank()) throw IOException("No local url")
            val fileuri = fileUrl!!.toUri()
            when (fileuri.scheme) {
                "file" -> {
                    if (fileuri.path.isNullOrBlank()) throw IOException("Local file does not exist")
                    val source = File(fileuri.path!!)
                    if (!source.exists()) throw IOException("Local file does not exist")
                    return CountingInputStream(BufferedInputStream(FileInputStream(source)))
                }
                "content" -> return CountingInputStream(BufferedInputStream(context.contentResolver.openInputStream(fileuri)))
            }
            throw IOException("Local file is not valid")
        } else {
            val streamurl = downloadUrl
            if (streamurl != null && streamurl.startsWith(ContentResolver.SCHEME_CONTENT))
                return CountingInputStream(BufferedInputStream(context.contentResolver.openInputStream(streamurl.toUri())))

            if (streamurl.isNullOrEmpty()) throw IOException("stream url is null of empty")
            val request: Request = Builder().url(streamurl).build()
            val response = getHttpClient().newCall(request).execute()
            if (response.body == null) throw IOException("Body is null")
            return CountingInputStream(BufferedInputStream(response.body.byteStream()))
        }
    }

    @Throws(IOException::class, ID3ReaderException::class)
    private fun readId3ChaptersFrom(inVal: CountingInputStream): List<Chapter> {
        val reader = ChapterReader(inVal)
        reader.readInputStream()
        var chapters = reader.getChapters()
        chapters = chapters.sortedWith(ChapterStartTimeComparator())
        enumerateEmptyChapterTitles(chapters)
        if (!chaptersValid(chapters)) return emptyList()
        return chapters
    }

    @Throws(VorbisCommentReaderException::class)
    private fun readOggChaptersFromInputStream(input: InputStream): List<Chapter> {
        val reader = VorbisCommentChapterReader(BufferedInputStream(input))
        reader.readInputStream()
        var chapters = reader.getChapters()
        chapters = chapters.sortedWith(ChapterStartTimeComparator())
        enumerateEmptyChapterTitles(chapters)
        if (chaptersValid(chapters)) return chapters
        return emptyList()
    }

    private fun enumerateEmptyChapterTitles(chapters: List<Chapter>) {
        for (i in chapters.indices) {
            val c = chapters[i]
            if (c.title == null) c.title = i.toString()
        }
    }

    private fun chaptersValid(chapters: List<Chapter>): Boolean {
        if (chapters.isEmpty()) return false
        for (c in chapters) if (c.start < 0) return false
        return true
    }

//    fun checkEmbeddedPicture(persist: Boolean = true) {
//        if (!localFileAvailable()) hasEmbeddedPicture = false
//        else {
//            var retriever: FFmpegMediaMetadataRetriever? = null
//            try {
//                retriever = FFmpegMediaMetadataRetriever()
//                retriever.setDataSource(fileUrl?.toUri().toString())
//                hasEmbeddedPicture = (retriever.embeddedPicture != null)
//            } catch (e: Exception) { Logs(TAG, e, "checkEmbeddedPicture failed.") } finally { retriever?.release() }
//        }
//        if (persist) upsertBlk(this) {}
//    }

    fun checkEmbeddedPicture(persist: Boolean = true) {
        if (!localFileAvailable()) hasEmbeddedPicture = false
        else {
            // TODO: what to do with this
//            try {
//                MediaMetadataRetrieverCompat().use { mmr ->
//                    mmr.setDataSource(getAppContext(), Uri.parse(fileUrl))
//                    val image = mmr.embeddedPicture
//                    hasEmbeddedPicture = image != null
//                }
//            } catch (e: Exception) {
//                Logs(TAG, e)
//                hasEmbeddedPicture = false
//            }
        }
        // TODO
//        if (persist && episode != null) upsertBlk(episode!!) {}
    }

    /**
     * On SDK<29, this class does not have a close method yet, so the app crashes when using try-with-resources.
     */
    class MediaMetadataRetrieverCompat : MediaMetadataRetriever(), AutoCloseable {
        override fun close() {
            try { release() } catch (e: IOException) { Logs(TAG, e, "MediaMetadataRetriever failed") }
        }
    }

    // above from EpisodeMedia

    companion object {
        val TAG: String = Episode::class.simpleName ?: "Anonymous"

        // from EpisodeMedia
        const val INVALID_TIME: Int = -1
        const val FEEDFILETYPE_FEEDMEDIA: Int = 2
        const val PLAYABLE_TYPE_FEEDMEDIA: Int = 1
        const val FILENAME_PREFIX_EMBEDDED_COVER: String = "metadata-retriever:"
        /**
         * Indicates we've checked on the size of the item via the network
         * and got an invalid response. Using Integer.MIN_VALUE because
         * 1) we'll still check on it in case it gets downloaded (it's <= 0)
         * 2) By default all EpisodeMedia have a size of 0 if we don't know it,
         * so this won't conflict with existing practice.
         */
        private const val CHECKED_ON_SIZE_BUT_UNKNOWN = Int.MIN_VALUE
    }
}
