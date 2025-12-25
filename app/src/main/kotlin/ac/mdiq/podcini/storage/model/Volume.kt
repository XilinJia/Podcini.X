package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.storage.database.deleteFeed
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.runOnIOScope
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

fun deleteVolumeTree(volume: Volume) {
    Logd(TAG, "deleteVolumeTree volume: ${volume.name}")
    val feeds_ = realm.query(Feed::class).query("volumeId == ${volume.id}").find()
    for (f in feeds_) runOnIOScope { deleteFeed(f.id) }

    val iterator = realm.query(Volume::class).query("parentId == ${volume.id}").find().iterator()
    while (iterator.hasNext()) deleteVolumeTree(iterator.next())

    realm.writeBlocking {
        val v = findLatest(volume)
        if (v != null) delete(v)
    }
}
