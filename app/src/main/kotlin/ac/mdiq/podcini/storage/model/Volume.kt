package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.database.deleteFeed
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.utils.Logd
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.notifications.UpdatedResults
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.annotations.Ignore
import io.github.xilinjia.krdb.types.annotations.Index
import io.github.xilinjia.krdb.types.annotations.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class Volume : RealmObject {
    @PrimaryKey
    var id: Long = 0L

    var name: String = ""

    var uriString: String = ""

    @Index
    var parentId: Long = -1L

    var isLocal: Boolean = false

    @Ignore
    val directFeeds: List<Feed>
        get() = getFeedList("volumeId == $id")

    @Ignore
    val allFeeds: List<Feed>
        get() {
            val volumes = this.allChildren()
            val afs = mutableListOf<Feed>()
            var fs = getFeedList("volumeId == $id")
            if (fs.isNotEmpty()) afs.addAll(fs)
            for (v in volumes) {
                fs = getFeedList("volumeId == ${v.id}")
                if (fs.isNotEmpty()) afs.addAll(fs)
            }
            return afs.toList()
        }
}

const val CATALOG_VOLUME_ID_START = -20L

const val ARCHIVED_VOLUME_ID = -10L
const val FROZEN_VOLUME_ID = -5L

var volumes = listOf<Volume>()
private var volumeMonitorJob: Job? = null
fun cancelMonitorVolumes() {
    volumeMonitorJob?.cancel()
    volumeMonitorJob = null
}

fun monitorVolumes(scope: CoroutineScope) {
    if (volumeMonitorJob != null) return

    val feedQuery = realm.query(Volume::class)
    volumeMonitorJob = scope.launch(Dispatchers.IO) {
        feedQuery.asFlow().collect { changes: ResultsChange<Volume> ->
            volumes = changes.list
            //            Logd(TAG, "monitorVolumes volumes size: ${volumes.size}")
            when (changes) {
                is UpdatedResults -> {
                    when {
                        changes.insertions.isNotEmpty() -> {}
                        changes.changes.isNotEmpty() -> {}
                        changes.deletions.isNotEmpty() -> {}
                        else -> {}
                    }
                }
                else -> {
                    // types other than UpdatedResults are not changes -- ignore them
                }
            }
        }
    }
    val archived = realm.query(Volume::class).query("id == $ARCHIVED_VOLUME_ID").first().find() ?: run {
        val v = Volume()
        v.id = ARCHIVED_VOLUME_ID
        v.name = "Archived"
        v.parentId = -1L
        upsertBlk(v) {}
    }
    val frozen = realm.query(Volume::class).query("id == $FROZEN_VOLUME_ID").first().find() ?: run {
        val v = Volume()
        v.id = FROZEN_VOLUME_ID
        v.name = "Frozen"
        v.parentId = -1L
        upsertBlk(v) {}
    }
    val catalog = realm.query(Volume::class).query("id == $CATALOG_VOLUME_ID_START").first().find() ?: run {
        val v = Volume()
        v.id = CATALOG_VOLUME_ID_START
        v.name = "Catelogs"
        v.parentId = -1L
        upsertBlk(v) {}
    }
}

fun Volume.allChildren(): List<Volume> {
    val result = mutableListOf<Volume>()
    val subVolumes = realm.query(Volume::class).query("parentId == $id").find()
    for (child in subVolumes) {
        result.add(child)
        result.addAll(child.allChildren())
    }
    return result
}

suspend fun deleteVolumeTree(volume: Volume) {
    Logd(TAG, "deleteVolumeTree volume: ${volume.name}")
    val feeds_ = realm.query(Feed::class).query("volumeId == ${volume.id}").find()
    for (f in feeds_) {
        val worthyEps = f.getWorthyEpisodes()
        deleteFeed(f.id, worthyEps.isNotEmpty())
    }

    val iterator = realm.query(Volume::class).query("parentId == ${volume.id}").find().iterator()
    while (iterator.hasNext()) deleteVolumeTree(iterator.next())

    realm.writeBlocking {
        val v = findLatest(volume)
        if (v != null) delete(v)
    }
}
