package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.ext.realmDictionaryOf
import io.github.xilinjia.krdb.ext.realmSetOf
import io.github.xilinjia.krdb.types.RealmDictionary
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.RealmSet
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

class AppAttribs: RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var curQueueId: Long = 0L

    var swipeActionsMap: RealmDictionary<String?> = realmDictionaryOf()


    constructor()
}
