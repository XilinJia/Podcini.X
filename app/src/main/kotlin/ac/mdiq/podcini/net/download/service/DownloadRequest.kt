package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.net.utils.NetworkUtils.prepareUrl
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Episode.Companion.FEEDFILETYPE_FEEDMEDIA
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.Companion.FEEDFILETYPE_FEED
import android.os.Bundle
import android.os.Parcel

class DownloadRequest private constructor(
        val destination: String?,
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

    private constructor(inVal: Parcel) : this(
        inVal.readString(),
        inVal.readString(),
        inVal.readString(),
        inVal.readLong(),
        inVal.readInt(),
        inVal.readString(),
        nullIfEmpty(inVal.readString()),
        nullIfEmpty(inVal.readString()),
        inVal.readByte() > 0,
        inVal.readBundle(DownloadRequest::class.java.classLoader),
        inVal.readByte() > 0)

//    override fun describeContents(): Int = 0

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
            this.source = if (media.downloadUrl != null) prepareUrl(media.downloadUrl!!) else null
            this.title = media.title ?: media.downloadUrl
            this.feedfileId = media.id
            this.feedfileType = FEEDFILETYPE_FEEDMEDIA
        }
        constructor(destination: String, feed: Feed) {
            this.destination = destination
            this.source = when {
                feed.isLocalFeed -> feed.downloadUrl
                feed.downloadUrl != null -> prepareUrl(feed.downloadUrl!!)
                else -> null
            }
            this.title = feed.getTextIdentifier()
            this.feedfileId = feed.id
            this.feedfileType = FEEDFILETYPE_FEED
            arguments.putInt(REQUEST_ARG_PAGE_NR, feed.pageNr)
        }
        fun withInitiatedByUser(initiatedByUser: Boolean): Builder {
            this.initiatedByUser = initiatedByUser
            return this
        }
        fun setForce(force: Boolean) {
            if (force) lastModified = null
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
        const val REQUEST_ARG_PAGE_NR: String = "page"

        private fun nullIfEmpty(str: String?): String? = if (str.isNullOrEmpty()) null else str

    }
}
