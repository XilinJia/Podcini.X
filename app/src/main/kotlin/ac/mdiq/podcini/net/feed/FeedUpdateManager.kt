package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.PodciniApp.Companion.getApp
import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.config.ClientConfigurator
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.FeedUpdateManager.EXTRA_FEED_IDS
import ac.mdiq.podcini.net.feed.FeedUpdateManager.EXTRA_FULL_UPDATE
import ac.mdiq.podcini.net.feed.FeedUpdateManager.KEY_IS_PERIODIC
import ac.mdiq.podcini.net.feed.FeedUpdateManager.rescheduleUpdateTaskOnce
import ac.mdiq.podcini.net.feed.FeedUpdaterBase.Companion.createNotification
import ac.mdiq.podcini.net.utils.NetworkUtils.isFeedRefreshAllowed
import ac.mdiq.podcini.net.utils.NetworkUtils.mobileAllowFeedRefresh
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.utils.fullDateTimeString
import android.Manifest
import android.content.Context
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import android.os.Build
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

class FeedUpdateWorkerBase(context: Context, private val params: WorkerParameters) : CoroutineWorker(context, params) {
    private val TAG = "FeedUpdateWorkerBase"
    private val MAX_BACKOFF_ATTEMPTS = 3

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun doWork(): Result {
        setForegroundAsync(getForegroundInfo())
        ClientConfigurator.initialize()

        val attemptCount = params.runAttemptCount
        if (attemptCount > 0) Logt(TAG, "Running backoff refresh due to prior errors")

        val isPeriodic = inputData.getBoolean(KEY_IS_PERIODIC, false)
        if (isPeriodic) upsertBlk(appAttribs) { it.prefLastFullUpdateTime = System.currentTimeMillis() }
        when {
            !getApp().networkMonitor.isConnected -> {
                EventFlow.postEvent(FlowEvent.MessageEvent(applicationContext.getString(R.string.download_error_no_connection)))
                return if (isPeriodic) Result.retry() else Result.success()
            }
            else -> {}
        }
        try {
            val fullUpdate = inputData.getBoolean(EXTRA_FULL_UPDATE, false)

            val feedIds = inputData.getLongArray(EXTRA_FEED_IDS) ?: longArrayOf()
            val feeds = if (feedIds.isNotEmpty()) realm.query(Feed::class).query("id IN $0", feedIds.toList()).find() else listOf()
            Logd(TAG, "doWork feeds: ${feeds.size}")
            if (feedIds.isNotEmpty() && feeds.isEmpty()) {
                Loge(TAG, "feeds not found for feedIds ${feedIds.joinToString()}. update abort")
                if (isPeriodic) rescheduleUpdateTaskOnce()
                return Result.success()
            }
            val updater = gearbox.feedUpdater(feeds, fullUpdate)
            if (!updater.prepare()) {
                Loge(TAG, "updater prepare failed")
                if (isPeriodic) rescheduleUpdateTaskOnce()
                return Result.success()
            }
            if (!getApp().networkMonitor.isConnected) {
                Loge(TAG, "Refresh not performed: network unavailable, will retry")
                return Result.retry()
            }
            if (updater.doWork()) {
                Logd(TAG, "end of doWork, isPeriodic: $isPeriodic")
                if (isPeriodic) rescheduleUpdateTaskOnce()
                return Result.success()
            } else return Result.success()
        } catch (e: Throwable) {
            Loge(TAG, "Some errors occurred during refresh, will retry: ${e.message}")
            if (isPeriodic) {
                if (attemptCount >= MAX_BACKOFF_ATTEMPTS) {
                    upsertBlk(appAttribs) { it.feedIdsToRefresh.clear() }
                    rescheduleUpdateTaskOnce()
                    return Result.success()
                }
                return Result.retry()   // to handle system interruption
            }
            return Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return withContext(Dispatchers.Main) {
            ForegroundInfo(R.id.notification_updating_feeds, createNotification(null),
                if (Build.VERSION.SDK_INT >= 29) FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0 )
        }
    }
}

object FeedUpdateManager {
    private val TAG: String = FeedUpdateManager::class.simpleName ?: "Anonymous"
    val feedUpdateWorkId = getAppContext().packageName + "FeedUpdateWorker"     // this one is for the old periodic work, now for migration purpose only
    val feedUpdateOnceWorkId = getAppContext().packageName + "FeedUpdateOnceWorker"

    const val WORK_TAG_FEED_UPDATE: String = "feedUpdate"
    private const val WORK_ID_FEED_UPDATE_MANUAL = "feedUpdateManual"
    internal const val EXTRA_FEED_IDS: String = "feedIds"

    const val EXTRA_NEXT_PAGE: String = "next_page"
    const val EXTRA_FULL_UPDATE: String = "full_update"

    const val KEY_IS_PERIODIC = "is_periodic"

    private val intervalInMillis: Long
        get() = getPref(AppPrefs.prefAutoUpdateIntervalMinutes, "360").toLong() * TimeUnit.MINUTES.toMillis(1)

    var nextRefreshTime by mutableStateOf("")

//    private fun isWorkScheduled(workName: String, context: Context): Boolean {
//        val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName).get()
//        return workInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
//    }

    private fun oneRequest(initialDelay: Long): OneTimeWorkRequest {
        return OneTimeWorkRequest.Builder(FeedUpdateWorkerBase::class.java)
            .setInputData(workDataOf(KEY_IS_PERIODIC to true))
            .setConstraints(Builder()
//                .setRequiredNetworkType(if (mobileAllowFeedRefresh) NetworkType.CONNECTED else NetworkType.UNMETERED)
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.LINEAR, intervalInMillis / 4, TimeUnit.MILLISECONDS)
            .build()
    }

    fun getInitialDelay(now: Boolean = false): Long {
        val initialDelay = if (now) 0L else intervalInMillis
        val lastUpdateTime = appAttribs.prefLastFullUpdateTime
        Logd(TAG, "lastUpdateTime: $lastUpdateTime updateInterval: $intervalInMillis")
        nextRefreshTime = if (lastUpdateTime == 0L) {
            if (initialDelay != 0L) fullDateTimeString(Calendar.getInstance().timeInMillis + initialDelay + intervalInMillis)
            else getAppContext().getString(R.string.before) + fullDateTimeString(Calendar.getInstance().timeInMillis + intervalInMillis)
        } else fullDateTimeString(lastUpdateTime + intervalInMillis)

        return initialDelay
    }

    fun scheduleUpdateTaskOnce(replace: Boolean, force: Boolean = false) {
        Logd(TAG, "scheduleUpdateTaskOnce intervalInMillis: $intervalInMillis")
        val context = getAppContext()
        var doItNow = true
        if (BuildConfig.DEBUG) {
            val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(feedUpdateOnceWorkId).get()
            for (wi in workInfos) Logd(TAG, "workInfos: ${wi.id} ${wi.initialDelayMillis} ${wi.runAttemptCount} ${wi.state}")
        }
        if (!force && intervalInMillis == 0L) WorkManager.getInstance(context).cancelUniqueWork(feedUpdateOnceWorkId)
        else {
            var policy = ExistingWorkPolicy.KEEP
            if (replace) {
                upsertBlk(appAttribs) { it.prefLastFullUpdateTime = System.currentTimeMillis() }
                policy = ExistingWorkPolicy.REPLACE
            }
            if (!mobileAllowFeedRefresh && !force) {
                Logt(TAG, context.getString(R.string.mobile_feed_refresh_message))
                doItNow = false
            }
            val initialDelay = getInitialDelay(doItNow)
            Logd(TAG, "initialDelay: $initialDelay")
            val oneTimeRequest = oneRequest(initialDelay)
            WorkManager.getInstance(context).enqueueUniqueWork(feedUpdateOnceWorkId, policy, oneTimeRequest)
        }
    }

    internal fun rescheduleUpdateTaskOnce() {
        val context = getAppContext()
        Logd(TAG, "rescheduleUpdateTaskOnce intervalInMillis: $intervalInMillis")
        if (BuildConfig.DEBUG) {
            val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(feedUpdateOnceWorkId).get()
            for (wi in workInfos) Logd(TAG, "workInfos: ${wi.id} ${wi.initialDelayMillis} ${wi.runAttemptCount} ${wi.state}")
        }
        if (intervalInMillis == 0L) WorkManager.getInstance(context).cancelUniqueWork(feedUpdateOnceWorkId)
        else {
            val initialDelay = getInitialDelay()
            Logd(TAG, "initialDelay: $initialDelay")
            val oneTimeRequest = oneRequest(initialDelay)
            WorkManager.getInstance(context).enqueueUniqueWork(feedUpdateOnceWorkId, ExistingWorkPolicy.APPEND_OR_REPLACE, oneTimeRequest)
        }
    }

    fun checkAndscheduleUpdateTaskOnce(replace: Boolean, force: Boolean = false) {
        val context = getAppContext()
        when {
            !getApp().networkMonitor.isConnected -> {
                Logt(TAG, "checkAndscheduleUpdateTaskOnce network not available")
                EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.download_error_no_connection)))
            }
            !isFeedRefreshAllowed -> {
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.feed_refresh_title),
                    message = context.getString(if (getApp().networkMonitor.isNetworkRestricted && getApp().networkMonitor.isVpnOverWifi) R.string.confirm_mobile_feed_refresh_dialog_message_vpn else R.string.confirm_mobile_feed_refresh_dialog_message),
                    confirmRes = R.string.confirm_mobile_streaming_button_once,
                    cancelRes = R.string.no,
                    neutralRes = R.string.confirm_mobile_streaming_button_always,
                    onConfirm = { scheduleUpdateTaskOnce(replace = replace, force = force) },
                    onNeutral = {
                        mobileAllowFeedRefresh = true
                        scheduleUpdateTaskOnce(replace = replace, force = force)
                    })
            }
            else -> scheduleUpdateTaskOnce(replace = replace, force = force)
        }
    }

    fun runOnce(feeds: List<Feed> = listOf(), nextPage: Boolean = false, fullUpdate: Boolean = false) {
        Logd(TAG, "runOnce feeda: ${feeds.size}")
        val workRequest: OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(FeedUpdateWorkerBase::class.java)
            .setInitialDelay(0L, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_TAG_FEED_UPDATE)
        if (feeds.isEmpty()) workRequest.setConstraints(Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        // TODO: need to handle: !feed.isLocalFeed

        val builder = Data.Builder()
        builder.putBoolean(EXTRA_FULL_UPDATE, fullUpdate)
        if (feeds.isNotEmpty()) {
            builder.putLongArray(EXTRA_FEED_IDS, feeds.map { it.id }.toLongArray())
            builder.putBoolean(EXTRA_NEXT_PAGE, nextPage)
        }
        workRequest.setInputData(builder.build())
        WorkManager.getInstance(getAppContext()).enqueueUniqueWork(WORK_ID_FEED_UPDATE_MANUAL, ExistingWorkPolicy.REPLACE, workRequest.build())
    }

    fun runOnceOrAsk(feeds: List<Feed> = listOf(), fullUpdate: Boolean = false) {
        val context = getAppContext()
        Logd(TAG, "Run auto update immediately in background.")
        when {
//            feeds.isNotEmpty() && feed.isLocalFeed -> runOnce(context, feeds, fullUpdate = fullUpdate)    // TODO
            !getApp().networkMonitor.isConnected -> EventFlow.postEvent(FlowEvent.MessageEvent(context.getString(R.string.download_error_no_connection)))
            isFeedRefreshAllowed -> runOnce(feeds, fullUpdate = fullUpdate)
            else -> {
                commonConfirm = CommonConfirmAttrib(
                    title = context.getString(R.string.feed_refresh_title),
                    message = context.getString(if (getApp().networkMonitor.isNetworkRestricted && getApp().networkMonitor.isVpnOverWifi) R.string.confirm_mobile_feed_refresh_dialog_message_vpn else R.string.confirm_mobile_feed_refresh_dialog_message),
                    confirmRes = R.string.confirm_mobile_streaming_button_once,
                    cancelRes = R.string.no,
                    neutralRes = R.string.confirm_mobile_streaming_button_always,
                    onConfirm = { runOnce(feeds, fullUpdate = fullUpdate)  },
                    onNeutral = {
                        mobileAllowFeedRefresh = true
                        runOnce(feeds, fullUpdate = fullUpdate)
                    })
            }
        }
    }
}
