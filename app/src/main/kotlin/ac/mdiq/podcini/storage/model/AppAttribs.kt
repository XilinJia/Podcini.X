package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.ext.realmDictionaryOf
import io.github.xilinjia.krdb.ext.realmListOf
import io.github.xilinjia.krdb.ext.realmSetOf
import io.github.xilinjia.krdb.types.RealmDictionary
import io.github.xilinjia.krdb.types.RealmList
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.RealmSet
import io.github.xilinjia.krdb.types.annotations.PrimaryKey
import java.util.Locale

const val SearchHistorySize = 20    // caps both local and online searches

class AppAttribs: RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var name: String = "My Podcini"

    var uniqueId: String = ""

    var restoreLastScreen: Boolean = false
    var prefLastScreen: String = ""

    var curQueueId: Long = 0L

    var queuesMode: String = ""

    var langSet: RealmSet<String> = realmSetOf()

    var langsPreferred: RealmSet<String> = realmSetOf()

    var feedTagSet: RealmSet<String> = realmSetOf()

    var episodeTagSet: RealmSet<String> = realmSetOf()

    var swipeActionsMap: RealmDictionary<String?> = realmDictionaryOf()

    var prefLastFullUpdateTime: Long = 0L

    var feedIdsToRefresh: RealmSet<Long> = realmSetOf()

    var episodeIdsToDownload: RealmSet<Long> = realmSetOf()

    var timetable: RealmList<Timer> = realmListOf()

    var searchHistory: RealmList<String> = realmListOf()

    var onlineSearchHistory: RealmList<String> = realmListOf()

    var statisticsFrom: Long = 0L

    var statisticsUntil: Long = 0L

    var topChartCountryCode: String = Locale.getDefault().country

    var treeRoots: RealmSet<String> = realmSetOf()

    var transceivePort: Int = 21080
    var udpPort: Int = 21088

    constructor()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AppAttribs

        if (id != other.id) return false
        if (restoreLastScreen != other.restoreLastScreen) return false
        if (curQueueId != other.curQueueId) return false
        if (prefLastFullUpdateTime != other.prefLastFullUpdateTime) return false
        if (statisticsFrom != other.statisticsFrom) return false
        if (statisticsUntil != other.statisticsUntil) return false
        if (transceivePort != other.transceivePort) return false
        if (udpPort != other.udpPort) return false
        if (name != other.name) return false
        if (uniqueId != other.uniqueId) return false
        if (prefLastScreen != other.prefLastScreen) return false
        if (queuesMode != other.queuesMode) return false
        if (langSet.size != other.langSet.size) return false
        if (langsPreferred.size != other.langsPreferred.size) return false
        if (feedTagSet.size != other.feedTagSet.size) return false
        if (episodeTagSet.size != other.episodeTagSet.size) return false
        if (swipeActionsMap.size != other.swipeActionsMap.size) return false
        if (feedIdsToRefresh.size != other.feedIdsToRefresh.size) return false
        if (episodeIdsToDownload.size != other.episodeIdsToDownload.size) return false
        if (timetable.size != other.timetable.size) return false
        if (searchHistory.size != other.searchHistory.size) return false
        if (onlineSearchHistory.size != other.onlineSearchHistory.size) return false
        if (topChartCountryCode != other.topChartCountryCode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + restoreLastScreen.hashCode()
        result = 31 * result + curQueueId.hashCode()
        result = 31 * result + prefLastFullUpdateTime.hashCode()
        result = 31 * result + statisticsFrom.hashCode()
        result = 31 * result + statisticsUntil.hashCode()
        result = 31 * result + transceivePort
        result = 31 * result + udpPort
        result = 31 * result + name.hashCode()
        result = 31 * result + uniqueId.hashCode()
        result = 31 * result + prefLastScreen.hashCode()
        result = 31 * result + queuesMode.hashCode()
        result = 31 * result + langSet.size
        result = 31 * result + langsPreferred.size
        result = 31 * result + feedTagSet.size
        result = 31 * result + episodeTagSet.size
        result = 31 * result + swipeActionsMap.size
        result = 31 * result + feedIdsToRefresh.size
        result = 31 * result + episodeIdsToDownload.size
        result = 31 * result + timetable.size
        result = 31 * result + searchHistory.size
        result = 31 * result + onlineSearchHistory.size
        result = 31 * result + topChartCountryCode.hashCode()
        return result
    }
}
