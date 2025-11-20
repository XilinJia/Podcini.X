package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.FeedUpdateManager.EXTRA_EVEN_ON_MOBILE
import ac.mdiq.podcini.net.feed.FeedUpdateManager.EXTRA_FEED_ID
import ac.mdiq.podcini.net.feed.FeedUpdateManager.EXTRA_FULL_UPDATE
import ac.mdiq.podcini.net.feed.FeedUpdateManager.KEY_IS_PERIODIC
import ac.mdiq.podcini.net.feed.FeedUpdateManager.rescheduleUpdateTaskOnce
import ac.mdiq.podcini.net.feed.FeedUpdaterBase.Companion.createNotification
import ac.mdiq.podcini.net.utils.NetworkUtils.isFeedRefreshAllowed
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.net.utils.NetworkUtils.isVpnOverWifi
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowFeedRefresh
import ac.mdiq.podcini.net.utils.NetworkUtils.networkAvailable
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.config.ClientConfigurator
import ac.mdiq.podcini.util.fullDateTimeString
import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.BackoffPolicy
import androidx.work.Constraints.Builder
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

open class FeedUpdateWorkerBase(context: Context, private val params: WorkerParameters) : CoroutineWorker(context, params) {
    protected val TAG = "FeedUpdateWorkerBase"
    private val MAX_BACKOFF_ATTEMPTS = 3

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun doWork(): Result {
        ClientConfigurator.initialize(applicationContext)

        val attemptCount = params.runAttemptCount
        if (attemptCount > 0) Logt(TAG, "Running backoff refresh due to prior errors")

        val isPeriodic = inputData.getBoolean(KEY_IS_PERIODIC, false)
        if (isPeriodic) putPref(AppPrefs.prefLastFullUpdateTime, System.currentTimeMillis())
        when {
            !networkAvailable() -> {
                EventFlow.postEvent(FlowEvent.MessageEvent(applicationContext.getString(R.string.download_error_no_connection)))
                return if (isPeriodic) Result.retry() else Result.success()
            }
            !isFeedRefreshAllowed -> {
                Logt(TAG, applicationContext.getString(if (isNetworkRestricted && isVpnOverWifi) R.string.confirm_mobile_feed_refresh_dialog_message_vpn else R.string.confirm_mobile_feed_refresh_dialog_message))
                return if (isPeriodic) Result.retry() else Result.success()
            }
            else -> {}
        }
        try {
            val fullUpdate = inputData.getBoolean(EXTRA_FULL_UPDATE, false)

            val feedId = inputData.getLong(EXTRA_FEED_ID, -1L)
            val feed = if (feedId > -1L) getFeed(feedId) else null
            if (feedId > -1L && feed == null) {
                Loge(TAG, "feed is null for feedId $feedId. update abort")
                if (isPeriodic) rescheduleUpdateTaskOnce(applicationContext)
                return Result.success()
            }
            val updater = gearbox.feedUpdater(feed, fullUpdate)
            if (!updater.prepare()) {
                Loge(TAG, "updater prepare failed")
                if (isPeriodic) rescheduleUpdateTaskOnce(applicationContext)
                return Result.success()
            }

            if (!inputData.getBoolean(EXTRA_EVEN_ON_MOBILE, false) && !updater.allAreLocal) {
                if (!networkAvailable() || !isFeedRefreshAllowed) {
                    Loge(TAG, "Refresh not performed: network unavailable or isFeedRefreshAllowed: $isFeedRefreshAllowed")
                    return Result.retry()
                }
            }
            if (updater.doWork()) {
                Logd(TAG, "end of doWork, isPeriodic: $isPeriodic")
                if (isPeriodic) rescheduleUpdateTaskOnce(applicationContext)
                return Result.success()
            } else return Result.success()
        } catch (e: Throwable) {
            Loge(TAG, "Some errors occurred during refresh, will retry: ${e.message}")
            if (isPeriodic) {
                if (attemptCount >= MAX_BACKOFF_ATTEMPTS) {
                    putPref(AppPrefs.feedIdsToRefresh, setOf<String>())
                    rescheduleUpdateTaskOnce(applicationContext)
                    return Result.success()
                }
                return Result.retry()   // to handle system interruption
            }
            return Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return withContext(Dispatchers.Main) { ForegroundInfo(R.id.notification_updating_feeds, createNotification(null)) }
    }
}

object FeedUpdateManager {
    private val TAG: String = FeedUpdateManager::class.simpleName ?: "Anonymous"
    val feedUpdateWorkId = getAppContext().packageName + "FeedUpdateWorker"     // this one is for the old periodic work, now for migration purpose only
    val feedUpdateOnceWorkId = getAppContext().packageName + "FeedUpdateOnceWorker"

    const val WORK_TAG_FEED_UPDATE: String = "feedUpdate"
    private const val WORK_ID_FEED_UPDATE_MANUAL = "feedUpdateManual"
    const val EXTRA_FEED_ID: String = "feed_id"
    const val EXTRA_NEXT_PAGE: String = "next_page"
    const val EXTRA_FULL_UPDATE: String = "full_update"
    const val EXTRA_EVEN_ON_MOBILE: String = "even_on_mobile"

    const val KEY_IS_PERIODIC = "is_periodic"

    private val intervalInMillis: Long
        get() = getPref(AppPrefs.prefAutoUpdateIntervalMinutes, "360").toLong() * TimeUnit.MINUTES.toMillis(1)

    var nextRefreshTime by mutableStateOf("")

//    private fun isWorkScheduled(workName: String, context: Context): Boolean {
//        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName).get()
//        return workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
//    }

    private fun oneRequest( initialDelay: Long): OneTimeWorkRequest {
        return OneTimeWorkRequest.Builder(FeedUpdateWorkerBase::class.java)
            .setInputData(workDataOf(KEY_IS_PERIODIC to true))
            .setConstraints(Builder()
                .setRequiredNetworkType(if (mobileAllowFeedRefresh) NetworkType.CONNECTED else NetworkType.UNMETERED)
                .build())
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, intervalInMillis / 4, TimeUnit.MILLISECONDS)
            .build()
    }

    fun getInitialDelay(context: Context, now: Boolean = false): Long {
        val initialDelay = if (now) 0L else intervalInMillis
        val lastUpdateTime = getPref(AppPrefs.prefLastFullUpdateTime, 0L)
        Logd(TAG, "lastUpdateTime: $lastUpdateTime updateInterval: $intervalInMillis")
        nextRefreshTime = if (lastUpdateTime == 0L) {
            if (initialDelay != 0L) fullDateTimeString(Calendar.getInstance().timeInMillis + initialDelay + intervalInMillis)
            else context.getString(R.string.before) + fullDateTimeString(Calendar.getInstance().timeInMillis + intervalInMillis)
        } else fullDateTimeString(lastUpdateTime + intervalInMillis)

        return initialDelay
    }

    fun scheduleUpdateTaskOnce(context: Context, replace: Boolean, force: Boolean = false) {
        Logd(TAG, "scheduleUpdateTaskOnce intervalInMillis: $intervalInMillis")
        if (BuildConfig.DEBUG) {
            val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(feedUpdateOnceWorkId).get()
            for (wi in workInfos) Logd(TAG, "workInfos: ${wi.id} ${wi.initialDelayMillis} ${wi.runAttemptCount} ${wi.state}")
        }
        if (!force && intervalInMillis == 0L) WorkManager.getInstance(context).cancelUniqueWork(feedUpdateOnceWorkId)
        else {
            var policy = ExistingWorkPolicy.KEEP
            if (replace) {
                putPref(AppPrefs.prefLastFullUpdateTime, System.currentTimeMillis())
                policy = ExistingWorkPolicy.REPLACE
            }
            val initialDelay = getInitialDelay(context, true)
            Logd(TAG, "initialDelay: $initialDelay")
            val oneTimeRequest = oneRequest(initialDelay)
            WorkManager.getInstance(context).enqueueUniqueWork(feedUpdateOnceWorkId, policy, oneTimeRequest)
        }
    }

    fun rescheduleUpdateTaskOnce(context: Context) {
        Logd(TAG, "rescheduleUpdateTaskOnce intervalInMillis: $intervalInMillis")
        if (BuildConfig.DEBUG) {
            val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(feedUpdateOnceWorkId).get()
            for (wi in workInfos) Logd(TAG, "workInfos: ${wi.id} ${wi.initialDelayMillis} ${wi.runAttemptCount} ${wi.state}")
        }
        if (intervalInMillis == 0L) WorkManager.getInstance(context).cancelUniqueWork(feedUpdateOnceWorkId)
        else {
            val initialDelay = getInitialDelay(context)
            Logd(TAG, "initialDelay: $initialDelay")
            val oneTimeRequest = oneRequest(initialDelay)
            WorkManager.getInstance(context).enqueueUniqueWork(feedUpdateOnceWorkId, ExistingWorkPolicy.APPEND_OR_REPLACE, oneTimeRequest)
        }
    }

    fun checkAndscheduleUpdateTaskOnce(context: Context, replace: Boolean, force: Boolean = false) {
        when {
            !networkAvailable() -> EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.download_error_no_connection)))
            !isFeedRefreshAllowed -> {
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.feed_refresh_title),
                    message = context.getString(if (isNetworkRestricted && isVpnOverWifi) R.string.confirm_mobile_feed_refresh_dialog_message_vpn else R.string.confirm_mobile_feed_refresh_dialog_message),
                    confirmRes = R.string.confirm_mobile_streaming_button_once,
                    cancelRes = R.string.no,
                    neutralRes = R.string.confirm_mobile_streaming_button_always,
                    onConfirm = { scheduleUpdateTaskOnce(context, replace = true, force = true) },
                    onNeutral = {
                        mobileAllowFeedRefresh = true
                        scheduleUpdateTaskOnce(context, replace = true, force = true)
                    })
            }
            else -> scheduleUpdateTaskOnce(context, replace = true, force = true)
        }
    }

    fun runOnce(context: Context, feed: Feed? = null, nextPage: Boolean = false, fullUpdate: Boolean = false) {
        Logd(TAG, "runOnce feed: ${feed?.title}")
        val workRequest: OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(FeedUpdateWorkerBase::class.java)
            .setInitialDelay(0L, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_TAG_FEED_UPDATE)
        if (feed == null || !feed.isLocalFeed) workRequest.setConstraints(Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())

        val builder = Data.Builder()
        builder.putBoolean(EXTRA_EVEN_ON_MOBILE, true)
        builder.putBoolean(EXTRA_FULL_UPDATE, fullUpdate)
        if (feed != null) {
            builder.putLong(EXTRA_FEED_ID, feed.id)
            builder.putBoolean(EXTRA_NEXT_PAGE, nextPage)
        }
        workRequest.setInputData(builder.build())
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(WORK_ID_FEED_UPDATE_MANUAL, ExistingWorkPolicy.REPLACE, workRequest.build())
    }

    fun runOnceOrAsk(context: Context, feed: Feed? = null, fullUpdate: Boolean = false) {
        Logd(TAG, "Run auto update immediately in background.")
        when {
            feed != null && feed.isLocalFeed -> runOnce(context, feed, fullUpdate = fullUpdate)
            !networkAvailable() -> EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.download_error_no_connection)))
            isFeedRefreshAllowed -> runOnce(context, feed, fullUpdate = fullUpdate)
            else -> {
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.feed_refresh_title),
                    message = context.getString(if (isNetworkRestricted && isVpnOverWifi) R.string.confirm_mobile_feed_refresh_dialog_message_vpn else R.string.confirm_mobile_feed_refresh_dialog_message),
                    confirmRes = R.string.confirm_mobile_streaming_button_once,
                    cancelRes = R.string.no,
                    neutralRes = R.string.confirm_mobile_streaming_button_always,
                    onConfirm = { runOnce(context, feed, fullUpdate = fullUpdate)  },
                    onNeutral = {
                        mobileAllowFeedRefresh = true
                        runOnce(context, feed, fullUpdate = fullUpdate)
                    })
            }
        }
    }
}
