package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.R
import ac.mdiq.podcini.automation.AutoDownloads.autodownload
import ac.mdiq.podcini.automation.AutoDownloads.autoenqueue
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.download.service.DefaultDownloaderFactory
import ac.mdiq.podcini.net.download.service.DownloadRequest
import ac.mdiq.podcini.net.download.service.DownloadRequestCreator.create
import ac.mdiq.podcini.net.feed.FeedUpdateManager.EXTRA_EVEN_ON_MOBILE
import ac.mdiq.podcini.net.feed.FeedUpdateManager.EXTRA_FEED_ID
import ac.mdiq.podcini.net.feed.FeedUpdateManager.EXTRA_FULL_UPDATE
import ac.mdiq.podcini.net.feed.FeedUpdateManager.EXTRA_NEXT_PAGE
import ac.mdiq.podcini.net.feed.parser.FeedHandler
import ac.mdiq.podcini.net.feed.parser.FeedHandler.FeedHandlerResult
import ac.mdiq.podcini.net.utils.NetworkUtils.isFeedRefreshAllowed
import ac.mdiq.podcini.net.utils.NetworkUtils.networkAvailable
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.database.Feeds
import ac.mdiq.podcini.storage.database.LogsAndStats
import ac.mdiq.podcini.storage.database.RealmDB.unmanaged
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.VolumeAdaptionSetting
import ac.mdiq.podcini.ui.utils.NotificationUtils
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.config.ClientConfigurator
import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.concurrent.Callable
import javax.xml.parsers.ParserConfigurationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.SAXException

open class FeedUpdateWorkerBase(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    protected val TAG = "FeedUpdateWorkerBase"
    private val notificationManager = NotificationManagerCompat.from(context)

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun doWork(): Result {
        ClientConfigurator.initialize(applicationContext)
        val feedsToUpdate: MutableList<Feed>
        val feedsToOnlyDownload: MutableList<Feed> = mutableListOf()
        val feedsToOnlyEnqueue: MutableList<Feed> = mutableListOf()
        val feedId = inputData.getLong(EXTRA_FEED_ID, -1L)
        var allAreLocal = true
        var force = false
        if (feedId == -1L) { // Update all
            feedsToUpdate = Feeds.getFeedList().toMutableList()
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
            feedsToUpdate.shuffle() // If the worker gets cancelled early, every feed has a chance to be updated
        } else {
            val feed = Feeds.getFeed(feedId) ?: return Result.success()
            Logd(TAG, "doWork updating single feed: ${feed.title} ${feed.downloadUrl}")
            if (!feed.isLocalFeed) allAreLocal = false
            feedsToUpdate = mutableListOf(feed)
            force = true
        }
        if (!inputData.getBoolean(EXTRA_EVEN_ON_MOBILE, false) && !allAreLocal) {
            if (!networkAvailable() || !isFeedRefreshAllowed) {
                Loge(TAG, "Refresh not performed: network unavailable or isFeedRefreshAllowed: $isFeedRefreshAllowed")
                return Result.retry()
            }
        }
        val fullUpdate = inputData.getBoolean(EXTRA_FULL_UPDATE, false)
        refreshFeeds(feedsToUpdate, force, fullUpdate)
        notificationManager.cancel(R.id.notification_updating_feeds)
        if (feedsToOnlyEnqueue.isNotEmpty()) feedsToUpdate.addAll(feedsToOnlyEnqueue)
        if (feedsToOnlyDownload.isNotEmpty()) feedsToUpdate.addAll(feedsToOnlyDownload)
        autoenqueue(feedsToUpdate.toList())
        autodownload(applicationContext, feedsToUpdate.toList())
        feedsToUpdate.clear()
        feedsToOnlyEnqueue.clear()
        feedsToOnlyDownload.clear()
        Logd(TAG, "feedId: $feedId prefAutoUpdateStartTime: [${getPref(AppPrefs.prefAutoUpdateStartTime, ":")}]")
        return Result.success()
    }

    private fun createNotification(titles: List<String>?): Notification {
        val context = applicationContext
        var contentText = ""
        var bigText: String? = ""
        if (titles != null) {
            contentText = context.resources.getQuantityString(R.plurals.downloads_left, titles.size, titles.size)
            bigText = titles.joinToString("\n") { "• $it" }
        }
        return NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID.downloading.name)
            .setContentTitle(context.getString(R.string.download_notification_title_feeds))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setSmallIcon(R.drawable.ic_notification_sync)
            .setOngoing(true)
            .addAction(R.drawable.ic_cancel, context.getString(R.string.cancel_label), WorkManager.getInstance(context).createCancelPendingIntent(id))
            .build()
    }

//    override fun getForegroundInfoAsync(): ListenableFuture<ForegroundInfo> {
//        return Futures.immediateFuture(ForegroundInfo(R.id.notification_updating_feeds, createNotification(null)))
//    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return withContext(Dispatchers.Main) { ForegroundInfo(R.id.notification_updating_feeds, createNotification(null)) }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private suspend fun refreshFeeds(feedsToUpdate: MutableList<Feed>, force: Boolean, fullUpdate: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this.applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
//            postFornotificationPermission()
            Loge(TAG, "refreshFeeds: require POST_NOTIFICATIONS permission")
            return
        }
        val titles = feedsToUpdate.map { it.title ?: "No title" }.toMutableList()
        var i = 0
        while (i < feedsToUpdate.size) {
            if (isStopped) return
            notificationManager.notify(R.id.notification_updating_feeds, createNotification(titles))
            val feed = unmanaged(feedsToUpdate[i++])
            try {
                Logd(TAG, "updating local feed? ${feed.isLocalFeed} ${feed.title}")
                when {
                    feed.isLocalFeed -> LocalFeedUpdater.updateFeed(feed, applicationContext, null)
                    else -> refreshFeed(feed, force, fullUpdate)
                }
            } catch (e: Exception) {
                Loge(TAG, "update failed ${e.message}")
                Feeds.persistFeedLastUpdateFailed(feed, true)
                val status = DownloadResult(feed.id, feed.title?:"", DownloadError.ERROR_IO_ERROR, false, e.message?:"")
                LogsAndStats.addDownloadStatus(status)
            }
            titles.removeAt(0)
        }
    }

    @Throws(Exception::class)
    open suspend fun refreshFeed(feed: Feed, force: Boolean, fullUpdate: Boolean) {
        val nextPage = (inputData.getBoolean(EXTRA_NEXT_PAGE, false) && feed.nextPageLink != null)
        if (nextPage) feed.pageNr += 1
        val builder = create(feed)
        builder.setForce(force || feed.lastUpdateFailed)
        if (nextPage) builder.source = feed.nextPageLink
        val request = builder.build()
        val downloader = DefaultDownloaderFactory().create(request) ?: throw Exception("Unable to create downloader")
        downloader.call()
        if (!downloader.result.isSuccessful) {
            if (downloader.cancelled || downloader.result.reason == DownloadError.ERROR_DOWNLOAD_CANCELLED) {
                Logd(TAG, "feed refresh cancelled, likely due to feed not changed: ${feed.title}")
                return
            }
            Logt(TAG, "feed update failed: unsuccessful. cancelled? ${feed.title}")
            Feeds.persistFeedLastUpdateFailed(feed, true)
            LogsAndStats.addDownloadStatus(downloader.result)
            return
        }
        val feedUpdateTask = FeedUpdateTask(applicationContext, request)
        val success = if (fullUpdate) feedUpdateTask.run() else feedUpdateTask.runSimple()
        if (!success) {
            Logt(TAG, "feed update failed: unsuccessful: ${feed.title}")
            Feeds.persistFeedLastUpdateFailed(feed, true)
            LogsAndStats.addDownloadStatus(feedUpdateTask.downloadStatus)
            return
        }
        // we create a 'successful' download log if the feed's last refresh failed
        val log = LogsAndStats.getFeedDownloadLog(request.feedfileId)
        if (log.isNotEmpty() && !log[0].isSuccessful) LogsAndStats.addDownloadStatus(feedUpdateTask.downloadStatus)
        if (!request.source.isNullOrEmpty()) {
            when {
                !downloader.permanentRedirectUrl.isNullOrEmpty() -> Feeds.updateFeedDownloadURL(request.source, downloader.permanentRedirectUrl!!)
                feedUpdateTask.redirectUrl.isNotEmpty() && feedUpdateTask.redirectUrl != request.source ->
                    Feeds.updateFeedDownloadURL(request.source, feedUpdateTask.redirectUrl)
            }
        }
    }

    class FeedParserTask(private val request: DownloadRequest) : Callable<FeedHandlerResult?> {
        val TAG = "FeedParserTask"

        var downloadStatus: DownloadResult
            private set
        var isSuccessful: Boolean = true
            private set

        init {
            downloadStatus = DownloadResult(request.title?:"", 0L, request.feedfileType, false,
                DownloadError.ERROR_REQUEST_ERROR, Date(), "Unknown error: Status not set")
        }
        override fun call(): FeedHandlerResult? {
            Logd(TAG, "in FeedParserTask call()")
            val feed = Feed(request.source, request.lastModified)
            feed.fileUrl = request.destination
            feed.id = request.feedfileId
            feed.fillPreferences(false, Feed.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, request.username, request.password)
//                if (feed.preferences == null) feed.preferences = FeedPreferences(feed.id, false, FeedPreferences.AutoDeleteAction.GLOBAL,
//                    VolumeAdaptionSetting.OFF, request.username, request.password)
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
                Logs(TAG, e)
                reason = DownloadError.ERROR_PARSER_EXCEPTION
                reasonDetailed = e.message
            } catch (e: IOException) {
                isSuccessful = false
                Logs(TAG, e)
                reason = DownloadError.ERROR_PARSER_EXCEPTION
                reasonDetailed = e.message
            } catch (e: ParserConfigurationException) {
                isSuccessful = false
                Logs(TAG, e)
                reason = DownloadError.ERROR_PARSER_EXCEPTION
                reasonDetailed = e.message
            } catch (e: FeedHandler.UnsupportedFeedtypeException) {
                Logs(TAG, e)
                isSuccessful = false
                reason = DownloadError.ERROR_UNSUPPORTED_TYPE
                if ("html".equals(e.rootElement, ignoreCase = true)) reason = DownloadError.ERROR_UNSUPPORTED_TYPE_HTML
                reasonDetailed = e.message
            } catch (e: InvalidFeedException) {
                Logs(TAG, e)
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

        fun run(): Boolean {
            feedHandlerResult = task.call()
            if (!task.isSuccessful) return false
            Feeds.updateFeedFull(context, feedHandlerResult!!.feed, false)
            return true
        }

        suspend fun runSimple(): Boolean {
            feedHandlerResult = task.call()
            if (!task.isSuccessful) return false
            Feeds.updateFeedSimple(feedHandlerResult!!.feed)
            return true
        }
    }
}
