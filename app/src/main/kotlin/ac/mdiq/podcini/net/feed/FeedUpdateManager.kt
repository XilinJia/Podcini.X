package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.utils.NetworkUtils.isAllowMobileFeedRefresh
import ac.mdiq.podcini.net.utils.NetworkUtils.isFeedRefreshAllowed
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.net.utils.NetworkUtils.isVpnOverWifi
import ac.mdiq.podcini.net.utils.NetworkUtils.networkAvailable
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.compose.CommonConfirmAttrib
import ac.mdiq.podcini.ui.compose.commonConfirm
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.MiscFormatter.fullDateTimeString
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.*
import androidx.work.Constraints.Builder
import java.util.*
import java.util.concurrent.TimeUnit

object FeedUpdateManager {
    private val TAG: String = FeedUpdateManager::class.simpleName ?: "Anonymous"
    val feedUpdateWorkId = getAppContext().packageName + "FeedUpdateWorker"

    const val WORK_TAG_FEED_UPDATE: String = "feedUpdate"
    private const val WORK_ID_FEED_UPDATE_MANUAL = "feedUpdateManual"
    const val EXTRA_FEED_ID: String = "feed_id"
    const val EXTRA_NEXT_PAGE: String = "next_page"
    const val EXTRA_FULL_UPDATE: String = "full_update"
    const val EXTRA_EVEN_ON_MOBILE: String = "even_on_mobile"

    private val updateInterval: Long
        get() = getPref(AppPrefs.prefAutoUpdateInterval, "12").toInt().toLong()

    var nextRefreshTime by mutableStateOf("")

    /**
     * Start / restart periodic auto feed refresh
     * @param context Context
     */
    @JvmStatic
    fun restartUpdateAlarm(context: Context, replace: Boolean) {
        if (updateInterval == 0L) WorkManager.getInstance(context).cancelUniqueWork(feedUpdateWorkId)
        else {
            var policy = ExistingPeriodicWorkPolicy.KEEP
            if (replace) {
                putPref(AppPrefs.prefLastFullUpdateTime, System.currentTimeMillis())
                policy = ExistingPeriodicWorkPolicy.UPDATE
            }
            val initialDelay = getInitialDelay(context)
            val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(gearbox.feedUpdateWorkerClass(), updateInterval, TimeUnit.HOURS)
                .setConstraints(Builder()
                    .setRequiredNetworkType(if (isAllowMobileFeedRefresh) NetworkType.CONNECTED else NetworkType.UNMETERED)
                    .build())
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, updateInterval*15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(feedUpdateWorkId, policy, workRequest)
        }
    }

    fun getInitialDelay(context: Context): Long {
        var initialDelay = 0L
        val startHM = getPref(AppPrefs.prefAutoUpdateStartTime, ":").split(":").toMutableList()
        if (startHM[0].isNotBlank() || startHM[1].isNotBlank()) {
            val hour = if (startHM[0].isBlank()) 0 else (startHM[0].toIntOrNull() ?: 0)
            val minute = if (startHM[1].isBlank()) 0 else (startHM[1].toIntOrNull() ?: 0)
            val targetTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            val currentTime = Calendar.getInstance()
            if (targetTime.before(currentTime)) targetTime.add(Calendar.DAY_OF_MONTH, 1)
            initialDelay = targetTime.timeInMillis - currentTime.timeInMillis
        }
        val intervalInMillis = (updateInterval * TimeUnit.HOURS.toMillis(1))
        val lastUpdateTime = getPref(AppPrefs.prefLastFullUpdateTime, 0L)
        Logd(TAG, "lastUpdateTime: $lastUpdateTime updateInterval: $updateInterval")
        nextRefreshTime = if (lastUpdateTime == 0L) {
            if (initialDelay != 0L) fullDateTimeString(Calendar.getInstance().timeInMillis + initialDelay + intervalInMillis)
            else context.getString(R.string.before) + fullDateTimeString(Calendar.getInstance().timeInMillis + intervalInMillis)
        } else fullDateTimeString(lastUpdateTime + intervalInMillis)
        return initialDelay
    }

    @JvmStatic
    @JvmOverloads
    fun runOnce(context: Context, feed: Feed? = null, nextPage: Boolean = false, fullUpdate: Boolean = false) {
        val workRequest: OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(gearbox.feedUpdateWorkerClass())
            .setInitialDelay(0L, TimeUnit.MILLISECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(WORK_TAG_FEED_UPDATE)
        if (feed == null || !feed.isLocalFeed) workRequest.setConstraints(Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())

        val builder = Data.Builder()
        builder.putBoolean(EXTRA_EVEN_ON_MOBILE, true)
        if (fullUpdate) builder.putBoolean(EXTRA_FULL_UPDATE, true)
        if (feed != null) {
            builder.putLong(EXTRA_FEED_ID, feed.id)
            builder.putBoolean(EXTRA_NEXT_PAGE, nextPage)
        }
        workRequest.setInputData(builder.build())
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_ID_FEED_UPDATE_MANUAL, ExistingWorkPolicy.REPLACE, workRequest.build())
    }

    @JvmStatic
    @JvmOverloads
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
                    onConfirm = { runOnce(context, feed)  },
                    onNeutral = {
                        isAllowMobileFeedRefresh = true
                        runOnce(context, feed)
                    })
            }
        }
    }
}
