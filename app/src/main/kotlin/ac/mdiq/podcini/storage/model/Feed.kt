package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.playback.base.VideoMode
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.utils.EpisodeSortOrder.Companion.fromCode
import ac.mdiq.podcini.storage.utils.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.utils.VolumeAdaptionSetting.Companion.fromInteger
import ac.mdiq.podcini.storage.utils.EpisodeFilter
import ac.mdiq.podcini.storage.utils.EpisodeSortOrder
import ac.mdiq.podcini.storage.utils.EpisodeState
import ac.mdiq.podcini.storage.utils.FeedAutoDownloadFilter
import ac.mdiq.podcini.storage.utils.FeedFunding
import ac.mdiq.podcini.storage.utils.Rating
import ac.mdiq.podcini.storage.utils.StorageUtils.generateFileName
import ac.mdiq.podcini.storage.utils.VolumeAdaptionSetting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import io.github.xilinjia.krdb.ext.realmListOf
import io.github.xilinjia.krdb.ext.realmSetOf
import io.github.xilinjia.krdb.types.RealmList
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.RealmSet
import io.github.xilinjia.krdb.types.annotations.Ignore
import io.github.xilinjia.krdb.types.annotations.Index
import io.github.xilinjia.krdb.types.annotations.PrimaryKey
import java.util.Date

class Feed : RealmObject {
    @PrimaryKey
    var id: Long = 0L  // increments from Date().time * 100 at time of creation

    @Index
    var identifier: String? = null

    var fileUrl: String? = null

    var downloadUrl: String? = null
//    var downloaded: Boolean = false

    var eigenTitle: String? = null  // title as defined by the feed.

    var customTitle: String? = null     // custom title set by the user.

    var link: String? = null

    var description: String? = null

    var language: String? = null

    var author: String? = null

    var imageUrl: String? = null

    var episodes: RealmList<Episode> = realmListOf()

    // recorded when an episode starts playing when FeedDetails is open
    var lastPlayed: Long = 0

    var lastUpdateTime: Long = 0

    var lastFullUpdateTime: Long = 0

    //Feed type, options are defined in [FeedType].
    var type: String? = null

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

    @Ignore
    var episodeFilter: EpisodeFilter = EpisodeFilter("")
        get() {
            val f = EpisodeFilter(filterString)
            f.titleText = titleFilterText
            f.durationFloor = durationFloor
            f.durationCeiling = durationCeiling
            return f
        }
        set(value) {
            field = value
            filterString = value.propertySet.joinToString()
            titleFilterText = value.titleText
            durationFloor = value.durationFloor
            durationCeiling = value.durationCeiling
        }
    var filterString: String = ""
    var titleFilterText: String = ""
    var durationFloor: Int = 0
    var durationCeiling: Int = Int.MAX_VALUE

    @Ignore
    var sortOrder: EpisodeSortOrder? = null
        get() = fromCode(sortOrderCode)
        set(value) {
            if (value == null) return
            field = value
            sortOrderCode = value.code
        }
    var sortOrderCode: Int = 2     // in EpisodeSortOrder

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

    @Ignore
    var sortInfo: String = ""

    @Ignore
    var isBuilding by mutableStateOf(false)

    // from FeedPreferences
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

    var introSkip: Int = 0
    var endingSkip: Int = 0

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
    val tagsAsString: String
        get() = tags.joinToString(TAG_SEPARATOR)
    var tags: RealmSet<String> = realmSetOf()

    var autoDownload: Boolean = false

    var autoEnqueue: Boolean = false

    @Ignore
    var queue: PlayQueue? = null
        get() = when {
            queueId >= 0 -> realm.query(PlayQueue::class).query("id == $queueId").first().find()
            queueId == -1L -> curQueue
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
    var sortOrderADL: EpisodeSortOrder? = null
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

    // above from FeedPreferences

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

    fun setCustomTitle1(value: String?) {
        customTitle = if (value == null || value == eigenTitle) null else value
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
        if (other.language != null) language = other.language
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
        if (other.language != null && (language == null || language != other.language)) return true
        if (other.author != null && (author == null || author != other.author)) return true
        if (other.paymentLinkList.isNotEmpty() && (paymentLinkList.isEmpty() || paymentLinkList != other.paymentLinkList)) return true
        if (other.isPaged && !this.isPaged) return true
        if (other.nextPageLink != this.nextPageLink) return true
        return false
    }

    fun getTypeAsInt(): Int = FEEDFILETYPE_FEED

    fun addPayment(funding: FeedFunding) {
        paymentLinkList.add(funding)
    }

    fun isSynthetic(): Boolean = id <= MAX_SYNTHETIC_ID

    fun getVirtualQueueItems():  List<Episode> {
        var qString = "feedId == $id AND (playState < ${EpisodeState.SKIPPED.code} OR playState == ${EpisodeState.AGAIN.code} OR playState == ${EpisodeState.FOREVER.code})"
//        TODO: perhaps need to set prefStreamOverDownload for youtube feeds
        if (type != FeedType.YOUTUBE.name && !prefStreamOverDownload) qString += " AND downloaded == true"
        val eList_ = realm.query(Episode::class, qString).query(episodeFilter.queryString()).find().toMutableList()
        if (sortOrder != null) getPermutor(sortOrder!!).reorder(eList_)
        return eList_
    }

    fun getFeedfileName(): String {
        var filename = downloadUrl
        if (!title.isNullOrEmpty()) filename = title
        if (filename == null) return ""
        return "feed-" + generateFileName(filename) + id
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
            fun fromCode(code: Int): AutoDownloadPolicy = enumValues<AutoDownloadPolicy>().firstOrNull { it.code == code } ?: ONLY_NEW
        }
    }

    enum class AutoDeleteAction(val code: Int, val tag: String) {
        GLOBAL(0, "global"),
        ALWAYS(1, "always"),
        NEVER(2, "never");

        companion object {
            fun fromCode(code: Int): AutoDeleteAction = enumValues<AutoDeleteAction>().firstOrNull { it.code == code } ?: NEVER
            fun fromTag(tag: String): AutoDeleteAction = enumValues<AutoDeleteAction>().firstOrNull { it.tag == tag } ?: NEVER
        }
    }

    enum class AudioType(val code: Int, val tag: String) {
        UNKNOWN(C.AUDIO_CONTENT_TYPE_UNKNOWN, "Unknown"),
        SPEECH(C.AUDIO_CONTENT_TYPE_SPEECH, "Speech"),
        MUSIC(C.AUDIO_CONTENT_TYPE_MUSIC, "Music"),
        MOVIE(C.AUDIO_CONTENT_TYPE_MOVIE, "Movie");

        companion object {
            fun fromCode(code: Int): AudioType = enumValues<AudioType>().firstOrNull { it.code == code } ?: SPEECH
            fun fromTag(tag: String): AudioType = enumValues<AudioType>().firstOrNull { it.tag == tag } ?: SPEECH
        }
    }

    enum class AVQuality(val code: Int, val tag: String) {
        GLOBAL(0, "Global"),
        LOW(1, "Low"),
        MEDIUM(5, "Medium"),
        HIGH(10, "High");

        companion object {
            fun fromCode(code: Int): AVQuality = enumValues<AVQuality>().firstOrNull { it.code == code } ?: GLOBAL
            fun fromTag(tag: String): AVQuality = enumValues<AVQuality>().firstOrNull { it.tag == tag } ?: GLOBAL
        }
    }

    companion object {
        val TAG: String = Feed::class.simpleName ?: "Anonymous"

        const val FEEDFILETYPE_FEED: Int = 0
        const val PREFIX_LOCAL_FOLDER: String = "podcini_local:"
        const val PREFIX_GENERATIVE_COVER: String = "podcini_generative_cover:"

        const val MAX_NATURAL_SYNTHETIC_ID: Long = 100L
        const val MAX_SYNTHETIC_ID: Long = 1000L

        const val SPEED_USE_GLOBAL: Float = -1f
        const val TAG_ROOT: String = "#root"
        const val TAG_SEPARATOR: String = "\u001e"

        val FeedAutoDeleteOptions = AutoDeleteAction.entries.map { it.tag }

        fun newId(): Long = Date().time * 100
    }
}
