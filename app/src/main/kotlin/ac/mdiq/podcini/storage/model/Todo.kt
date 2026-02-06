package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.types.EmbeddedRealmObject
import io.github.xilinjia.krdb.types.annotations.Index
import kotlinx.serialization.Serializable

class Todo : EmbeddedRealmObject {
    @Index
    var id: Long = 0

    var title: String = ""

    var note: String = ""

    var completed: Boolean = false

    var dueTime: Long = 0L

    constructor() {}
}

@Serializable
data class TodoDTO(
    val id: Long,
    val title: String = "",
    val note: String = "",
    val completed: Boolean = false,
    val dueTime: Long = 0L,
)

fun Todo.toDTO() = TodoDTO(
    id = this.id,
    title = this.title,
    note = this.note,
    completed = this.completed,
    dueTime = this.dueTime
)

fun TodoDTO.toRealm() = Todo().apply {
    id = this@toRealm.id
    title = this@toRealm.title
    note = this@toRealm.note
    completed = this@toRealm.completed
    dueTime = this@toRealm.dueTime
}