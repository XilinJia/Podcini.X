package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.database.realm
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

class Volume : RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var name: String = ""

    var uriString: String = ""

    var parentId: Long = -1L
}

fun Volume.getAllChildren(): List<Volume> {
    val result = mutableListOf<Volume>()
    val subVolumes = realm.query(Volume::class).query("parentId == $id").find()
    for (child in subVolumes) {
        result.add(child)
        result.addAll(child.getAllChildren())
    }
    return result
}