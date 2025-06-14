package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.database.Episodes.deleteEpisodesSync
import ac.mdiq.podcini.storage.database.Feeds.EpisodeAssistant.searchEpisodeByIdentifyingValue
import ac.mdiq.podcini.storage.database.Feeds.EpisodeDuplicateGuesser.canonicalizeTitle
import ac.mdiq.podcini.storage.database.LogsAndStats.addDownloadStatus
import ac.mdiq.podcini.storage.database.Queues.addToQueueSync
import ac.mdiq.podcini.storage.database.Queues.removeFromAllQueuesQuiet
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.runOnIOScope
import ac.mdiq.podcini.storage.database.RealmDB.upsert
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_NATURAL_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Feed.Companion.MAX_SYNTHETIC_ID
import ac.mdiq.podcini.storage.model.Feed.Companion.TAG_ROOT
import ac.mdiq.podcini.storage.model.Feed.Companion.newId
import ac.mdiq.podcini.storage.utils.MediaType
import ac.mdiq.podcini.storage.utils.EpisodeState
import ac.mdiq.podcini.storage.utils.Rating
import ac.mdiq.podcini.storage.utils.StorageUtils.feedfilePath
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.Logt
import android.app.backup.BackupManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import io.github.xilinjia.krdb.ext.asFlow
import io.github.xilinjia.krdb.notifications.InitialResults
import io.github.xilinjia.krdb.notifications.ResultsChange
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import io.github.xilinjia.krdb.notifications.UpdatedResults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutionException
import kotlin.math.abs
import kotlin.text.startsWith

object Feeds {
    private val TAG: String = Feeds::class.simpleName ?: "Anonymous"
    var feedOperationText by mutableStateOf("")

    private val tags = mutableStateListOf<String>()
    val languages = mutableStateListOf<String>()

    @Synchronized
    fun getFeedList(queryString: String = ""): List<Feed> {
        return if (queryString.isEmpty()) realm.query(Feed::class).find()
        else realm.query(Feed::class, queryString).find()
    }

    fun getFeedCount(): Int = realm.query(Feed::class).count().find().toInt()

    fun compileLanguages() {
        val langsSet = mutableSetOf<String>()
        val feeds = getFeedList()
        for (feed in feeds) langsSet.add(if (!feed.language.isNullOrBlank()) feed.language!! else "")
        val newLanguages = langsSet - languages.toSet()
        if (newLanguages.isNotEmpty()) {
            languages.clear()
            languages.addAll(langsSet)
            languages.sort()
//            EventFlow.postEvent(FlowEvent.FeedTagsChangedEvent())
        }
    }

    fun getTags(): List<String> = tags

    fun compileTags() {
        val tagsSet = mutableSetOf<String>()
        val feeds = getFeedList()
        for (feed in feeds) tagsSet.addAll(feed.tags.filter { it != TAG_ROOT })
        val newTags = tagsSet - tags.toSet()
        if (newTags.isNotEmpty()) {
            tags.clear()
            tags.addAll(tagsSet)
            tags.sort()
            EventFlow.postEvent(FlowEvent.FeedTagsChangedEvent())
        }
    }

    private val feedIds = MutableStateFlow<List<Long>>(emptyList())
    private var monitoringJob: Job? = null

    fun monitorFeeds(scope: CoroutineScope) {
        Logd(TAG, "monitorFeeds starting")
        monitoringJob?.cancel()
        monitoringJob = scope.launch(Dispatchers.IO) {
            feedIds.collect { itemIds ->
                Logd(TAG, "monitorFeeds itemIds: ${itemIds.size}")
                val results = if (itemIds.isNotEmpty()) realm.query(Feed::class).query("id IN ${itemIds.joinToString(prefix = "{", postfix = "}", separator = ", ")}").find()
                else realm.query(Feed::class).find()
                Logd(TAG, "monitorFeeds results: ${results.size}")
                results.map { it.asFlow() }
                    .merge()
                    .collect { changes: SingleQueryChange<Feed> ->
                        when (changes) {
                            is UpdatedObject -> {
                                Logd(TAG, "monitorFeed UpdatedObject ${changes.obj.title} ${changes.changedFields.joinToString()}")
                                if (changes.changedFields.isNotEmpty() && (changes.changedFields.size > 1 || changes.changedFields[0] != "lastPlayed"))
                                    EventFlow.postEvent(FlowEvent.FeedChangeEvent(changes.obj, changes.changedFields))
                            }
//                    is DeletedObject -> {
//                        Logd(TAG, "monitorFeed DeletedObject ${feed.title}")
//                        EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.REMOVED, feed.id))
//                    }
                            else -> {}
                        }
                    }
            }
        }
    }

    private var monitorJob: Job? = null
    fun cancelMonitorFeeds() {
        monitorJob?.cancel()
        monitorJob = null
        monitoringJob?.cancel()
        monitoringJob = null
    }

    fun monitorFeedList(scope: CoroutineScope) {
        if (monitorJob != null) return

        feedIds.value = realm.query(Feed::class).find().map { it.id }
        monitorFeeds(scope)

        val feedQuery = realm.query(Feed::class)
        monitorJob = scope.launch(Dispatchers.IO) {
            feedQuery.asFlow().collect { changes: ResultsChange<Feed> ->
                when (changes) {
                    is UpdatedResults -> {
                        when {
                            changes.insertions.isNotEmpty() -> {
                                val ids = feedIds.value.toMutableList()
                                for (i in changes.insertions) {
                                    Logd(TAG, "monitorFeedList inserted feed: ${changes.list[i].title}")
                                    ids.add(changes.list[i].id)
                                }
                                feedIds.value = ids.toList()
                                // TODO: not sure why the state flow doens not collect
                                monitorFeeds(scope)
                                EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.ADDED))
                            }
//                            changes.changes.isNotEmpty() -> {
//                                for (i in changes.changes) {
//                                    Logd(TAG, "monitorFeeds feed changed: ${changes.list[i].title}")
//                                }
//                            }
                            changes.deletions.isNotEmpty() -> {
                                val ids = feedIds.value.toMutableList()
                                for (i in changes.deletions) {
//                                    Logd(TAG, "monitorFeedList deleted feed: ${feeds[i].title}")
                                    ids.removeAt(i)
                                }
                                feedIds.value = ids.toList()
                                monitorFeeds(scope)
                                Logd(TAG, "monitorFeedList feed deleted: ${changes.deletions.size}")
                                compileTags()
                            }
                        }
                    }
                    else -> {
                        // types other than UpdatedResults are not changes -- ignore them
                    }
                }
            }
        }
    }

    fun getFeedListDownloadUrls(): List<String> {
        Logd(TAG, "getFeedListDownloadUrls called")
        val result: MutableList<String> = mutableListOf()
        val feeds = getFeedList()
        for (f in feeds) {
            val url = f.downloadUrl
            if (url != null && !url.startsWith(Feed.PREFIX_LOCAL_FOLDER)) result.add(url)
        }
        return result
    }

    fun getFeed(feedId: Long, copy: Boolean = false): Feed? {
        val f = realm.query(Feed::class, "id == $feedId").first().find()
        return if (f != null) {
            if (copy) realm.copyFromRealm(f) else f
        } else null
    }

    private fun searchFeedByIdentifyingValueOrID(feed: Feed, copy: Boolean = false): Feed? {
        Logd(TAG, "searchFeedByIdentifyingValueOrID called")
        if (feed.id != 0L) return getFeed(feed.id, copy)
        val feeds = getFeedList()
        val feedId = feed.identifyingValue
        for (f in feeds) if (f.identifyingValue == feedId) return if (copy) realm.copyFromRealm(f) else f
        return null
    }

    fun isSubscribed(feed: Feed): Boolean {
        val f = realm.query(Feed::class, "eigenTitle == $0 && author == $1", feed.eigenTitle, feed.author).first().find()
        return f != null
    }

    fun getFeedByTitleAndAuthor(title: String, author: String): Feed? {
        return realm.query(Feed::class, "eigenTitle == $0 && author == $1", title, author).first().find()
    }

    /**
     * Adds new Feeds to the database or updates the old versions if they already exists. If another Feed with the same
     * identifying value already exists, this method will add new FeedItems from the new Feed to the existing Feed.
     * These FeedItems will be marked as unread with the exception of the most recent FeedItem.
     * This method can update multiple feeds at once. Submitting a feed twice in the same method call can result in undefined behavior.
     * This method should NOT be executed on the GUI thread.
     * @param context Used for accessing the DB.
     * @param newFeed The new Feed object.
     * @param removeUnlistedItems The episode list in the new Feed object is considered to be exhaustive.
     * I.e. episodes are removed from the database if they are not in this episode list.
     * @return The updated Feed from the database if it already existed, or the new Feed from the parameters otherwise.
     */
    @Synchronized
    fun updateFeedFull(context: Context, newFeed: Feed, removeUnlistedItems: Boolean = false, overwriteStates: Boolean = false): Feed? {
        Logd(TAG, "updateFeedFull called")
//        showStackTrace()
        var resultFeed: Feed?

        Logd(TAG, "newFeed id: ${newFeed.id} episodes: ${newFeed.episodes.size}")
        // Look up feed in the feedslist
        val savedFeed = searchFeedByIdentifyingValueOrID(newFeed, true)
        if (savedFeed == null) {
            Logd(TAG, "Found no existing Feed with title ${newFeed.title}. Adding as new one.")
            Logd(TAG, "newFeed.episodes: ${newFeed.episodes.size}")
            newFeed.lastUpdateTime = System.currentTimeMillis()
            newFeed.lastFullUpdateTime = System.currentTimeMillis()
            resultFeed = newFeed
            try {
                addNewFeedsSync(context, newFeed)
                // Update with default values that are set in database
                resultFeed = searchFeedByIdentifyingValueOrID(newFeed)
                // TODO: This doesn't appear needed as unlistedItems is still empty
//                if (removeUnlistedItems && unlistedItems.isNotEmpty()) runBlocking { deleteEpisodes(context, unlistedItems).join() }
            } catch (e: InterruptedException) { Logs(TAG, e, "updateFeedFull failed")
            } catch (e: ExecutionException) { Logs(TAG, e, "updateFeedFull failed") }
            return resultFeed
        }

        Logd(TAG, "Feed with title " + newFeed.title + " already exists. Syncing new with existing one.")
        newFeed.episodes.sortWith(EpisodePubdateComparator())
        if (newFeed.pageNr == savedFeed.pageNr) {
            if (overwriteStates) savedFeed.updateFromOther(newFeed, true)
            else if (savedFeed.differentFrom(newFeed)) {
                Logd(TAG, "Feed has updated attribute values. Updating old feed's attributes")
                savedFeed.updateFromOther(newFeed)
            }
        } else {
            Logd(TAG, "New feed has a higher page number: ${newFeed.nextPageLink}")
            savedFeed.nextPageLink = newFeed.nextPageLink
        }
        Logd(TAG, "savedFeed.isLocalFeed: ${savedFeed.isLocalFeed} savedFeed.prefStreamOverDownload: ${savedFeed.prefStreamOverDownload}")
        val priorMostRecent = savedFeed.mostRecentItem
        val priorMostRecentDate: Date? = priorMostRecent?.getPubDate()
        var idLong = newId()
        Logd(TAG, "updateFeed building savedFeedAssistant")
        val savedFeedAssistant = FeedAssistant(savedFeed)
        // Look for new or updated Items
        for (idx in newFeed.episodes.indices) {
            val episode = newFeed.episodes[idx]
            var oldItems = savedFeedAssistant.guessDuplicate(episode)
            if (!newFeed.isLocalFeed && !oldItems.isNullOrEmpty()) {
//                Logd(TAG, "Update existing episode: ${episode.title}")
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
                    SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, action)
                }
            }

            if (idx % 50 == 0) Logd(TAG, "updateFeedFull processing item $idx / ${newFeed.episodes.size} ")

            if (!oldItems.isNullOrEmpty()) oldItems[0].updateFromOther(episode, overwriteStates)
            else {
//                Logd(TAG, "Found new episode: ${episode.getPubDate()} ${episode.title}")
                episode.feed = savedFeed
                episode.id = idLong++
                episode.feedId = savedFeed.id
                if (!savedFeed.isLocalFeed && !savedFeed.prefStreamOverDownload) runBlocking { episode.fetchMediaSize(false) }

                if (!savedFeed.hasVideoMedia && episode.getMediaType() == MediaType.VIDEO) savedFeed.hasVideoMedia = true
                savedFeed.episodes.add(episode)
                savedFeedAssistant.addidvToMap(episode)

                val pubDate = episode.getPubDate()
                if (priorMostRecentDate == null || priorMostRecentDate.before(pubDate) || priorMostRecentDate == pubDate) {
                    Logd(TAG, "Marking episode published on $pubDate new, prior most recent date = $priorMostRecentDate")
                    episode.setPlayState(EpisodeState.NEW)
                    if (savedFeed.autoAddNewToQueue) {
                        val q = savedFeed.queue
                        if (q != null) runOnIOScope {  addToQueueSync(episode, q) }
                    }
                }
            }
        }
        savedFeedAssistant.clear()

        val unlistedItems: MutableList<Episode> = mutableListOf()
        // identify episodes to be removed
        if (removeUnlistedItems) {
            Logd(TAG, "updateFeed building newFeedAssistant")
            val newFeedAssistant = FeedAssistant(newFeed, savedFeed.id)
            val it = savedFeed.episodes.toMutableList().iterator()
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
        savedFeed.lastUpdateTime = System.currentTimeMillis()
        savedFeed.lastFullUpdateTime = System.currentTimeMillis()
        savedFeed.type = newFeed.type
        savedFeed.lastUpdateFailed = false
        savedFeed.totleDuration = 0
        for (e in savedFeed.episodes) savedFeed.totleDuration += e.duration

        resultFeed = savedFeed
        try {
            upsertBlk(savedFeed) {}
            if (removeUnlistedItems && unlistedItems.isNotEmpty()) deleteEpisodesSync(context, unlistedItems)
        } catch (e: InterruptedException) { Logs(TAG, e, "updateFeedFull failed")
        } catch (e: ExecutionException) { Logs(TAG, e, "updateFeedFull failed") }
        return resultFeed
    }

    suspend fun updateFeedSimple(newFeed: Feed): Feed? {
        Logd(TAG, "updateFeedSimple called")
        val savedFeed = searchFeedByIdentifyingValueOrID(newFeed, true) ?: return newFeed

        Logd(TAG, "Feed with title " + newFeed.title + " already exists. Syncing new with existing one.")
        newFeed.episodes.sortWith(EpisodePubdateComparator())
        if (newFeed.pageNr == savedFeed.pageNr) {
            if (savedFeed.differentFrom(newFeed)) {
                Logd(TAG, "Feed has updated attribute values. Updating old feed's attributes")
                savedFeed.updateFromOther(newFeed)
            }
        } else {
            Logd(TAG, "New feed has a higher page number: ${newFeed.nextPageLink}")
            savedFeed.nextPageLink = newFeed.nextPageLink
        }
        val priorMostRecent = savedFeed.mostRecentItem
        val priorMostRecentDate: Date = priorMostRecent?.getPubDate() ?: Date(0)
        var idLong = newId()
        Logd(TAG, "updateFeedSimple building savedFeedAssistant")

        // Look for new or updated Items
        for (idx in newFeed.episodes.indices) {
            val episode = newFeed.episodes[idx]
            if (episode.duration < 1000) continue
            val pubDate = episode.getPubDate()
            if (pubDate <= priorMostRecentDate || episode.downloadUrl == priorMostRecent?.downloadUrl || episode.title == priorMostRecent?.title) continue

            Logd(TAG, "Found new episode: ${episode.title}")
            episode.feed = savedFeed
            episode.id = idLong++
            episode.feedId = savedFeed.id
            if (!savedFeed.isLocalFeed && !savedFeed.prefStreamOverDownload) episode.fetchMediaSize(persist = false)
            if (!savedFeed.hasVideoMedia && episode.getMediaType() == MediaType.VIDEO) savedFeed.hasVideoMedia = true
            savedFeed.episodes.add(episode)

            if (priorMostRecentDate < pubDate) {
                Logd(TAG, "Marking episode published on $pubDate new, prior most recent date = $priorMostRecentDate")
                episode.setPlayState(EpisodeState.NEW)
                if (savedFeed.autoAddNewToQueue) {
                    val q = savedFeed.queue
                    if (q != null) runOnIOScope {  addToQueueSync(episode, q) }
                }
            }
        }

        // update attributes
        savedFeed.lastUpdate = newFeed.lastUpdate
        savedFeed.lastUpdateTime = System.currentTimeMillis()
        savedFeed.type = newFeed.type
        savedFeed.lastUpdateFailed = false
        savedFeed.totleDuration = 0
        for (e in savedFeed.episodes) savedFeed.totleDuration += e.duration

        val resultFeed = savedFeed
        try { upsert(savedFeed) {} } catch (e: InterruptedException) { Logs(TAG, e, "updateFeedSimple failed") } catch (e: ExecutionException) { Logs(TAG, e, "updateFeedSimple failed") }
        return resultFeed
    }

    fun persistFeedLastUpdateFailed(feed: Feed, lastUpdateFailed: Boolean) : Job {
        Logd(TAG, "persistFeedLastUpdateFailed called")
        return runOnIOScope {
            upsert(feed) { it.lastUpdateFailed = lastUpdateFailed }
            EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.ERROR, feed.id))
        }
    }

    fun updateFeedDownloadURL(original: String, updated: String) {
        Logd(TAG, "updateFeedDownloadURL(original: $original, updated: $updated)")
        val feed = realm.query(Feed::class).query("downloadUrl == $0", original).first().find()
        if (feed != null) upsertBlk(feed) { it.downloadUrl = updated }
    }

    private fun addNewFeedsSync(context: Context, vararg feeds: Feed) {
        Logd(TAG, "addNewFeedsSync called")
        realm.writeBlocking {
            var idLong = newId()
            for (feed in feeds) {
                feed.id = idLong
//                if (feed.preferences == null)
//                    feed.preferences = FeedPreferences(feed.id, false, AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, "", "")
//                else feed.preferences!!.feedID = feed.id
                feed.totleDuration = 0
                Logd(TAG, "feed.episodes count: ${feed.episodes.size}")
                for (episode in feed.episodes) {
                    episode.id = idLong++
                    Logd(TAG, "addNewFeedsSync ${episode.id} ${episode.downloadUrl}")
                    episode.feedId = feed.id
                    feed.totleDuration += episode.duration
                }
                copyToRealm(feed)
            }
        }
        for (feed in feeds) {
            if (!feed.isLocalFeed && feed.downloadUrl != null) SynchronizationQueueSink.enqueueFeedAddedIfSyncActive(context, feed.downloadUrl!!)
        }
        val backupManager = BackupManager(context)
        backupManager.dataChanged()
    }

    /**
     * Deletes a Feed and all downloaded files of its components like images and downloaded episodes.
     * @param context A context that is used for opening a database connection.
     * @param feedId  ID of the Feed that should be deleted.
     */
    suspend fun deleteFeedSync(context: Context, feedId: Long, postEvent: Boolean = true) {
        Logd(TAG, "deleteFeed called")
        val feed = getFeed(feedId)
        if (feed != null) {
            val eids = feed.episodes.map { it.id }
//                remove from queues
            removeFromAllQueuesQuiet(eids)
//                remove media files
            realm.write {
                val feed_ = query(Feed::class).query("id == $0", feedId).first().find()
                if (feed_ != null) {
                    val episodes = feed_.episodes
                    if (episodes.isNotEmpty()) {
                        episodes.forEach { episode ->
                            val url = episode.fileUrl
                            when {
                                // Local feed or custom media folder
                                url != null && url.startsWith("content://") -> DocumentFile.fromSingleUri(context, url.toUri())?.delete()
                                url != null -> {
                                    val path = url.toUri().path
                                    if (path != null) File(path).delete()
                                }
                            }
                        }
                        delete(episodes)
                    }
                    val feedToDelete = findLatest(feed_)
                    if (feedToDelete != null) delete(feedToDelete)
                }
            }
            if (!feed.isLocalFeed && feed.downloadUrl != null) SynchronizationQueueSink.enqueueFeedRemovedIfSyncActive(context, feed.downloadUrl!!)
            val backupManager = BackupManager(context)
            backupManager.dataChanged()
        }
    }

    suspend fun feedIdsOfAllEpisodes(): Set<Long> {
        val changes = realm.query(Episode::class).asFlow().first { it is InitialResults }
        val idSet = when (changes) {
            is InitialResults -> changes.list.mapNotNull { it.feedId }.toSet()
            else -> emptySet()
        }
        Logd(TAG, "compileEpisodesFeedIds idSet: ${idSet.size}")
        return idSet
    }

    @JvmStatic
    fun allowForAutoDelete(feed: Feed): Boolean {
        if (!getPref(AppPrefs.prefAutoDelete, false)) return false
        return !feed.isLocalFeed || getPref(AppPrefs.prefAutoDeleteLocal, false)
    }

    suspend fun shelveToFeed(episodes: List<Episode>, toFeed: Feed, removeChecked: Boolean = false) {
        val eList: MutableList<Episode> = mutableListOf()
        for (e in episodes) {
            if (searchEpisodeByIdentifyingValue(toFeed.episodes, e) != null) continue
            var e_ = e
            if (!removeChecked || (e.feedId != null && e.feedId!! >= MAX_SYNTHETIC_ID)) {
                e_ = realm.copyFromRealm(e)
                e_.id = newId()
                if (e.feedId != null && e.feedId!! >= MAX_SYNTHETIC_ID) {
                    e_.origFeedTitle = e.feed?.title
                    e_.origFeeddownloadUrl = e.feed?.downloadUrl
                    e_.origFeedlink = e.feed?.link
                }
            } else {
                val feed = realm.query(Feed::class).query("id == $0", e_.feedId).first().find()
                if (feed != null) upsert(feed) { it.episodes.remove(e_) }
            }
            upsert(e_) {
                it.feed = toFeed
                it.feedId = toFeed.id
                eList.add(it)
            }
        }
        upsert(toFeed) { it.episodes.addAll(eList) }
    }

    fun createSynthetic(feedId: Long, name: String, video: Boolean = false): Feed {
        val feed = Feed()
        var feedId_ = feedId
        if (feedId_ <= 0) {
            var i = MAX_NATURAL_SYNTHETIC_ID
            while (true) {
                if (getFeed(i++) != null) continue
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

    private fun getMiscSyndicate(): Feed {
        val feedId: Long = 11
        var feed = getFeed(feedId, true)
        if (feed != null) return feed

        feed = createSynthetic(feedId, "Misc Syndicate")
        feed.type = Feed.FeedType.RSS.name
        upsertBlk(feed) {}
        EventFlow.postEvent(FlowEvent.FeedListEvent(FlowEvent.FeedListEvent.Action.ADDED))
        return feed
    }

    fun addRemoteToMiscSyndicate(episode: Episode) {
        val feed = getMiscSyndicate()
        Logd(TAG, "addToMiscSyndicate: feed: ${feed.title}")
        if (searchEpisodeByIdentifyingValue(feed.episodes, episode) != null) return
        Logd(TAG, "addToMiscSyndicate adding new episode: ${episode.title}")
//        if (episode.feedId != null && episode.feedId!! >= MAX_SYNTHETIC_ID) {
//            episode.origFeedTitle = episode.feed?.title
//            episode.origFeeddownloadUrl = episode.feed?.downloadUrl
//            episode.origFeedlink = episode.feed?.link
//        }
        episode.feed = feed
        episode.id = newId()
        episode.feedId = feed.id
        upsertBlk(episode) {}
        feed.episodes.add(episode)
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

    /**
     * Compares the pubDate of two FeedItems for sorting in reverse order
     */
    class EpisodePubdateComparator : Comparator<Episode> {
        override fun compare(lhs: Episode, rhs: Episode): Int {
            return rhs.pubDate.compareTo(lhs.pubDate)
        }
    }

    // savedFeedId == 0L means saved feed
    class FeedAssistant(val feed: Feed, private val savedFeedId: Long = 0L) {
        val map = mutableMapOf<String, MutableList<Episode>>()
        val tag: String = if (savedFeedId == 0L) "Saved feed" else "New feed"

        init {
            if (savedFeedId == 0L) {
                val ids = feed.episodes.map { it.id }
                val elLoose = realm.query(Episode::class).query("feedId == ${feed.id} AND (NOT (id IN $0))", ids).find()
                if (elLoose.isNotEmpty()) {
                    Logt(TAG, "Found ${elLoose.size} loose episodes")
                    feed.episodes.addAll(elLoose)
                }
            }
            val iterator = feed.episodes.iterator()
            while (iterator.hasNext()) {
                val e = iterator.next()
                if (!e.identifier.isNullOrEmpty()) {
                    if (map.containsKey(e.identifier!!)) {
                        Logd(TAG, "FeedAssistant init $tag identifier duplicate: ${e.identifier} ${e.title}")
                        if (savedFeedId > 0L) {
//                             addDownloadStatus(e, map[e.identifier!!]!!)
                            iterator.remove()
                        }
                        map[e.identifier!!]!!.add(e)
                    } else map[e.identifier!!] = mutableListOf(e)
                }
                val idv = e.identifyingValue
                if (idv != e.identifier && !idv.isNullOrEmpty()) {
                    if (map.containsKey(idv)) {
                        Logd(TAG, "FeedAssistant init $tag identifyingValue duplicate: $idv ${e.title}")
                        if (savedFeedId > 0L) {
//                             addDownloadStatus(e, map[idv]!!)
                            iterator.remove()
                        }
                        map[idv]!!.add(e)
                    } else map[idv] = mutableListOf(e)
                }
                val url = e.downloadUrl
                if (url != idv && !url.isNullOrEmpty()) {
                    if (map.containsKey(url)) {
                        Logd(TAG, "FeedAssistant init $tag url duplicate: $url ${e.title}")
                        if (savedFeedId > 0L) {
//                             addDownloadStatus(e, map[url]!!)
                            iterator.remove()
                        }
                        map[url]!!.add(e)
                    } else map[url] = mutableListOf(e)
                }
                val title = canonicalizeTitle(e.title)
                if (title != idv && title.isNotEmpty()) {
                    if (map.containsKey(title)) {
                        Logd(TAG, "FeedAssistant init $tag title duplicate: $title ${e.title}")
                        if (savedFeedId > 0L) {
//                             addDownloadStatus(e, map[url]!!)
                            iterator.remove()
                        }
                    } else map[title] = mutableListOf(e)
                }
            }
            if (savedFeedId == 0L) {
                suspend fun eraseEpisode(e: Episode) {
                    feed.episodes.remove(e)
                    realm.write {
                        findLatest(e)?.let { delete(it) }
//                        val e = query(Episode::class).query("id == $0", e.id).first().find()
//                        e?.let { delete(it) }
                    }
                }
                for ((k, v) in map.entries) {
                    if (v.size < 2) continue
                    Logd(TAG, "FeedAssistant removing ${v.size-1} duplicates on $k")
                    var episode = v[0]
                    val ecs = v.sortedByDescending { it.comment.length }
                    val comment = if (ecs[0].comment.isBlank()) "" else {
                        var c = ecs[0].comment
                        for (i in 1..ecs.size-1) if (ecs[i].comment.isNotBlank()) c += "\n" + ecs[i].comment
                        c
                    }
                    val ers = v.sortedByDescending { it.rating }
                    if (ers[0].rating > Rating.UNRATED.code) runOnIOScope {
                        if (ers[0].id != ecs[0].id && comment.isNotEmpty()) episode = upsertBlk(ers[0]) { it.comment = comment }
                        else episode = ers[0]
                        for (i in 1..ers.size - 1) eraseEpisode(ers[i])
                    } else {
                        val eps = v.sortedByDescending { it.lastPlayedTime }
                        if (eps[0].lastPlayedTime > 0L) {
                            if (eps[0].id != ecs[0].id && comment.isNotEmpty()) episode = upsertBlk(eps[0]) { it.comment = comment }
                            else episode = eps[0]
                            runOnIOScope { for (i in 1..eps.size - 1) eraseEpisode(eps[i]) }
                        } else {
                            val eps = v.sortedByDescending { it.pubDate }
                            if (eps[0].id != ecs[0].id && comment.isNotEmpty()) episode = upsertBlk(eps[0]) { it.comment = comment }
                            else episode = eps[0]
                            runOnIOScope { for (i in 1..eps.size - 1) eraseEpisode(eps[i]) }
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
            addDownloadStatus(DownloadResult(savedFeedId, episode.title ?: "", DownloadError.ERROR_PARSER_EXCEPTION_DUPLICATE, false,
                """
                The podcast host appears to have added the same episode twice. Podcini still refreshed the feed and attempted to repair it.

                Original episode:
                ${EpisodeAssistant.duplicateEpisodeDetails(episode)}

                Second episode that is also in the feed:
                ${EpisodeAssistant.duplicateEpisodeDetails(possibleDuplicate)}
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

    object EpisodeAssistant {
        fun searchEpisodeByIdentifyingValue(episodes: List<Episode>?, searchItem: Episode): Episode? {
            if (episodes.isNullOrEmpty()) return null
            for (episode in episodes) if (episode.identifyingValue == searchItem.identifyingValue) return episode
            return null
        }
        /**
         * Guess if one of the episodes could actually mean the searched episode, even if it uses another identifying value.
         * This is to work around podcasters breaking their GUIDs.
         */
//        fun searchEpisodeGuessDuplicate(episodes: List<Episode>?, searchItem: Episode): Episode? {
//            if (episodes.isNullOrEmpty()) return null
//            for (episode in episodes) {
//                if (EpisodeDuplicateGuesser.sameAndNotEmpty(episode.identifier, searchItem.identifier)) return episode
//            }
//            for (episode in episodes) {
//                if (EpisodeDuplicateGuesser.seemDuplicates(episode, searchItem)) return episode
//            }
//            return null
//        }
        fun duplicateEpisodeDetails(episode: Episode): String {
            return ("""
                Title: ${episode.title}
                ID: ${episode.identifier}
                """.trimIndent() + """
                 
                URL: ${episode.downloadUrl}
                """.trimIndent())
        }
    }

    /**
     * Publishers sometimes mess up their feed by adding episodes twice or by changing the ID of existing episodes.
     * This class tries to guess if publishers actually meant another episode,
     * even if their feed explicitly says that the episodes are different.
     */
    object EpisodeDuplicateGuesser {
        private fun sameAndNotEmpty(string1: String?, string2: String?): Boolean {
            if (string1.isNullOrEmpty() || string2.isNullOrEmpty()) return false
            return string1 == string2
        }
        internal fun datesLookSimilar(item1: Episode, item2: Episode): Boolean {
//            if (item1.getPubDate() == null || item2.getPubDate() == null) return false
            val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.US) // MM/DD/YY
            val dateOriginal = dateFormat.format(item2.getPubDate())
            val dateNew = dateFormat.format(item1.getPubDate())
            return dateOriginal == dateNew // Same date; time is ignored.
        }
        internal fun durationsLookSimilar(media1: Episode, media2: Episode): Boolean {
            return abs((media1.duration - media2.duration).toDouble()) < 10 * 60L * 1000L
        }
        internal fun mimeTypeLooksSimilar(media1: Episode, media2: Episode): Boolean {
            var mimeType1 = media1.mimeType
            var mimeType2 = media2.mimeType
            if (mimeType1 == null || mimeType2 == null) return true
            if (mimeType1.contains("/") && mimeType2.contains("/")) {
                mimeType1 = mimeType1.substring(0, mimeType1.indexOf("/"))
                mimeType2 = mimeType2.substring(0, mimeType2.indexOf("/"))
            }
            return (mimeType1 == mimeType2)
        }
        private fun titlesLookSimilar(item1: Episode, item2: Episode): Boolean {
            return sameAndNotEmpty(canonicalizeTitle(item1.title), canonicalizeTitle(item2.title))
        }
        internal fun canonicalizeTitle(title: String?): String {
            if (title == null) return ""
            return title.trim { it <= ' ' }.replace('“', '"').replace('”', '"').replace('„', '"').replace('—', '-')
        }
    }
}