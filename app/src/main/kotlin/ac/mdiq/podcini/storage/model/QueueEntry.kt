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
}