package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.ext.realmDictionaryOf
import io.github.xilinjia.krdb.ext.realmListOf
import io.github.xilinjia.krdb.ext.realmSetOf
import io.github.xilinjia.krdb.types.RealmDictionary
import io.github.xilinjia.krdb.types.RealmList
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.RealmSet
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

class AppAttribs: RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var prefLastScreen: String = ""

    var prefLastScreenArg: String = ""      // TODO: not really used now?

    var curQueueId: Long = 0L

    var queuesMode: String = ""

    var languages: RealmList<String> = realmListOf()

    var feedTags: RealmList<String> = realmListOf()

    var episodeTags: RealmList<String> = realmListOf()

    var swipeActionsMap: RealmDictionary<String?> = realmDictionaryOf()

    var prefLastFullUpdateTime: Long = 0L

    var feedIdsToRefresh: RealmSet<Long> = realmSetOf()

    var timetable: RealmList<Timer> = realmListOf()

    var statisticsFrom: Long = 0L

    var statisticsUntil: Long = 0L

    constructor()
}
