package ac.mdiq.podcini.net.download

import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.cacheDir
import ac.mdiq.podcini.storage.utils.div
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import android.os.Bundle

enum class RequestTye { FEED, FEEDMEDIA }

class DownloadRequest private constructor(
    var destination: String,
    val source: String?,
    val title: String?,
    val feedfileId: Long,
    val feedfileType: Int,
    var lastModified: String?,
    var username: String?,
    var password: String?,
    private val mediaEnqueued: Boolean,
    val arguments: Bundle?,
    private val initiatedByUser: Boolean) {

    var progressPercent: Int = 0
    var soFar: Long = 0
    var size: Long = 0
    var statusMsg = 0

    private constructor(builder: Builder) : this(
        builder.destination,
        builder.source,
        builder.title,
        builder.feedfileId,
        builder.feedfileType,
        builder.lastModified,
        builder.username,
        builder.password,
        false,
        builder.arguments,
        builder.initiatedByUser)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DownloadRequest) return false

        if (if (lastModified != null) lastModified != other.lastModified else other.lastModified != null) return false
        if (feedfileId != other.feedfileId) return false
        if (feedfileType != other.feedfileType) return false
        if (progressPercent != other.progressPercent) return false
        if (size != other.size) return false
        if (soFar != other.soFar) return false
        if (statusMsg != other.statusMsg) return false
        if (destination != other.destination) return false
        if (if (password != null) password != other.password else other.password != null) return false
        if (source != other.source) return false
        if (if (title != null) title != other.title else other.title != null) return false
        if (if (username != null) username != other.username else other.username != null) return false
        if (mediaEnqueued != other.mediaEnqueued) return false
        if (initiatedByUser != other.initiatedByUser) return false
        return true
    }

    override fun hashCode(): Int {
        var result = destination.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (username?.hashCode() ?: 0)
        result = 31 * result + (password?.hashCode() ?: 0)
        result = 31 * result + (lastModified?.hashCode() ?: 0)
        result = 31 * result + (feedfileId xor (feedfileId ushr 32)).toInt()
        result = 31 * result + feedfileType
        result = 31 * result + arguments.hashCode()
        result = 31 * result + progressPercent
        result = 31 * result + (soFar xor (soFar ushr 32)).toInt()
        result = 31 * result + (size xor (size ushr 32)).toInt()
        result = 31 * result + statusMsg
        result = 31 * result + (if (mediaEnqueued) 1 else 0)
        return result
    }

    suspend fun ensureMediaFileExists() {
        val destinationPath = destination
        Logd(TAG, "ensureMediaFileExists destinationUri: $destinationPath ")
        var file = destinationPath.toUF()
        if (!file.exists()) file = file.createFile()
        if (!file.exists()) Loge(TAG, "ensureMediaFileExists no: ${file.absPath}")
        Logd(TAG, "ensureMediaFileExists request.destination: $destination")
        Logd(TAG, "ensureMediaFileExists file.absPath: ${file.absPath}")
        destination = file.absPath
    }

    class Builder {
        internal val destination: String
        var source: String?
        val title: String?
        var username: String? = null
        var password: String? = null
        var lastModified: String? = null
        val feedfileId: Long
        val feedfileType: Int
        val arguments: Bundle = Bundle()
        var initiatedByUser: Boolean = true

        constructor(destination: String, media: Episode) {
            this.destination = destination
            this.source = if (media.downloadUrl != null) NetworkUtils.prepareUrl(media.downloadUrl!!) else null
            this.title = media.title ?: media.downloadUrl
            this.feedfileId = media.id
            this.feedfileType = RequestTye.FEEDMEDIA.ordinal
        }
        constructor(destination: String, feed: Feed) {
            this.destination = destination
            this.source = when {
                feed.isLocalFeed -> feed.downloadUrl
                feed.downloadUrl != null -> NetworkUtils.prepareUrl(feed.downloadUrl!!)
                else -> null
            }
            this.title = feed.getTextIdentifier()
            this.feedfileId = feed.id
            this.feedfileType = RequestTye.FEED.ordinal
            arguments.putInt(REQUEST_ARG_PAGE_NR, feed.pageNr)
        }
        fun withInitiatedByUser(initiatedByUser: Boolean): Builder {
            this.initiatedByUser = initiatedByUser
            return this
        }
        fun lastModified(lastModified: String?): Builder {
            this.lastModified = lastModified
            return this
        }
        fun withAuthentication(username: String?, password: String?): Builder {
            this.username = username
            this.password = password
            return this
        }
        fun build(): DownloadRequest = DownloadRequest(this)
    }

    companion object {
        private const val TAG = "DownloadRequest"
        const val REQUEST_ARG_PAGE_NR: String = "page"

        suspend fun requestFor(feed: Feed): Builder {
            val dest = cacheDir / feed.getFeedfileName()
            if (dest.exists()) runOnIOScope { dest.delete() }
            Logd(TAG, "requestFor download feed from url " + feed.downloadUrl)
            val username = feed.username
            val password = feed.password
            return Builder(dest.absPath, feed).withAuthentication(username, password).lastModified(feed.lastUpdate)
        }

        suspend fun requestFor(media: Episode): Builder {
            Logd(TAG, "requestFor: ${media.fileUrl} ${media.title}")
            val destUriString = try { media.getMediaFileUriString() } catch (e: Throwable ) {
                Logs(TAG, e, "destUriString is invalid")
                ""
            }
            Logd(TAG, "requestFor destUriString: $destUriString")
            if (destUriString.isBlank()) Loge(TAG, "destUriString is empty")
            val feed = media.feed
            val username = feed?.username
            val password = feed?.password
            return Builder(destUriString, media).withAuthentication(username, password)
        }
    }
}