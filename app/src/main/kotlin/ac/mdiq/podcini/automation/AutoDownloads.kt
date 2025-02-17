package ac.mdiq.podcini.automation

import ac.mdiq.podcini.automation.AutoCleanups.EpisodeCleanupAlgorithm
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.utils.NetworkUtils.isAutoDownloadAllowed
import ac.mdiq.podcini.playback.base.InTheatre.isCurMedia
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.isAutodownloadEnabled
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodesSync
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Episodes.getEpisodesCount
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Queues.addToQueueSync
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.*
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logt
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import io.realm.kotlin.UpdatePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object AutoDownloads {
    private const val TAG = "AutoDownloads"

    fun autodownload(context: Context, feeds: List<Feed>? = null): Job {
        return CoroutineScope(Dispatchers.IO).launch { AutoDownloadAlgorithm().run(context, feeds) }
    }

    fun autoenqueue(feeds: List<Feed>? = null): Job {
        return CoroutineScope(Dispatchers.IO).launch { AutoEnqueueAlgorithm().run(feeds) }
    }

    /**
     * Implements the automatic download algorithm used by Podcini. This class assumes that
     * the client uses the [EpisodeCleanupAlgorithm].
     */
    class AutoDownloadAlgorithm {
        private val TAG = "AutoDownloadAlgorithm"
        /**
         * Looks for undownloaded episodes in the queue or list of new items and request a download if
         * 1. Network is available
         * 2. The device is charging or the user allows auto download on battery
         * 3. There is free space in the episode cache
         * This method is executed on an internal single thread executor.
         * @param context  Used for accessing the DB.
         */
        fun run(context: Context, feeds: List<Feed>?) {
            // true if we should auto download based on network status
            val networkShouldAutoDl = (isAutoDownloadAllowed && isAutodownloadEnabled)
            // true if we should auto download based on power status
            val powerShouldAutoDl = (deviceCharging(context) || getPref(AppPrefs.prefEnableAutoDownloadOnBattery, false))
            Logd(TAG, "autoDownloadEpisodeMedia prepare $networkShouldAutoDl $powerShouldAutoDl")
            // we should only auto download if both network AND power are happy
            if (networkShouldAutoDl && powerShouldAutoDl) {
                Logd(TAG, "autoDownloadEpisodeMedia Performing auto-dl of undownloaded episodes")
                val toReplace: MutableSet<Episode> = mutableSetOf()
                val candidates: MutableSet<Episode> = mutableSetOf()
                val queues = realm.query(PlayQueue::class).find()
                val includedQueues = getPref(AppPrefs.prefAutoDLIncludeQueues, queues.map { it.name }.toSet(), true)
                if (includedQueues.isNotEmpty()) {
                    for (qn in includedQueues) {
                        val q = queues.first { it.name == qn }
                        val queueItems = realm.query(Episode::class).query("id IN $0 AND downloaded == false", q.episodeIds).find()
                        Logd(TAG, "autoDownloadEpisodeMedia add from queue: ${q.name} ${queueItems.size}")
                        if (queueItems.isNotEmpty()) candidates.addAll(queueItems)
                    }
                }
                assembleFeedsCandidates(feeds, candidates, toReplace)
                runOnIOScope {
                    val feeds = feeds ?: getFeedList()
                    feeds.forEach { f ->
                        realm.write {
                            while (true) {
                                val episodesNew = query(Episode::class, "feedId == ${f.id} AND playState == ${PlayState.NEW.code} LIMIT(20)").find()
                                if (episodesNew.isEmpty()) break
                                Logd(AutoDownloads.TAG, "autoDownloadEpisodeMedia episodesNew: ${episodesNew.size}")
                                episodesNew.map { e ->
                                    e.setPlayed(false)
                                    Logd(AutoDownloads.TAG, "autoDownloadEpisodeMedia reset NEW ${e.title} ${e.playState} ${e.downloadUrl}")
                                    copyToRealm(e, UpdatePolicy.ALL)
                                }
                            }
                        }
                    }
                }
                if (candidates.isNotEmpty()) {
                    val autoDownloadableCount = candidates.size
                    if (toReplace.isNotEmpty()) deleteEpisodesSync(context, toReplace.toList())
                    val downloadedCount = getEpisodesCount(EpisodeFilter(EpisodeFilter.States.downloaded.name))
                    val deletedCount = toReplace.size + AutoCleanups.build().makeRoomForEpisodes(context, autoDownloadableCount - toReplace.size)
                    val appEpisodeCache = getPref(AppPrefs.prefEpisodeCacheSize, "0").toInt()
                    val cacheIsUnlimited = appEpisodeCache <= AppPreferences.EPISODE_CACHE_SIZE_UNLIMITED
                    val allowedCount =
                        if (cacheIsUnlimited || appEpisodeCache >= downloadedCount + autoDownloadableCount) autoDownloadableCount
                        else appEpisodeCache - (downloadedCount - deletedCount)
                    if (allowedCount > 0) {
                        var itemsToDownload = candidates.toMutableList()
                        if (allowedCount < candidates.size) itemsToDownload = itemsToDownload.subList(0, allowedCount)
                        if (itemsToDownload.isNotEmpty()) {
                            Logt(TAG, "Enqueueing ${itemsToDownload.size} items for download")
                            for (e in itemsToDownload) {
                                Logd(TAG, "autoDownloadEpisodeMedia reset NEW ${e.title} ${e.playState} ${e.downloadUrl}")
                                DownloadServiceInterface.impl?.download(context, e)
                            }
                        }
                        Logt(TAG, "Auto downloaded episodes: ${itemsToDownload.size}")
                        itemsToDownload.clear()
                    }
                    candidates.clear()
                }
            }
            else Logt(TAG, "Auto download not performed: network allowed: $networkShouldAutoDl power allowed $powerShouldAutoDl")
        }

        fun deviceCharging(context: Context): Boolean {
            // from http://developer.android.com/training/monitoring-device-state/battery-monitoring.html
            val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, iFilter)

            val status = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            return (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
        }
    }

    class AutoEnqueueAlgorithm {
        private val TAG = "AutoEnqueueAlgorithm"
        suspend fun run(feeds: List<Feed>?) {
            Logd(TAG, "Performing auto-dl of undownloaded episodes")
            val toReplace: MutableSet<Episode> = mutableSetOf()
            val candidates: MutableSet<Episode> = mutableSetOf()

            assembleFeedsCandidates(feeds, candidates, toReplace, dl = false)
            if (candidates.isNotEmpty()) {
                if (toReplace.isNotEmpty()) removeFromAllQueuesSync(*toReplace.toTypedArray())
                if (candidates.isNotEmpty()) {
                    Logt(TAG, "Enqueueing ${candidates.size} items")
                    for (e in candidates) {
                        Logd(TAG, "reset NEW ${e.title} ${e.playState} ${e.downloadUrl}")
                        val q = e.feed?.queue ?: continue
                        addToQueueSync(e, q)
                    }
                }
                Logt(TAG, "Auto enqueued episodes: ${candidates.size}")
                candidates.clear()
            }
        }
    }

    private fun assembleFeedsCandidates(feeds: List<Feed>?, candidates: MutableSet<Episode>, toReplace: MutableSet<Episode>, dl: Boolean = true) {
        val feeds = feeds ?: getFeedList()
        feeds.forEach { f ->
            if (((dl && f.autoDownload) || (!dl && f.autoEnqueue)) && !f.isLocalFeed) {
                val dlFilter = if (dl) {
                    if (f.countingPlayed) EpisodeFilter(EpisodeFilter.States.downloaded.name)
                    else EpisodeFilter(EpisodeFilter.States.downloaded.name,
                        EpisodeFilter.States.unplayed.name, EpisodeFilter.States.inQueue.name,
                        EpisodeFilter.States.inProgress.name, EpisodeFilter.States.skipped.name)
                } else EpisodeFilter(EpisodeFilter.States.inQueue.name)
                val downloadedCount = getEpisodesCount(dlFilter, f.id)
                var allowedDLCount = if (f.autoDLMaxEpisodes == AppPreferences.EPISODE_CACHE_SIZE_UNLIMITED) Int.MAX_VALUE else f.autoDLMaxEpisodes - downloadedCount
                Logd(TAG, "autoDownloadEpisodeMedia ${f.autoDLMaxEpisodes} downloadedCount: $downloadedCount allowedDLCount: $allowedDLCount")
                Logd(TAG, "autoDownloadEpisodeMedia autoDLPolicy: ${f.autoDLPolicy.name}")
                var episodes = mutableListOf<Episode>()
                var queryString = "feedId == ${f.id} AND isAutoDownloadEnabled == true AND downloaded == false"
                if (allowedDLCount > 0) {
                    if (f.autoDLSoon) {
                        val queryStringSoon = queryString + " AND playState == ${PlayState.SOON.code} SORT(pubDate DESC) LIMIT($allowedDLCount)"
                        val es = realm.query(Episode::class).query(queryStringSoon).find()
                        if (es.isNotEmpty()) {
                            episodes.addAll(es)
                            allowedDLCount -= es.size
                        }
                    }
                }
                if (allowedDLCount > 0 || f.autoDLPolicy.replace) {
                    when (f.autoDLPolicy) {
                        Feed.AutoDownloadPolicy.ONLY_NEW -> {
                            if (f.autoDLPolicy.replace) {
                                allowedDLCount = if (f.autoDLMaxEpisodes == AppPreferences.EPISODE_CACHE_SIZE_UNLIMITED) Int.MAX_VALUE else f.autoDLMaxEpisodes
                                queryString += " AND playState == ${PlayState.NEW.code} SORT(pubDate DESC) LIMIT($allowedDLCount)"
                                Logd(TAG, "autoDownloadEpisodeMedia queryString: $queryString")
                                val es = realm.query(Episode::class).query(queryString).find()
                                if (es.isNotEmpty()) {
                                    val numToDelete = es.size + downloadedCount - allowedDLCount
                                    Logd(TAG, "autoDownloadEpisodeMedia numToDelete: $numToDelete")
                                    val toDelete_ = getEpisodes(dlFilter, f.id, numToDelete)
                                    if (toDelete_.isNotEmpty()) toReplace.addAll(toDelete_)
                                    Logd(TAG, "autoDownloadEpisodeMedia toDelete_: ${toDelete_.size}")
                                    episodes.addAll(es)
                                    Logd(TAG, "autoDownloadEpisodeMedia episodes: ${episodes.size}")
                                }
                            } else {
                                queryString += " AND playState == ${PlayState.NEW.code} SORT(pubDate DESC) LIMIT($allowedDLCount)"
                                val es = realm.query(Episode::class).query(queryString).find()
                                if (es.isNotEmpty()) episodes.addAll(es)
                            }
                        }
                        Feed.AutoDownloadPolicy.NEWER -> {
                            queryString += " AND playState <= ${PlayState.SOON.code} SORT(pubDate DESC) LIMIT($allowedDLCount)"
                            val es = realm.query(Episode::class).query(queryString).find()
                            if (es.isNotEmpty()) episodes.addAll(es)
                        }
//                        Feed.AutoDownloadPolicy.SOON -> {
//                            queryString += " AND playState == ${PlayState.SOON.code} SORT(pubDate DESC) LIMIT($allowedDLCount)"
//                            episodes = realm.query(Episode::class).query(queryString).find().toMutableList()
//                        }
                        Feed.AutoDownloadPolicy.OLDER -> {
                            queryString += " AND playState <= ${PlayState.SOON.code} SORT(pubDate ASC) LIMIT($allowedDLCount)"
                            val es = realm.query(Episode::class).query(queryString).find()
                            if (es.isNotEmpty()) episodes.addAll(es)
                        }
                        Feed.AutoDownloadPolicy.FILTER_SORT -> {
                            Logd(TAG, "FILTER_SORT queryString: $queryString")
                            val q = realm.query(Episode::class).query(queryString)
                            val filterADL = f.episodeFilterADL.queryString()
                            Logd(TAG, "FILTER_SORT filterADL: $filterADL")
                            if (filterADL.isNotBlank()) q.query(filterADL)
                            val eList = q.find().toMutableList()
                            Logd(TAG, "FILTER_SORT eList: ${eList.size}")
                            if (eList.isNotEmpty()) {
                                val sortOrder = f.sortOrderADL ?: EpisodeSortOrder.DATE_NEW_OLD
                                Logd(TAG, "FILTER_SORT sortOrder: $sortOrder")
                                getPermutor(sortOrder).reorder(eList)
                                episodes.addAll(if (eList.size > allowedDLCount) eList.subList(0, allowedDLCount) else eList)
                                Logd(TAG, "FILTER_SORT episodes: ${episodes.size}")
                            }
                        }
                    }
                    if (episodes.isNotEmpty()) {
                        var count = 0
                        for (e in episodes) {
                            if (isCurMedia(e)) continue
                            if (f.autoDownloadFilter?.meetsAutoDLCriteria(e) == true) {
                                Logd(TAG, "autoDownloadEpisodeMedia add to cadidates: ${e.title} ${e.downloaded}")
                                candidates.add(e)
                                if (++count >= allowedDLCount) break
                            } else upsertBlk(e) { it.setPlayed(true)}
                        }
                    }
                    episodes.clear()
                }
                Logd(TAG, "autoDownloadEpisodeMedia ${f.title} candidate size: ${candidates.size}")
            }
        }
    }
}