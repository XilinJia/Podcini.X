package ac.mdiq.podcini.net.sync.queue

import ac.mdiq.podcini.net.sync.SynchronizationSettings
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.storage.database.syncPrefs
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.utils.Logs
import org.json.JSONArray
import org.json.JSONException

class SynchronizationQueueStorage() {
    private val TAG = "SynchronizationQueueStorage"

    val queuedEpisodeActions: MutableList<EpisodeAction>
        get() {
            val actions = mutableListOf<EpisodeAction>()
            try {
                val json = syncPrefs.QUEUED_EPISODE_ACTIONS
                val queue = JSONArray(json)
                for (i in 0 until queue.length()) {
                    val act = EpisodeAction.readFromJsonObject(queue.getJSONObject(i))?: continue
                    actions.add(act)
                }
            } catch (e: JSONException) { Logs(TAG, e) }
            return actions
        }

    val queuedRemovedFeeds: MutableList<String>
        get() {
            val removedFeedUrls = mutableListOf<String>()
            try {
                val json = syncPrefs.QUEUED_FEEDS_REMOVED
                val queue = JSONArray(json)
                for (i in 0 until queue.length()) removedFeedUrls.add(queue.getString(i))
            } catch (e: JSONException) { Logs(TAG, e) }
            return removedFeedUrls
        }

    val queuedAddedFeeds: MutableList<String>
        get() {
            val addedFeedUrls = mutableListOf<String>()
            try {
                val json = syncPrefs.QUEUED_FEEDS_ADDED
                val queue = JSONArray(json)
                for (i in 0 until queue.length()) addedFeedUrls.add(queue.getString(i))
            } catch (e: JSONException) { Logs(TAG, e) }
            return addedFeedUrls
        }

    fun clearEpisodeActionQueue() {
        upsertBlk(syncPrefs) { it.QUEUED_EPISODE_ACTIONS = "[]"}
    }

    fun clearFeedQueues() {
        upsertBlk(syncPrefs) {
            it.QUEUED_FEEDS_ADDED = "[]"
            it.QUEUED_FEEDS_REMOVED = "[]"
        }
    }

    fun clearQueue() {
        SynchronizationSettings.resetTimestamps()
        upsertBlk(syncPrefs) {
            it.QUEUED_EPISODE_ACTIONS = "[]"
            it.QUEUED_FEEDS_ADDED = "[]"
            it.QUEUED_FEEDS_REMOVED = "[]"
        }
    }

    fun enqueueFeedAdded(downloadUrl: String) {
        try {
            val addedQueue = JSONArray(syncPrefs.QUEUED_FEEDS_ADDED)
            addedQueue.put(downloadUrl)
            val removedQueue = JSONArray(syncPrefs.QUEUED_FEEDS_REMOVED)
            removedQueue.remove(indexOf(downloadUrl, removedQueue))
            upsertBlk(syncPrefs) {
                it.QUEUED_FEEDS_ADDED = addedQueue.toString()
                it.QUEUED_FEEDS_REMOVED = removedQueue.toString()
            }
        } catch (jsonException: JSONException) { Logs("SynchronizationQueueStorage", jsonException) }
    }

    fun enqueueFeedRemoved(downloadUrl: String) {
        try {
            val removedQueue = JSONArray(syncPrefs.QUEUED_FEEDS_REMOVED)
            removedQueue.put(downloadUrl)
            val addedQueue = JSONArray(syncPrefs.QUEUED_FEEDS_ADDED)
            addedQueue.remove(indexOf(downloadUrl, addedQueue))
            upsertBlk(syncPrefs) {
                it.QUEUED_FEEDS_ADDED = addedQueue.toString()
                it.QUEUED_FEEDS_REMOVED = removedQueue.toString()
            }
        } catch (jsonException: JSONException) { Logs("SynchronizationQueueStorage", jsonException) }
    }

    private fun indexOf(string: String, array: JSONArray): Int {
        try { for (i in 0 until array.length()) if (array.getString(i) == string) return i } catch (jsonException: JSONException) { Logs("SynchronizationQueueStorage", jsonException) }
        return -1
    }

    fun enqueueEpisodeAction(action: EpisodeAction) {
        val json = syncPrefs.QUEUED_EPISODE_ACTIONS
        try {
            val queue = JSONArray(json)
            queue.put(action.writeToJsonObjectForServer())
            upsertBlk(syncPrefs) { it.QUEUED_EPISODE_ACTIONS = queue.toString() }
        } catch (jsonException: JSONException) { Logs("SynchronizationQueueStorage", jsonException) }
    }
}
