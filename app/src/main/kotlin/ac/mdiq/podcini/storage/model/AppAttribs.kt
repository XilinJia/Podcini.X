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

    var prefLastScreen: String = ""

    var prefLastScreenArg: String = ""      // TODO: not really used now?

    var curQueueId: Long = 0L

    var queuesMode: String = ""

    var langSet: RealmSet<String> = realmSetOf()

    var langsPreferred: RealmSet<String> = realmSetOf()

    var feedTagSet: RealmSet<String> = realmSetOf()

    var episodeTagSet: RealmSet<String> = realmSetOf()

    var swipeActionsMap: RealmDictionary<String?> = realmDictionaryOf()

    var prefLastFullUpdateTime: Long = 0L

    var feedIdsToRefresh: RealmSet<Long> = realmSetOf()

    var timetable: RealmList<Timer> = realmListOf()

    var searchHistory: RealmList<String> = realmListOf()

    var onlineSearchHistory: RealmList<String> = realmListOf()

    var statisticsFrom: Long = 0L

    var statisticsUntil: Long = 0L

    var topChartCountryCode: String = Locale.getDefault().country

    var peerAddress: String = ""

    var transceivePort: Int = 21080

    constructor()
}
