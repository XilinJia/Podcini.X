package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.PodciniApp.Companion.getApp
import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.automation.autodownload
import ac.mdiq.podcini.automation.autoenqueue
import ac.mdiq.podcini.config.CHANNEL_ID
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.download.DownloadRequest
import ac.mdiq.podcini.net.download.DownloadRequest.Companion.requestFor
import ac.mdiq.podcini.net.download.Downloader.Companion.downloaderFor
import ac.mdiq.podcini.net.feed.FeedHandler.FeedHandlerResult
import ac.mdiq.podcini.net.utils.NetworkUtils.isFeedRefreshAllowed
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowFeedRefresh
import ac.mdiq.podcini.storage.database.addDownloadStatus
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.compileLanguages
import ac.mdiq.podcini.storage.database.compileTags
import ac.mdiq.podcini.storage.database.feedOperationText
import ac.mdiq.podcini.storage.database.getFeedDownloadLog
import ac.mdiq.podcini.storage.database.getFeedList
import ac.mdiq.podcini.storage.database.persistFeedLastUpdateFailed
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.unmanaged
import ac.mdiq.podcini.storage.database.updateFeedFull
import ac.mdiq.podcini.storage.database.updateFeedSimple
import ac.mdiq.podcini.storage.database.updateLocalFeed
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.VolumeAdaptionSetting
import ac.mdiq.podcini.storage.utils.toUF
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
import kotlinx.io.IOException
import org.xml.sax.SAXException
import javax.xml.parsers.ParserConfigurationException

open class FeedUpdaterBase(val feeds: List<Feed>, val fullUpdate: Boolean = false, val doItAnyway: Boolean = false) {
    private val TAG = "FeedUpdaterBase"
    protected val context = getAppContext()
    private val notificationManager = NotificationManagerCompat.from(context)

    private var feedsToUpdate: MutableList<Feed> = mutableListOf()
    private val feedsToOnlyDownload: MutableList<Feed> = mutableListOf()
    private val feedsToOnlyEnqueue: MutableList<Feed> = mutableListOf()

    var force = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    fun startRefresh() {
        Logd(TAG, "startRefresh doItAnyway: $doItAnyway")
        val ready = prepare()
        if (!ready) {
            Loge(TAG, "startRefresh but not ready")
            return
        }
        val allLocalFeeds = run {
            for (f in feeds) if (!f.isLocalFeed) return@run false
            true
        }
        when {
            allLocalFeeds -> scope.launch { doWork() }
            !getApp().networkMonitor.isConnected -> EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.download_error_no_connection)))
            isFeedRefreshAllowed -> scope.launch { doWork() }
            else -> {
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.feed_refresh_title),
                    message = context.getString(if (getApp().networkMonitor.isNetworkRestricted && getApp().networkMonitor.isVpnOverWifi) R.string.confirm_mobile_feed_refresh_dialog_message_vpn else R.string.confirm_mobile_feed_refresh_dialog_message),
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
        if (feeds.isEmpty()) {
            val feedIds = appAttribs.feedIdsToRefresh
            if (feedIds.isNotEmpty()) {
                Logt(TAG, "Partial refresh of ${feedIds.size} feeds")
                feedsToUpdate = realm.query(Feed::class, "id IN $0", feedIds).find().toMutableList()
            } else feedsToUpdate = getFeedList("keepUpdated == true").toMutableList()
        } else {
            feedsToUpdate = feeds.toMutableList()
            force = true
        }
        val itr = feedsToUpdate.iterator()
        while (itr.hasNext()) {
            val feed = itr.next()
            if ((!feed.keepUpdated || !feed.inNormalVolume) && !doItAnyway) {
                Logt(TAG, "feed set not to update, igored: ${feed.title}")
                if (feed.autoEnqueue) feedsToOnlyEnqueue.add(feed)
                else if (feed.autoDownload) feedsToOnlyDownload.add(feed)
                itr.remove()
            }
        }
        scope.launch(Dispatchers.Main) { feedOperationText = "" }
        return true
    }

    suspend fun doWork(): Boolean {
        withContext(Dispatchers.Main) { feedOperationText = context.getString(R.string.refreshing_label) }
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Loge(TAG, "refreshFeeds: require POST_NOTIFICATIONS permission")
        } else {
            val titles = feedsToUpdate.map { it.title ?: "No title" }.toMutableList()
            val feedIdsToRefresh = feedsToUpdate.map { it.id }.toMutableList()
            var i = 0
            while (i < feedsToUpdate.size) {
                notificationManager.notify(R.id.notification_updating_feeds, createNotification(titles))
                val feed = unmanaged(feedsToUpdate[i++])
                try {
                    Logd(TAG, "updating local feed? ${feed.isLocalFeed} ${feed.title}")
                    when {
                        feed.isLocalFeed -> updateLocalFeed(feed, null)
                        else -> refreshFeed(feed)
                    }
                } catch (e: Exception) {
                    Loge(TAG, "refreshFeeds: update failed ${feed.title} ${e.message}")
                    persistFeedLastUpdateFailed(feed, true)
                    val status = DownloadResult(feed.id, feed.title ?: "", DownloadError.ERROR_IO_ERROR, false, e.message ?: "")
                    addDownloadStatus(status)
                }
                titles.removeAt(0)
                feedIdsToRefresh.removeAt(0)
                upsertBlk(appAttribs) { it.feedIdsToRefresh = feedIdsToRefresh.toRealmSet() }
            } // TODO: not sure these need to be here
            compileLanguages()
            compileTags()
        }
        notificationManager.cancel(R.id.notification_updating_feeds)
        withContext(Dispatchers.Main) { feedOperationText = context.getString(R.string.post_refreshing) }

        if (feedsToOnlyEnqueue.isNotEmpty()) feedsToUpdate.addAll(feedsToOnlyEnqueue)
        if (feedsToOnlyDownload.isNotEmpty()) feedsToUpdate.addAll(feedsToOnlyDownload)
        autoenqueue(feedsToUpdate.toList())
        autodownload(feedsToUpdate.toList())
        feedsToUpdate.clear()
        feedsToOnlyEnqueue.clear()
        feedsToOnlyDownload.clear()
        withContext(Dispatchers.Main) { feedOperationText = "" }

        return true
    }

    open suspend fun refreshFeed(feed: Feed) {
        // TODO
        val nextPage = false
        //        val nextPage = (inputData.getBoolean(EXTRA_NEXT_PAGE, false) && feed.nextPageLink != null)
        if (nextPage) feed.pageNr += 1
        val builder = requestFor(feed)
        if (force || feed.lastUpdateFailed) builder.lastModified = null
        if (nextPage) builder.source = feed.nextPageLink
        val request = builder.build()
        val downloader = downloaderFor(request) ?: throw Exception("Unable to create downloader")
        downloader.download { source ->
            val feedToParse = Feed(request.source, request.lastModified)
            feedToParse.fileUrl = request.destination
            feedToParse.id = request.feedfileId
            feedToParse.fillPreferences(false, Feed.AutoDeleteAction.GLOBAL, VolumeAdaptionSetting.OFF, request.username, request.password)
            if (request.arguments != null) feedToParse.pageNr = request.arguments.getInt(DownloadRequest.REQUEST_ARG_PAGE_NR, 0)
            var reason: DownloadError? = null
            var reasonDetailed: String? = null

            var feedHandlerResult: FeedHandlerResult? = null

            var isSuccessful = true
            try {
                feedHandlerResult = FeedHandler().parseFeed(source, feedToParse)
                Logd(TAG,  "Parsed ${feedToParse.title}")
                if (feedToParse.title == null) throw InvalidFeedException("Feed has no title")
                for (item in feedToParse.episodes) if (item.title == null) throw InvalidFeedException("Item has no title: $item")
                if (feedToParse.imageUrl.isNullOrEmpty()) feedToParse.imageUrl = Feed.PREFIX_GENERATIVE_COVER + feedToParse.downloadUrl
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
                val feedFile = (request.destination).toUF()
                if (feedFile.exists()) {
                    feedFile.delete()
                    Logd(TAG, "Deletion of file '" + feedFile.absPath + "' ")
                }
            }

            var downloadStatus: DownloadResult
            if (isSuccessful) {
                downloadStatus = DownloadResult(feedToParse.id, feedToParse.getTextIdentifier()?:"", DownloadError.SUCCESS, isSuccessful, reasonDetailed?:"")
            } else {
                downloadStatus = DownloadResult(feedToParse.id, feedToParse.getTextIdentifier() ?: "", reason ?: DownloadError.ERROR_NOT_FOUND, isSuccessful, reasonDetailed ?: "")
                Logt(TAG, "refreshFeed: feed update failed: unsuccessful: ${feed.title}")
                persistFeedLastUpdateFailed(feed, true)
                addDownloadStatus(downloadStatus)
                return@download
            }

            if (fullUpdate) updateFeedFull(feedHandlerResult!!.feed, removeUnlistedItems = false)
            else updateFeedSimple(feedHandlerResult!!.feed)

            // we create a 'successful' download log if the feed's last refresh failed
            val log = getFeedDownloadLog(request.feedfileId)
            if (log.isNotEmpty() && !log[0].isSuccessful) addDownloadStatus(downloadStatus)
            if (!request.source.isNullOrEmpty()) {
                fun updateFeedDownloadURL(original: String, updated: String) {
                    Logd(TAG, "updateFeedDownloadURL(original: $original, updated: $updated)")
                    val feed = realm.query(Feed::class).query("downloadUrl == $0", original).first().find()
                    if (feed != null) upsertBlk(feed) { it.downloadUrl = updated }
                }
                val redirectUrl: String = feedHandlerResult.redirectUrl
                when {
                    !downloader.permanentRedirectUrl.isNullOrEmpty() -> updateFeedDownloadURL(request.source, downloader.permanentRedirectUrl!!)
                    redirectUrl.isNotEmpty() && redirectUrl != request.source -> updateFeedDownloadURL(request.source, redirectUrl)
                }
            }
        }

        if (!downloader.result.isSuccessful) {
            if (downloader.cancelled || downloader.result.reason == DownloadError.ERROR_DOWNLOAD_CANCELLED) {
                Logd(TAG, "refreshFeed: feed refresh cancelled, likely due to feed not changed: ${feed.title}")
                return
            }
            Logt(TAG, "refreshFeed: feed update failed: unsuccessful. cancelled? ${feed.title}")
            persistFeedLastUpdateFailed(feed, true)
            addDownloadStatus(downloader.result)
            return
        }
    }

    class InvalidFeedException(message: String?) : Exception(message)

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