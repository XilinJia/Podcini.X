package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.types.EmbeddedRealmObject
import io.github.xilinjia.krdb.types.annotations.Index

class Todo : EmbeddedRealmObject {
    @Index
    var id: Long = 0

    var title: String = ""

    var note: String = ""

    var completed: Boolean = false

    var dueTime: Long = 0L

    constructor() {}
}