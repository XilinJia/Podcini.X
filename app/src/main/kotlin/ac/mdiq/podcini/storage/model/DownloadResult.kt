package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.download.DownloadError.Companion.fromCode
import ac.mdiq.podcini.net.download.RequestTye
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.utils.nowInMillis
import ac.mdiq.podcini.utils.Logd
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.Ignore
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

class DownloadResult : RealmObject {
    /**
     * A human-readable string which is shown to the user so that he can
     * identify the download. Should be the title of the item/feed/media or the
     * URL if the download has no other title.
     */
    var title: String
    var feedfileId: Long
    /**
     * Is used to determine the type of the feedfile even if the feedfile does
     * not exist anymore. The value should be FEEDFILETYPE_FEED,
     * FEEDFILETYPE_FEEDIMAGE or FEEDFILETYPE_FEEDMEDIA
     */
    var feedfileType: Int
    var isSuccessful: Boolean

    @Ignore var reason: DownloadError? = DownloadError.ERROR_NOT_FOUND
        get() = fromCode(reasonCode)
        set(value) {
            field = value
            reasonCode = field?.code ?: DownloadError.ERROR_NOT_FOUND.code
        }
    var reasonCode: Int = 0

    var completionTime: Long = 0L
    /**
     * A message which can be presented to the user to give more information.
     * Should be null if Download was successful.
     */
    var reasonDetailed: String

    @PrimaryKey
    var id: Long = 0L
        private set

    constructor(feedId: Long, title: String, reason: DownloadError?, successful: Boolean, reasonDetailed: String, feedfileType: Int = RequestTye.FEED.ordinal, completionDate: Long = nowInMillis()) {
        this.title = title
        this.feedfileId = feedId
        this.isSuccessful = successful
        this.feedfileType = feedfileType
        this.reason = reason
        this.completionTime = completionDate
        this.reasonDetailed = reasonDetailed
    }

    constructor() : this(0L, "", DownloadError.ERROR_NOT_FOUND, false, "") {}

    override fun toString(): String {
        return ("DownloadStatus [id=$id, title=$title, reason=$reason, reasonDetailed=$reasonDetailed, successful=$isSuccessful, completionDate=$completionTime, feedfileId=$feedfileId, feedfileType=$feedfileType]")
    }

    fun setId() {
        if (idCounter < 0) idCounter = nowInMillis()
        id = idCounter++
    }

    fun setSuccessful() {
        this.isSuccessful = true
        this.reason = DownloadError.SUCCESS
    }

    fun setFailed(reason: DownloadError, reasonDetailed: String) {
        this.isSuccessful = false
        this.reason = reason
        this.reasonDetailed = reasonDetailed
    }

    fun setCancelled() {
        this.isSuccessful = false
        this.reason = DownloadError.ERROR_DOWNLOAD_CANCELLED
    }

    companion object {
        private const val TAG = "DownloadResult"
        /**
         * Downloaders should use this constant for the size attribute if necessary so that the listadapters etc. can react properly.
         */
        const val SIZE_UNKNOWN: Int = -1

        var idCounter: Long = -1

        fun getFeedDownloadLog(feedId: Long): List<DownloadResult> {
            Logd(TAG, "getFeedDownloadLog() called with: $feedId")
            val dlog = realm.query(DownloadResult::class).query("feedfileId == $0", feedId).find().toMutableList()
            dlog.sortWith { lhs, rhs ->  (rhs.completionTime - lhs.completionTime).toInt() }
            return realm.copyFromRealm(dlog)
        }

        fun addDownloadStatus(status: DownloadResult) {
            Logd(TAG, "addDownloadStatus called")
            runOnIOScope {
                if (status.id == 0L) status.setId()
                upsert(status) {}
            }
        }
    }
}