package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.DownloadError
import ac.mdiq.podcini.net.download.service.DownloadRequestCreator.create
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowEpisodeDownload
import ac.mdiq.podcini.playback.base.InTheatre.curQueue
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.database.Episodes
import ac.mdiq.podcini.storage.database.LogsAndStats
import ac.mdiq.podcini.storage.database.Queues
import ac.mdiq.podcini.storage.database.Queues.removeFromQueueSync
import ac.mdiq.podcini.storage.database.RealmDB.realm
import ac.mdiq.podcini.storage.database.RealmDB.upsertBlk
import ac.mdiq.podcini.storage.model.DownloadResult
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Episode.MediaMetadataRetrieverCompat
import ac.mdiq.podcini.storage.utils.ChapterUtils
import ac.mdiq.podcini.storage.utils.StorageUtils.ensureMediaFileExists
import ac.mdiq.podcini.ui.activity.MainActivity.Companion.mainNavController
import ac.mdiq.podcini.ui.screens.Screens
import ac.mdiq.podcini.ui.utils.NotificationUtils
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.config.ClientConfigurator
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.Constraints.Builder
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.net.URI
import java.util.Locale
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.apache.commons.io.FileUtils
import kotlin.coroutines.cancellation.CancellationException

class DownloadServiceInterfaceImpl : DownloadServiceInterface() {
    override fun downloadNow(context: Context, item: Episode, ignoreConstraints: Boolean) {
        if (item.downloadUrl.isNullOrEmpty()) {
            Loge(TAG, "downloadUrl is null or empty ${item.title}")
            return
        }
        Logd(TAG, "starting downloadNow")
        val workRequest: OneTimeWorkRequest.Builder = getRequest(item)
        workRequest.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        if (ignoreConstraints) workRequest.setConstraints(Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        else workRequest.setConstraints(constraints)
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(item.downloadUrl!!, ExistingWorkPolicy.KEEP, workRequest.build())
    }

    override fun download(context: Context, item: Episode) {
        if (item.downloadUrl.isNullOrEmpty()) {
            Loge(TAG, "downloadUrl is null or empty ${item.title}")
            return
        }
        Logd(TAG, "starting download")
        val workRequest: OneTimeWorkRequest.Builder = getRequest(item)
        workRequest.setConstraints(constraints)
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(item.downloadUrl!!, ExistingWorkPolicy.KEEP, workRequest.build())
    }

    override fun cancel(context: Context, media: Episode) {
        Logd(TAG, "starting cancel")
        // This needs to be done here, not in the worker. Reason: The worker might or might not be running.
        Episodes.deleteAndRemoveFromQueues(context, media) // Remove partially downloaded file
        val tag = WORK_TAG_EPISODE_URL + media.downloadUrl
        val future: Future<List<WorkInfo>> = WorkManager.getInstance(context).getWorkInfosByTag(tag)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val workInfoList = future.get() // Wait for the completion of the future operation and retrieve the result
                workInfoList.forEach { workInfo -> if (workInfo.tags.contains(WORK_DATA_WAS_QUEUED)) removeFromQueueSync(curQueue, media) }
            } catch (exception: Throwable) { Logs(TAG, exception)
            } finally { WorkManager.getInstance(context).cancelAllWorkByTag(tag) }
        }
    }

    override fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(WORK_TAG)
    }

    class EpisodeDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        private var downloader: Downloader? = null
        private val isLastRunAttempt: Boolean
            get() = runAttemptCount >= 2

        override suspend fun doWork(): Result {
            Logd(TAG, "starting doWork")
            ClientConfigurator.initialize(applicationContext)
            val mediaId = inputData.getLong(WORK_DATA_MEDIA_ID, 0)
            val media = realm.query(Episode::class).query("id == $0", mediaId).first().find()
            if (media == null) {
                Loge(TAG, "media is null for mediaId: $mediaId")
                return Result.failure()
            }
            val request = create(media).build()
            val progressUpdaterJob = CoroutineScope(Dispatchers.IO).launch {
                val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                while (isActive) {
                    try {
                        synchronized(notificationProgress) { notificationProgress.put(media.getEpisodeTitle(), request.progressPercent) }
                        withTimeoutOrNull(5000) {
                            setProgressAsync(Data.Builder().putInt(WORK_DATA_PROGRESS, request.progressPercent).build()).get()
                            nm.notify(R.id.notification_downloading, generateProgressNotification())
                        }
                        delay(1000)
                    } catch (e: CancellationException) { return@launch
                    } catch (e: Exception) {
                        Loge(TAG, "Episode download progressUpdaterJob exception: ${e.message}")
                        return@launch
                    }
                }
            }
            var result: Result = Result.failure()
            try { result = performDownload(request)
            } catch (e: Exception) {
                Logs(TAG, e)
                result = Result.failure()
            } finally {
                if (result == Result.failure() && downloader?.downloadRequest?.destination != null) FileUtils.deleteQuietly(File(downloader!!.downloadRequest.destination!!))
                downloader?.cancel()
            }
            progressUpdaterJob.cancel()
            runBlocking { progressUpdaterJob.join() }

            synchronized(notificationProgress) {
                notificationProgress.remove(media.getEpisodeTitle())
                if (notificationProgress.isEmpty()) {
                    val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(R.id.notification_downloading)
                }
            }
            Logd(TAG, "Worker for " + media.downloadUrl + " returned.")
            return result
        }

        override suspend fun getForegroundInfo(): ForegroundInfo {
            return withContext(Dispatchers.Main) { ForegroundInfo(R.id.notification_downloading, generateProgressNotification()) }
        }

        private suspend fun performDownload(request: DownloadRequest): Result {
            Logd(TAG, "starting performDownload: ${request.destination}")
            if (request.destination.isNullOrBlank()) {
                Loge(TAG, "performDownload request.destination is null or blank")
                return Result.failure()
            }

            ensureMediaFileExists(request.destination.toUri())
            downloader = DefaultDownloaderFactory().create(request)
            if (downloader == null) {
                Loge(TAG, "performDownload Unable to create downloader")
                return Result.failure()
            }

            try { downloader!!.run()
            } catch (e: Exception) {
                Logs(TAG, e, "failed performDownload exception on downloader!!.call()")
                LogsAndStats.addDownloadStatus(downloader!!.result)
                sendErrorNotification(request.title?:"")
                return Result.failure()
            }
            // This also happens when the worker was preempted, not just when the user cancelled it
            if (downloader!!.cancelled) return Result.success()
            val status = downloader!!.result
            if (status.isSuccessful) {
                val handler = MediaDownloadedHandler(applicationContext, downloader!!.result, request)
                handler.run()
                LogsAndStats.addDownloadStatus(handler.updatedStatus)
                return Result.success()
            }
            if (status.reason == DownloadError.ERROR_HTTP_DATA_ERROR && status.reasonDetailed.toInt() == 416) {
                Logd(TAG, "Requested invalid range, restarting download from the beginning")
                if (downloader?.downloadRequest?.destination != null) FileUtils.deleteQuietly(File(downloader!!.downloadRequest.destination!!))
                sendMessage(request.title?:"", false)
                return retry3times()
            }
            Loge(TAG, "Download failed ${request.title} ${status.reason}")
            LogsAndStats.addDownloadStatus(status)
            if (status.reason in listOf(DownloadError.ERROR_FORBIDDEN, DownloadError.ERROR_NOT_FOUND, DownloadError.ERROR_UNAUTHORIZED, DownloadError.ERROR_IO_BLOCKED)) {
                Loge(TAG, "performDownload failure on various reasons ${status.reason?.name}")
                // Fail fast, these are probably unrecoverable
                sendErrorNotification(request.title?:"")
                return Result.failure()
            }
            sendMessage(request.title?:"", false)
            return retry3times()
        }
        private fun retry3times(): Result {
            if (isLastRunAttempt) {
                Loge(TAG, "retry3times failure on isLastRunAttempt")
                sendErrorNotification(downloader!!.downloadRequest.title?:"")
                return Result.failure()
            } else return Result.retry()
        }
        private fun sendMessage(episodeTitle_: String, isImmediateFail: Boolean) {
            var episodeTitle = episodeTitle_
            val retrying = !isLastRunAttempt && !isImmediateFail
            if (episodeTitle.length > 20) episodeTitle = episodeTitle.substring(0, 19) + "…"

            // TODO: the action may need to be changed
            EventFlow.postEvent(FlowEvent.MessageEvent(
                applicationContext.getString(if (retrying) R.string.download_error_retrying else R.string.download_error_not_retrying, episodeTitle),
                { ctx: Context -> {
                    mainNavController.navigate(Screens.Logs.name)
                } },
                applicationContext.getString(R.string.download_error_details)))
        }
        private fun sendErrorNotification(title: String) {
            val builder = NotificationCompat.Builder(applicationContext, NotificationUtils.CHANNEL_ID.error.name)
            builder.setTicker(applicationContext.getString(R.string.download_report_title))
                .setContentTitle(applicationContext.getString(R.string.download_report_title))
                .setContentText(applicationContext.getString(R.string.download_error_tap_for_details))
                .setSmallIcon(R.drawable.ic_notification_sync_error)
//                .setContentIntent(getDownloadLogsIntent(applicationContext))
                .setAutoCancel(true)
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(R.id.notification_download_report, builder.build())
        }
        private fun generateProgressNotification(): Notification {
            val sb = StringBuilder()
            var progressCopy: Map<String, Int>
            synchronized(notificationProgress) { progressCopy = notificationProgress.toMap() }
            for ((key, value) in progressCopy) sb.append(String.format(Locale.getDefault(), "%s (%d%%)\n", key, value))
            val bigText = sb.toString().trim { it <= ' ' }
            val contentText = if (progressCopy.size == 1) bigText else applicationContext.resources.getQuantityString(R.plurals.downloads_left, progressCopy.size, progressCopy.size)
            val builder = NotificationCompat.Builder(applicationContext, NotificationUtils.CHANNEL_ID.downloading.name)
            builder.setTicker(applicationContext.getString(R.string.download_notification_title_episodes))
                .setContentTitle(applicationContext.getString(R.string.download_notification_title_episodes))
                .setContentText(contentText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
//                .setContentIntent(getDownloadsIntent(applicationContext))
                .setAutoCancel(false)
                .setOngoing(true)
                .setWhen(0)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setSmallIcon(R.drawable.ic_notification_sync)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            return builder.build()
        }

        class MediaDownloadedHandler(private val context: Context, var updatedStatus: DownloadResult, private val request: DownloadRequest) {
            fun run() {
                var item = realm.query(Episode::class).query("id == ${request.feedfileId}").first().find()
                if (item == null) {
                    Loge(TAG, "Could not find downloaded episode object in database")
                    return
                }
                val broadcastUnreadStateUpdate = item.isNew
                item = upsertBlk(item) {
                    it.setIsDownloaded()
                    it.setfileUrlOrNull(request.destination)
                    Logd(TAG, "run() set request.destination: ${request.destination}")
                    if (!request.destination.isNullOrBlank()) {
                        val file = File(URI.create(request.destination).path)
                        it.size = if (file.exists()) file.length() else 0
                        Logd(TAG, "run() set size: ${it.size}")
                    }
                    it.checkEmbeddedPicture(false) // enforce check
                    if (it.chapters.isEmpty()) it.setChapters(it.loadChaptersFromMediaFile(context))
                    if (!it.podcastIndexChapterUrl.isNullOrBlank()) ChapterUtils.loadChaptersFromUrl(it.podcastIndexChapterUrl!!, false)
                    if (!it.fileUrl.isNullOrBlank()) {
                        var durationStr: String? = null
                        try {
                            MediaMetadataRetrieverCompat().use { mmr ->
                                mmr.setDataSource(context, it.fileUrl?.toUri())
                                durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                if (durationStr != null) it.duration = (durationStr!!.toInt())
                            }
                        } catch (e: NumberFormatException) { Logs(TAG, e, "Invalid file duration: $durationStr")
                        } catch (e: Exception) {
                            Logs(TAG, e, "Get duration failed. Reset to 30sc")
                            it.duration = 30000
                        }
                    } else {
                        Loge(TAG, "Get duration failed. fileUrl: ${it.fileUrl} Reset to 30sc")
                        it.duration = 30000
                    }
                    Logd(TAG, "run() set duration: ${it.duration}")
                    it.disableAutoDownload()
                }
                // TODO: need to post two events?
                EventFlow.postEvent(FlowEvent.EpisodeEvent.updated(item))
                if (broadcastUnreadStateUpdate) EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.updated(item))
                if (isProviderConnected) {
                    Logd(TAG, "enqueue synch")
                    val action = EpisodeAction.Builder(item, EpisodeAction.DOWNLOAD).currentTimestamp().build()
                    SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(context, action)
                }
                Logd(TAG, "episode.isNew: ${item.isNew} ${item.playState}")
            }
        }

        companion object {
            private val notificationProgress: MutableMap<String, Int> = mutableMapOf()
        }
    }

    companion object {
        private val TAG: String = DownloadServiceInterfaceImpl::class.simpleName ?: "Anonymous"

        private val constraints: Constraints
            get() = Builder().setRequiredNetworkType(if (mobileAllowEpisodeDownload) NetworkType.CONNECTED else NetworkType.UNMETERED).build()

        private fun getRequest(item: Episode): OneTimeWorkRequest.Builder {
            Logd(TAG, "starting getRequest")
            val workRequest: OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(EpisodeDownloadWorker::class.java)
                .setInitialDelay(0L, TimeUnit.MILLISECONDS)
                .addTag(WORK_TAG)
                .addTag(WORK_TAG_EPISODE_URL + item.downloadUrl)
            if (getPref(AppPrefs.prefEnqueueDownloaded, false)) {
                if (item.feed?.queue != null) runBlocking { Queues.addToQueueSync(item, item.feed?.queue) }
                workRequest.addTag(WORK_DATA_WAS_QUEUED)
            }
            workRequest.setInputData(Data.Builder().putLong(WORK_DATA_MEDIA_ID, item.id).build())
            return workRequest
        }
    }
}
