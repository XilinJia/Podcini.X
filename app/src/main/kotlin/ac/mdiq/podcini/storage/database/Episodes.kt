package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.EpisodeAdrDLManager
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.base.InTheatre.curState
import ac.mdiq.podcini.playback.base.InTheatre.savePlayerStatus
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.prefSpeedOf
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.ACTION_SHUTDOWN_PLAYBACK_SERVICE
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.SubscriptionLog
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.sortPairOf
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.EpisodeState.Companion.fromCode
import ac.mdiq.podcini.storage.utils.durationStringShort
import ac.mdiq.podcini.storage.utils.nowInMillis
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.fullDateTimeString
import ac.mdiq.podcini.utils.sendLocalBroadcast
import androidx.core.app.NotificationManagerCompat
import io.github.xilinjia.krdb.notifications.ResultsChange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
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
    Logd(TAG, "getEpisodesCount called queryString: $queryString $feedId")
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
fun episodeByGuidOrUrl(guid: String?, episodeUrl: String, copy: Boolean = true): Episode? {
    Logd(TAG, "episodeByGuidOrUrl called $guid $episodeUrl")
    val episode = if (guid != null) realm.query(Episode::class).query("identifier == $0", guid).first().find()
    else realm.query(Episode::class).query("downloadUrl == $0", episodeUrl).first().find()
    if (!copy || episode == null) return episode
    return realm.copyFromRealm(episode)
}

fun episodeById(id: Long): Episode? = realm.query(Episode::class).query("id == $0", id).first().find()

fun getHistoryAsFlow(feedId: Long = 0L, start: Long = 0L, end: Long = nowInMillis(), filter: EpisodeFilter? = null, sortOrder: EpisodeSortOrder = EpisodeSortOrder.PLAYED_DATE_DESC): Flow<ResultsChange<Episode>> {
    Logd(TAG, "getHistory() called")
    var qStr = "((playbackCompletionTime > 0) OR (lastPlayedTime > $start AND lastPlayedTime <= $end))"
    if (feedId > 0L) qStr += " AND feedId == $feedId "
    val fqstr = filter?.queryString()
    if (!fqstr.isNullOrBlank()) qStr += " AND $fqstr "
    val episodes = realm.query(Episode::class).query(qStr).sort(sortPairOf(sortOrder)).asFlow()
    return episodes
}

suspend fun deleteEpisodesWarnLocalRepeat(items: Iterable<Episode>) {
    val context = getAppContext()
    val localItems: MutableList<Episode> = mutableListOf()
    val repeatItems: MutableList<Episode> = mutableListOf()
    suspend fun deleteItems(items_: List<Episode>) {
        for (episode in items_) {
            if (episode.feed != null && !episode.feed!!.isLocalFeed) {
                EpisodeAdrDLManager.manager?.cancel(episode)
                if (episode.downloaded) deleteMedia(episode)
            }
        }
        if (appPrefs.deleteRemovesFromQueue) removeFromAllQueues(items_)
    }
    for (item in items) {
        var toConfirm = false
        if (item.feed?.isLocalFeed == true) {
            localItems.add(item)
            toConfirm = true
        }
        if (item.playState == EpisodeState.AGAIN.code || item.playState == EpisodeState.FOREVER.code) {
            repeatItems.add(item)
            toConfirm = true
        }
        if (!toConfirm) deleteItems(listOf(item))
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
                   runOnIOScope {
                       deleteItems(localItems)
                       userDone.complete(Unit)
                   }
                },
                onNeutral = { userDone.complete(Unit)},
                onCancel = { userDone.complete(Unit)})
        }
        userDone.await()
    }
    if (repeatItems.isNotEmpty()) {
        withContext(Dispatchers.Main) {
            commonConfirm = CommonConfirmAttrib(
                title = context.getString(R.string.delete_episode_label),
                message = context.getString(R.string.delete_repeat_warning_msg),
                confirmRes = R.string.delete_label,
                cancelRes = R.string.cancel_label,
                onConfirm = {
                    runOnIOScope { deleteItems(repeatItems) }
                })
        }
    }
}

suspend fun eraseEpisodes(episodes: List<Episode>, msg: String = "") {
    if (msg.isNotEmpty()) realm.write {
        for (e in episodes) {
            val sLog = SubscriptionLog(e.id, e.title ?: "", e.downloadUrl ?: "", e.link ?: "", SubscriptionLog.Type.Media.name)
            sLog.id = getId()
            sLog.let {
                it.rating = e.rating
                it.comment = if (e.comment.isBlank()) "" else (e.comment + "\n")
                it.comment += fullDateTimeString() + "\nReason to remove:\n" + msg
                it.cancelDate = nowInMillis()
            }
            copyToRealm(sLog)
        }
    }
    for (e in episodes) if (e.feed?.isLocalFeed != true) deleteMedia(e)
    Logd(TAG, "eraseEpisodes deleting episodes: ${episodes.size}")
    realm.write { for (e in episodes) findLatest(e)?.let { delete(it) } }
    EventFlow.postStickyEvent(FlowEvent.FeedUpdatingEvent(false))
}

suspend fun deleteMedia(episode: Episode): Episode {
    val context = getAppContext()
    val url = episode.fileUrl
    Logd(TAG, "deleteMedia [id=${episode.id}, title=${episode.getEpisodeTitle()}, downloaded=${episode.downloaded} $url")
    var episode = episode
    if (!url.isNullOrBlank()) {
        try {
            url.toUF().delete()
            episode = upsertBlk(episode) {
                it.fileUrl = null
                it.hasEmbeddedPicture = false
                if (it.playState < EpisodeState.SKIPPED.code && !shouldPreserve(it.playState)) it.setPlayState(EpisodeState.SKIPPED)
            }
            EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.removed(episode))
        } catch (e: Throwable) { Logs(TAG, e, "deleteMedia failed") }
    }

    if (episode.id == curState.curMediaId) {
        savePlayerStatus(null)
        sendLocalBroadcast(ACTION_SHUTDOWN_PLAYBACK_SERVICE)
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(R.id.notification_playing)
    }

    if (isProviderConnected) {
        // Gpodder: queue delete action for synchronization
        val action = EpisodeAction.Builder(episode, EpisodeAction.DELETE).currentTimestamp().build()
        SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(action)
    }
    return episode
}

fun checkAndMarkDuplicates(episode: Episode): Episode {
    var updated = false
    realm.writeBlocking {
        val duplicates = query(Episode::class, "title == $0 OR downloadUrl == $1", episode.title, episode.downloadUrl).find()
        if (duplicates.size > 1) {
            Logt(TAG, "Found ${duplicates.size - 1} duplicate episodes, setting to Ignored")
            val comment = "duplicate"
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
                            Logt(TAG, "Duplicate item was previously set to ${fromCode(e.playState).name} ${e.title} ${e.downloadUrl}")
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

fun shouldPreserve(stat: Int): Boolean = stat in listOf(EpisodeState.SOON.code, EpisodeState.LATER.code, EpisodeState.AGAIN.code, EpisodeState.FOREVER.code)

fun buildListInfo(episodes: List<Episode>, total: Int = 0): String {
    Logd(TAG, "buildListInfo")
    var infoText = episodes.size.toString()
    if (total > 0) infoText += "/$total"
    if (episodes.isNotEmpty()) {
        var timeLeft: Long = 0
        for (item in episodes) timeLeft += ((item.duration - item.position) / (prefSpeedOf(item).first.takeIf { it > 0 } ?: 1f)).toLong()
        infoText += " * " + durationStringShort(timeLeft, true)
    }
    return infoText
}

fun List<Episode>.indexWithId(id: Long): Int = indexOfFirst { it.id == id }

fun List<Episode>.indexWithUrl(downloadUrl: String): Int = indexOfFirst { it.downloadUrl == downloadUrl }
