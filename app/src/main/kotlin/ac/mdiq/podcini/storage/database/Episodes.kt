package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.feed.updateLocalFeed
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.base.InTheatre.actQueue
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.InTheatre.savePlayerStatus
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.getCurrentPlaybackSpeed
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.ACTION_SHUTDOWN_PLAYBACK_SERVICE
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.sortPairOf
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.EpisodeState.Companion.fromCode
import ac.mdiq.podcini.storage.utils.getDurationStringShort
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.fullDateTimeString
import ac.mdiq.podcini.utils.sendLocalBroadcast
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.util.UnstableApi
import io.github.xilinjia.krdb.ext.isManaged
import io.github.xilinjia.krdb.notifications.ResultsChange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date
import java.util.Locale
import kotlin.math.min

private const val TAG: String = "Episodes"

/**
 * @param offset The first episode that should be loaded.
 * @param limit The maximum number of episodes that should be loaded.
 * @param filter The filter describing which episodes to filter out.
 * TODO: filters of queued and notqueued don't work in this
 */
fun getEpisodes(filter: EpisodeFilter?, sortOrder: EpisodeSortOrder?, feedId: Long = -1, offset: Int = 0, limit: Int = Int.MAX_VALUE, copy: Boolean = true): List<Episode> {
    var queryString = filter?.queryString()?:"id > 0"
    if (feedId >= 0) queryString += " AND feedId == $feedId "
    Logd(TAG, "getEpisodes called with: offset=$offset, limit=$limit queryString: $queryString")
    if (offset > 0) {
        var episodes = realm.query(Episode::class).query(queryString).sort(sortPairOf(sortOrder)).find().toMutableList()
        val size = episodes.size
        if (offset < size) {
            episodes = episodes.subList(offset, min(size, offset + limit))
            return if (copy) realm.copyFromRealm(episodes) else episodes
        } else return listOf()
    } else {
        val episodes = realm.query(Episode::class).query(queryString).sort(sortPairOf(sortOrder)).limit(limit).find()
        return if (copy) realm.copyFromRealm(episodes) else episodes
    }
}

fun getEpisodesAsFlow(filter: EpisodeFilter?, sortOrder: EpisodeSortOrder?, feedId: Long = -1): Flow<ResultsChange<Episode>> {
    var queryString = filter?.queryString()
    if (queryString.isNullOrBlank()) queryString = "id > 0"
    if (feedId >= 0) queryString += " AND feedId == $feedId "
    Logd(TAG, "getEpisodesAsFlow called with: queryString: $queryString sortOrder: $sortOrder")
    return realm.query(Episode::class).query(queryString).sort(sortPairOf(sortOrder)).asFlow()
}

fun getEpisodesCount(filter: EpisodeFilter?, feedId: Long = -1): Int {
    var queryString = filter?.queryString()?:"id > 0"
    Logd(TAG, "getEpisodesCount called queryString: $queryString")
    if (feedId >= 0) queryString += " AND feedId == $feedId "
    return realm.query(Episode::class).query(queryString).count().find().toInt()
}

/**
 * Loads a specific FeedItem from the database.
 * @param guid feed episode guid
 * @param episodeUrl the feed episode's url
 * @return The FeedItem or null if the FeedItem could not be found.
 * Does NOT load additional attributes like feed or queue state.
 */
fun getEpisodeByGuidOrUrl(guid: String?, episodeUrl: String, copy: Boolean = true): Episode? {
    Logd(TAG, "getEpisodeByGuidOrUrl called $guid $episodeUrl")
    val episode = if (guid != null) realm.query(Episode::class).query("identifier == $0", guid).first().find()
    else realm.query(Episode::class).query("downloadUrl == $0", episodeUrl).first().find()
    if (!copy || episode == null) return episode
    return realm.copyFromRealm(episode)
}

fun getEpisode(id: Long, copy: Boolean = true): Episode? {
    Logd(TAG, "getEpisodeMedia called $id")
    val episode = realm.query(Episode::class).query("id == $0", id).first().find()
    if (!copy || episode == null) return episode
    return realm.copyFromRealm(episode)
}

fun getHistoryAsFlow(feedId: Long = 0L, start: Long = 0L, end: Long = Date().time, filter: EpisodeFilter? = null, sortOrder: EpisodeSortOrder = EpisodeSortOrder.PLAYED_DATE_DESC): Flow<ResultsChange<Episode>> {
    Logd(TAG, "getHistory() called")
    var qStr = "((playbackCompletionTime > 0) OR (lastPlayedTime > $start AND lastPlayedTime <= $end))"
    if (feedId > 0L) qStr += " AND feedId == $feedId "
    val fqstr = filter?.queryString()
    if (!fqstr.isNullOrBlank()) qStr += " AND $fqstr "
    val episodes = realm.query(Episode::class).query(qStr).sort(sortPairOf(sortOrder)).asFlow()
    return episodes
}

suspend fun deleteEpisodesWarnLocalRepeat(context: Context, items: Iterable<Episode>) {
    val localItems: MutableList<Episode> = mutableListOf()
    val repeatItems: MutableList<Episode> = mutableListOf()
    for (item in items) {
        if (item.feed?.isLocalFeed == true) localItems.add(item)
        if (item.playState == EpisodeState.AGAIN.code || item.playState == EpisodeState.FOREVER.code) repeatItems.add(item)
        else deleteAndRemoveFromQueues(context, item)
    }

    val userDone = CompletableDeferred<Unit>()

    if (localItems.isNotEmpty()) {
        withContext(Dispatchers.Main) {
            commonConfirm = CommonConfirmAttrib(
                title = context.getString(R.string.delete_episode_label),
                message = context.getString(R.string.delete_local_feed_warning_body),
                confirmRes = R.string.delete_label,
                cancelRes = R.string.cancel_label,
                onConfirm = {
                    for (item in localItems) deleteAndRemoveFromQueues(context, item)
                    userDone.complete(Unit)
                },
                onNeutral = { userDone.complete(Unit)},
                onCancel = { userDone.complete(Unit)})
        }
    }

    userDone.await()

    if (repeatItems.isNotEmpty()) {
        withContext(Dispatchers.Main) {
            commonConfirm = CommonConfirmAttrib(
                title = context.getString(R.string.delete_episode_label),
                message = context.getString(R.string.delete_repeat_warning_msg),
                confirmRes = R.string.delete_label,
                cancelRes = R.string.cancel_label,
                onConfirm = { for (item in repeatItems) deleteAndRemoveFromQueues(context, item) })
        }
    }
}

fun deleteAndRemoveFromQueues(context: Context, episode: Episode) {
    Logd(TAG, "deleteMediaOfEpisode called ${episode.title}")
    val episode_ = deleteMedia(context, episode)
    if (getPref(AppPrefs.prefDeleteRemovesFromQueue, true)) removeFromAllQueues(listOf(episode_))
}

@OptIn(UnstableApi::class)
fun deleteMedia(context: Context, episode: Episode): Episode {
    Logd(TAG, String.format(Locale.US, "deleteMedia [id=%d, title=%s, downloaded=%s", episode.id, episode.getEpisodeTitle(), episode.downloaded))
    var localDelete = false
    val url = episode.fileUrl
    Logd(TAG, "deleteMedia $url")
    var episode = episode
    try {
        when {
            url != null && url.startsWith("content://") -> {
                // Local feed or custom media folder
                val documentFile = DocumentFile.fromSingleUri(context, url.toUri())
                if (documentFile == null || !documentFile.exists() || !documentFile.delete()) {
                    Loge(TAG, "deleteMedia delete media file failed: file not exists? ${episode.title} $url")
                    EventFlow.postEvent(FlowEvent.MessageEvent(getAppContext().getString(R.string.delete_local_failed)))
                    return episode
                }
                episode = upsertBlk(episode) {
                    it.setfileUrlOrNull(null)
                    if (it.playState < EpisodeState.SKIPPED.code && !stateToPreserve(it.playState)) it.setPlayState(EpisodeState.SKIPPED)
                }
                // TODO: need to change event
                EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.removed(episode))
                localDelete = true
            }
            url != null -> {
                // delete downloaded media file
                val path = url.toUri().path
                if (path == null) {
                    Loge(TAG, "deleteMedia delete media file failed: file not exists? ${episode.title} $url")
                    EventFlow.postEvent(FlowEvent.MessageEvent(getAppContext().getString(R.string.delete_local_failed)))
                    return episode
                }
                val mediaFile = File(path)
                if (mediaFile.exists() && !mediaFile.delete()) {
                    Loge(TAG, "deleteMedia delete media file failed: file not exists? ${episode.title} $url")
                    val evt = FlowEvent.MessageEvent(getAppContext().getString(R.string.delete_failed_simple) + ": $url")
                    EventFlow.postEvent(evt)
                    return episode
                }
                episode = upsertBlk(episode) {
                    it.setfileUrlOrNull(null)
                    it.hasEmbeddedPicture = false
                    if (it.playState < EpisodeState.SKIPPED.code && !stateToPreserve(it.playState)) it.setPlayState(EpisodeState.SKIPPED)
                }
                // TODO: need to change event
                EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.removed(episode))
            }
        }
    } catch (e: Throwable) { Logs(TAG, e, "deleteMedia failed") }

    if (episode.id == curState.curMediaId) {
        savePlayerStatus(null)
        sendLocalBroadcast(context, ACTION_SHUTDOWN_PLAYBACK_SERVICE)
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(R.id.notification_playing)
    }

    // Do full update of this feed to get rid of the episode
    if (localDelete) {
        if (episode.feed != null) updateLocalFeed(episode.feed!!, context.applicationContext, null)
    } else {
        if (isProviderConnected) {
            // Gpodder: queue delete action for synchronization
            val action = EpisodeAction.Builder(episode, EpisodeAction.DELETE).currentTimestamp().build()
            SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, action)
        }
        EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.removed(episode))
    }
    return episode
}

/**
 * This is used when the episodes are not listed with the feed.
 * Remove the listed episodes and their EpisodeMedia entries.
 * Deleting media also removes the download log entries.
 */
@OptIn(UnstableApi::class)
fun deleteMedias(context: Context, episodes: List<Episode>)  {
    val removedFromQueue: MutableList<Episode> = mutableListOf()
    val queueItems = actQueue.episodes.toMutableList()
    for (episode in episodes) {
        if (queueItems.remove(episode)) removedFromQueue.add(episode)
        if (episode.id == curState.curMediaId) {
            // Applies to both downloaded and streamed media
            savePlayerStatus(null)
            sendLocalBroadcast(context, ACTION_SHUTDOWN_PLAYBACK_SERVICE)
        }
        if (episode.feed != null && !episode.feed!!.isLocalFeed) {
            DownloadServiceInterface.impl?.cancel(context, episode)
            if (episode.downloaded) deleteMedia(context, episode)
        }
    }
    if (removedFromQueue.isNotEmpty()) removeFromAllQueues(removedFromQueue)
//    for (episode in removedFromQueue) EventFlow.postEvent(FlowEvent.QueueEvent.irreversibleRemoved(episode))

    // we assume we also removed download log entries for the feed or its media files.
    // especially important if download or refresh failed, as the user should not be able
    // to retry these
//    EventFlow.postEvent(FlowEvent.DownloadLogEvent())
}

fun checkAndMarkDuplicates(episode: Episode): Episode {
    var updated = false
    realm.writeBlocking {
        val duplicates = query(Episode::class, "title == $0 OR downloadUrl == $1", episode.title, episode.downloadUrl).find()
        if (duplicates.size > 1) {
            Logt(TAG, "Found ${duplicates.size - 1} duplicate episodes, setting to Ignored")
            val localTime = System.currentTimeMillis()
            val comment = fullDateTimeString(localTime) + ":\nduplicate"
            for (e in duplicates) {
                if (e.id != episode.id) {
                    when {
                        e.playState <= EpisodeState.AGAIN.code -> {
                            e.setPlayState(EpisodeState.IGNORED)
                            e.addComment(comment)
                        }
                        episode.playState == EpisodeState.IGNORED.code -> { }
                        else -> {
                            val m = findLatest(episode)?.let {
                                it.setPlayState(EpisodeState.IGNORED)
                                it.addComment(comment)
                                it
                            }
                            m?.let { updated = true }
                            Logt(TAG, "Duplicate item was previously set to ${fromCode(e.playState).name} ${e.title}")
                        }
                    }
                }
            }
            for (e in duplicates) {
                for (e1 in duplicates) {
                    if (e.id == e1.id) continue
                    e.related.add(e1)
                }
            }
        }
    }
    return if (updated) realm.query(Episode::class, "id == ${episode.id}").first().find() ?: episode else episode
}

fun stateToPreserve(stat: Int): Boolean = stat in listOf(EpisodeState.SOON.code, EpisodeState.LATER.code, EpisodeState.AGAIN.code, EpisodeState.FOREVER.code)

suspend fun setPlayState(state: EpisodeState, episode: Episode, resetMediaPosition: Boolean, removeFromQueue: Boolean = true) : Episode {
    Logd(TAG, "setPlayState called played: $state resetMediaPosition: $resetMediaPosition ${episode.title}")
    var episode_ = episode
    if (!episode.isManaged()) episode_ = realm.query(Episode::class).query("id == $0", episode.id).first().find() ?: episode
    val result = upsert(episode_) {
        if (state != EpisodeState.UNSPECIFIED) it.setPlayState(state)
        else {
            if (it.playState == EpisodeState.PLAYED.code) it.setPlayState(EpisodeState.UNPLAYED)
            else it.setPlayState(EpisodeState.PLAYED)
        }
        if (resetMediaPosition || it.playState == EpisodeState.PLAYED.code || it.playState == EpisodeState.IGNORED.code) it.setPosition(0)
        if (state in listOf(EpisodeState.SKIPPED, EpisodeState.PLAYED, EpisodeState.PASSED, EpisodeState.IGNORED)) it.isAutoDownloadEnabled = false
    }
    Logd(TAG, "setPlayState played0: ${result.playState}")
    if (removeFromQueue && state == EpisodeState.PLAYED && getPref(AppPrefs.prefRemoveFromQueueMarkedPlayed, true)) removeFromAllQueues(listOf(result))
    Logd(TAG, "setPlayState played1: ${result.playState}")
    return result
}

fun buildListInfo(episodes: List<Episode>): String {
    Logd(TAG, "buildListInfo")
    var infoText = String.format(Locale.getDefault(), "%d", episodes.size)
    if (episodes.isNotEmpty()) {
//        val useSpeed = getPref(AppPrefs.prefShowTimeLeft, 0) == TimeLeftMode.TimeLeftOnSpeed.ordinal
        val useSpeed = true
        var timeLeft: Long = 0
        for (item in episodes) {
            var playbackSpeed = 1f
            if (useSpeed) {
                playbackSpeed = getCurrentPlaybackSpeed(item)
                if (playbackSpeed <= 0) playbackSpeed = 1f
            }
            val itemTimeLeft: Long = (item.duration - item.position).toLong()
            timeLeft = (timeLeft + itemTimeLeft / playbackSpeed).toLong()
        }
        infoText += if (useSpeed) " * "  else " • "
        infoText += getDurationStringShort(timeLeft, true)
    }
    return infoText
}

fun List<Episode>.indexWithId(id: Long): Int = indexOfFirst { it.id == id }

fun List<Episode>.indexWithUrl(downloadUrl: String): Int = indexOfFirst { it.downloadUrl == downloadUrl }
