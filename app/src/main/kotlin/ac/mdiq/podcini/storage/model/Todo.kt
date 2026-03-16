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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Todo

        if (id != other.id) return false
        if (completed != other.completed) return false
        if (dueTime != other.dueTime) return false
        if (title != other.title) return false
        if (note != other.note) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + completed.hashCode()
        result = 31 * result + dueTime.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + note.hashCode()
        return result
    }
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