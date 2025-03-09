package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.automation.AutoDownloads.autodownloadForQueue
import ac.mdiq.podcini.automation.AutoDownloads.autoenqueueForQueue
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.storage.database.Episodes.indexOfItemWithId
import ac.mdiq.podcini.storage.database.Episodes.setPlayStateSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.PlayState
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import kotlinx.coroutines.Job
import kotlin.random.Random

object Queues {
    private val TAG: String = Queues::class.simpleName ?: "Anonymous"

    enum class EnqueueLocation(val res: Int) {
        BACK(R.string.enqueue_location_back),
        FRONT(R.string.enqueue_location_front),
        AFTER_CURRENTLY_PLAYING(R.string.enqueue_location_after_current),
        RANDOM(R.string.enqueue_location_random)
    }

    /**
     * Returns if the queue is in keep sorted mode.
     * Enables/disables the keep sorted mode of the queue.
     * @see .queueKeepSortedOrder
     */
    var isQueueKeepSorted: Boolean
        get() = getPref(AppPrefs.prefQueueKeepSorted, false)
        set(keepSorted) {
            putPref(AppPrefs.prefQueueKeepSorted, keepSorted)
        }

    val autoDLOnEmptyQueues: Set<String>
        get() = getPref(AppPrefs.prefAutoDLOnEmptyIncludeQueues, setOf<String>())

    /**
     * Returns the sort order for the queue keep sorted mode.
     * Note: This value is stored independently from the keep sorted state.
     * Sets the sort order for the queue keep sorted mode.
     * @see .isQueueKeepSorted
     */
    var queueKeepSortedOrder: EpisodeSortOrder?
        get() {
            val sortOrderStr = getPref(AppPrefs.prefQueueKeepSortedOrder, "use-default")
            return EpisodeSortOrder.parseWithDefault(sortOrderStr, EpisodeSortOrder.DATE_NEW_OLD)
        }
        set(sortOrder) {
            if (sortOrder == null) return
            putPref(AppPrefs.prefQueueKeepSortedOrder, sortOrder.name)
        }

    var enqueueLocation: EnqueueLocation
        get() {
            val valStr = getPref(AppPrefs.prefEnqueueLocation, EnqueueLocation.BACK.name)
            try { return EnqueueLocation.valueOf(valStr)
            } catch (t: Throwable) {
                // should never happen but just in case
                Logs(TAG, t, "getEnqueueLocation: invalid value '$valStr' Use default.")
                return EnqueueLocation.BACK
            }
        }
        set(location) {
            putPref(AppPrefs.prefEnqueueLocation, location.name)
        }
    
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
    @JvmStatic @Synchronized
    fun addToActiveQueue(vararg episodes: Episode) : Job {
        Logd(TAG, "addToActiveQueue( ... ) called")
        return runOnIOScope {
            if (episodes.isEmpty()) return@runOnIOScope

            var queueModified = false
            val setInQueue = mutableListOf<Episode>()
            val events: MutableList<FlowEvent.QueueEvent> = mutableListOf()
            val updatedItems: MutableList<Episode> = mutableListOf()
            val positionCalculator = EnqueuePositionPolicy(enqueueLocation)
            val currentlyPlaying = curEpisode
            var insertPosition = positionCalculator.calcPosition(curQueue.episodes, currentlyPlaying)

            val qItems = curQueue.episodes.toMutableList()
            val qItemIds = curQueue.episodeIds.toMutableList()
            val items_ = episodes.toList()
            for (episode in items_) {
                if (qItemIds.contains(episode.id)) continue
                events.add(FlowEvent.QueueEvent.added(episode, insertPosition))
                qItemIds.add(insertPosition, episode.id)
                updatedItems.add(episode)
                qItems.add(insertPosition, episode)
                queueModified = true
                if (episode.playState < PlayState.QUEUE.code) setInQueue.add(episode)
                insertPosition++
            }
            if (queueModified) {
//                TODO: handle sorting
                applySortOrder(qItems, events)
                curQueue = upsert(curQueue) {
                    it.episodeIds.clear()
                    it.episodeIds.addAll(qItemIds)
                    it.update()
                }
                for (event in events) EventFlow.postEvent(event)
                for (episode in setInQueue) setPlayStateSync(PlayState.QUEUE.code, episode, false)
            }
        }
    }

    suspend fun addToQueueSync(episode: Episode, queue_: PlayQueue? = null) {
        Logd(TAG, "addToQueueSync( ... ) called")
        var queue = queue_ ?: episode.feed?.queue ?: curQueue
        queue = realm.query(PlayQueue::class).query("id == ${queue.id}").first().find() ?: return
        if (queue.episodeIds.contains(episode.id)) return

        val currentlyPlaying = curEpisode
        val positionCalculator = EnqueuePositionPolicy(enqueueLocation)
        var insertPosition = positionCalculator.calcPosition(queue.episodes, currentlyPlaying)
        Logd(TAG, "addToQueueSync insertPosition: $insertPosition")

        val queueNew = upsert(queue) {
            it.episodeIds.add(insertPosition, episode.id)
            insertPosition++
            it.update()
        }
        if (queue.id == curQueue.id) curQueue = queueNew
        if (episode.playState < PlayState.QUEUE.code) setPlayStateSync(PlayState.QUEUE.code, episode, false)
        if (queue.id == curQueue.id) EventFlow.postEvent(FlowEvent.QueueEvent.added(episode, insertPosition))
    }

    /**
     * Sorts the queue depending on the configured sort order.
     * If the queue is not in keep sorted mode, nothing happens.
     * @param queueItems  The queue to be sorted.
     * @param events Replaces the events by a single SORT event if the list has to be sorted automatically.
     */
    private fun applySortOrder(queueItems: MutableList<Episode>, events: MutableList<FlowEvent.QueueEvent>) {
        // queue is not in keep sorted mode, there's nothing to do
        if (!isQueueKeepSorted) return

        // Sort queue by configured sort order
        val sortOrder = queueKeepSortedOrder
        // do not shuffle the list on every change
        if (sortOrder == EpisodeSortOrder.RANDOM) return

        if (sortOrder != null) {
            val permutor = getPermutor(sortOrder)
            permutor.reorder(queueItems)
        }
        // Replace ADDED events by a single SORTED event
        events.clear()
        events.add(FlowEvent.QueueEvent.sorted(queueItems))
    }

    fun clearQueue() : Job {
        Logd(TAG, "clearQueue called")
        return runOnIOScope {
            curQueue = upsert(curQueue) {
                it.idsBinList.addAll(it.episodeIds)
                trimBin(it)
                it.episodeIds.clear()
                it.update()
            }
            for (e in curQueue.episodes) if (e.playState < PlayState.SKIPPED.code) setPlayStateSync(PlayState.SKIPPED.code, e, false)
            curQueue.episodes.clear()
            EventFlow.postEvent(FlowEvent.QueueEvent.cleared())
            autoenqueueForQueue(curQueue)
            if(autoDLOnEmptyQueues.contains(curQueue.name)) autodownloadForQueue(getAppContext(), curQueue)
        }
    }

    private fun trimBin(queue: PlayQueue) {
        if (queue.binLimit == 0) return
        if (queue.idsBinList.size > queue.binLimit * 1.2) {
            val newSize = (0.2 * queue.binLimit).toInt()
            val subList = queue.idsBinList.subList(0, newSize)
            queue.idsBinList.clear()
            queue.idsBinList.addAll(subList)
        }
    }

    fun removeFromAllQueuesSync(vararg episodes: Episode) {
        Logd(TAG, "removeFromAllQueuesSync called ")
        val queues = realm.query(PlayQueue::class).find()
        for (q in queues) {
            if (q.id != curQueue.id) removeFromQueueSync(q, *episodes)
        }
//        ensure curQueue is last updated
        if (curQueue.size() > 0) removeFromQueueSync(curQueue, *episodes)
        else upsertBlk(curQueue) { it.update() }
        if (curQueue.size() == 0) {
            autoenqueueForQueue(curQueue)
            if(autoDLOnEmptyQueues.contains(curQueue.name)) autodownloadForQueue(getAppContext(), curQueue)
        }
    }

    /**
     * @param queue_    if null, use curQueue
     */
    internal fun removeFromQueueSync(queue_: PlayQueue?, vararg episodes: Episode) {
        Logd(TAG, "removeFromQueueSync called ")
        if (episodes.isEmpty()) return
        var queue = queue_ ?: curQueue
        if (queue.size() == 0) {
            autoenqueueForQueue(queue)
            if(autoDLOnEmptyQueues.contains(queue.name)) autodownloadForQueue(getAppContext(), queue)
            return
        }
        val events: MutableList<FlowEvent.QueueEvent> = mutableListOf()
        val indicesToRemove: MutableList<Int> = mutableListOf()
        val qItems = queue.episodes.toMutableList()
        val eList = episodes.toList()
        for (i in qItems.indices) {
            val episode = qItems[i]
            if (eList.indexOfItemWithId(episode.id) >= 0) {
                Logd(TAG, "removing from queue: ${episode.id} ${episode.title}")
                indicesToRemove.add(i)
                if (queue.id == curQueue.id) events.add(FlowEvent.QueueEvent.removed(episode))
            }
        }
        if (indicesToRemove.isNotEmpty()) {
            val queueNew = upsertBlk(queue) {
                for (i in indicesToRemove.indices.reversed()) {
                    val id = qItems[indicesToRemove[i]].id
                    it.idsBinList.remove(id)
                    it.idsBinList.add(id)
                    trimBin(it)
                    qItems.removeAt(indicesToRemove[i])
                }
                it.update()
                it.episodeIds.clear()
                for (e in qItems) it.episodeIds.add(e.id)
            }
            if (queueNew.id == curQueue.id) {
                queueNew.episodes.clear()
                curQueue = queueNew
            }
            for (event in events) EventFlow.postEvent(event)
            if (queueNew.size() == 0) {
                autoenqueueForQueue(queueNew)
                if(autoDLOnEmptyQueues.contains(queueNew.name)) autodownloadForQueue(getAppContext(), queueNew)
            }
        } else Logd(TAG, "Queue was not modified by call to removeQueueItem")
    }

    suspend fun removeFromAllQueuesQuiet(episodeIds: List<Long>) {
        Logd(TAG, "removeFromAllQueuesQuiet called ")
        var idsInQueuesToRemove: MutableSet<Long>
        val queues = realm.query(PlayQueue::class).find()
        for (q in queues) {
            if (q.id == curQueue.id) continue
            if (q.size() == 0) {
                autoenqueueForQueue(q)
                if(autoDLOnEmptyQueues.contains(q.name)) autodownloadForQueue(getAppContext(), q)
                continue
            }
            idsInQueuesToRemove = q.episodeIds.intersect(episodeIds.toSet()).toMutableSet()
            if (idsInQueuesToRemove.isNotEmpty()) {
                val eList = realm.query(Episode::class).query("id IN $0", idsInQueuesToRemove).find()
                for (e in eList) if (e.playState < PlayState.SKIPPED.code) setPlayStateSync(PlayState.SKIPPED.code, e, false)
                val qNew = upsert(q) {
                    it.idsBinList.removeAll(idsInQueuesToRemove)
                    it.idsBinList.addAll(idsInQueuesToRemove)
                    trimBin(it)
                    val qeids = it.episodeIds.minus(idsInQueuesToRemove)
                    it.episodeIds.clear()
                    it.episodeIds.addAll(qeids)
                    it.update()
                }
                if (qNew.size() == 0) {
                    autoenqueueForQueue(qNew)
                    if(autoDLOnEmptyQueues.contains(qNew.name)) autodownloadForQueue(getAppContext(), qNew)
                }
            }
        }
        //        ensure curQueue is last updated
        val q = curQueue
        if (q.size() == 0) {
            upsert(q) { it.update() }
            autoenqueueForQueue(q)
            if(autoDLOnEmptyQueues.contains(q.name)) autodownloadForQueue(getAppContext(), q)
            return
        }
        idsInQueuesToRemove = q.episodeIds.intersect(episodeIds.toSet()).toMutableSet()
        if (idsInQueuesToRemove.isNotEmpty()) {
            val eList = realm.query(Episode::class).query("id IN $0", idsInQueuesToRemove).find()
            for (e in eList) if (e.playState < PlayState.SKIPPED.code) setPlayStateSync(PlayState.SKIPPED.code, e, false)
            curQueue = upsert(q) {
                it.idsBinList.removeAll(idsInQueuesToRemove)
                it.idsBinList.addAll(idsInQueuesToRemove)
                trimBin(it)
                val qeids = it.episodeIds.minus(idsInQueuesToRemove)
                it.episodeIds.clear()
                it.episodeIds.addAll(qeids)
                it.update()
            }
            if (curQueue.size() == 0) {
                autoenqueueForQueue(curQueue)
                if(autoDLOnEmptyQueues.contains(curQueue.name)) autodownloadForQueue(getAppContext(), curQueue)
            }
        }
    }

    /**
     * Changes the position of a Episode in the queue.
     * This function must be run using the ExecutorService (dbExec).
     * @param from            Source index. Must be in range 0..queue.size()-1.
     * @param to              Destination index. Must be in range 0..queue.size()-1.
     * @param broadcastUpdate true if this operation should trigger a QueueUpdateBroadcast. This option should be set to
     * false if the caller wants to avoid unexpected updates of the GUI.
     * @throws IndexOutOfBoundsException if (to < 0 || to >= queue.size()) || (from < 0 || from >= queue.size())
     */
    fun moveInQueueSync(from: Int, to: Int, broadcastUpdate: Boolean) {
        val episodes = curQueue.episodes.toMutableList()
        if (episodes.isNotEmpty()) {
            if ((from in 0 ..< episodes.size) && (to in 0..<episodes.size)) {
                val episode = episodes.removeAt(from)
                episodes.add(to, episode)
                if (broadcastUpdate) EventFlow.postEvent(FlowEvent.QueueEvent.moved(episode, to))
            }
        } else Loge(TAG, "moveQueueItemHelper: Could not load queue")
        curQueue.episodes.clear()
        curQueue = upsertBlk(curQueue) {
            it.episodeIds.clear()
            for (e in episodes) it.episodeIds.add(e.id)
            it.update()
        }
    }

    class EnqueuePositionPolicy(private val enqueueLocation: EnqueueLocation) {
        /**
         * Determine the position (0-based) that the item(s) should be inserted to the named queue.
         * @param queueItems           the queue to which the item is to be inserted
         * @param currentPlaying     the currently playing media
         */
        fun calcPosition(queueItems: List<Episode>, currentPlaying: Episode?): Int {
            if (queueItems.isEmpty()) return 0
            return when (enqueueLocation) {
                EnqueueLocation.BACK -> queueItems.size
                // Return not necessarily 0, so that when a list of items are downloaded and enqueued
                // in succession of calls (e.g., users manually tapping download one by one),
                // the items enqueued are kept the same order.
                // Simply returning 0 will reverse the order.
                EnqueueLocation.FRONT -> getPositionOfFirstNonDownloadingItem(0, queueItems)
                EnqueueLocation.AFTER_CURRENTLY_PLAYING -> getPositionOfFirstNonDownloadingItem(getCurrentlyPlayingPosition(queueItems, currentPlaying) + 1, queueItems)
                EnqueueLocation.RANDOM -> Random.Default.nextInt(queueItems.size + 1)
        //                else -> throw AssertionError("calcPosition() : unrecognized enqueueLocation option: $enqueueLocation")
            }
        }
        private fun getPositionOfFirstNonDownloadingItem(startPosition: Int, queueItems: List<Episode>): Int {
            val curQueueSize = queueItems.size
            for (i in startPosition until curQueueSize) if (!isItemAtPositionDownloading(i, queueItems)) return i
            return curQueueSize
        }
        private fun isItemAtPositionDownloading(position: Int, queueItems: List<Episode>): Boolean {
            val curItem = try { queueItems[position] } catch (e: IndexOutOfBoundsException) { null }
            if (curItem?.downloadUrl == null) return false
            return DownloadServiceInterface.impl?.isDownloadingEpisode(curItem.downloadUrl!!) == true
        }
        private fun getCurrentlyPlayingPosition(queueItems: List<Episode>, currentPlaying: Episode?): Int {
            if (currentPlaying == null) return -1
            val curPlayingItemId = currentPlaying.id
            for (i in queueItems.indices) if (curPlayingItemId == queueItems[i].id) return i
            return -1
        }
    }
}