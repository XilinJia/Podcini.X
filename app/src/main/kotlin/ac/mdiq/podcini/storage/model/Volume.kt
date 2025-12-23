package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

class Volume : RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var name: String = ""

    var uriString: String = ""

    var parentId: Long = -1L
}