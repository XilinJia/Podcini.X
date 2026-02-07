package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.queuesLive
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.CurrentState.Companion.SPEED_USE_GLOBAL
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.fromCode
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.FeedAutoDownloadFilter
import ac.mdiq.podcini.storage.specs.FeedFunding
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.specs.VolumeAdaptionSetting
import ac.mdiq.podcini.storage.specs.VolumeAdaptionSetting.Companion.fromInteger
import ac.mdiq.podcini.storage.utils.generateFileName
import ac.mdiq.podcini.utils.Logd
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import io.github.xilinjia.krdb.ext.realmSetOf
import io.github.xilinjia.krdb.ext.toRealmList
import io.github.xilinjia.krdb.ext.toRealmSet
import io.github.xilinjia.krdb.types.RealmList
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.RealmSet
import io.github.xilinjia.krdb.types.annotations.Ignore
import io.github.xilinjia.krdb.types.annotations.Index
import io.github.xilinjia.krdb.types.annotations.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.Date

@Stable
class Feed : RealmObject {
    @PrimaryKey
    var id: Long = 0L  // increments from Date().time * 100 at time of creation

    @Index
    var volumeId: Long = -1L

    var identifier: String? = null

    var fileUrl: String? = null

    var downloadUrl: String? = null

    var eigenTitle: String? = null  // title as defined by the feed.

    var customTitle: String? = null     // custom title set by the user.

    var link: String? = null

    var description: String? = null

    var langSet: RealmSet<String> = realmSetOf()

    var preferredLnaguages: RealmSet<String> = realmSetOf()

    var author: String? = null

    var imageUrl: String? = null

    //Feed type, options are defined in [FeedType].
    var type: String? = null

    @Ignore
    var episodes: MutableList<Episode> = mutableListOf()    // used only for new feed

    var episodesCount: Int = 0

    var score: Int = -1000
    var scoreCount: Int = 0

    var scoreUpdated: Long = 0

    var limitEpisodesCount: Int = 0

    // recorded when an episode starts playing when FeedDetails is open
    var lastPlayed: Long = 0

    var lastUpdateTime: Long = 0

    var lastFullUpdateTime: Long = 0

    // String that identifies the last update (adopted from Last-Modified or ETag header).
    var lastUpdate: String? = null

    var lastUpdateFailed = false

    var totleDuration: Long = 0L

    var hasVideoMedia: Boolean = false

    var rating: Int =  Rating.OK.code

    var comment: String = ""

    var commentTime: Long = 0L

    var subscriberCount: Int = 0

    /**
     * Returns the value that uniquely identifies this Feed. If the
     * feedIdentifier attribute is not null, it will be returned. Else it will
     * try to return the title. If the title is not given, it will use the link
     * of the feed.
     */
    @Ignore
    val identifyingValue: String?
        get() = when {
            !identifier.isNullOrEmpty() -> identifier
            !downloadUrl.isNullOrEmpty() -> downloadUrl
            !eigenTitle.isNullOrEmpty() -> eigenTitle
            else -> link
        }

    @Ignore
    var paymentLinkList: MutableList<FeedFunding> = mutableListOf()
        private set
    var payment_link: String? = null

    @Ignore
    val isLocalFeed: Boolean
        get() = downloadUrl?.startsWith(PREFIX_LOCAL_FOLDER) == true

    // ============ filters and sorts    ==========================
    @Ignore
    var episodeFilter: EpisodeFilter = EpisodeFilter("")
        get() {
            val f = EpisodeFilter(filterString, andOr = filterAndOr)
            f.titleText = titleFilterText
            f.durationFloor = durationFloor
            f.durationCeiling = durationCeiling
            return f
        }
        set(value) {
            field = value
            filterString = value.propertySet.joinToString()
            Logd(TAG, "episodeFilter filterString: $filterString")
            filterAndOr = value.andOr
            titleFilterText = value.titleText
            durationFloor = value.durationFloor
            durationCeiling = value.durationCeiling
        }
    var filterString: String = ""
    var titleFilterText: String = ""
    var durationFloor: Int = 0
    var durationCeiling: Int = Int.MAX_VALUE

    var filterAndOr: String = "AND"

    @Ignore
    var episodeSortOrder: EpisodeSortOrder = EpisodeSortOrder.DATE_DESC
        get() = fromCode(sortOrderCode)
        set(value) {
            field = value
            sortOrderCode = value.code
        }
    var sortOrderCode: Int = 2     // in EpisodeSortOrder

    var sortValue: Long = 0L

    var sortInfo: String = ""
    // ============ filters and sorts    ==========================

    @Ignore
    val mostRecentItem: Episode?
        get() = realm.query(Episode::class).query("feedId == $id SORT (pubDate DESC)").first().find()

    @Ignore
    val oldestItem: Episode?
        get() = realm.query(Episode::class).query("feedId == $id SORT (pubDate ASC)").first().find()

    @Ignore
    var title: String?
        get() = if (!customTitle.isNullOrEmpty()) customTitle else eigenTitle
        set(value) {
            this.eigenTitle = value
        }

    var prefActionType: String? = null

    @Ignore
    var isBuilding by mutableStateOf(false)

    var useEpisodeImage: Boolean = false

    var useWideLayout: Boolean = false

    var keepUpdated: Boolean = true

    var username: String? = null
    var password: String? = null

    @Ignore
    var videoModePolicy: VideoMode = VideoMode.NONE
        get() = VideoMode.fromCode(videoMode)
        set(value) {
            field = value
            videoMode = field.code
        }
    var videoMode: Int = VideoMode.NONE.code

    var playSpeed: Float = SPEED_USE_GLOBAL

    var skipSilence: Boolean? = null

    var introSkip: Int = 0
    var endingSkip: Int = 0

    var repeatIntervals: RealmList<Int> = DEFAULT_INTERVALS.toRealmList()     // minutes, hours, days, weeks

    @Ignore
    var autoDeleteAction: AutoDeleteAction = AutoDeleteAction.GLOBAL
        get() = AutoDeleteAction.fromCode(autoDelete)
        set(value) {
            field = value
            autoDelete = field.code
        }
    var autoDelete: Int = AutoDeleteAction.GLOBAL.code

    @Ignore
    var audioTypeSetting: AudioType = AudioType.SPEECH
        get() = AudioType.fromCode(audioType)
        set(value) {
            field = value
            audioType = field.code
        }
    var audioType: Int = AudioType.SPEECH.code

    @Ignore
    var volumeAdaptionSetting: VolumeAdaptionSetting = VolumeAdaptionSetting.OFF
        get() = fromInteger(volumeAdaption)
        set(value) {
            field = value
            volumeAdaption = field.value
        }
    var volumeAdaption: Int = VolumeAdaptionSetting.OFF.value

    @Ignore
    var audioQualitySetting: AVQuality = AVQuality.GLOBAL
        get() = AVQuality.fromCode(audioQuality)
        set(value) {
            field = value
            audioQuality = field.code
        }
    var audioQuality: Int = AVQuality.GLOBAL.code

    @Ignore
    var videoQualitySetting: AVQuality = AVQuality.GLOBAL
        get() = AVQuality.fromCode(videoQuality)
        set(value) {
            field = value
            videoQuality = field.code
        }
    var videoQuality: Int = AVQuality.GLOBAL.code

    var prefStreamOverDownload: Boolean = false

    @Ignore
    val inNormalVolume: Boolean = volumeId >= -1L

    @Ignore
    val tagsAsString: String
        get() = tags.joinToString(TAG_SEPARATOR)
    var tags: RealmSet<String> = realmSetOf()

    // ============= Queue ==============
    @Ignore
    var queue: PlayQueue? = null
        get() = when {
            queueId >= 0 -> queuesLive.find { it.id == queueId }
            queueId == -1L -> actQueue
            queueId == -2L -> null
            else -> null
        }
        set(value) {
            field = value
            queueId = value?.id ?: -1L
        }
    @Ignore
    val queueText: String
        get() = when (queueId) {
            0L -> "Default"
            -1L -> "Active"
            -2L -> "None"
            else -> "Custom"
        }
    @Ignore
    val queueTextExt: String
        get() = when (queueId) {
            -1L -> "Active"
            -2L -> "None"
            else -> queue?.name ?: "Default"
        }
    var queueId: Long = 0L

    var autoAddNewToQueue: Boolean = false
    // ============= Queue ==============


    // ============ auto-download/enqueue ==============
    var autoDownload: Boolean = false
    var autoEnqueue: Boolean = false

    @Ignore
    var episodeFilterADL: EpisodeFilter = EpisodeFilter()
        set(value) {
            field = value
            filterStringADL = value.propertySet.joinToString()
            durationFloorADL = value.durationFloor
            durationCeilingADL = value.durationCeiling
        }
        get() {
            val f = EpisodeFilter(filterStringADL)
            f.durationFloor = durationFloorADL
            f.durationCeiling = durationCeilingADL
            return f
        }
    var filterStringADL: String = ""
    var durationFloorADL: Int = 0
    var durationCeilingADL: Int = Int.MAX_VALUE

    @Ignore
    var episodesSortOrderADL: EpisodeSortOrder? = null
        get() = fromCode(sortOrderCodeADL)
        set(value) {
            if (value == null) return
            field = value
            sortOrderCodeADL = value.code
        }
    var sortOrderCodeADL: Int = 2     // in EpisodeSortOrder

    @Ignore
    var autoDownloadFilter: FeedAutoDownloadFilter? = null
        get() = field ?: FeedAutoDownloadFilter(autoDLInclude, autoDLExclude, autoDLMinDuration, autoDLMaxDuration, markExcludedPlayed)
        set(value) {
            field = value
            autoDLInclude = value?.includeFilterRaw ?: ""
            autoDLExclude = value?.excludeFilterRaw ?: ""
            autoDLMinDuration = value?.minDurationFilter ?: 0
            autoDLMaxDuration = value?.maxDurationFilter ?: 0
            markExcludedPlayed = value?.markExcludedPlayed == true
        }
    var autoDLInclude: String? = ""
    var autoDLExclude: String? = ""
    var autoDLMinDuration: Int = 0
    var autoDLMaxDuration: Int = 0
    var markExcludedPlayed: Boolean = false

    var autoDLSoon: Boolean = false

    var autoDLMaxEpisodes: Int = 3
    var countingPlayed: Boolean = true      // relates to autoDLMaxEpisodes

    @Ignore
    var autoDLPolicy: AutoDownloadPolicy = AutoDownloadPolicy.ONLY_NEW
        get() {
            val value = AutoDownloadPolicy.fromCode(autoDLPolicyCode)
            value.replace = autoDLPolicyReplace
            return value
        }
        set(value) {
            field = value
            autoDLPolicyCode = value.code
            autoDLPolicyReplace = value.replace
        }
    var autoDLPolicyCode: Int = AutoDownloadPolicy.ONLY_NEW.code
    var autoDLPolicyReplace: Boolean = false
    // ============ auto-download/enqueue ==============


    // ========================= TODO: probably not needed
    /**
     * The page number that this feed is on. Only feeds with page number "0" should be stored in the
     * database, feed objects with a higher page number only exist temporarily and should be merged
     * into feeds with page number "0".
     * This attribute's value is not saved in the database
     */
    @Ignore
    var pageNr: Int = 0
    /**
     * True if this is a "paged feed", i.e. there exist other feed files that belong to the same
     * logical feed.
     */
    var isPaged: Boolean = false
    /**
     * Link to the next page of this feed. If this feed object represents a logical feed (i.e. a feed
     * that is saved in the database) this might be null while still being a paged feed.
     */
    var nextPageLink: String? = null
    // ==================================


    constructor() : super()

    /**
     * This constructor is used for requesting a feed download (it must not be used for anything else!). It should NOT be
     * used if the title of the feed is already known. TODO:
     */
    constructor(url: String?, lastUpdate: String?, title: String? = null, username: String? = null, password: String? = null) {
        this.lastUpdate = lastUpdate
        fileUrl = null
        this.downloadUrl = url
        this.eigenTitle = title
        fillPreferences(false, AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, username, password)
    }

    fun fillPreferences(autoDownload: Boolean, autoDeleteAction: AutoDeleteAction,
                        volumeAdaptionSetting: VolumeAdaptionSetting?, username: String?, password: String?) {
        this.autoDownload = autoDownload
        this.autoDeleteAction = autoDeleteAction
        if (volumeAdaptionSetting != null) this.volumeAdaptionSetting = volumeAdaptionSetting
        this.username = username
        this.password = password
        this.autoDelete = autoDeleteAction.code
        this.volumeAdaption = volumeAdaptionSetting?.value ?: 0
    }

    fun freezeFeed(value: Boolean) {
        volumeId = if (value) FROZEN_VOLUME_ID else -1L
    }

    fun getTextIdentifier(): String? {
        return when {
            !customTitle.isNullOrEmpty() -> customTitle
            !eigenTitle.isNullOrEmpty() -> eigenTitle
            else -> downloadUrl
        }
    }

    fun updateFromOther(other: Feed, includingPrefs: Boolean = false) {
        // don't update feed's download_url, we do that manually if redirected
        // see PodciniHttpClient
        if (other.imageUrl != null) this.imageUrl = other.imageUrl
        if (other.eigenTitle != null) eigenTitle = other.eigenTitle
        if (other.identifier != null) identifier = other.identifier
        if (other.link != null) link = other.link
        if (other.description != null) description = other.description
        if (other.author != null) author = other.author
        if (other.paymentLinkList.isNotEmpty()) paymentLinkList = other.paymentLinkList

        // this feed's nextPage might already point to a higher page, so we only update the nextPage value
        // if this feed is not paged and the other feed is.
        if (!this.isPaged && other.isPaged) {
            this.isPaged = other.isPaged
            this.nextPageLink = other.nextPageLink
        }

        if (includingPrefs) {
//            if (this.preferences == null) this.preferences = FeedPreferences(id, false, AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
            this.tags = other.tags
            this.keepUpdated = other.keepUpdated
            this.username = other.username
            this.password = other.password
            this.playSpeed = other.playSpeed
            this.autoDownload = other.autoDownload
            this.autoEnqueue = other.autoEnqueue
        }
    }

    fun differentFrom(other: Feed): Boolean {
        if (other.imageUrl != null && (imageUrl == null || imageUrl != other.imageUrl)) return true
        if (eigenTitle != other.eigenTitle) return true
        if (other.identifier != null && (identifier == null || identifier != other.identifier)) return true
        if (other.link != null && (link == null || link != other.link)) return true
        if (other.description != null && (description == null || description != other.description)) return true
        if (other.author != null && (author == null || author != other.author)) return true
        if (other.paymentLinkList.isNotEmpty() && (paymentLinkList.isEmpty() || paymentLinkList != other.paymentLinkList)) return true
        if (other.isPaged && !this.isPaged) return true
        if (other.nextPageLink != this.nextPageLink) return true
        return false
    }

    fun useFeedImage(): Boolean = !getPref(AppPrefs.prefEpisodeCover, false) || !useEpisodeImage

    fun addPayment(funding: FeedFunding) {
        paymentLinkList.add(funding)
    }

    fun isSynthetic(): Boolean = id <= MAX_SYNTHETIC_ID

    @Ignore
    val isWorthyQuerryStr: String
        get() = "(playState != ${EpisodeState.IGNORED.code} AND comment != '')" +
                "OR rating >= ${Rating.GOOD.code}" +
                "OR playState == ${EpisodeState.AGAIN.code}" +
                "OR playState == ${EpisodeState.FOREVER.code}" +
                "OR playState == ${EpisodeState.SOON.code}" +
                "OR playState == ${EpisodeState.LATER.code}" +
                "OR clips.@count > 0" +
                "OR marks.@count > 0"

    fun getWorthyEpisodes(): List<Episode> {
        return realm.query(Episode::class).query("feedId == $id AND ($isWorthyQuerryStr)").find()
    }

    fun getUnworthyEpisodes(): List<Episode> {
        return realm.query(Episode::class).query("feedId == $id AND !($isWorthyQuerryStr)").find()
    }

    fun getFeedfileName(): String {
        var filename = downloadUrl
        if (!title.isNullOrEmpty()) filename = title
        if (filename == null) return ""
        return "feed-" + generateFileName(filename) + id
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Feed

        if (id != other.id) return false
        if (volumeId != other.volumeId) return false
        if (useEpisodeImage != other.useEpisodeImage) return false
        if (score != other.score) return false
        if (scoreCount != other.scoreCount) return false
        if (scoreUpdated != other.scoreUpdated) return false
        if (limitEpisodesCount != other.limitEpisodesCount) return false
        if (lastPlayed != other.lastPlayed) return false
        if (lastUpdateTime != other.lastUpdateTime) return false
        if (lastFullUpdateTime != other.lastFullUpdateTime) return false
        if (pageNr != other.pageNr) return false
        if (isPaged != other.isPaged) return false
        if (lastUpdateFailed != other.lastUpdateFailed) return false
        if (totleDuration != other.totleDuration) return false
        if (hasVideoMedia != other.hasVideoMedia) return false
        if (rating != other.rating) return false
        if (commentTime != other.commentTime) return false
        if (subscriberCount != other.subscriberCount) return false
        if (durationFloor != other.durationFloor) return false
        if (durationCeiling != other.durationCeiling) return false
        if (sortOrderCode != other.sortOrderCode) return false
        if (sortValue != other.sortValue) return false
        if (useWideLayout != other.useWideLayout) return false
        if (keepUpdated != other.keepUpdated) return false
        if (videoMode != other.videoMode) return false
        if (playSpeed != other.playSpeed) return false
        if (skipSilence != other.skipSilence) return false
        if (introSkip != other.introSkip) return false
        if (endingSkip != other.endingSkip) return false
        if (autoDelete != other.autoDelete) return false
        if (audioType != other.audioType) return false
        if (volumeAdaption != other.volumeAdaption) return false
        if (audioQuality != other.audioQuality) return false
        if (videoQuality != other.videoQuality) return false
        if (prefStreamOverDownload != other.prefStreamOverDownload) return false
        if (autoDownload != other.autoDownload) return false
        if (autoEnqueue != other.autoEnqueue) return false
        if (queueId != other.queueId) return false
        if (autoAddNewToQueue != other.autoAddNewToQueue) return false
        if (durationFloorADL != other.durationFloorADL) return false
        if (durationCeilingADL != other.durationCeilingADL) return false
        if (sortOrderCodeADL != other.sortOrderCodeADL) return false
        if (autoDLMinDuration != other.autoDLMinDuration) return false
        if (autoDLMaxDuration != other.autoDLMaxDuration) return false
        if (markExcludedPlayed != other.markExcludedPlayed) return false
        if (autoDLSoon != other.autoDLSoon) return false
        if (autoDLMaxEpisodes != other.autoDLMaxEpisodes) return false
        if (countingPlayed != other.countingPlayed) return false
        if (autoDLPolicyCode != other.autoDLPolicyCode) return false
        if (autoDLPolicyReplace != other.autoDLPolicyReplace) return false
        if (identifier != other.identifier) return false
        if (fileUrl != other.fileUrl) return false
        if (downloadUrl != other.downloadUrl) return false
        if (eigenTitle != other.eigenTitle) return false
        if (customTitle != other.customTitle) return false
        if (link != other.link) return false
        if (description != other.description) return false
        if (langSet != other.langSet) return false
        if (author != other.author) return false
        if (imageUrl != other.imageUrl) return false
        if (episodes != other.episodes) return false
        if (type != other.type) return false
        if (prefActionType != other.prefActionType) return false
        if (nextPageLink != other.nextPageLink) return false
        if (lastUpdate != other.lastUpdate) return false
        if (comment != other.comment) return false
        if (paymentLinkList != other.paymentLinkList) return false
        if (payment_link != other.payment_link) return false
        if (filterString != other.filterString) return false
        if (titleFilterText != other.titleFilterText) return false
        if (filterAndOr != other.filterAndOr) return false
        if (sortInfo != other.sortInfo) return false
        if (username != other.username) return false
        if (password != other.password) return false
        if (tags != other.tags) return false
        if (repeatIntervals != other.repeatIntervals) return false
        if (filterStringADL != other.filterStringADL) return false
        if (autoDLInclude != other.autoDLInclude) return false
        if (autoDLExclude != other.autoDLExclude) return false
        if (preferredLnaguages != other.preferredLnaguages) return false
        if (isBuilding != other.isBuilding) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + volumeId.hashCode()
        result = 31 * result + useEpisodeImage.hashCode()
        result = 31 * result + score
        result = 31 * result + scoreCount
        result = 31 * result + scoreUpdated.hashCode()
        result = 31 * result + limitEpisodesCount
        result = 31 * result + lastPlayed.hashCode()
        result = 31 * result + lastUpdateTime.hashCode()
        result = 31 * result + lastFullUpdateTime.hashCode()
        result = 31 * result + pageNr
        result = 31 * result + isPaged.hashCode()
        result = 31 * result + lastUpdateFailed.hashCode()
        result = 31 * result + totleDuration.hashCode()
        result = 31 * result + hasVideoMedia.hashCode()
        result = 31 * result + rating
        result = 31 * result + commentTime.hashCode()
        result = 31 * result + subscriberCount
        result = 31 * result + durationFloor
        result = 31 * result + durationCeiling
        result = 31 * result + sortOrderCode
        result = 31 * result + sortValue.hashCode()
        result = 31 * result + useWideLayout.hashCode()
        result = 31 * result + keepUpdated.hashCode()
        result = 31 * result + videoMode
        result = 31 * result + playSpeed.hashCode()
        result = 31 * result + (skipSilence?.hashCode() ?: 0)
        result = 31 * result + introSkip
        result = 31 * result + endingSkip
        result = 31 * result + autoDelete
        result = 31 * result + audioType
        result = 31 * result + volumeAdaption
        result = 31 * result + audioQuality
        result = 31 * result + videoQuality
        result = 31 * result + prefStreamOverDownload.hashCode()
        result = 31 * result + autoDownload.hashCode()
        result = 31 * result + autoEnqueue.hashCode()
        result = 31 * result + queueId.hashCode()
        result = 31 * result + autoAddNewToQueue.hashCode()
        result = 31 * result + durationFloorADL
        result = 31 * result + durationCeilingADL
        result = 31 * result + sortOrderCodeADL
        result = 31 * result + autoDLMinDuration
        result = 31 * result + autoDLMaxDuration
        result = 31 * result + markExcludedPlayed.hashCode()
        result = 31 * result + autoDLSoon.hashCode()
        result = 31 * result + autoDLMaxEpisodes
        result = 31 * result + countingPlayed.hashCode()
        result = 31 * result + autoDLPolicyCode
        result = 31 * result + autoDLPolicyReplace.hashCode()
        result = 31 * result + (identifier?.hashCode() ?: 0)
        result = 31 * result + (fileUrl?.hashCode() ?: 0)
        result = 31 * result + (downloadUrl?.hashCode() ?: 0)
        result = 31 * result + (eigenTitle?.hashCode() ?: 0)
        result = 31 * result + (customTitle?.hashCode() ?: 0)
        result = 31 * result + (link?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + langSet.hashCode()
        result = 31 * result + (author?.hashCode() ?: 0)
        result = 31 * result + (imageUrl?.hashCode() ?: 0)
        result = 31 * result + episodes.hashCode()
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (prefActionType?.hashCode() ?: 0)
        result = 31 * result + (nextPageLink?.hashCode() ?: 0)
        result = 31 * result + (lastUpdate?.hashCode() ?: 0)
        result = 31 * result + comment.hashCode()
        result = 31 * result + paymentLinkList.hashCode()
        result = 31 * result + (payment_link?.hashCode() ?: 0)
        result = 31 * result + filterString.hashCode()
        result = 31 * result + titleFilterText.hashCode()
        result = 31 * result + filterAndOr.hashCode()
        result = 31 * result + sortInfo.hashCode()
        result = 31 * result + (username?.hashCode() ?: 0)
        result = 31 * result + (password?.hashCode() ?: 0)
        result = 31 * result + tags.hashCode()
        result = 31 * result + repeatIntervals.hashCode()
        result = 31 * result + filterStringADL.hashCode()
        result = 31 * result + (autoDLInclude?.hashCode() ?: 0)
        result = 31 * result + (autoDLExclude?.hashCode() ?: 0)
        result = 31 * result + preferredLnaguages.hashCode()
        result = 31 * result + isBuilding.hashCode()
        return result
    }

    enum class FeedType(name: String) {
        RSS("rss"),
        ATOM1("atom"),
        YOUTUBE("YouTube")
    }

    enum class AutoDownloadPolicy(val code: Int, val resId: Int, var replace: Boolean) {
        DISCRETION(-1, R.string.feed_auto_dleq_discretion, false),
        ONLY_NEW(0, R.string.feed_auto_dleq_new, false),
        NEWER(1, R.string.feed_auto_dleq_newer, false),
        OLDER(2, R.string.feed_auto_dleq_older, false),
        FILTER_SORT(4, R.string.feed_auto_dleq_filter_sort, false);

        companion object {
            fun fromCode(code: Int): AutoDownloadPolicy = AutoDownloadPolicy.entries.firstOrNull { it.code == code } ?: ONLY_NEW
        }
    }

    enum class AutoDeleteAction(val code: Int, val tag: String) {
        GLOBAL(0, "global"),
        ALWAYS(1, "always"),
        NEVER(2, "never");

        companion object {
            fun fromCode(code: Int): AutoDeleteAction = AutoDeleteAction.entries.firstOrNull { it.code == code } ?: NEVER
            fun fromTag(tag: String): AutoDeleteAction = AutoDeleteAction.entries.firstOrNull { it.tag == tag } ?: NEVER
        }
    }

    enum class AudioType(val code: Int, val tag: String) {
        UNKNOWN(C.AUDIO_CONTENT_TYPE_UNKNOWN, "Unknown"),
        SPEECH(C.AUDIO_CONTENT_TYPE_SPEECH, "Speech"),
        MUSIC(C.AUDIO_CONTENT_TYPE_MUSIC, "Music"),
        MOVIE(C.AUDIO_CONTENT_TYPE_MOVIE, "Movie");

        companion object {
            fun fromCode(code: Int): AudioType = AudioType.entries.firstOrNull { it.code == code } ?: SPEECH
            fun fromTag(tag: String): AudioType = AudioType.entries.firstOrNull { it.tag == tag } ?: SPEECH
        }
    }

    enum class AVQuality(val code: Int, val tag: String) {
        GLOBAL(0, "Global"),
        LOW(1, "Low"),
        MEDIUM(5, "Medium"),
        HIGH(10, "High");

        companion object {
            fun fromCode(code: Int): AVQuality = AVQuality.entries.firstOrNull { it.code == code } ?: GLOBAL
            fun fromTag(tag: String): AVQuality = AVQuality.entries.firstOrNull { it.tag == tag } ?: GLOBAL
        }
    }

    companion object {
        val TAG: String = Feed::class.simpleName ?: "Anonymous"

        const val FEEDFILETYPE_FEED: Int = 0
        const val PREFIX_LOCAL_FOLDER: String = "podcini_local:"
        const val PREFIX_GENERATIVE_COVER: String = "podcini_generative_cover:"

        const val MAX_NATURAL_SYNTHETIC_ID: Long = 100L
        const val MAX_SYNTHETIC_ID: Long = 1000L

        const val TAG_ROOT: String = "#root"
        const val TAG_SEPARATOR: String = "\u001e"

        val DEFAULT_INTERVALS = listOf(60, 24, 30, 52)
        val INTERVAL_UNITS = listOf(R.string.time_minutes, R.string.time_hours, R.string.time_days, R.string.time_weeks)

        val FeedAutoDeleteOptions = AutoDeleteAction.entries.map { it.tag }

        fun newId(): Long = Date().time * 100

        fun duetime(n: Int, i: Int): Long {
            return System.currentTimeMillis() + when (i) {
                0 -> n * 60000
                1 -> n * 3.6e6
                2 -> n * 8.64e7
                3 -> n * 6.048e8
                else -> 0
            }.toLong()
        }

    }
}

@Serializable
data class FeedDTO(
    val id: Long,
//    val volumeId: Long,
    val fileUrl: String? = null,
    val downloadUrl: String? = null,
    val eigenTitle: String? = null,
    val customTitle: String? = null,
    val link: String? = null,
    val description: String? = null,
    val author: String? = null,
    val imageUrl: String? = null,
    val type: String? = null,

    val limitEpisodesCount: Int = 0,
    val lastPlayed: Long = 0,

    val lastUpdateTime: Long = 0,
    val lastFullUpdateTime: Long = 0,

    val tags: Set<String> = setOf(),
    val rating: Int =  Rating.OK.code,

    val comment: String = "",
    val commentTime: Long = 0L,
    )

fun Feed.toDTO() = FeedDTO(
    id = this.id,
//    volumeId = this.volumeId,
    fileUrl = this.fileUrl,
    downloadUrl = this.downloadUrl,
    eigenTitle = this.eigenTitle,
    customTitle = this.customTitle,
    link = this.link,
    description = this.description,
    author = this.author,
    imageUrl = this.imageUrl,
    type = this.type,
    limitEpisodesCount = this.limitEpisodesCount,
    lastPlayed = this.lastPlayed,
    lastUpdateTime = this.lastUpdateTime,
    lastFullUpdateTime = this.lastFullUpdateTime,
    tags = this.tags.toSet(),
    rating = this.rating,
    comment = this.comment,
    commentTime = this.commentTime
)

fun FeedDTO.toRealm() = Feed().apply {
    id = this@toRealm.id
    val feed = getFeed(id) ?: this

    return upsertBlk(feed) {
//        it.volumeId = this@toRealm.volumeId
        if (it.fileUrl == null) it.fileUrl = this@toRealm.fileUrl
        if (it.downloadUrl == null) it.downloadUrl = this@toRealm.downloadUrl
        if (it.eigenTitle == null) it.eigenTitle = this@toRealm.eigenTitle
        it.customTitle = this@toRealm.customTitle

        if (it.link == null) it.link = this@toRealm.link
        if (it.description == null) it.description = this@toRealm.description
        if (it.author == null) it.author = this@toRealm.author
        if (it.imageUrl == null) it.imageUrl = this@toRealm.imageUrl
        if (it.type == null) it.type = this@toRealm.type
        it.limitEpisodesCount = this@toRealm.limitEpisodesCount
        it.lastPlayed = this@toRealm.lastPlayed
        it.lastUpdateTime = this@toRealm.lastUpdateTime
        it.lastFullUpdateTime = this@toRealm.lastFullUpdateTime
        it.tags = this@toRealm.tags.toRealmSet()
        it.rating = this@toRealm.rating
        it.comment = this@toRealm.comment
        it.commentTime = this@toRealm.commentTime
    }
}

@Serializable
data class ComboPackage(
    val feed: FeedDTO,
    val episodes: List<EpisodeDTO>
)