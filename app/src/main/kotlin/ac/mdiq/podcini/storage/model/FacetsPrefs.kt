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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FacetsPrefs

        if (id != other.id) return false
        if (prefFacetsCurIndex != other.prefFacetsCurIndex) return false
        if (filtersMap.size != other.filtersMap.size) return false
        if (sortCodesMap.size != other.sortCodesMap.size) return false
        if (screenMode != other.screenMode) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + prefFacetsCurIndex
        result = 31 * result + filtersMap.size
        result = 31 * result + sortCodesMap.size
        result = 31 * result + screenMode.hashCode()
        return result
    }
}