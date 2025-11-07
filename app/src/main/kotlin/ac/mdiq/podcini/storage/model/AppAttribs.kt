package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

class AppAttribs: RealmObject {
    @PrimaryKey
    var id: Long = 0L


    constructor()
}