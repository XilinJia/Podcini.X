package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.automation.autodownloadForQueue
import ac.mdiq.podcini.automation.autoenqueueForQueue
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curIndexInQueue
import ac.mdiq.podcini.playback.base.InTheatre.writeMediaPlaying
import ac.mdiq.podcini.playback.base.InTheatre.writeNoMediaPlaying
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.episodeChangedWhenScreenOff
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.specs.EnqueueLocation
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import java.util.Date
import kotlin.random.Random

private const val TAG: String = "Queues"


fun getInQueueEpisodeIds(): Set<Long> {
    Logd(TAG, "getQueueIDList() called")
    val queues = realm.query(PlayQueue::class).find()
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
    val events: MutableList<FlowEvent.QueueEvent> = mutableListOf()
    val updatedItems: MutableList<Episode> = mutableListOf()
    val currentlyPlaying = curEpisode
    var insertPosition = calcPosition(queue, currentlyPlaying)
    val qItems = queue.episodes.toMutableList()
    val qItemIds = queue.episodeIds.toMutableList()

    val t = System.currentTimeMillis()
    var c = 0L
    for (episode in episodes) {
        if (qItemIds.contains(episode.id)) continue
        events.add(FlowEvent.QueueEvent.added(episode, insertPosition))
        try {
            qItems.add(insertPosition, episode)
        } catch (e: Throwable) { Loge(TAG, "addToActiveQueue: ${e.message}")}
        updatedItems.add(episode)
        upsert(episode) {it.timeInQueue = t+c++ }
        queueModified = true
        if (episode.playState < EpisodeState.QUEUE.code) setInQueue.add(episode)
        insertPosition++
    }
    if (queueModified) {
        queue = applySortOrder(qItems, queue, events = events)
        if (queue.id == actQueue.id) actQueue = queue
//        else if (queue.id == curQueue.id) curQueue = queue
        for (event in events) EventFlow.postEvent(event)
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

    val currentlyPlaying = curEpisode
    val insertPosition = calcPosition(queue, currentlyPlaying)
    Logd(TAG, "addToQueueSync insertPosition: $insertPosition")
    val t = System.currentTimeMillis()
    upsert(episode) { it.timeInQueue = t }

    try { qItems.add(insertPosition, episode) } catch (e: Throwable) { Loge(TAG, "addToQueue ${e.message}")}
    val events = mutableListOf(FlowEvent.QueueEvent.added(episode, insertPosition))
    val queueNew = applySortOrder(qItems, queue, events = events)
    if (episode.playState < EpisodeState.QUEUE.code) setPlayState(EpisodeState.QUEUE, episode, false)
    if (queue.id == actQueue.id) {
        actQueue = queueNew
        EventFlow.postEvent(events[0])
    }
}

/**
 * Sorts the queue depending on the configured sort order.
 * If the queue is not in keep sorted mode, nothing happens.
 * @param queueItems  The queue to be sorted.
 * @param events Replaces the events by a single SORT event if the list has to be sorted automatically.
 */
private fun applySortOrder(queueItems: MutableList<Episode>, queue_: PlayQueue, events: MutableList<FlowEvent.QueueEvent>): PlayQueue {
    var sorted = false
    var queue = queue_
    if (queue.sortOrder !in listOf(EpisodeSortOrder.TIME_IN_QUEUE_ASC, EpisodeSortOrder.TIME_IN_QUEUE_DESC, EpisodeSortOrder.RANDOM, EpisodeSortOrder.RANDOM1)) {
        getPermutor(queue.sortOrder).reorder(queueItems)
        sorted = true
    }

    if (queue.enqueueLocation != EnqueueLocation.BACK.code && queue.sortOrder != EpisodeSortOrder.TIME_IN_QUEUE_DESC) resetInQueueTime(queueItems)

    if (sorted) queue = resetIds(queue, queueItems)

    // Replace ADDED events by a single SORTED event
    if (sorted) {
        events.clear()
        events.add(FlowEvent.QueueEvent.sorted(queueItems))
    }
    return queue
}

suspend fun smartRemoveFromActQueue(item_: Episode) {
    var item = item_
    val almostEnded = item.hasAlmostEnded()
    if (almostEnded && item.playState < EpisodeState.PLAYED.code && !stateToPreserve(item.playState)) item = setPlayState(EpisodeState.PLAYED, item, resetMediaPosition = true, removeFromQueue = false)
    if (almostEnded) item = upsert(item) { it.playbackCompletionDate = Date() }
    if (item.playState < EpisodeState.SKIPPED.code && !stateToPreserve(item.playState)) {
        val stat = if (item.lastPlayedTime > 0L) EpisodeState.SKIPPED else EpisodeState.PASSED
        item = setPlayState(stat, item, resetMediaPosition = false, removeFromQueue = false)
    }
    removeFromQueue(actQueue, listOf(item))
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
    val queues = realm.query(PlayQueue::class).find()
    for (q in queues) if (q.id != actQueue.id) removeFromQueue(q, episodes, playState)
    //        ensure actQueue is last updated
    if (actQueue.size() > 0) removeFromQueue(actQueue, episodes, playState)
    else upsertBlk(actQueue) { it.update() }
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
    val events: MutableList<FlowEvent.QueueEvent> = mutableListOf()
    val indicesToRemove: MutableList<Int> = mutableListOf()
    val qItems = queue.episodes.toMutableList()
    val eList = episodes.toList()
    val t = System.currentTimeMillis()
    var c = 0L
    for (i in qItems.indices) {
        val episode = qItems[i]
        if (eList.indexWithId(episode.id) >= 0) {
            Logd(TAG, "removing from queue: ${episode.id} ${episode.title}")
            indicesToRemove.add(i)
            upsertBlk(episode) {
                it.timeOutQueue = t+c++
                if (playState != null && it.playState == EpisodeState.QUEUE.code) it.setPlayState(playState)
            }
            if (queue.id == actQueue.id) events.add(FlowEvent.QueueEvent.removed(episode))
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
        for (event in events) EventFlow.postEvent(event)
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

    val queues = realm.query(PlayQueue::class).find()
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
    val eList = actQueue.episodes
    if (eList.isEmpty()) {
        Logd(TAG, "getNextInQueue queue is empty")
        writeNoMediaPlaying()
        return null
    }
    Logd(TAG, "getNextInQueue curIndexInQueue: $curIndexInQueue ${eList.size}")
    var nextItem = if (curIndexInQueue >= 0 && curIndexInQueue < eList.size) {
        when {
            eList[curIndexInQueue].id != currentMedia?.id -> eList[curIndexInQueue]
            eList.size == 1 -> return null
            else -> {
                val j = if (curIndexInQueue < eList.size - 1) curIndexInQueue + 1 else 0
                Logd(TAG, "getNextInQueue next j: $j")
                eList[j]
            }
        }
    } else eList[0]
    Logd(TAG, "getNextInQueue nextItem ${nextItem.title}")
    val continuePlay = getPref(AppPrefs.prefFollowQueue, true)
    if (!continuePlay) {
        Logd(TAG, "getNextInQueue(), but follow queue is not enabled.")
        writeMediaPlaying(nextItem, PlayerStatus.STOPPED)
        return null
    }
    nextItem = checkAndMarkDuplicates(nextItem)
    episodeChangedWhenScreenOff = true
    return nextItem
}

private fun calcPosition(queue: PlayQueue, currentPlaying: Episode?): Int {
    val queueItems = queue.episodes
    if (queueItems.isEmpty() || queue.sortOrder == EpisodeSortOrder.TIME_IN_QUEUE_DESC) return 0
    if (queue.sortOrder == EpisodeSortOrder.TIME_IN_QUEUE_ASC) return queueItems.size

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

    return when (EnqueueLocation.fromCode(queue.enqueueLocation)) {
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
