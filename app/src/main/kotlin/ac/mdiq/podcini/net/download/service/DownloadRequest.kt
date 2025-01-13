package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.net.utils.NetworkUtils.prepareUrl
import ac.mdiq.podcini.storage.model.Episode

import ac.mdiq.podcini.storage.model.Episode.Companion.FEEDFILETYPE_FEEDMEDIA
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Logd
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable

class DownloadRequest private constructor(
        @JvmField val destination: String?,
        @JvmField val source: String?,
        val title: String?,
        val feedfileId: Long,
        val feedfileType: Int,
        var lastModified: String?,
        var username: String?,
        @JvmField var password: String?,
        private val mediaEnqueued: Boolean,
        @JvmField val arguments: Bundle?,
        private val initiatedByUser: Boolean) : Parcelable {

    var progressPercent: Int = 0
    var soFar: Long = 0
    var size: Long = 0
    private var statusMsg = 0

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
        inVal.readBundle(), // TODO: this may have problem
        inVal.readByte() > 0)

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(destination)
        dest.writeString(source)
        dest.writeString(title)
        dest.writeString(feedfileId.toString())
        dest.writeInt(feedfileType)
        dest.writeString(lastModified)
        // in case of null username/password, still write an empty string
        // (rather than skipping it). Otherwise, unmarshalling  a collection
        // of them from a Parcel (from an Intent extra to submit a request to DownloadService) will fail.
        //
        // see: https://stackoverflow.com/a/22926342
        dest.writeString(username ?: "")
        dest.writeString(password ?: "")
        dest.writeByte(if ((mediaEnqueued)) 1.toByte() else 0)
        dest.writeBundle(arguments)
        dest.writeByte(if (initiatedByUser) 1.toByte() else 0)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is DownloadRequest) return false

        if (if (lastModified != null) lastModified != o.lastModified else o.lastModified != null) return false
        if (feedfileId != o.feedfileId) return false
        if (feedfileType != o.feedfileType) return false
        if (progressPercent != o.progressPercent) return false
        if (size != o.size) return false
        if (soFar != o.soFar) return false
        if (statusMsg != o.statusMsg) return false
        if (destination != o.destination) return false
        if (if (password != null) password != o.password else o.password != null) return false
        if (source != o.source) return false
        if (if (title != null) title != o.title else o.title != null) return false
        if (if (username != null) username != o.username else o.username != null) return false
        if (mediaEnqueued != o.mediaEnqueued) return false
        if (initiatedByUser != o.initiatedByUser) return false
        return true
    }

    override fun hashCode(): Int {
        var result = destination.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (if (username != null) username.hashCode() else 0)
        result = 31 * result + (if (password != null) password.hashCode() else 0)
        result = 31 * result + (if (lastModified != null) lastModified.hashCode() else 0)
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

    fun setStatusMsg(statusMsg: Int) {
        this.statusMsg = statusMsg
    }

    fun setLastModified(lastModified: String?): DownloadRequest {
        Logd("DownloadRequest", "setLastModified: $lastModified")
//        showStackTrace()
        this.lastModified = lastModified
        return this
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
            this.feedfileType = feed.getTypeAsInt()
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

        @JvmField
        val CREATOR: Parcelable.Creator<DownloadRequest> = object : Parcelable.Creator<DownloadRequest> {
            override fun createFromParcel(inVal: Parcel): DownloadRequest {
                return DownloadRequest(inVal)
            }

            override fun newArray(size: Int): Array<DownloadRequest?> {
                return arrayOfNulls(size)
            }
        }
    }
}
