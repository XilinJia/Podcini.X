package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.ext.realmDictionaryOf
import io.github.xilinjia.krdb.types.RealmDictionary
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

class FacetsPrefs: RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var prefFacetsCurIndex: Int = 0

    var filtersMap: RealmDictionary<String?> = realmDictionaryOf()

    var sortCodesMap: RealmDictionary<Int?> = realmDictionaryOf()

    var screenMode: String = ""
}