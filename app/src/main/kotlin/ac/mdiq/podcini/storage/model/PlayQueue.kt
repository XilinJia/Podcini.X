package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.automation.autodownloadForQueue
import ac.mdiq.podcini.automation.autoenqueueForQueue
import ac.mdiq.podcini.storage.database.persistOrdered
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.specs.EnqueueLocation
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.fromCode
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.sortPairOf
import io.github.xilinjia.krdb.ext.realmListOf
import io.github.xilinjia.krdb.types.RealmList
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.Ignore
import io.github.xilinjia.krdb.types.annotations.PrimaryKey
import java.util.Date

const val VIRTUAL_QUEUE_ID = 100000L
const val TMP_QUEUE_ID = -1L

class PlayQueue : RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var name: String = ""

    var playInSequence: Boolean = true

    var identity: String = ""

    var updated: Long = Date().time
        private set

    var enqueueLocation: Int = EnqueueLocation.BACK.code

    var launchAutoEQDlWhenEmpty: Boolean = true     // this means to auto-download, enqueue is done anyway

    var autoDownloadEpisodes: Boolean = false       // TODO: need to rethink

    @Ignore
    var sortOrder: EpisodeSortOrder = EpisodeSortOrder.DATE_DESC
        get() = fromCode(sortOrderCode)
        set(value) {
            field = value
            sortOrderCode = value.code
        }
    var sortOrderCode: Int = EpisodeSortOrder.DATE_DESC.code     // in EpisodeSortOrder

    var autoSort: Boolean = false

    var isLocked: Boolean = true

    @Ignore
    val episodes: MutableList<Episode> = mutableListOf()
        get() {
            if (field.isEmpty()) {
                val eids = entries.map { it.episodeId }
                field.addAll(realm.query(Episode::class).query("id IN $0", eids).sort(sortPairOf(sortOrder)).find())
            }
            return field
        }

    @Ignore
    val entries: List<QueueEntry>
        get() = realm.query(QueueEntry::class).query("queueId == $id").sort("position").find()

    var scrollPosition: Int = 0

    var idsBinList: RealmList<Long> = realmListOf()

    var binLimit: Int = 0

    fun contains(episode: Episode): Boolean = realm.query(QueueEntry::class).query("queueId == $id AND episodeId == ${episode.id}").count().find() > 0

    fun update() {
        updated = Date().time
    }

    fun size() : Int = realm.query(QueueEntry::class).query("queueId == $id").count().find().toInt()

    fun isVirtual(): Boolean = id == VIRTUAL_QUEUE_ID

    constructor() {}

    fun trimBin() {
        if (binLimit <= 0) return
        if (idsBinList.size > binLimit * 1.2) {
            val newSize = (0.2 * binLimit).toInt()
            val subList = idsBinList.subList(0, newSize)
            idsBinList.clear()
            idsBinList.addAll(subList)
        }
    }

    suspend fun sort() {
        val queueEntries = entries
        val episodes = realm.query(Episode::class).query("id IN $0", queueEntries.map { it.episodeId }).find().toMutableList()
        getPermutor(sortOrder).reorder(episodes)
        persistOrdered(episodes, queueEntries)
    }

    fun checkAndFill() {
        if (size() == 0 && !isVirtual()) {
            autoenqueueForQueue(this)
            if(launchAutoEQDlWhenEmpty) autodownloadForQueue(this)
        }
    }
}

fun tmpQueue(): PlayQueue = PlayQueue().apply { id = TMP_QUEUE_ID }
