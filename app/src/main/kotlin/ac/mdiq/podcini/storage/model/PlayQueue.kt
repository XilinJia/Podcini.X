package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.fromCode
import io.github.xilinjia.krdb.ext.realmListOf
import io.github.xilinjia.krdb.types.RealmList
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.Ignore
import io.github.xilinjia.krdb.types.annotations.PrimaryKey
import java.util.Date

const val VIRTUAL_QUEUE_ID = 100000L

class PlayQueue : RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var name: String = ""

    var identity: String = ""

    var updated: Long = Date().time
        private set

    var enqueueLocation: Int = 0

    var launchAutoEQDlWhenEmpty: Boolean = true

    var autoDownloadEpisodes: Boolean = false

    @Ignore
    var sortOrder: EpisodeSortOrder = EpisodeSortOrder.TIME_IN_QUEUE_OLD_NEW
        get() = fromCode(sortOrderCode)
        set(value) {
            field = value
            sortOrderCode = value.code
        }
    var sortOrderCode: Int = EpisodeSortOrder.TIME_IN_QUEUE_OLD_NEW.code     // in EpisodeSortOrder

    var isLocked: Boolean = true

    @Ignore
    val isSorted: Boolean
        get() = sortOrder != EpisodeSortOrder.TIME_IN_QUEUE_OLD_NEW

    var episodeIds: RealmList<Long> = realmListOf()

    @Ignore
    val episodes: MutableList<Episode> = mutableListOf()
        get() {
            if (field.isEmpty() && episodeIds.isNotEmpty())
                field.addAll(realm.query(Episode::class, "id IN $0", episodeIds).find().sortedBy { episodeIds.indexOf(it.id) })
//            size = episodeIds.size
            return field
        }

    var idsBinList: RealmList<Long> = realmListOf()

    var binLimit: Int = 0

    fun contains(episode: Episode): Boolean = episodeIds.contains(episode.id)

    fun update() {
        updated = Date().time
    }

    fun size() : Int = episodeIds.size

    fun isVirtual(): Boolean = id == VIRTUAL_QUEUE_ID

    constructor() {}
}