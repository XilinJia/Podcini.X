package ac.mdiq.podcini.storage.model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date

class ShareLog : RealmObject {
    @PrimaryKey
    var id: Long = 0L   // this is the Date

    var url: String? = null

    var title: String? = null

    var author: String? = null

    var type: String? = null

    var status: Int = Status.ERROR.ordinal

    var details: String = ""

    constructor() {}

    constructor(url: String) {
        id = Date().time
        this.url = url
    }

    enum class Type {
        Text,
        YTMedia,
        Podcast
    }

    enum class Status {
        ERROR,
        SUCCESS,
        EXISTING
    }
}