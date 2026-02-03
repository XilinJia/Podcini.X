package ac.mdiq.podcini.storage.model

import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.Index
import io.github.xilinjia.krdb.types.annotations.PrimaryKey

class QueueEntry : RealmObject {
    @PrimaryKey
    var id: Long = 0L

    @Index
    var queueId: Long = 0L

    @Index
    var episodeId: Long = 0L

    var position: Long = 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueueEntry

        if (id != other.id) return false
        if (queueId != other.queueId) return false
        if (episodeId != other.episodeId) return false
        if (position != other.position) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + queueId.hashCode()
        result = 31 * result + episodeId.hashCode()
        result = 31 * result + position.hashCode()
        return result
    }
}