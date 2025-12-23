package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.automation.autodownloadForQueue
import ac.mdiq.podcini.automation.autoenqueueForQueue
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.notifications.UpdatedResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.min
import kotlin.random.Random

private const val TAG: String = "Queues"

const val QUEUE_POSITION_DELTA = 10000L

val queuesFlow = realm.query(PlayQueue::class).sort("name").asFlow()
var queues = realm.query(PlayQueue::class).sort("name").find()
    private set

val queuesJob = CoroutineScope(Dispatchers.Default).launch {
    queuesFlow.collect { changes: ResultsChange<PlayQueue> ->
        queues = changes.list
        Logd(TAG, "queues updated")
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

fun cancelQueuesJob() {
    queuesJob.cancel()
}

var virQueue by mutableStateOf(realm.query(PlayQueue::class).query("id == $VIRTUAL_QUEUE_ID").first().find() ?:
run {
    val vq = PlayQueue()
    vq.id = VIRTUAL_QUEUE_ID
    vq.name = "Virtual"
    upsertBlk(vq) {}
})

var curIndexInActQueue = -1

fun inQueueEpisodeIdSet(): Set<Long> {
    Logd(TAG, "getQueueIDList() called")
    return realm.query(QueueEntry::class).find().map { it.episodeId }.toSet()
}

/**
 * Appends Episode objects to the end of the queue. The 'read'-attribute of all episodes will be set to true.
 * If a Episode is already in the queue, the Episode will not change its position in the queue.
 * @param episodes               the Episode objects that should be added to the queue.
 */
suspend fun addToActQueue(episodes: List<Episode>) {
    Logd(TAG, "addToActQueue( ... ) called")
    if (episodes.isEmpty()) return
    val queue = actQueue
    if (queue.isVirtual()) {
        Loge(TAG, "Active queue is virtual, ignored")
        return
    }

    var queueModified = false
    val setInQueue = mutableListOf<Episode>()
    val qes = queueEntriesOf(queue)
    var insertPosition = calcPosition(qes, EnqueueLocation.fromCode(queue.enqueueLocation), curEpisode)

    for (episode in episodes) {
        if (realm.query(QueueEntry::class).query("queueId == $0 AND episodeId == $1", queue.id, episode.id).count().find() > 0) continue
        val qe = QueueEntry()
        qe.id = System.currentTimeMillis()
        upsert(qe) {
            it.queueId = queue.id
            it.episodeId = episode.id
            it.position = insertPosition
        }
        queueModified = true
        if (episode.playState < EpisodeState.QUEUE.code) setInQueue.add(episode)
        insertPosition += QUEUE_POSITION_DELTA
    }
    if (queueModified) {
        if (queue.id == actQueue.id) actQueue = queue
        for (episode in setInQueue) setPlayState(EpisodeState.QUEUE, episode, false)
    }
}

fun queueEntriesOf(queue: PlayQueue): List<QueueEntry> {
    return realm.query(QueueEntry::class).query("queueId == ${queue.id}").sort("position").find()
}

suspend fun addToAssOrActQueue(episode: Episode, queue_: PlayQueue? = null) {
    Logd(TAG, "addToQueueSync( ... ) called")
    var queue = queue_ ?: episode.feed?.queue ?: actQueue
    if (queue.isVirtual()) {
        Loge(TAG, "Current queue is virtual, ignored")
        return
    }
    queue = realm.query(PlayQueue::class).query("id == ${queue.id}").first().find() ?: return

    if (realm.query(QueueEntry::class).query("queueId == $0 AND episodeId == $1", queue.id, episode.id).count().find() > 0) return

    val qes = queueEntriesOf(queue)
    val currentlyPlaying = if (queue.id == actQueue.id) curEpisode else null
    val insertPosition = calcPosition(qes, EnqueueLocation.fromCode(queue.enqueueLocation), currentlyPlaying)
    Logd(TAG, "addToQueueSync insertPosition: $insertPosition")
    val qe = QueueEntry()
    qe.id = System.currentTimeMillis()
    upsert(qe) {
        it.queueId = queue.id
        it.episodeId = episode.id
        it.position = insertPosition
    }
    if (episode.playState < EpisodeState.QUEUE.code) setPlayState(EpisodeState.QUEUE, episode, false)
//    if (queue.id == actQueue.id) actQueue = queueNew
}

suspend fun queueToVirtual(episode: Episode, episodes: List<Episode>, listIdentity: String, sortOrder: EpisodeSortOrder, playInSequence: Boolean = true) {
    Logd(TAG, "queueToVirtual ${virQueue.identity} $listIdentity ${episodes.size}")
    virQueue = realm.query(PlayQueue::class).query("id == $VIRTUAL_QUEUE_ID").first().find() ?: return
    if (virQueue.identity != listIdentity) {
        if (realm.query(QueueEntry::class).query("queueId == $0 AND episodeId == $1", virQueue.id, episode.id).count().find() > 0) {
            Logd(TAG, "VirQueue has the episode, ignore")
            actQueue = virQueue
            return
        }
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
            var i = 1L
            for (eid in eIdsToQueue) {
                realm.write {
                    val qe = QueueEntry()
                    qe.let {
                        it.id = System.currentTimeMillis()
                        it.queueId = virQueue.id
                        it.episodeId = eid
                        it.position = i
                    }
                    copyToRealm(qe)
                }
                i += QUEUE_POSITION_DELTA
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
    if (almostEnded && item.playState < EpisodeState.PLAYED.code && !stateToPreserve(item.playState)) item = setPlayState(EpisodeState.PLAYED, item, resetMediaPosition = true, removeFromQueue = false)
    if (almostEnded) item = upsert(item) { it.playbackCompletionDate = Date() }
    if (item.playState < EpisodeState.SKIPPED.code && !stateToPreserve(item.playState)) {
        val stat = if (item.lastPlayedTime > 0L) EpisodeState.SKIPPED else EpisodeState.PASSED
        item = setPlayState(stat, item, resetMediaPosition = false, removeFromQueue = false)
    }
    for (q in queues) {
        if (q.id != actQueue.id && realm.query(QueueEntry::class).query("queueId == $0 AND episodeId == $1", q.id, item.id).count().find() > 0) removeFromQueue(q, listOf(item))
    }
    //        ensure actQueue is last updated
    Logd(TAG, "actQueue: [${actQueue.name}]")
    val qes = queueEntriesOf(actQueue)
    if (curEpisode != null) curIndexInActQueue = qes.indexOfFirst { it.episodeId == curEpisode!!.id }
    if (actQueue.size() > 0 && realm.query(QueueEntry::class).query("queueId == $0 AND episodeId == $1", actQueue.id, item.id).count().find() > 0) removeFromQueue(actQueue, listOf(item))
    else upsertBlk(actQueue) { it.update() }
    if (actQueue.size() == 0 && !actQueue.isVirtual()) {
        autoenqueueForQueue(actQueue)
        if(actQueue.launchAutoEQDlWhenEmpty) autodownloadForQueue(getAppContext(), actQueue)
    }
}

fun trimBin(queue: PlayQueue) {
    if (queue.binLimit <= 0) return
    if (queue.idsBinList.size > queue.binLimit * 1.2) {
        val newSize = (0.2 * queue.binLimit).toInt()
        val subList = queue.idsBinList.subList(0, newSize)
        queue.idsBinList.clear()
        queue.idsBinList.addAll(subList)
    }
}

fun removeFromAllQueues(episodes: Collection<Episode>, playState: EpisodeState? = null) {
    Logd(TAG, "removeFromAllQueuesSync called ")
    for (e in episodes) {
        for (q in queues) {
            if (q.id != actQueue.id && realm.query(QueueEntry::class).query("queueId == $0 AND episodeId == $1", q.id, e.id).count().find() > 0) removeFromQueue(q, listOf(e), playState)
        }
    }
    //        ensure actQueue is last updated
    val qes = queueEntriesOf(actQueue)
    for (e in episodes) {
        if (curEpisode != null && e.id == curEpisode!!.id) curIndexInActQueue = qes.indexOfFirst { it.episodeId == curEpisode!!.id }
        if (actQueue.size() > 0 && realm.query(QueueEntry::class).query("queueId == $0 AND episodeId == $1", actQueue.id, e.id).count().find() > 0) removeFromQueue(actQueue, listOf(e), playState)
    }
    upsertBlk(actQueue) { it.update() }
    if (actQueue.size() == 0 && !actQueue.isVirtual()) {
        autoenqueueForQueue(actQueue)
        if(actQueue.launchAutoEQDlWhenEmpty) autodownloadForQueue(getAppContext(), actQueue)
    }
}

/**
 * @param queue_    if null, use actQueue
 */
internal fun removeFromQueue(queue_: PlayQueue?, episodes: Collection<Episode>, playState: EpisodeState? = null) {
    Logd(TAG, "removeFromQueue called ")
    if (episodes.isEmpty()) return
    val queue = queue_ ?: actQueue
    if (queue.size() == 0) {
        if (!queue.isVirtual()) {
            autoenqueueForQueue(queue)
            if (queue.launchAutoEQDlWhenEmpty) autodownloadForQueue(getAppContext(), queue)
        }
        return
    }
    val removeFromActQueue = mutableListOf<Episode>()
    val indicesToRemove: MutableList<Int> = mutableListOf()
    val qItems = queue.episodes.toMutableList()
    val eList = episodes.toList()
    realm.writeBlocking {
        val qes = query(QueueEntry::class).query("queueId == $0 AND episodeId IN $1", queue.id, episodes.map { it.id }).find()
        delete(qes)
    }
    for (e in eList) {
        val i = qItems.indexWithId(e.id)
        if (i >= 0) {
            indicesToRemove.add(i)
            upsertBlk(e) {
                if (playState != null && it.playState == EpisodeState.QUEUE.code) it.setPlayState(playState)
            }
            if (queue.id == actQueue.id) removeFromActQueue.add(e)
        }
    }
    if (indicesToRemove.isNotEmpty()) {
        val queueNew = upsertBlk(queue) {
            for (i in indicesToRemove.indices.reversed()) {
                val id = qItems[indicesToRemove[i]].id
                it.idsBinList.remove(id)
                it.idsBinList.add(id)
            }
            trimBin(it)
            it.update()
        }
        EventFlow.postEvent(QueueEvent.removed(removeFromActQueue))
        if (queueNew.size() == 0 && !queueNew.isVirtual()) {
            autoenqueueForQueue(queueNew)
            if(queueNew.launchAutoEQDlWhenEmpty) autodownloadForQueue(getAppContext(), queueNew)
        }
    } else Logd(TAG, "Queue was not modified by call to removeQueueItem")
}

suspend fun removeFromAllQueuesQuiet(episodeIds: List<Long>, updateState: Boolean = true) {
    Logd(TAG, "removeFromAllQueuesQuiet called ")

    suspend fun doit(q: PlayQueue, isActQueue: Boolean = false) {
        if (q.size() == 0) {
            if (isActQueue) upsert(q) { it.update() }
            if (!q.isVirtual()) {
                autoenqueueForQueue(q)
                if (q.launchAutoEQDlWhenEmpty) autodownloadForQueue(getAppContext(), q)
            }
            return
        }
        val qes = realm.query(QueueEntry::class).query("queueId == $0 AND episodeId IN $1", q.id, episodeIds).find()
        val idsInQueuesToRemove = qes.map { it.episodeId }
        if (idsInQueuesToRemove.isNotEmpty()) {
            realm.write { delete(qes) }
            val eList = realm.query(Episode::class).query("id IN $0", idsInQueuesToRemove).find()
            for (e in eList) {
                if (updateState && e.playState < EpisodeState.SKIPPED.code && !stateToPreserve(e.playState))
                    setPlayState(EpisodeState.SKIPPED, e, false)
            }
            val qNew = upsert(q) {
                it.idsBinList.removeAll(idsInQueuesToRemove)
                it.idsBinList.addAll(idsInQueuesToRemove)
                trimBin(it)
                it.update()
            }
            if (qNew.size() == 0 && !q.isVirtual()) {
                autoenqueueForQueue(qNew)
                if(qNew.launchAutoEQDlWhenEmpty) autodownloadForQueue(getAppContext(), qNew)
            }
            if (isActQueue) actQueue = qNew
        }
    }

    for (q in queues) {
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
    val qes = queueEntriesOf(actQueue)
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
    var nextItem = getEpisode(nextQE.episodeId) ?: return null
    Logd(TAG, "getNextInQueue nextItem ${nextItem.title}")
    nextItem = checkAndMarkDuplicates(nextItem)
    episodeChangedWhenScreenOff = true
    return nextItem
}

private fun calcPosition(queueItems: MutableList<Episode>, loc: EnqueueLocation, currentPlaying: Episode?): Int {
    if (queueItems.isEmpty()) return 0

    fun getPositionOfFirstNonDownloadingItem(startPosition: Int): Int {
        fun isItemAtPositionDownloading(position: Int): Boolean {
            val curItem = try { queueItems[position] } catch (e: IndexOutOfBoundsException) { null }
            if (curItem?.downloadUrl == null) return false
            return DownloadServiceInterface.impl?.isDownloadingEpisode(curItem.downloadUrl!!) == true
        }
        val qSize = queueItems.size
        for (i in startPosition until qSize) if (!isItemAtPositionDownloading(i)) return i
        return qSize
    }
    fun getCurrentlyPlayingPosition(): Int {
        if (currentPlaying == null) return -1
        val curPlayingItemId = currentPlaying.id
        for (i in queueItems.indices) if (curPlayingItemId == queueItems[i].id) return i
        return -1
    }

    return when (loc) {
        EnqueueLocation.BACK -> queueItems.size
        // Return not necessarily 0, so that when a list of items are downloaded and enqueued
        // in succession of calls (e.g., users manually tapping download one by one),
        // the items enqueued are kept the same order.
        // Simply returning 0 will reverse the order.
        EnqueueLocation.FRONT -> getPositionOfFirstNonDownloadingItem(0)
        EnqueueLocation.AFTER_CURRENTLY_PLAYING -> getPositionOfFirstNonDownloadingItem(getCurrentlyPlayingPosition() + 1)
        EnqueueLocation.RANDOM -> Random.nextInt(queueItems.size + 1)
        //                else -> throw AssertionError("calcPosition() : unrecognized enqueueLocation option: $enqueueLocation")
    }
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
        EnqueueLocation.AFTER_CURRENTLY_PLAYING -> queueEntries[getCurrentlyPlayingPosition()].position + QUEUE_POSITION_DELTA / 2
        EnqueueLocation.RANDOM -> queueEntries[Random.nextInt(queueEntries.size)].position + QUEUE_POSITION_DELTA / 2
    }
}

