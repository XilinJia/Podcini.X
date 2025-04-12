package ac.mdiq.podcini.automation

import ac.mdiq.podcini.automation.AutoCleanups.EpisodeCleanupAlgorithm
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.utils.NetworkUtils.networkAllowAutoDownload
import ac.mdiq.podcini.playback.base.InTheatre.isCurMedia
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.isAutodownloadEnabled
import ac.mdiq.podcini.preferences.AppPreferences.prefStreamOverDownload
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodesSync
import ac.mdiq.podcini.storage.database.Episodes.getEpisodes
import ac.mdiq.podcini.storage.database.Episodes.getEpisodesCount
import ac.mdiq.podcini.storage.database.Feeds.getFeedList
import ac.mdiq.podcini.storage.database.Queues.addToQueueSync
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.EpisodeSortOrder.Companion.getPermutor
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PlayQueue
import ac.mdiq.podcini.storage.model.PlayState
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logt
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import io.github.xilinjia.krdb.UpdatePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object AutoDownloads {
    private const val TAG = "AutoDownloads"

    fun autodownload(context: Context, feeds: List<Feed>? = null): Job {
        return CoroutineScope(Dispatchers.IO).launch { if (isAutodownloadEnabled) AutoDownloadAlgorithm().run(context, feeds) }
    }

    fun autoenqueue(feeds: List<Feed>? = null): Job {
        return CoroutineScope(Dispatchers.IO).launch { AutoEnqueueAlgorithm().run(feeds) }
    }

    fun autodownloadForQueue(context: Context, queue: PlayQueue): Job {
        val feeds = realm.query(Feed::class).query("queueId == ${queue.id}").find()
        return CoroutineScope(Dispatchers.IO).launch { if (isAutodownloadEnabled) AutoDownloadAlgorithm().run(context, feeds, false, true) }
    }

    fun autoenqueueForQueue(queue: PlayQueue): Job {
        val feeds = realm.query(Feed::class).query("queueId == ${queue.id}").find()
        return CoroutineScope(Dispatchers.IO).launch { AutoEnqueueAlgorithm().run(feeds, true) }
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
        fun run(context: Context, feeds: List<Feed>?, checkQueues: Boolean = true, onlyExisting: Boolean = false) {
            val powerShouldAutoDl = (deviceCharging(context) || getPref(AppPrefs.prefEnableAutoDownloadOnBattery, false))
            Logd(TAG, "run prepare $networkAllowAutoDownload $powerShouldAutoDl")
            // we should only auto download if both network AND power are happy
            if (networkAllowAutoDownload && powerShouldAutoDl) {
                Logd(TAG, "run Performing auto-dl of undownloaded episodes")
                val toReplace: MutableSet<Episode> = mutableSetOf()
                val candidates: MutableSet<Episode> = mutableSetOf()
                if (checkQueues) {
                    val queues = realm.query(PlayQueue::class).find()
                    val includedQueues = getPref(AppPrefs.prefAutoDLIncludeQueues, queues.map { it.name }.toSet(), true)
                    if (includedQueues.isNotEmpty()) {
                        for (qn in includedQueues) {
                            val q = queues.first { it.name == qn }
                            val queueItems = realm.query(Episode::class).query("id IN $0 AND downloaded == false", q.episodeIds).find()
                            Logd(TAG, "run add from queue: ${q.name} ${queueItems.size}")
                            if (queueItems.isNotEmpty()) queueItems.forEach { if (!prefStreamOverDownload || it.feed?.prefStreamOverDownload != true) candidates.add(it) }
                        }
                    }
                }
                assembleFeedsCandidates(feeds, candidates, toReplace, onlyExisting = onlyExisting)
                Logd(TAG, "run candidates ${candidates.size} for download")
                if (candidates.isNotEmpty()) {
                    val autoDownloadableCount = candidates.size
                    if (toReplace.isNotEmpty()) deleteEpisodesSync(context, toReplace.toList())
                    val downloadedCount = getEpisodesCount(EpisodeFilter(EpisodeFilter.States.downloaded.name))
                    val deletedCount = toReplace.size + AutoCleanups.build().makeRoomForEpisodes(context, autoDownloadableCount - toReplace.size)
                    val appEpisodeCache = getPref(AppPrefs.prefEpisodeCacheSize, "0").toInt()
                    val cacheIsUnlimited = appEpisodeCache <= AppPreferences.EPISODE_CACHE_SIZE_UNLIMITED
                    Logd(TAG, "run cacheIsUnlimited: $cacheIsUnlimited appEpisodeCache: $appEpisodeCache downloadedCount: $downloadedCount autoDownloadableCount: $autoDownloadableCount deletedCount: $deletedCount")
                    val allowedCount =
                        if (cacheIsUnlimited || appEpisodeCache >= downloadedCount + autoDownloadableCount) autoDownloadableCount
                        else appEpisodeCache - (downloadedCount - deletedCount)
                    Logd(TAG, "run allowedCount $allowedCount")
                    if (allowedCount > 0) {
                        var itemsToDownload = candidates.toMutableList()
                        if (allowedCount < candidates.size) itemsToDownload = itemsToDownload.subList(0, allowedCount)
                        Logt(TAG, "Auto download requesting episodes: ${itemsToDownload.size}")
                        if (itemsToDownload.isNotEmpty()) {
                            for (e in itemsToDownload) {
                                Logd(TAG, "run download ${e.title} ${e.playState} ${e.downloadUrl}")
                                DownloadServiceInterface.impl?.download(context, e)
                            }
                        }
                        itemsToDownload.clear()
                    } else Logt(TAG, "Auto download not performed: candidates: ${candidates.size} allowedCount: $allowedCount")
                    candidates.clear()
                }
            } else Logt(TAG, "Auto download not performed: network: $networkAllowAutoDownload power: $powerShouldAutoDl")
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
        suspend fun run(feeds: List<Feed>?, onlyExisting: Boolean = false) {
            Logd(TAG, "Performing auto-enqueue of undownloaded episodes")
            val toReplace: MutableSet<Episode> = mutableSetOf()
            val candidates: MutableSet<Episode> = mutableSetOf()

            assembleFeedsCandidates(feeds, candidates, toReplace, dl = false, onlyExisting = onlyExisting)
            if (candidates.isNotEmpty()) {
                if (toReplace.isNotEmpty()) removeFromAllQueuesSync(*toReplace.toTypedArray())
                Logd(TAG, "Enqueueing ${candidates.size} items")
                for (e in candidates) {
                    Logd(TAG, "adding to queue ${e.title} ${e.playState} ${e.downloadUrl}")
                    val q = e.feed?.queue
                    if (q != null) {
                        val e_ = upsertBlk(e) { it.disableAutoDownload() }
                        addToQueueSync(e_, q)
                    }
                    else Loge(TAG, "auto-enqueue not performed: feed associated queue is null: ${e.title}")
                }
                Logt(TAG, "Auto enqueued episodes: ${candidates.size}")
                candidates.clear()
            }
        }
    }

    private fun assembleFeedsCandidates(feeds: List<Feed>?, candidates: MutableSet<Episode>, toReplace: MutableSet<Episode>, dl: Boolean = true, onlyExisting: Boolean = false) {
        val feeds = feeds ?: getFeedList()
        feeds.forEach { f ->
            Logd(TAG, "assembleFeedsCandidates: autoDL: ${f.autoDownload} autoEQ: ${f.autoEnqueue} isLocal: ${f.isLocalFeed} ${f.title}")
            if (((dl && f.autoDownload) || (!dl && f.autoEnqueue)) && !f.isLocalFeed) {
                val dlFilter = if (dl) {
                    if (f.countingPlayed) EpisodeFilter(EpisodeFilter.States.downloaded.name)
                    else EpisodeFilter(EpisodeFilter.States.downloaded.name,
                        EpisodeFilter.States.unplayed.name, EpisodeFilter.States.inQueue.name,
                        EpisodeFilter.States.inProgress.name, EpisodeFilter.States.skipped.name)
                } else EpisodeFilter(EpisodeFilter.States.inQueue.name)
                val downloadedCount = getEpisodesCount(dlFilter, f.id)
                var allowedDLCount = if (f.autoDLMaxEpisodes == AppPreferences.EPISODE_CACHE_SIZE_UNLIMITED) Int.MAX_VALUE else f.autoDLMaxEpisodes - downloadedCount
                Logd(TAG, "assembleFeedsCandidates ${f.autoDLMaxEpisodes} downloadedCount: $downloadedCount allowedDLCount: $allowedDLCount")
                Logd(TAG, "assembleFeedsCandidates autoDLPolicy: ${f.autoDLPolicy.name}")
                var episodes = mutableListOf<Episode>()
                var queryString = "feedId == ${f.id} AND isAutoDownloadEnabled == true AND downloaded == false"
                if (allowedDLCount > 0 && f.autoDLSoon) {
                    val queryStringSoon = queryString + " AND playState == ${PlayState.SOON.code} SORT(pubDate DESC) LIMIT($allowedDLCount)"
                    val es = realm.query(Episode::class).query(queryStringSoon).find()
                    Logd(TAG, "assembleFeedsCandidates queryStringSoon: [${es.size}] $queryStringSoon")
                    if (es.isNotEmpty()) {
                        episodes.addAll(es)
                        allowedDLCount -= es.size
                    }
                }
                if (allowedDLCount > 0 || f.autoDLPolicy.replace) {
                    when (f.autoDLPolicy) {
                        Feed.AutoDownloadPolicy.DISCRETION -> {}
                        Feed.AutoDownloadPolicy.ONLY_NEW -> {
                            if (!onlyExisting) {
                                if (f.autoDLPolicy.replace) {
                                    allowedDLCount = if (f.autoDLMaxEpisodes == AppPreferences.EPISODE_CACHE_SIZE_UNLIMITED) Int.MAX_VALUE else f.autoDLMaxEpisodes
                                    queryString += " AND playState == ${PlayState.NEW.code} SORT(pubDate DESC) LIMIT(${allowedDLCount})"
                                    val es = realm.query(Episode::class).query(queryString).find()
                                    Logd(TAG, "assembleFeedsCandidates Replace queryString: [${es.size}] $queryString")
                                    if (es.isNotEmpty()) {
                                        val numToDelete = es.size + downloadedCount - allowedDLCount
                                        Logd(TAG, "assembleFeedsCandidates numToDelete: $numToDelete")
                                        if (numToDelete > 0) {
                                            val toDelete_ = getEpisodes(dlFilter, f.id, numToDelete)
                                            if (toDelete_.isNotEmpty()) toReplace.addAll(toDelete_)
                                            Logd(TAG, "assembleFeedsCandidates toDelete_: ${toDelete_.size}")
                                        }
                                        episodes.addAll(es)
                                        Logd(TAG, "assembleFeedsCandidates episodes: ${episodes.size}")
                                    }
                                } else {
                                    queryString += " AND playState == ${PlayState.NEW.code} SORT(pubDate DESC) LIMIT(${3 * allowedDLCount})"
                                    val es = realm.query(Episode::class).query(queryString).find()
                                    Logd(TAG, "assembleFeedsCandidates Non-Replace queryString: [${es.size}] $queryString")
                                    if (es.isNotEmpty()) episodes.addAll(es)
                                }
                            }
                        }
                        Feed.AutoDownloadPolicy.NEWER -> {
                            queryString += " AND playState <= ${PlayState.SOON.code} SORT(pubDate DESC) LIMIT(${3*allowedDLCount})"
                            val es = realm.query(Episode::class).query(queryString).find()
                            Logd(TAG, "assembleFeedsCandidates Newer queryString: [${es.size}] $queryString")
                            if (es.isNotEmpty()) episodes.addAll(es)
                        }
                        Feed.AutoDownloadPolicy.OLDER -> {
                            queryString += " AND playState <= ${PlayState.SOON.code} SORT(pubDate ASC) LIMIT(${3*allowedDLCount})"
                            val es = realm.query(Episode::class).query(queryString).find()
                            Logd(TAG, "assembleFeedsCandidates Older queryString: [${es.size}] $queryString")
                            if (es.isNotEmpty()) episodes.addAll(es)
                        }
                        Feed.AutoDownloadPolicy.FILTER_SORT -> {
                            Logd(TAG, "FILTER_SORT queryString: $queryString")
                            val q = realm.query(Episode::class).query(queryString)
                            val filterADL = f.episodeFilterADL.queryString()
                            Logd(TAG, "FILTER_SORT filterADL: $filterADL")
                            if (filterADL.isNotBlank()) q.query(filterADL)
                            val es = q.find().toMutableList()
                            Logd(TAG, "assembleFeedsCandidates Filter-sort queryString: [${es.size}] $queryString")
                            if (es.isNotEmpty()) {
                                val sortOrder = f.sortOrderADL ?: EpisodeSortOrder.DATE_NEW_OLD
                                Logd(TAG, "FILTER_SORT sortOrder: $sortOrder")
                                getPermutor(sortOrder).reorder(es)
                                episodes.addAll(if (es.size > allowedDLCount) es.subList(0, allowedDLCount) else es)
                                Logd(TAG, "FILTER_SORT episodes: ${episodes.size}")
                            }
                        }
                    }
                }
                if (episodes.isNotEmpty()) {
                    var count = 0
                    for (e in episodes) {
                        if (isCurMedia(e)) continue
                        if (e.downloadUrl.isNullOrBlank()) {
                            Loge(TAG, "episode downloadUrl is null or blank, skipped from auto-download: ${e.title}")
                            upsertBlk(e) { it.disableAutoDownload() }
                            continue
                        }
                        if (f.autoDownloadFilter?.meetsAutoDLCriteria(e) == true) {
                            Logd(TAG, "assembleFeedsCandidates add to candidates: ${e.title} ${e.downloaded}")
                            candidates.add(e)
                            if (++count >= allowedDLCount) break
                        } else {
                            Logt(TAG, "episode not meed criteria for auto-download: ${e.title}")
                            upsertBlk(e) {
                                if (f.autoDownloadFilter?.markExcludedPlayed == true) it.setPlayed(true)
                                else it.disableAutoDownload()
                            }
                        }
                    }
                }
                episodes.clear()
                Logd(TAG, "assembleFeedsCandidates ${f.title} candidate size: ${candidates.size}")
                if (!onlyExisting) {
                    runOnIOScope {
                        realm.write {
                            while (true) {
                                val episodesNew = query(Episode::class, "feedId == ${f.id} AND playState == ${PlayState.NEW.code} LIMIT(20)").find()
                                if (episodesNew.isEmpty()) break
                                Logd(TAG, "run episodesNew: ${episodesNew.size}")
                                episodesNew.map { e ->
                                    e.setPlayed(false)
                                    Logd(TAG, "run reset NEW ${e.title} ${e.playState} ${e.downloadUrl}")
                                    copyToRealm(e, UpdatePolicy.ALL)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}