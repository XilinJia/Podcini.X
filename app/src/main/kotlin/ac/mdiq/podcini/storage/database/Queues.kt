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
import ac.mdiq.podcini.storage.model.VIRTUAL_QUEUE_ID
import ac.mdiq.podcini.storage.specs.EnqueueLocation
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.getPermutor
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

var virQueue by mutableStateOf(realm.query(PlayQueue::class).query("name == 'Virtual'").first().find() ?:
run {
    val vq = PlayQueue()
    vq.id = VIRTUAL_QUEUE_ID
    vq.name = "Virtual"
    upsertBlk(vq) {}
})

var curIndexInActQueue = -1

fun inQueueEpisodeIdSet(): Set<Long> {
    Logd(TAG, "getQueueIDList() called")
    val ids = mutableSetOf<Long>()
    for (queue in queues) ids.addAll(queue.episodeIds)
    return ids
}

/**
 * Appends Episode objects to the end of the queue. The 'read'-attribute of all episodes will be set to true.
 * If a Episode is already in the queue, the Episode will not change its position in the queue.
 * @param episodes               the Episode objects that should be added to the queue.
 */
suspend fun addToActQueue(episodes: List<Episode>) {
    Logd(TAG, "addToActQueue( ... ) called")
    if (episodes.isEmpty()) return
    var queue = actQueue
    if (queue.isVirtual()) {
        Loge(TAG, "Active queue is virtual, ignored")
        return
    }

    var queueModified = false
    val setInQueue = mutableListOf<Episode>()
    val updatedItems: MutableList<Episode> = mutableListOf()
    val qItems = queue.episodes.toMutableList()
    if (qItems.isNotEmpty() && queue.sortOrder != EpisodeSortOrder.TIME_IN_QUEUE_ASC) getPermutor(EpisodeSortOrder.TIME_IN_QUEUE_ASC).reorder(qItems)
    var insertPosition = calcPosition(qItems, EnqueueLocation.fromCode(queue.enqueueLocation), curEpisode)

    val qItemIds = queue.episodeIds.toSet()
    val t = System.currentTimeMillis()
    var c = 0L
    for (episode in episodes) {
        if (qItemIds.contains(episode.id)) continue
        try { qItems.add(insertPosition, episode) } catch (e: Throwable) { Loge(TAG, "addToActiveQueue: ${e.message}")}
        updatedItems.add(episode)
        upsert(episode) {it.timeInQueue = t+c++ }
        queueModified = true
        if (episode.playState < EpisodeState.QUEUE.code) setInQueue.add(episode)
        insertPosition++
    }
    if (queueModified) {
        applySortOrder(qItems, queue)
        queue = resetIds(queue, qItems)
        if (queue.id == actQueue.id) actQueue = queue
        for (episode in setInQueue) setPlayState(EpisodeState.QUEUE, episode, false)
    }
}

suspend fun addToAssOrActQueue(episode: Episode, queue_: PlayQueue? = null) {
    Logd(TAG, "addToQueueSync( ... ) called")
    var queue = queue_ ?: episode.feed?.queue ?: actQueue
    if (queue.isVirtual()) {
        Loge(TAG, "Current queue is virtual, ignored")
        return
    }
    queue = realm.query(PlayQueue::class).query("id == ${queue.id}").first().find() ?: return
    if (queue.episodeIds.contains(episode.id)) return

    val qItems = queue.episodes.toMutableList()
    if (qItems.isNotEmpty() && queue.sortOrder != EpisodeSortOrder.TIME_IN_QUEUE_ASC) getPermutor(EpisodeSortOrder.TIME_IN_QUEUE_ASC).reorder(qItems)

    val currentlyPlaying = if (queue.id == actQueue.id) curEpisode else null
    val insertPosition = calcPosition(qItems, EnqueueLocation.fromCode(queue.enqueueLocation), currentlyPlaying)
    Logd(TAG, "addToQueueSync insertPosition: $insertPosition")
    val t = System.currentTimeMillis()
    upsert(episode) { it.timeInQueue = t }

    try { qItems.add(insertPosition, episode) } catch (e: Throwable) { Loge(TAG, "addToQueue ${e.message}")}
    applySortOrder(qItems, queue)
    val queueNew = resetIds(queue, qItems)
    if (episode.playState < EpisodeState.QUEUE.code) setPlayState(EpisodeState.QUEUE, episode, false)
    if (queue.id == actQueue.id) actQueue = queueNew
}

private fun applySortOrder(queueItems: MutableList<Episode>, queue: PlayQueue) {
    if (queue.enqueueLocation != EnqueueLocation.BACK.code) resetInQueueTime(queueItems)
    if (queue.sortOrder !in listOf(EpisodeSortOrder.TIME_IN_QUEUE_ASC, EpisodeSortOrder.RANDOM, EpisodeSortOrder.RANDOM1)) {
        getPermutor(queue.sortOrder).reorder(queueItems)
    }
}

suspend fun queueToVirtual(episode: Episode, episodes: List<Episode>, listIdentity: String, sortOrder: EpisodeSortOrder, playInSequence: Boolean = true) {
    if (virQueue.identity != listIdentity && !virQueue.episodeIds.contains(episode.id)) {
        val index = episodes.indexOfFirst { it.id == episode.id }
        if (index >= 0) {
            val eIdsToQueue = episodes.subList(index, min(episodes.size, index + VIRTUAL_QUEUE_SIZE)).map { it.id }
            virQueue = upsert(virQueue) { q ->
                q.identity = listIdentity
                q.playInSequence = playInSequence
                q.sortOrder = sortOrder
                q.episodeIds.clear()
                q.episodeIds.addAll(eIdsToQueue)
            }
            virQueue.episodes.clear()
            actQueue = virQueue
            Logt(TAG, "first ${virQueue.size()} episodes are added to the Virtual queue")
        }
    } else actQueue = virQueue
}

suspend fun smartRemoveFromAllQueues(item_: Episode) {
    var item = item_
    val almostEnded = item.hasAlmostEnded()
    if (almostEnded && item.playState < EpisodeState.PLAYED.code && !stateToPreserve(item.playState)) item = setPlayState(EpisodeState.PLAYED, item, resetMediaPosition = true, removeFromQueue = false)
    if (almostEnded) item = upsert(item) { it.playbackCompletionDate = Date() }
    if (item.playState < EpisodeState.SKIPPED.code && !stateToPreserve(item.playState)) {
        val stat = if (item.lastPlayedTime > 0L) EpisodeState.SKIPPED else EpisodeState.PASSED
        item = setPlayState(stat, item, resetMediaPosition = false, removeFromQueue = false)
    }
    for (q in queues) if (q.id != actQueue.id && q.episodeIds.contains(item.id)) removeFromQueue(q, listOf(item))
    //        ensure actQueue is last updated
    if (curEpisode != null && item.id == curEpisode!!.id) curIndexInActQueue = actQueue.episodes.indexWithId(curEpisode!!.id)
    if (actQueue.size() > 0 && actQueue.episodeIds.contains(item.id)) removeFromQueue(actQueue, listOf(item))
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
            if (q.id != actQueue.id && q.episodeIds.contains(e.id)) removeFromQueue(q, listOf(e), playState)
        }
    }
    //        ensure actQueue is last updated
    for (e in episodes) {
        if (curEpisode != null && e.id == curEpisode!!.id) curIndexInActQueue = actQueue.episodes.indexWithId(curEpisode!!.id)
        if (actQueue.size() > 0 && actQueue.episodeIds.contains(e.id)) removeFromQueue(actQueue, listOf(e), playState)
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
    val t = System.currentTimeMillis()
    var c = 0L
    for (e in eList) {
        val i = qItems.indexWithId(e.id)
        if (i >= 0) {
            indicesToRemove.add(i)
            upsertBlk(e) {
                it.timeOutQueue = t+c++
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
                qItems.removeAt(indicesToRemove[i])
            }
            trimBin(it)
            it.update()
            it.episodeIds.clear()
            for (e in qItems) it.episodeIds.add(e.id)
        }
        if (queueNew.id == actQueue.id) {
            queueNew.episodes.clear()
            actQueue = queueNew
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
        val idsInQueuesToRemove: MutableSet<Long> = q.episodeIds.intersect(episodeIds.toSet()).toMutableSet()
        if (idsInQueuesToRemove.isNotEmpty()) {
            val eList = realm.query(Episode::class).query("id IN $0", idsInQueuesToRemove).find()
            val t = System.currentTimeMillis()
            var c = 0L
            for (e in eList) {
                var e_ = e
                if (updateState && e.playState < EpisodeState.SKIPPED.code && !stateToPreserve(e.playState))
                    e_ = setPlayState(EpisodeState.SKIPPED, e, false)
                upsert(e_) { it.timeOutQueue = t+c++}
            }
            val qNew = upsert(q) {
                it.idsBinList.removeAll(idsInQueuesToRemove)
                it.idsBinList.addAll(idsInQueuesToRemove)
                trimBin(it)
                val qeids = it.episodeIds.minus(idsInQueuesToRemove)
                it.episodeIds.clear()
                it.episodeIds.addAll(qeids)
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

fun resetIds(queue: PlayQueue, episodes: Collection<Episode>): PlayQueue {
    return upsertBlk(queue) {
        it.episodeIds.clear()
        for (e in episodes) it.episodeIds.add(e.id)
        it.update()
    }
}

fun resetInQueueTime(episodes: Collection<Episode>) {
    val t = System.currentTimeMillis()
    var c = 0L
    realm.writeBlocking {
        for (e_ in episodes) {
            val e = findLatest(e_) ?: continue
            e.timeInQueue = t+c++
        }
    }
}

fun getNextInQueue(currentMedia: Episode?): Episode? {
    Logd(TAG, "getNextInQueue called currentMedia: ${currentMedia?.getEpisodeTitle()}")
    if (!actQueue.playInSequence) {
        Logd(TAG, "getNextInQueue(), but follow queue is not enabled.")
        savePlayerStatus(null)
        return null
    }
    val eList = actQueue.episodes.toList()
    if (eList.isEmpty()) {
        Logd(TAG, "getNextInQueue queue is empty")
        savePlayerStatus(null)
        return null
    }
    var curIndex = if (currentMedia != null) eList.indexWithId(currentMedia.id) else 0
    if (curIndex < 0 && curIndexInActQueue >= 0) {
        curIndex = curIndexInActQueue
        curIndexInActQueue = -1
    }
    Logd(TAG, "getNextInQueue curIndexInQueue: $curIndex ${eList.size}")
    var nextItem = if (curIndex >= 0 && curIndex < eList.size) {
        when {
            eList[curIndex].id != currentMedia?.id -> eList[curIndex]
            eList.size == 1 -> return null
            else -> {
                val j = if (curIndex < eList.size - 1) curIndex + 1 else 0
                Logd(TAG, "getNextInQueue next j: $j")
                eList[j]
            }
        }
    } else eList[0]
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

