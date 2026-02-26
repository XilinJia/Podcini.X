package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.storage.model.ARCHIVED_VOLUME_ID
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Episode.MediaMetadataRetrieverCompat
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_NATURAL_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Feed.Companion.TAG_ROOT
import ac.mdiq.podcini.storage.model.QueueEntry
import ac.mdiq.podcini.storage.parser.Id3MetadataReader
import ac.mdiq.podcini.storage.parser.VorbisCommentMetadataReader
import ac.mdiq.podcini.storage.parser.VorbisCommentReaderException
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.MediaType
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.CountingInputStream2
import ac.mdiq.podcini.storage.utils.DAY_MIL
import ac.mdiq.podcini.storage.utils.FOUR_DAY_MIL
import ac.mdiq.podcini.storage.utils.feedfilePath
import ac.mdiq.podcini.storage.utils.getMimeType
import ac.mdiq.podcini.storage.utils.parseDate
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.parsePatternTimestampToMillis
import android.app.backup.BackupManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.notifications.UpdatedResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import kotlin.math.abs
import ac.mdiq.podcini.storage.utils.nowInMillis
import kotlin.use
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG: String = "Feeds"

var feedOperationText by mutableStateOf("")

val feedsFlow = realm.query(Feed::class).asFlow()

var allFeeds = realm.query(Feed::class).find()
var feedsMap: Map<Long, Feed> = allFeeds.associateBy { it.id }

var feedCount by mutableIntStateOf(-1)

@Synchronized
fun getFeedList(queryString: String = ""): List<Feed> {
    return if (queryString.isEmpty()) allFeeds
    else realm.query(Feed::class, queryString).find()
}

fun compileLanguages() {
    val langsSet = mutableSetOf<String>()
//    val feeds = getFeedList()
    for (feed in allFeeds) {
        val langs = feed.langSet
        if (langs.isNotEmpty()) langsSet.addAll(langs)
        else langsSet.add("")
    }
    Logd(TAG, "langsSet: ${langsSet.size} appAttribs.langSet: ${appAttribs.langSet.size}")
    val newLanguages = langsSet - appAttribs.langSet
    if (newLanguages.isNotEmpty()) {
        upsertBlk(appAttribs) {
            it.langSet.clear()
            it.langSet.addAll(langsSet)
        }
    }
}

fun compileTags() {
    val tagsSet = mutableSetOf<String>()
//    val feeds = getFeedList()
    for (feed in allFeeds) tagsSet.addAll(feed.tags.filter { it != TAG_ROOT })
    val newTags = tagsSet - appAttribs.feedTagSet
    if (newTags.isNotEmpty()) {
        upsertBlk(appAttribs) {
            it.feedTagSet.clear()
            it.feedTagSet.addAll(tagsSet)
        }
    }
}

private var feedMonitorJob: Job? = null
fun cancelMonitorFeeds() {
    feedMonitorJob?.cancel()
    feedMonitorJob = null
}

fun monitorFeeds() {
    if (feedMonitorJob != null) return

    feedMonitorJob = CoroutineScope(Dispatchers.IO).launch {
        feedsFlow.collect { changes: ResultsChange<Feed> ->
            allFeeds = changes.list
            feedsMap = allFeeds.associateBy { it.id }
            feedCount = allFeeds.size
            Logd(TAG, "monitorFeedList feeds updated size: ${allFeeds.size}")
            when (changes) {
                is UpdatedResults -> {
                    when {
                        changes.insertions.isNotEmpty() -> {
                            compileLanguages()
                            compileTags()
                            // TODO: not sure why the state flow does not collect
                            EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.ADDED))
                        }
                        changes.changes.isNotEmpty() -> {
//                            for (i in changes.changes) {
//                                Logd(TAG, "monitorFeedList feed changed: ${feeds[i].title}")
//                            }
                        }
                        changes.deletions.isNotEmpty() -> {
                            Logd(TAG, "monitorFeedList feed deleted: ${changes.deletions.size}")
                            compileTags()
                        }
                        else -> Logd(TAG, "monitorFeedList else $changes")
                    }
                }
                else -> Logd(TAG, "monitorFeedList other $changes")
            }
        }
    }
}

fun getFeed(feedId: Long, copy: Boolean = false): Feed? {
//    val f = realm.query(Feed::class, "id == $feedId").first().find()
    val f = feedsMap[feedId]
    return if (f != null) {
        if (copy) realm.copyFromRealm(f) else f
    } else null
}

fun feedByIdentityOrID(feed: Feed, copy: Boolean = false): Feed? {
    Logd(TAG, "searchFeedByIdentifyingValueOrID called")
    if (feed.id != 0L) return getFeed(feed.id, copy)
    val feedId = feed.identifyingValue
    val f = allFeeds.firstOrNull { it.identifyingValue == feedId }
    if (f != null) return if (copy) realm.copyFromRealm(f) else f
    return null
}

/**
 * Adds new Feeds to the database or updates the old versions if they already exists. If another Feed with the same
 * identifying value already exists, this method will add new FeedItems from the new Feed to the existing Feed.
 * These FeedItems will be marked as unread with the exception of the most recent FeedItem.
 * This method can update multiple feeds at once. Submitting a feed twice in the same method call can result in undefined behavior.
 * This method should NOT be executed on the GUI thread.
 * @param newFeed The new Feed object.
 * @param removeUnlistedItems The episode list in the new Feed object is considered to be exhaustive.
 * I.e. episodes are removed from the database if they are not in this episode list.
 * @return The updated Feed from the database if it already existed, or the new Feed from the parameters otherwise.
 */
@Synchronized
fun updateFeedFull(newFeed: Feed, removeUnlistedItems: Boolean = false, overwriteStates: Boolean = false) {
    Logd(TAG, "updateFeedFull called")
    //        showStackTrace()

    Logd(TAG, "updateFeedFull newFeed id: ${newFeed.id} episodes: ${newFeed.episodes.size}")
    // Look up feed in the feedslist
    val savedFeed = feedByIdentityOrID(newFeed, true)
    if (savedFeed == null) {
        Logd(TAG, "")
        addNewFeed(newFeed)
        return
    }

    Logd(TAG, "updateFeedFull Feed with title " + newFeed.title + " already exists. Syncing new with existing one.")
    newFeed.episodes.sortedByDescending { it.pubDate }
    if (newFeed.pageNr == savedFeed.pageNr) {
        if (overwriteStates) savedFeed.updateFromOther(newFeed, true)
        else if (savedFeed.differentFrom(newFeed)) {
            Logd(TAG, "updateFeedFull Feed has updated attribute values. Updating old feed's attributes")
            savedFeed.updateFromOther(newFeed)
        }
    } else {
        Logd(TAG, "updateFeedFull New feed has a higher page number: ${newFeed.nextPageLink}")
        savedFeed.nextPageLink = newFeed.nextPageLink
    }
    Logd(TAG, "updateFeedFull savedFeed.isLocalFeed: ${savedFeed.isLocalFeed} savedFeed.prefStreamOverDownload: ${savedFeed.prefStreamOverDownload}")
    val priorMostRecent = savedFeed.mostRecentItem
    val priorMostRecentDate = priorMostRecent?.let { it.pubDate }
    var idLong = getId()
    Logd(TAG, "updateFeedFull building savedFeedAssistant")
    val savedFeedAssistant = FeedAssistant(savedFeed)
    val oldestDate = savedFeed.oldestItem?.pubDate ?: 0L
    for (idx in newFeed.episodes.indices) {
        var episode = newFeed.episodes[idx]
        if (savedFeed.limitEpisodesCount > 0 && episode.pubDate < oldestDate) continue

        val oldItems = savedFeedAssistant.guessDuplicate(episode)
        if (!oldItems.isNullOrEmpty()) {
            if (oldItems.size > 1) {
                Loge(TAG, "found duplicate episodes in feed: ${savedFeed.title}")
                for (e in oldItems) Loge(TAG, "duplicate episode: ${e.title}")
            }
            if (!newFeed.isLocalFeed) {
//            Logd(TAG, "updateFeedFull Update existing episode: ${episode.title}")
                oldItems[0].identifier = episode.identifier
                // queue for syncing with server
                if (isProviderConnected && oldItems[0].isPlayed()) {
                    val durs = oldItems[0].duration / 1000
                    val action = EpisodeAction.Builder(oldItems[0], EpisodeAction.PLAY)
                        .currentTimestamp()
                        .started(durs)
                        .position(durs)
                        .total(durs)
                        .build()
                    SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(action)
                }
            }
            upsertBlk(oldItems[0]) { it.updateFromOther(episode, overwriteStates) }
        } else {
            Logd(TAG, "updateFeedFull Found new episode: ${episode.pubDate} ${episode.title}")
//            episode.feed = savedFeed
            episode.id = idLong++
            episode.feedId = savedFeed.id
            if (!savedFeed.isLocalFeed && !savedFeed.prefStreamOverDownload) runBlocking { episode.fetchMediaSize(false) }

            if (!savedFeed.hasVideoMedia && episode.getMediaType() == MediaType.VIDEO) savedFeed.hasVideoMedia = true

            savedFeedAssistant.addidvToMap(episode)

            val pubDate = (episode.pubDate)
            if (priorMostRecentDate == null || priorMostRecentDate < (pubDate) || priorMostRecentDate == pubDate) {
                Logd(TAG, "updateFeedFull Marking episode published on $pubDate new, prior most recent date = $priorMostRecentDate")
                episode = upsertBlk(episode) { it.setPlayState(EpisodeState.NEW) }
                if (savedFeed.autoAddNewToQueue && savedFeed.queue != null) runOnIOScope { addToAssQueue(listOf(episode)) }
            } else upsertBlk(episode) {}
        }
        if (idx % 50 == 0) Logd(TAG, "updateFeedFull processing item $idx / ${newFeed.episodes.size} ")
    }
    savedFeedAssistant.clear()

    val unlistedItems: MutableList<Episode> = mutableListOf()
    // identify episodes to be removed
    if (removeUnlistedItems) {
        Logd(TAG, "updateFeedFull building newFeedAssistant")
        val newFeedAssistant = FeedAssistant(newFeed, savedFeed.id, isNew = true)

        val it = getEpisodes(null, null, feedId=savedFeed.id, copy = true).toMutableList().iterator()
        while (it.hasNext()) {
            val feedItem = it.next()
            if (newFeedAssistant.getEpisodeByIdentifyingValue(feedItem) == null) {
                unlistedItems.add(feedItem)
                it.remove()
            }
        }
        newFeedAssistant.clear()
    }

    // update attributes
    savedFeed.lastUpdate = newFeed.lastUpdate
    savedFeed.lastUpdateTime = nowInMillis()
    savedFeed.lastFullUpdateTime = nowInMillis()
    savedFeed.type = newFeed.type
    savedFeed.lastUpdateFailed = false
    savedFeed.totleDuration = 0
    Logd(TAG, "updateFeedFull savedFeed lastFullUpdateTime: ${savedFeed.lastFullUpdateTime}")

    val episodes = getEpisodes(null, null, feedId=savedFeed.id, copy = false)
    savedFeed.episodesCount = episodes.size
    for (e in episodes) savedFeed.totleDuration += e.duration

    upsertBlk(savedFeed) {}
    if (removeUnlistedItems && unlistedItems.isNotEmpty()) deleteMedias(unlistedItems)

    runOnIOScope {
        trimEpisodes(savedFeed)
        computeScores(savedFeed)
    }
}

suspend fun updateFeedSimple(newFeed: Feed) {
    Logd(TAG, "updateFeedSimple called")
    val savedFeed = feedByIdentityOrID(newFeed, true) ?: return

    Logd(TAG, "Feed with title " + newFeed.title + " already exists. Syncing new with existing one.")
    newFeed.episodes.sortedByDescending { it.pubDate }
    if (newFeed.pageNr == savedFeed.pageNr) {
        if (savedFeed.differentFrom(newFeed)) {
            Logd(TAG, "Feed has updated attribute values. Updating old feed's attributes")
            savedFeed.updateFromOther(newFeed)
        }
    } else {
        Logd(TAG, "New feed has a higher page number: ${newFeed.nextPageLink}")
        savedFeed.nextPageLink = newFeed.nextPageLink
    }
    val priorMostRecents = savedFeed.mostRecentItems
    val priorMostRecentDate = savedFeed.lastUpdateTime
    var idLong = getId()
    Logd(TAG, "updateFeedSimple building savedFeedAssistant")

    // Look for new or updated Items
    for (idx in newFeed.episodes.indices) {
        var episode = newFeed.episodes[idx]
        if (episode.duration < 1000) continue
        val pubDate = episode.pubDate
        if (pubDate <= priorMostRecentDate || episode.downloadUrl in priorMostRecents.map { it.downloadUrl} || episode.title in priorMostRecents.map { it.title }) continue

        Logd(TAG, "Found new episode: ${episode.title}")
        episode.id = idLong++
        episode.feedId = savedFeed.id
        if (!savedFeed.isLocalFeed && !savedFeed.prefStreamOverDownload) episode.fetchMediaSize(persist = false)
        if (!savedFeed.hasVideoMedia && episode.getMediaType() == MediaType.VIDEO) savedFeed.hasVideoMedia = true

        if (priorMostRecentDate < pubDate) {
            Logd(TAG, "Marking episode published on $pubDate new, prior most recent date = $priorMostRecentDate")
            episode = upsert(episode) { it.setPlayState(EpisodeState.NEW) }
            if (savedFeed.autoAddNewToQueue && savedFeed.queue != null) runOnIOScope { addToAssQueue(listOf(episode)) }
        } else upsert(episode) {}
    }

    // update attributes
    savedFeed.lastUpdate = newFeed.lastUpdate
    savedFeed.lastUpdateTime = nowInMillis()
    savedFeed.type = newFeed.type
    savedFeed.lastUpdateFailed = false
    savedFeed.totleDuration = 0
    val episodes = getEpisodes(null, null, feedId=savedFeed.id, copy = false)
    savedFeed.episodesCount = episodes.size
    for (e in episodes) savedFeed.totleDuration += e.duration

    upsert(savedFeed) {}

    trimEpisodes(savedFeed)
    computeScores(savedFeed)
}

suspend fun computeScores(f: Feed) {
    val cTime = nowInMillis()
    if (cTime - f.scoreUpdated < DAY_MIL) return
    val upCount = realm.query(Episode::class).query("feedId == $0 AND (ratingTime > $1 OR playStateSetTime > $1)", f.id, f.scoreUpdated).count().find()
    if (upCount == 0L || (upCount < 4L && cTime - f.scoreUpdated < FOUR_DAY_MIL)) return
    val episodes = realm.query(Episode::class).query("feedId == ${f.id}").find()
    if (episodes.isNotEmpty()) {
        var sumR = 0.0
        var scoreCount = 0
        for (e in episodes) {
            if (e.playState >= EpisodeState.PROGRESS.code) {
                scoreCount++
                if (e.rating != Rating.UNRATED.code) sumR += e.rating
                if (e.playState >= EpisodeState.SKIPPED.code) sumR += - 0.5 + 1.0 * e.playedDuration / e.duration
                else if (e.playState in listOf(EpisodeState.AGAIN.code, EpisodeState.FOREVER.code)) sumR += 0.5
            }
        }
        upsert(f) {
            it.scoreCount = scoreCount
            it.score = if (scoreCount > 0) (100 * sumR / scoreCount / Rating.SUPER.code).toInt() else -1000
            it.scoreUpdated = cTime
        }
    }
}

private suspend fun trimEpisodes(feed_: Feed) {
    if (feed_.limitEpisodesCount > 0) {
        val n = realm.query(Episode::class).query("feedId == ${feed_.id} AND !(${feed_.isWorthyQuerryStr})").count().find().toInt()
        if (n > feed_.limitEpisodesCount + 5) {
            val f = feedByIdentityOrID(feed_, true) ?: return
            val dc = n - f.limitEpisodesCount
            val episodes = realm.query(Episode::class).query("feedId == ${feed_.id} SORT (pubDate ASC)").find()
            realm.write {
                var n = 1
                for (e_ in episodes) {
                    val qes = query(QueueEntry::class).query("episodeId == ${e_.id}").find()
                    if (qes.isNotEmpty()) delete(qes)
                    val e = findLatest(e_)
                    if (e != null && !e.isWorthy) {
                        delete(e)
                        if (n++ >= dc) break
                    }
                }
            }
        }
    }
}

fun persistFeedLastUpdateFailed(feed: Feed, lastUpdateFailed: Boolean) : Job {
    Logd(TAG, "persistFeedLastUpdateFailed called")
    return runOnIOScope {
        upsert(feed) { it.lastUpdateFailed = lastUpdateFailed }
        EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.ERROR, feed.id))
    }
}

fun addNewFeed(feed: Feed) {
    Logd(TAG, "addNewFeeds called")
    feed.lastUpdateTime = nowInMillis()
    feed.lastFullUpdateTime = nowInMillis()
    realm.writeBlocking {
        var idLong = getId()
        feed.id = idLong
        feed.totleDuration = 0
        Logd(TAG, "feed.episodes count: ${feed.episodes.size}")
        for (episode in feed.episodes) {
            episode.id = getId()
            Logd(TAG, "addNewFeeds ${episode.id} ${episode.downloadUrl}")
            episode.feedId = feed.id
            feed.totleDuration += episode.duration
            copyToRealm(episode)
        }
        copyToRealm(feed)
    }
    if (!feed.isLocalFeed && feed.downloadUrl != null) SynchronizationQueueSink.enqueueFeedAddedIfSyncActive(feed.downloadUrl!!)
    BackupManager(getAppContext()).dataChanged()
}

suspend fun deleteFeed(feedId: Long, preserve: Boolean = false) {
    Logd(TAG, "deleteFeed called")
    val feed = feedsMap[feedId]
    val episodes = if (preserve && feed != null) feed.getUnworthyEpisodes() else getEpisodes(null, null, feedId=feedId, copy = false)
    removeFromAllQueuesQuiet(episodes.map { it.id }, false)
    eraseEpisodes(episodes, "", false)

    if (feed != null) {
        realm.write {
            findLatest(feed)?.let {
                if (preserve) {
                    it.volumeId = ARCHIVED_VOLUME_ID
                    it.keepUpdated = false
                    it.autoEnqueue = false
                    it.autoDownload = false
                    it.autoDeleteAction = Feed.AutoDeleteAction.NEVER
                    it.queue = null
                } else  delete(it)
            }
        }
        if (!feed.isLocalFeed && feed.downloadUrl != null) SynchronizationQueueSink.enqueueFeedRemovedIfSyncActive(feed.downloadUrl!!)
        BackupManager(getAppContext()).dataChanged()
    }
}

fun allowForAutoDelete(feed: Feed): Boolean {
    if (!appPrefs.autoDelete) return false
    return !feed.isLocalFeed || appPrefs.autoDeleteLocal
}

suspend fun shelveToFeed(episodes: List<Episode>, toFeed: Feed, removeChecked: Boolean = false) {
    val toFeedEpisodes = getEpisodes(null, null, feedId=toFeed.id, copy = false)
    for (e in episodes) {
        if (toFeedEpisodes.firstOrNull { it.identifyingValue == e.identifyingValue } != null) continue
        var e_ = e
        if (!removeChecked || (e.feedId != null && e.feedId!! >= MAX_SYNTHETIC_ID)) {
            e_ = realm.copyFromRealm(e)
            e_.id = getId()
            if (e.feedId != null && e.feedId!! >= MAX_SYNTHETIC_ID) {
                e_.origFeedTitle = e.feed?.title
                e_.origFeeddownloadUrl = e.feed?.downloadUrl
                e_.origFeedlink = e.feed?.link
            }
        }
        upsert(e_) { it.feedId = toFeed.id }
    }
    val eps = realm.query(Episode::class).query("feedId == ${toFeed.id}").find()
    val dur = eps.sumOf { it.duration }
    upsertBlk(toFeed) {
        it.episodesCount = eps.size
        it.totleDuration = dur.toLong()
    }
}

fun createSynthetic(feedId: Long, name: String, video: Boolean = false): Feed {
    val feed = Feed()
    var feedId_ = feedId
    if (feedId_ <= 0) {
        var i = MAX_NATURAL_SYNTHETIC_ID
        while (true) {
            if (feedsMap[i++] != null) continue
            feedId_ = --i
            break
        }
    }
    feed.id = feedId_
    feed.title = name
    feed.author = "Yours Truly"
    feed.downloadUrl = null
    feed.hasVideoMedia = video
    feed.fileUrl = File(feedfilePath, feed.getFeedfileName()).toString()
    //        feed.preferences = FeedPreferences(feed.id, false, AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
    feed.keepUpdated = false
    feed.queueId = -2L
    return feed
}

fun addRemoteToMiscSyndicate(episode: Episode) {
    fun getMiscSyndicate(): Feed {
        val feedId: Long = 11
        var feed = getFeed(feedId, true)
        if (feed != null) return feed

        feed = createSynthetic(feedId, "Misc Syndicate")
        feed.type = Feed.FeedType.RSS.name
        upsertBlk(feed) {}
        EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.ADDED))
        return feed
    }
    val feed = getMiscSyndicate()
    Logd(TAG, "addToMiscSyndicate: feed: ${feed.title}")
    val episodes = getEpisodes(null, null, feedId=feed.id, copy = false)
    if (episodes.firstOrNull { it.identifyingValue == episode.identifyingValue } != null) return
    Logd(TAG, "addToMiscSyndicate adding new episode: ${episode.title}")
    //        if (episode.feedId != null && episode.feedId!! >= MAX_SYNTHETIC_ID) {
    //            episode.origFeedTitle = episode.feed?.title
    //            episode.origFeeddownloadUrl = episode.feed?.downloadUrl
    //            episode.origFeedlink = episode.feed?.link
    //        }
//    episode.feed = feed
    episode.id = getId()
    episode.feedId = feed.id
    upsertBlk(episode) {}
//    feed.episodes.add(episode)
    upsertBlk(feed) {}
    EventFlow.postStickyEvent(FlowEvent.FeedUpdatingEvent(false))
}

fun getPreserveSyndicate(): Feed {
    val feedId: Long = 21
    var feed = getFeed(feedId, true)
    if (feed != null) return feed

    feed = createSynthetic(feedId, "Preserve Syndicate")
    feed.type = Feed.FeedType.RSS.name
    upsertBlk(feed) {}
    EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.ADDED))
    return feed
}

// savedFeedId == 0L means saved feed
class FeedAssistant(val feed: Feed, private val savedFeedId: Long = 0L, private val isNew: Boolean = false) {
    val map = mutableMapOf<String, MutableList<Episode>>()
    val tag: String = if (savedFeedId == 0L) "Saved feed" else "New feed"

    init {
//        if (savedFeedId == 0L) {
//            val ids = feed.episodes.map { it.id }
//            val elLoose = realm.query(Episode::class).query("feedId == ${feed.id} AND (NOT (id IN $0))", ids).find()
//            if (elLoose.isNotEmpty()) {
//                Logt(TAG, "Found ${elLoose.size} loose episodes")
//                elLoose.forEach {
//                    feed.episodes.add(realm.copyFromRealm(it))
//                }
//                //                    feed.episodes.addAll(elLoose)
//            }
//        }
        val iterator = if (isNew) feed.episodes.iterator() else getEpisodes(null, null, feedId=feed.id, copy = true).iterator()
        while (iterator.hasNext()) {
            val e = iterator.next()
            if (!e.identifier.isNullOrEmpty()) {
                if (map.containsKey(e.identifier!!)) {
                    Logd(TAG, "FeedAssistant init $tag identifier duplicate: ${e.identifier} ${e.title}")
//                    if (savedFeedId > 0L) {
//                        //                             addDownloadStatus(e, map[e.identifier!!]!!)
//                        iterator.remove()
//                    }
                    map[e.identifier!!]!!.add(e)
                } else map[e.identifier!!] = mutableListOf(e)
            }
            val idv = e.identifyingValue
            if (idv != e.identifier && !idv.isNullOrEmpty()) {
                if (map.containsKey(idv)) {
                    Logd(TAG, "FeedAssistant init $tag identifyingValue duplicate: $idv ${e.title}")
//                    if (savedFeedId > 0L) {
//                        //                             addDownloadStatus(e, map[idv]!!)
//                        iterator.remove()
//                    }
                    map[idv]!!.add(e)
                } else map[idv] = mutableListOf(e)
            }
            val url = e.downloadUrl
            if (url != idv && !url.isNullOrEmpty()) {
                if (map.containsKey(url)) {
                    Logd(TAG, "FeedAssistant init $tag url duplicate: $url ${e.title}")
//                    if (savedFeedId > 0L) {
//                        //                             addDownloadStatus(e, map[url]!!)
//                        iterator.remove()
//                    }
                    map[url]!!.add(e)
                } else map[url] = mutableListOf(e)
            }
            val title = canonicalizeTitle(e.title)
            if (title != idv && title.isNotEmpty()) {
                if (map.containsKey(title)) {
                    Logd(TAG, "FeedAssistant init $tag title duplicate: $title ${e.title}")
//                    if (savedFeedId > 0L) {
//                        //                             addDownloadStatus(e, map[url]!!)
//                        iterator.remove()
//                    }
                } else map[title] = mutableListOf(e)
            }
        }
        if (savedFeedId == 0L) {
            for ((k, v) in map.entries) {
                if (v.size < 2) continue
                Logd(TAG, "FeedAssistant removing ${v.size-1} duplicates on $k")
                var episode = v[0]
                val ecs = v.sortedByDescending { it.comment.length }
                val comment = if (ecs[0].comment.isBlank()) "" else {
                    var c = ecs[0].comment
                    for (i in 1..<ecs.size) if (ecs[i].comment.isNotBlank()) c += "\n" + ecs[i].comment
                    c
                }
                val ers = v.sortedByDescending { it.rating }
                if (ers[0].rating > Rating.UNRATED.code) {
                    episode = if (ers[0].id != ecs[0].id && comment.isNotEmpty()) upsertBlk(ers[0]) { it.addComment(comment) } else ers[0]
                    runOnIOScope { realm.write { for (i in 1..<ers.size) {
                        val e = query(Episode::class).query("id == ${ers[i].id}").first().find()
                        if (e != null) delete(e)
                    } } }
                } else {
                    val eps = v.sortedByDescending { it.lastPlayedTime }
                    if (eps[0].lastPlayedTime > 0L) {
                        episode = if (eps[0].id != ecs[0].id && comment.isNotEmpty()) upsertBlk(eps[0]) { it.addComment(comment) } else eps[0]
                        runOnIOScope { realm.write { for (i in 1..<eps.size) {
                            val e = query(Episode::class).query("id == ${eps[i].id}").first().find()
                            if (e != null) delete(e)
                        } } }
                    } else {
                        val eps = v.sortedByDescending { it.pubDate }
                        episode = if (eps[0].id != ecs[0].id && comment.isNotEmpty()) upsertBlk(eps[0]) { it.addComment(comment) } else eps[0]
                        runOnIOScope { realm.write { for (i in 1..<eps.size) {
                            val e = query(Episode::class).query("id == ${eps[i].id}").first().find()
                            if (e != null) delete(e)
                        } } }
                    }
                }
                map[k] = mutableListOf(episode)
            }
        }
    }
    //        fun addUrlToMap(episode: Episode) {
    //            val url = episode.downloadUrl
    //            if (url != episode.identifyingValue && !url.isNullOrEmpty() && !map.containsKey(url)) map[url] = episode
    //        }
    fun addidvToMap(episode: Episode) {
        val idv = episode.identifyingValue
        if (idv != episode.identifier && !idv.isNullOrEmpty()) {
            if (map.containsKey(idv)) map[idv]!!.add(episode)
            else map[idv] = mutableListOf(episode)
        }
    }
    private fun addDownloadStatus(episode: Episode, possibleDuplicate: Episode) {
        fun duplicateEpisodeDetails(episode: Episode): String {
            return ("""
                Title: ${episode.title}
                ID: ${episode.identifier}
                """.trimIndent() + """
                 
                URL: ${episode.downloadUrl}
                """.trimIndent())
        }
        addDownloadStatus(DownloadResult(savedFeedId, episode.title ?: "", DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, false,
            """
                The podcast host appears to have added the same episode twice. Podcini still refreshed the feed and attempted to repair it.

                Original episode:
                ${duplicateEpisodeDetails(episode)}

                Second episode that is also in the feed:
                ${duplicateEpisodeDetails(possibleDuplicate)}
                """.trimIndent()))
    }
    fun getEpisodeByIdentifyingValue(item: Episode): List<Episode>? = map[item.identifyingValue]
    fun guessDuplicate(item: Episode): List<Episode>? {
        var episodes = map[item.identifier]
        if (!episodes.isNullOrEmpty()) return episodes
        val url = item.downloadUrl
        if (!url.isNullOrEmpty()) {
            episodes = map[url]
            if (!episodes.isNullOrEmpty()) return episodes
        }
        val title = canonicalizeTitle(item.title)
        if (title.isNotEmpty()) {
            episodes = map[title]
            if (!episodes.isNullOrEmpty()) return episodes
            //                if (!episodes.isNullOrEmpty()) {
            //                    val e = episodes[0]
            //                    if (datesLookSimilar(e, item) && durationsLookSimilar(e, item) && mimeTypeLooksSimilar(e, item)) return e
            //                }
        }
        return null
    }
    fun clear() = map.clear()
}

@OptIn(ExperimentalUuidApi::class)
fun updateLocalFeed(feed: Feed, progressCB: ((Int, Int)->Unit)? = null) {
    /**
     * Android's DocumentFile is slow because every single method call queries the ContentResolver.
     * This queries the ContentResolver a single time with all the information.
     */
    data class FastDocumentFile(val name: String, val type: String, val uri: Uri, val length: Long, val lastModified: Long)

    fun getImageUrl(files: List<FastDocumentFile>, folderUri: Uri): String {
        val PREFERRED_FEED_IMAGE_FILENAMES: Array<String> = arrayOf("folder.jpg", "Folder.jpg", "folder.png", "Folder.png")
        for (iconLocation in PREFERRED_FEED_IMAGE_FILENAMES) {
            for (file in files) if (iconLocation == file.name) return file.uri.toString()
        }
        for (file in files) {
            val mime = file.type
            if (mime.startsWith("image/jpeg") || mime.startsWith("image/png")) return file.uri.toString()
        }
        return Feed.PREFIX_GENERATIVE_COVER + folderUri
    }
    fun createEpisode(feed: Feed, file: FastDocumentFile): Episode {
        val item = Episode(0L, file.name, Uuid.random().toString(), file.name, file.lastModified, EpisodeState.UNPLAYED.code, feed)
        item.isAutoDownloadEnabled = false
        val size = file.length
        Logd(TAG, "createEpisode file.uri: ${file.uri}")
        item.fillMedia(0, 0, size, file.type, file.uri.toString(), file.uri.toString(), false, 0L, 0, 0)
        val episodes = feed.episodes
        for (existingItem in episodes) {
            if (existingItem.downloadUrl == file.uri.toString() && existingItem.size == file.length) {
                // We found an old file that we already scanned. Re-use metadata.
                item.updateFromOther(existingItem)
                return item
            }
        }
        // Did not find existing item. Scan metadata.
        try {
            MediaMetadataRetrieverCompat().use { mediaMetadataRetriever ->
                val NULL_DATE_PLACEHOLDER = "19040101T000000.000Z"
                mediaMetadataRetriever.setDataSource(getAppContext(), file.uri)
                val dateStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                if (!dateStr.isNullOrEmpty() && dateStr != NULL_DATE_PLACEHOLDER) {
                    try {
                        item.pubDate = parsePatternTimestampToMillis("yyyyMMdd'T'HHmmss", dateStr)?: 0L
                    } catch (e: Throwable) {
                        Logs(TAG, e, "loadMetadata failed")
                        val date = parseDate(dateStr)
                        if (date != null) item.pubDate = date.toEpochMilliseconds()
                    }
                }
                val title = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                if (!title.isNullOrEmpty()) item.title = title
                val durationStr = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                if (!durationStr.isNullOrBlank()) item.duration = durationStr.toIntOrNull() ?: 30000
                item.hasEmbeddedPicture = (mediaMetadataRetriever.embeddedPicture != null)
                try {
                    getAppContext().contentResolver.openInputStream(file.uri).use { inputStream ->
                        val reader = Id3MetadataReader(CountingInputStream2(BufferedInputStream(inputStream)))
                        reader.readInputStream()
                        item.setDescriptionIfLonger(reader.comment)
                    }
                } catch (e: Throwable) {
                    Logs(TAG, e, "Unable to parse ID3 of " + file.uri + ": ")
                    try {
                        getAppContext().contentResolver.openInputStream(file.uri)?.use { inputStream ->
                            val reader = VorbisCommentMetadataReader(inputStream)
                            reader.readInputStream()
                            item.setDescriptionIfLonger(reader.description)
                        }
                    } catch (e2: IOException) { Logs(TAG, e2, "Unable to parse vorbis comments of " + file.uri + ": ")
                    } catch (e2: VorbisCommentReaderException) { Logs(TAG, e2, "Unable to parse vorbis comments of " + file.uri + ": ") }
                }
            }
        } catch (e: Exception) {
            Logs(TAG, e, "loadMetadata failed")
            item.setDescriptionIfLonger(e.message) }
        return item
    }
    fun listFastFiles(folderUri: Uri?): List<FastDocumentFile> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getDocumentId(folderUri))
        val cursor = getAppContext().contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)
        val list = mutableListOf<FastDocumentFile>()
        while (cursor!!.moveToNext()) {
            val id = cursor.getString(0)
            val uri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id)
            val name = cursor.getString(1)
            val size = cursor.getLong(2)
            val lastModified = cursor.getLong(3)
            val mimeType = cursor.getString(4)
            list.add(FastDocumentFile(name, mimeType, uri, size, lastModified))
        }
        cursor.close()
        return list
    }

    if (feed.downloadUrl.isNullOrEmpty()) return
    val uriString = feed.downloadUrl!!.replace(Feed.PREFIX_LOCAL_FOLDER, "")
    val documentFolder = DocumentFile.fromTreeUri(getAppContext(), uriString.toUri()) ?: throw IOException("Unable to retrieve document tree. Try re-connecting the folder on the podcast info page.")
    if (!documentFolder.exists() || !documentFolder.canRead()) throw IOException("Cannot read local directory. Try re-connecting the folder on the podcast info page.")

    val folderUri = documentFolder.uri
    val allFiles = listFastFiles(folderUri)
    val mediaFiles: MutableList<FastDocumentFile> = mutableListOf()
    val mediaFileNames: MutableSet<String> = HashSet()
    for (file in allFiles) {
        val mimeType = getMimeType(file.type, file.uri.toString()) ?: continue
        val mediaType = MediaType.fromMimeType(mimeType)
        if (mediaType == MediaType.AUDIO || mediaType == MediaType.VIDEO) {
            mediaFiles.add(file)
            mediaFileNames.add(file.name)
            Logd(TAG, "updateLocalFeed add to mediaFileNames ${file.name}")
        }
    }

    // add new files to feed and update item data
    val newItems = mutableListOf<Episode>()
    for (i in mediaFiles.indices) {
        Logd(TAG, "updateLocalFeed mediaFiles ${mediaFiles[i].name}")
        val oldItem = realm.query(Episode::class).query("feedId == ${feed.id} AND link == $0", mediaFiles[i].name).first().find()
        val newItem = createEpisode(feed, mediaFiles[i])
        Logd(TAG, "updateLocalFeed oldItem: ${oldItem?.title} url: ${oldItem?.downloadUrl}")
        Logd(TAG, "updateLocalFeed newItem: ${newItem.title} url: ${newItem.downloadUrl}")
        if (oldItem != null) upsertBlk(oldItem) { it.updateFromOther(newItem) }
        else newItems.add(newItem)
        progressCB?.invoke(i, mediaFiles.size)
    }
    // remove feed items without corresponding file
    val it = newItems.iterator()
    while (it.hasNext()) {
        val item = it.next()
        if (!mediaFileNames.contains(item.link)) {
            Logt(TAG, "updateLocalFeed removing episode without file: ${item.link} ${item.title} ")
            it.remove()
        }
    }
    feed.imageUrl = getImageUrl(allFiles, folderUri)
    feed.episodes.addAll(newItems)
    updateFeedFull(feed, removeUnlistedItems = true)

    fun mustReportDownloadSuccessful(feed: Feed): Boolean {
        val downloadResults = getFeedDownloadLog(feed.id).toMutableList()
        // report success if never reported before
        if (downloadResults.isEmpty()) return true
        downloadResults.sortWith { ds1: DownloadResult, ds2: DownloadResult -> (ds1.completionTime - ds2.completionTime).toInt() }
        val lastDownloadResult = downloadResults[downloadResults.size - 1]
        // report success if the last update was not successful
        // (avoid logging success again if the last update was ok)
        return !lastDownloadResult.isSuccessful
    }

    if (mustReportDownloadSuccessful(feed)) {
        val status = DownloadResult(feed.id, feed.title?:"", DownloadError.SUCCESS, true, "")
        addDownloadStatus(status)
        persistFeedLastUpdateFailed(feed, false)
    }
}

/**
 * Publishers sometimes mess up their feed by adding episodes twice or by changing the ID of existing episodes.
 * This class tries to guess if publishers actually meant another episode,
 * even if their feed explicitly says that the episodes are different.
 */
internal fun canonicalizeTitle(title: String?): String {
    if (title == null) return ""
    return title.trim { it <= ' ' }.replace('“', '"').replace('”', '"').replace('„', '"').replace('—', '-')
}
//internal fun datesLookSimilar(item1: Episode, item2: Episode): Boolean {
//    //            if (item1.getPubDate() == null || item2.getPubDate() == null) return false
//    val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.US) // MM/DD/YY
//    val dateOriginal = dateFormat.format(item2.pubDate)
//    val dateNew = dateFormat.format(item1.pubDate)
//    return dateOriginal == dateNew // Same date; time is ignored.
//}
internal fun durationsLookSimilar(media1: Episode, media2: Episode): Boolean {
    return abs((media1.duration - media2.duration).toDouble()) < 10 * 60L * 1000L
}
internal fun mimeTypeLooksSimilar(media1: Episode, media2: Episode): Boolean {
    var mimeType1 = media1.mimeType
    var mimeType2 = media2.mimeType
    if (mimeType1 == null || mimeType2 == null) return true
    if (mimeType1.contains("/") && mimeType2.contains("/")) {
        mimeType1 = mimeType1.substringBefore("/")
        mimeType2 = mimeType2.substringBefore("/")
    }
    return (mimeType1 == mimeType2)
}
private fun sameAndNotEmpty(string1: String?, string2: String?): Boolean {
    if (string1.isNullOrEmpty() || string2.isNullOrEmpty()) return false
    return string1 == string2
}
private fun titlesLookSimilar(item1: Episode, item2: Episode): Boolean {
    return sameAndNotEmpty(canonicalizeTitle(item1.title), canonicalizeTitle(item2.title))
}

