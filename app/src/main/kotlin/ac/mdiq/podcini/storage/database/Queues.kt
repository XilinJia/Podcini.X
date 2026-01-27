package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.playback.base.InTheatre.VIRTUAL_QUEUE_SIZE
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.savePlayerStatus
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.episodeChangedWhenScreenOff
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.QueueEntry
import ac.mdiq.podcini.storage.model.VIRTUAL_QUEUE_ID
import ac.mdiq.podcini.storage.specs.EnqueueLocation
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent.QueueEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.timeIt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.notifications.UpdatedResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.min
import kotlin.random.Random

private const val TAG: String = "Queues"

const val QUEUE_POSITION_DELTA = 10000L

val queuesFlow = realm.query(PlayQueue::class).sort("name").asFlow()
var queuesLive = listOf<PlayQueue>()
    private set
var virQueue by mutableStateOf(PlayQueue())

var queuesJob: Job? = null

fun initQueues() {
    Logd(TAG, "initQueues called ")
    timeIt("$TAG start of initQueues")
    queuesLive = realm.query(PlayQueue::class).sort("name").find()

    if (queuesJob == null) queuesJob = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        Logd(TAG, "starting queues queuesLive: ${queuesLive.size}")
        if (queuesLive.isEmpty()) {
            realm.write {
                for (i in 0..4) {
                    Logd(TAG, "creating queue id: $i")
                    val q = PlayQueue()
                    if (i == 0) q.name = "Default"
                    else {
                        q.id = i.toLong()
                        q.name = "Queue $i"
                    }
                    copyToRealm(q)
                }
            }
        }
        virQueue = realm.query(PlayQueue::class).query("id == $VIRTUAL_QUEUE_ID").first().find() ?: run {
            val vq = PlayQueue()
            vq.id = VIRTUAL_QUEUE_ID
            vq.name = "Virtual"
            upsertBlk(vq) {}
        }

        queuesFlow.collect { changes: ResultsChange<PlayQueue> ->
            queuesLive = changes.list
            val q = queuesLive.find { it.id == actQueue.id }
            if (q != null) actQueue = q
            Logd(TAG, "queuesLive updated")
            when (changes) {
                is UpdatedResults -> {
                    when {
                        changes.insertions.isNotEmpty() -> {}
                        changes.changes.isNotEmpty() -> {}
                        changes.deletions.isNotEmpty() -> {}
                        else -> {}
                    }
                }
                else -> {}
            }
        }
    }
    timeIt("$TAG start of initQueues")
}

fun cancelQueuesJob() {
    queuesJob?.cancel()
}

var curIndexInActQueue = -1

fun inQueueEpisodeIdSet(): Set<Long> {
    Logd(TAG, "getQueueIDList() called")
    return realm.query(QueueEntry::class).find().map { it.episodeId }.toSet()
}

suspend fun persistOrdered(episodes: List<Episode>, queueEntries: List<QueueEntry>) {
    realm.write {
        for (i in episodes.indices) {
            val e = episodes[i]
            val qe = queueEntries.find { it.episodeId == e.id }
            if (qe == null) {
                Loge(TAG, "Can't find queueEntry for episode: ${e.title}")
                continue
            }
            findLatest(qe)?.position = (i+1) * QUEUE_POSITION_DELTA
        }
    }
}

suspend fun addToAssQueue(episodes: List<Episode>) {
    Logd(TAG, "addToAssQueue( ... ) called")
    val mapByFeed = episodes.groupBy { it.feedId }
    for (en in mapByFeed.entries) {
        val fid = en.key ?: continue
        val f = feedsMap[fid] ?: continue
        val q = f.queue ?: continue
        val episodes = en.value
        addToQueue(episodes, q)
    }
}

suspend fun addToQueue(episodes: List<Episode>, queue: PlayQueue) {
    Logd(TAG, "addToQueue( ... ) called")
    if (queue.isVirtual()) {
        Loge(TAG, "Current queue is virtual, ignored")
        return
    }
    val time = System.currentTimeMillis()
    var i = 0L
    var qes = queue.entries
    realm.write {
        for (e in episodes) {
            if (qes.indexOfFirst { it.episodeId == e.id } >= 0) continue
            val insertPosition = if (queue.autoSort) 0 else {
                qes = queue.entries
                calcPosition(qes, EnqueueLocation.fromCode(queue.enqueueLocation), (if (queue.id == actQueue.id) curEpisode else null))
            }
            Logd(TAG, "addToQueue insertPosition: $insertPosition")
            val qe = QueueEntry().apply {
                id = time + i++
                queueId = queue.id
                episodeId = e.id
                position = insertPosition
            }
            copyToRealm(qe)
        }
    }
    val toSetStat = episodes.filter { it.playState < EpisodeState.QUEUE.code }
    if (toSetStat.isNotEmpty()) setPlayState(EpisodeState.QUEUE, toSetStat, false)
    if (queue.autoSort) queue.sort()
}

suspend fun queueToVirtual(episode: Episode, episodes: List<Episode>, listIdentity: String, sortOrder: EpisodeSortOrder, playInSequence: Boolean = true) {
    Logd(TAG, "queueToVirtual ${virQueue.identity} $listIdentity ${episodes.size}")
    virQueue = queuesLive.find { it.id == VIRTUAL_QUEUE_ID } ?: return
    if (virQueue.identity != listIdentity || !virQueue.contains(episode)) {
        val index = episodes.indexOfFirst { it.id == episode.id }
        if (index >= 0) {
            Logd(TAG, "queueToVirtual index: $index")
            realm.write {
                val qes = query(QueueEntry::class).query("queueId == $VIRTUAL_QUEUE_ID").find()
                delete(qes)
            }
            val eIdsToQueue = episodes.subList(index, min(episodes.size, index + VIRTUAL_QUEUE_SIZE)).map { it.id }
            virQueue = upsert(virQueue) { q ->
                q.identity = listIdentity
                q.playInSequence = playInSequence
                q.sortOrder = sortOrder
            }
            val time = System.currentTimeMillis()
            var i = 0L
            var ip = QUEUE_POSITION_DELTA
            realm.write {
                for (eid in eIdsToQueue) {
                    val qe = QueueEntry().apply {
                        id = time + i++
                        queueId = virQueue.id
                        episodeId = eid
                        position = ip
                    }
                    copyToRealm(qe)
                    ip += QUEUE_POSITION_DELTA
                }
            }
            actQueue = virQueue
            Logt(TAG, "first ${virQueue.size()} episodes are added to the Virtual queue")
        }
    } else actQueue = virQueue
}


suspend fun smartRemoveFromAllQueues(item_: Episode) {
    Logd(TAG, "smartRemoveFromAllQueues: ${item_.title}")
    var item = item_
    val almostEnded = item.hasAlmostEnded()
    if (almostEnded) {
        item = upsert(item) { it.playbackCompletionDate = Date() }
        if (item.playState < EpisodeState.PLAYED.code && !shouldPreserve(item.playState))
            item = upsert(item) { it.setPlayState(EpisodeState.PLAYED) }
    }
    if (item.playState < EpisodeState.SKIPPED.code && !shouldPreserve(item.playState)) {
        val stat = if (item.lastPlayedTime > 0L) EpisodeState.SKIPPED else EpisodeState.PASSED
        setPlayState(stat, listOf(item), resetMediaPosition = false)
    }
    for (q in queuesLive) {
        if (q.id != actQueue.id && q.contains(item)) removeFromQueue(q, listOf(item))
    }
    //        ensure actQueue is last updated
    Logd(TAG, "actQueue: [${actQueue.name}]")
    val qes = actQueue.entries
    if (curEpisode != null) curIndexInActQueue = qes.indexOfFirst { it.episodeId == curEpisode!!.id }
    if (actQueue.size() > 0 && actQueue.contains(item)) removeFromQueue(actQueue, listOf(item))
    else upsertBlk(actQueue) { it.update() }
    actQueue.checkAndFill()
}

fun removeFromAllQueues(episodes: Collection<Episode>, playState: EpisodeState? = null) {
    Logd(TAG, "removeFromAllQueuesSync called ")
    for (q in queuesLive) {
        if (q.id != actQueue.id) removeFromQueue(q, episodes, playState)
    }
    //        ensure actQueue is last updated
    val qes = actQueue.entries
    if (curEpisode != null) curIndexInActQueue = qes.indexOfFirst { it.episodeId == curEpisode!!.id }
    if (actQueue.size() > 0) removeFromQueue(actQueue, episodes, playState)
}

internal fun removeFromQueue(queue_: PlayQueue?, episodes: Collection<Episode>, playState: EpisodeState? = null) {
    Logd(TAG, "removeFromQueue called ")
    val queue = queue_ ?: actQueue
    if (queue.size() == 0) {
        queue.checkAndFill()
        return
    }
    if (episodes.isEmpty()) return
    val removeFromActQueue = mutableListOf<Episode>()
    realm.writeBlocking {
        val qes = query(QueueEntry::class).query("queueId == $0 AND episodeId IN $1", queue.id, episodes.map { it.id }).find()
        if (qes.isNotEmpty()) {
            findLatest(queue)?.let {
                for (qe in qes) {
                    val id = qe.episodeId
                    it.idsBinList.remove(id)
                    it.idsBinList.add(id)
                }
                it.trimBin()
                it.update()
            }
            for (e in episodes) {
                if (qes.indexOfFirst { it.episodeId == e.id } >= 0) {
                    if (playState != null) findLatest(e)?.let { if (it.playState == EpisodeState.QUEUE.code) it.setPlayState(playState) }
                    if (queue.id == actQueue.id) removeFromActQueue.add(e)
                }
            }
            delete(qes)
        }
        val qqes = query(QueueEntry::class).query("queueId == $0", queue.id).find()
        val eps = query(Episode::class).query("id IN $0", qqes.map { it.episodeId }).find()
        if (eps.size < qqes.size) {
            for (qe in qqes) {
                val e = query(Episode::class).query("id == $0", qe.episodeId).find()
                if (e.isEmpty()) delete(qe)
            }
        }
    }
    if (removeFromActQueue.isNotEmpty()) EventFlow.postEvent(QueueEvent.removed(removeFromActQueue))
    queue.checkAndFill()
}

suspend fun removeFromAllQueuesQuiet(episodeIds: List<Long>, updateState: Boolean = true) {
    Logd(TAG, "removeFromAllQueuesQuiet called ")

    suspend fun doit(q: PlayQueue, isActQueue: Boolean = false) {
        if (q.size() == 0) {
            if (isActQueue) upsert(q) { it.update() }
            q.checkAndFill()
            return
        }
        val qes = realm.query(QueueEntry::class).query("queueId == $0 AND episodeId IN $1", q.id, episodeIds).find()
        val idsInQueuesToRemove = qes.map { it.episodeId }
        if (idsInQueuesToRemove.isNotEmpty()) {
            realm.write {
                for (qe in qes) {
                    val qe_ = findLatest(qe)
                    if (qe_ != null) delete (qe_)
                }
            }
            if (updateState) {
                val eList = realm.query(Episode::class).query("id IN $0 AND playState < ${EpisodeState.SKIPPED.code}", idsInQueuesToRemove).find().filter { !shouldPreserve(it.playState) }
                if (eList.isNotEmpty()) setPlayState(EpisodeState.SKIPPED, eList, false)
            }
            val qNew = upsert(q) {
                it.idsBinList.removeAll(idsInQueuesToRemove)
                it.idsBinList.addAll(idsInQueuesToRemove)
                it.trimBin()
                it.update()
            }
            qNew.checkAndFill()
        }
    }

    for (q in queuesLive) {
        if (q.id == actQueue.id) continue
        doit(q)
    }
    //        ensure actQueue is last updated
    doit(actQueue, true)
}

fun getNextInQueue(currentMedia: Episode?): Episode? {
    Logd(TAG, "getNextInQueue called currentMedia: ${currentMedia?.getEpisodeTitle()}")
    if (!actQueue.playInSequence) {
        Logd(TAG, "getNextInQueue(), but follow queue is not enabled.")
        savePlayerStatus(null)
        return null
    }
    val qes = actQueue.entries
    if (qes.isEmpty()) {
        Logd(TAG, "getNextInQueue queue is empty")
        savePlayerStatus(null)
        return null
    }
    var curIndex = if (currentMedia != null) qes.indexOfFirst { it.episodeId == currentMedia.id } else 0
    if (curIndex < 0 && curIndexInActQueue >= 0) {
        curIndex = curIndexInActQueue
        curIndexInActQueue = -1
    }
    Logd(TAG, "getNextInQueue curIndexInQueue: $curIndex ${qes.size}")
    val nextQE = if (curIndex >= 0 && curIndex < qes.size) {
        when {
            qes[curIndex].episodeId != currentMedia?.id -> qes[curIndex]
            qes.size == 1 -> return null
            else -> {
                val j = if (curIndex < qes.size - 1) curIndex + 1 else 0
                Logd(TAG, "getNextInQueue next j: $j")
                qes[j]
            }
        }
    } else qes[0]
    var nextItem = episodeById(nextQE.episodeId) ?: return null
    Logd(TAG, "getNextInQueue nextItem ${nextItem.title}")
    nextItem = checkAndMarkDuplicates(nextItem)
    episodeChangedWhenScreenOff = true
    return nextItem
}

private fun calcPosition(queueEntries: List<QueueEntry>, loc: EnqueueLocation, currentPlaying: Episode?): Long {
    if (queueEntries.isEmpty()) return 0

    fun getCurrentlyPlayingPosition(): Int {
        if (currentPlaying == null) return -1
        val curPlayingItemId = currentPlaying.id
        for (i in queueEntries.indices) if (curPlayingItemId == queueEntries[i].episodeId) return i
        return -1
    }
    val size = queueEntries.size
    return when (loc) {
        EnqueueLocation.BACK -> queueEntries[size-1].position + QUEUE_POSITION_DELTA
        EnqueueLocation.FRONT -> QUEUE_POSITION_DELTA / 2
        EnqueueLocation.AFTER_CURRENTLY_PLAYING -> {
            val curPlayPos = getCurrentlyPlayingPosition()
            if (curPlayPos in 0..<size) queueEntries[curPlayPos].position + QUEUE_POSITION_DELTA / 2 else QUEUE_POSITION_DELTA / 2
        }
        EnqueueLocation.RANDOM -> queueEntries[Random.nextInt(queueEntries.size)].position + QUEUE_POSITION_DELTA / 2
    }
}

