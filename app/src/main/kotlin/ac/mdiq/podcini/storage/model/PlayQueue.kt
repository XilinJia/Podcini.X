package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.database.RealmDB.realm
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.Ignore
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.Date

class PlayQueue : RealmObject {

    @PrimaryKey
    var id: Long = 0L

    var name: String = ""

    var updated: Long = Date().time
        private set

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

//    @Ignore
//    var size by mutableIntStateOf( episodeIds.size )

    fun size() : Int = episodeIds.size

    constructor() {}
}