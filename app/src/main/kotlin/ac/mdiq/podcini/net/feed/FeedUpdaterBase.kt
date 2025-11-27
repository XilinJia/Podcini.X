package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.automation.autodownload
import ac.mdiq.podcini.automation.autoenqueue
import ac.mdiq.podcini.config.CHANNEL_ID
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.download.service.DefaultDownloaderFactory
import ac.mdiq.podcini.net.download.service.DownloadRequest
import ac.mdiq.podcini.net.download.service.DownloadRequestCreator.create
import ac.mdiq.podcini.net.feed.parser.FeedHandler
import ac.mdiq.podcini.net.feed.parser.FeedHandler.FeedHandlerResult
import ac.mdiq.podcini.net.utils.NetworkUtils.isFeedRefreshAllowed
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.net.utils.NetworkUtils.isVpnOverWifi
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowFeedRefresh
import ac.mdiq.podcini.net.utils.NetworkUtils.networkAvailable
import ac.mdiq.podcini.storage.database.addDownloadStatus
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.feedOperationText
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.getFeedDownloadLog
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.persistFeedLastUpdateFailed
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.unmanaged
import ac.mdiq.podcini.storage.database.updateFeedDownloadURL
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.database.updateFeedSimple
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.VolumeAdaptionSetting
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.xilinjia.krdb.ext.toRealmSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xml.sax.SAXException
import java.io.File
import java.io.IOException
import java.util.Date
import javax.xml.parsers.ParserConfigurationException

open class FeedUpdaterBase(val feed: Feed? = null, val fullUpdate: Boolean = false) {
    protected val TAG = "FeedUpdaterBase"
    protected val context = getAppContext()
    private val notificationManager = NotificationManagerCompat.from(context)

    var feedsToUpdate: MutableList<Feed> = mutableListOf()
    val feedsToOnlyDownload: MutableList<Feed> = mutableListOf()
    val feedsToOnlyEnqueue: MutableList<Feed> = mutableListOf()
    val feedId = feed?.id ?: -1L
    var allAreLocal = true
    var force = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun startRefresh(context: Context) {
        val ready = prepare()
        if (!ready) return
        when {
            feed != null && feed.isLocalFeed -> scope.launch { doWork() }
            !networkAvailable() -> EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.download_error_no_connection)))
            isFeedRefreshAllowed -> scope.launch { doWork() }
            else -> {
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.feed_refresh_title),
                    message = context.getString(if (isNetworkRestricted && isVpnOverWifi) R.string.confirm_mobile_feed_refresh_dialog_message_vpn else R.string.confirm_mobile_feed_refresh_dialog_message),
                    confirmRes = R.string.confirm_mobile_streaming_button_once,
                    cancelRes = R.string.no,
                    neutralRes = R.string.confirm_mobile_streaming_button_always,
                    onConfirm = { scope.launch { doWork() }  },
                    onNeutral = {
                        mobileAllowFeedRefresh = true
                        scope.launch { doWork() }
                    })
            }
        }
    }

    fun prepare(): Boolean {
        scope.launch(Dispatchers.Main) { feedOperationText = context.getString(R.string.preparing) }
        if (feedId == -1L) { // Update all
            val feedIds = appAttribs.feedIdsToRefresh
            if (feedIds.isNotEmpty()) {
                Logt(TAG, "Partial refresh of ${feedIds.size} feeds")
                feedsToUpdate = realm.query(Feed::class, "id IN $0", feedIds).find().toMutableList()
            } else feedsToUpdate = getFeedList().toMutableList()

            val itr = feedsToUpdate.iterator()
            while (itr.hasNext()) {
                val feed = itr.next()
                if (!feed.keepUpdated) {
                    if (feed.autoEnqueue) feedsToOnlyEnqueue.add(feed)
                    else if (feed.autoDownload) feedsToOnlyDownload.add(feed)
                    itr.remove()
                }
                if (!feed.isLocalFeed) allAreLocal = false
            }
//            feedsToUpdate.shuffle() // If the worker gets cancelled early, every feed has a chance to be updated
        } else {
            val feed = getFeed(feedId)
            if (feed == null) {
                Loge(TAG, "feed is null for feedId $feedId. update abort")
                scope.launch(Dispatchers.Main) { feedOperationText = "" }
                return false
            }
            Logd(TAG, "doWork updating single feed: ${feed.title} ${feed.downloadUrl}")
            if (!feed.isLocalFeed) allAreLocal = false
            feedsToUpdate = mutableListOf(feed)
            force = true
        }
        scope.launch(Dispatchers.Main) { feedOperationText = "" }
        return true
    }

    suspend fun doWork(): Boolean {
        withContext(Dispatchers.Main) { feedOperationText = context.getString(R.string.refreshing_label) }
        refreshFeeds(feedsToUpdate, force)
        notificationManager.cancel(R.id.notification_updating_feeds)
        withContext(Dispatchers.Main) { feedOperationText = context.getString(R.string.post_refreshing) }
        postWork()
        return true
    }

    private suspend fun postWork() {
        if (feedsToOnlyEnqueue.isNotEmpty()) feedsToUpdate.addAll(feedsToOnlyEnqueue)
        if (feedsToOnlyDownload.isNotEmpty()) feedsToUpdate.addAll(feedsToOnlyDownload)
        autoenqueue(feedsToUpdate.toList())
        autodownload(context, feedsToUpdate.toList())
        feedsToUpdate.clear()
        feedsToOnlyEnqueue.clear()
        feedsToOnlyDownload.clear()
        withContext(Dispatchers.Main) { feedOperationText = "" }
    }

    //    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun refreshFeeds(feedsToUpdate: MutableList<Feed>, force: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Loge(TAG, "refreshFeeds: require POST_NOTIFICATIONS permission")
            return
        }
        val titles = feedsToUpdate.map { it.title ?: "No title" }.toMutableList()
        val feedIdsToRefresh = feedsToUpdate.map { it.id }.toMutableList()
        var i = 0
        while (i < feedsToUpdate.size) {
            notificationManager.notify(R.id.notification_updating_feeds, createNotification(titles))
            val feed = unmanaged(feedsToUpdate[i++])
            try {
                Logd(TAG, "updating local feed? ${feed.isLocalFeed} ${feed.title}")
                when {
                    feed.isLocalFeed -> updateLocalFeed(feed, context, null)
                    else -> refreshFeed(feed, force)
                }
            } catch (e: Exception) {
                Loge(TAG, "refreshFeeds: update failed ${feed.title} ${e.message}")
                persistFeedLastUpdateFailed(feed, true)
                val status = DownloadResult(feed.id, feed.title?:"", DownloadError.ERROR_IO_ERROR, false, e.message?:"")
                addDownloadStatus(status)
            }
            titles.removeAt(0)
            feedIdsToRefresh.removeAt(0)
            upsertBlk(appAttribs) { it.feedIdsToRefresh = feedIdsToRefresh.toRealmSet()}
        }
    }

    @Throws(Exception::class)
    open suspend fun refreshFeed(feed: Feed, force: Boolean) {
        // TODO
        val nextPage = false
//        val nextPage = (inputData.getBoolean(EXTRA_NEXT_PAGE, false) && feed.nextPageLink != null)
        if (nextPage) feed.pageNr += 1
        val builder = create(feed)
        builder.setForce(force || feed.lastUpdateFailed)
        if (nextPage) builder.source = feed.nextPageLink
        val request = builder.build()
        val downloader = DefaultDownloaderFactory().create(request) ?: throw Exception("Unable to create downloader")
        downloader.run()
        if (!downloader.result.isSuccessful) {
            if (downloader.cancelled || downloader.result.reason == DownloadError.ERROR_DOWNLOAD_CANCELLED) {
                Logt(TAG, "refreshFeed: feed refresh cancelled, likely due to feed not changed: ${feed.title}")
                return
            }
            Logt(TAG, "refreshFeed: feed update failed: unsuccessful. cancelled? ${feed.title}")
            persistFeedLastUpdateFailed(feed, true)
            addDownloadStatus(downloader.result)
            return
        }
        val feedUpdateTask = FeedUpdateTask(context, request)
        val success = if (fullUpdate) feedUpdateTask.run() else feedUpdateTask.runSimple()
        if (!success) {
            Logt(TAG, "refreshFeed: feed update failed: unsuccessful: ${feed.title}")
            persistFeedLastUpdateFailed(feed, true)
            addDownloadStatus(feedUpdateTask.downloadStatus)
            return
        }
        // we create a 'successful' download log if the feed's last refresh failed
        val log = getFeedDownloadLog(request.feedfileId)
        if (log.isNotEmpty() && !log[0].isSuccessful) addDownloadStatus(feedUpdateTask.downloadStatus)
        if (!request.source.isNullOrEmpty()) {
            when {
                !downloader.permanentRedirectUrl.isNullOrEmpty() -> updateFeedDownloadURL(request.source, downloader.permanentRedirectUrl!!)
                feedUpdateTask.redirectUrl.isNotEmpty() && feedUpdateTask.redirectUrl != request.source ->
                    updateFeedDownloadURL(request.source, feedUpdateTask.redirectUrl)
            }
        }
    }

    class FeedParserTask(private val request: DownloadRequest) {
        val TAG = "FeedParserTask"

        var downloadStatus: DownloadResult
            private set
        var isSuccessful: Boolean = true
            private set

        init {
            downloadStatus = DownloadResult(request.title?:"", 0L, request.feedfileType, false, DownloadError.ERROR_REQUEST_ERROR, Date(), "Unknown error: Status not set")
        }
        suspend fun run(): FeedHandlerResult? {
            Logd(TAG, "in FeedParserTask call()")
            val feed = Feed(request.source, request.lastModified)
            feed.fileUrl = request.destination
            feed.id = request.feedfileId
            feed.fillPreferences(false, Feed.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, request.username, request.password)
            if (request.arguments != null) feed.pageNr = request.arguments.getInt(DownloadRequest.REQUEST_ARG_PAGE_NR, 0)
            var reason: DownloadError? = null
            var reasonDetailed: String? = null
            val feedHandler = FeedHandler()
            var result: FeedHandlerResult? = null
            try {
                result = feedHandler.parseFeed(feed)
                Logd(TAG,  "Parsed ${feed.title}")
                checkFeedData(feed)
//            TODO: what the shit is this??
                if (feed.imageUrl.isNullOrEmpty()) feed.imageUrl = Feed.PREFIX_GENERATIVE_COVER + feed.downloadUrl
            } catch (e: SAXException) {
                isSuccessful = false
                Logs(TAG, e, "SAXException")
                reason = DownloadError.ERROR_PARSER_EXCEPTION
                reasonDetailed = e.message
            } catch (e: IOException) {
                isSuccessful = false
                Logs(TAG, e, "IOException")
                reason = DownloadError.ERROR_IO_ERROR
                reasonDetailed = e.message
            } catch (e: ParserConfigurationException) {
                isSuccessful = false
                Logs(TAG, e, "ParserConfigurationException")
                reason = DownloadError.ERROR_PARSER_EXCEPTION
                reasonDetailed = e.message
            } catch (e: FeedHandler.UnsupportedFeedtypeException) {
                Logs(TAG, e, "UnsupportedFeedtypeException")
                isSuccessful = false
                reason = DownloadError.ERROR_UNSUPPORTED_TYPE
                if ("html".equals(e.rootElement, ignoreCase = true)) reason = DownloadError.ERROR_UNSUPPORTED_TYPE_HTML
                reasonDetailed = e.message
            } catch (e: InvalidFeedException) {
                Logs(TAG, e, "InvalidFeedException")
                isSuccessful = false
                reason = DownloadError.ERROR_PARSER_EXCEPTION
                reasonDetailed = e.message
            } finally {
                val feedFile = File(request.destination?:"junk")
                if (feedFile.exists()) {
                    val deleted = feedFile.delete()
                    Logd(TAG, "Deletion of file '" + feedFile.absolutePath + "' " + (if (deleted) "successful" else "FAILED"))
                }
            }
            if (isSuccessful) {
                downloadStatus = DownloadResult(feed.id, feed.getTextIdentifier()?:"", DownloadError.SUCCESS, isSuccessful, reasonDetailed?:"")
                return result
            }
            downloadStatus = DownloadResult(feed.id, feed.getTextIdentifier()?:"", reason?: DownloadError.ERROR_NOT_FOUND, isSuccessful, reasonDetailed?:"")
            return null
        }
        /**
         * Checks if the feed was parsed correctly.
         */
        @Throws(InvalidFeedException::class)
        private fun checkFeedData(feed: Feed) {
            if (feed.title == null) throw InvalidFeedException("Feed has no title")
            for (item in feed.episodes) {
                if (item.title == null) throw InvalidFeedException("Item has no title: $item")
            }
        }

        /**
         * Thrown if a feed has invalid attribute values.
         */
        class InvalidFeedException(message: String?) : Exception(message) {
            companion object {
                private const val serialVersionUID = 1L
            }
        }
    }

    class FeedUpdateTask(private val context: Context, request: DownloadRequest) {
        private val task = FeedParserTask(request)
        private var feedHandlerResult: FeedHandlerResult? = null
        val downloadStatus: DownloadResult
            get() = task.downloadStatus
        val redirectUrl: String
            get() = feedHandlerResult?.redirectUrl?:""

        suspend fun run(): Boolean {
            feedHandlerResult = task.run()
            if (!task.isSuccessful) return false
            updateFeedFull(context, feedHandlerResult!!.feed, removeUnlistedItems = false)
            return true
        }

        suspend fun runSimple(): Boolean {
            feedHandlerResult = task.run()
            if (!task.isSuccessful) return false
            updateFeedSimple(feedHandlerResult!!.feed)
            return true
        }
    }

    companion object {
        fun createNotification(titles: List<String>?): Notification {
            val context = getAppContext()
            var contentText = ""
            var bigText: String? = ""
            if (titles != null) {
                contentText = context.resources.getQuantityString(R.plurals.downloads_left, titles.size, titles.size)
                bigText = titles.joinToString("\n") { "• $it" }
            }
            return NotificationCompat.Builder(context, CHANNEL_ID.refreshing.name)
                .setContentTitle(context.getString(R.string.download_notification_title_feeds))
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setOngoing(true)
//                .addAction(R.drawable.ic_cancel, context.getString(R.string.cancel_label), WorkManager.getInstance(context).createCancelPendingIntent(id))
                .build()
        }
    }
}