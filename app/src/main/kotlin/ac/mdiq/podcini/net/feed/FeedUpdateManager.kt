package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.utils.NetworkUtils.isAllowMobileFeedRefresh
import ac.mdiq.podcini.net.utils.NetworkUtils.isFeedRefreshAllowed
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.net.utils.NetworkUtils.isVpnOverWifi
import ac.mdiq.podcini.net.utils.NetworkUtils.networkAvailable
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.DialogInterface
import androidx.work.*
import androidx.work.Constraints.Builder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.TimeUnit

object FeedUpdateManager {
    private val TAG: String = FeedUpdateManager::class.simpleName ?: "Anonymous"

    const val WORK_TAG_FEED_UPDATE: String = "feedUpdate"
    private const val WORK_ID_FEED_UPDATE = "ac.mdiq.podcini.service.download.FeedUpdateWorker"
    private const val WORK_ID_FEED_UPDATE_MANUAL = "feedUpdateManual"
    const val EXTRA_FEED_ID: String = "feed_id"
    const val EXTRA_NEXT_PAGE: String = "next_page"
    const val EXTRA_FULL_UPDATE: String = "full_update"
    const val EXTRA_EVEN_ON_MOBILE: String = "even_on_mobile"

    private val updateInterval: Long
        get() = getPref(AppPrefs.prefAutoUpdateIntervall, "12").toInt().toLong()

    private val isAutoUpdateDisabled: Boolean
        get() = updateInterval == 0L

    /**
     * Start / restart periodic auto feed refresh
     * @param context Context
     */
    @JvmStatic
    fun restartUpdateAlarm(context: Context, replace: Boolean) {
        if (isAutoUpdateDisabled) WorkManager.getInstance(context).cancelUniqueWork(WORK_ID_FEED_UPDATE)
        else {
            val workRequest: PeriodicWorkRequest = PeriodicWorkRequest.Builder(FeedUpdateWorker::class.java, updateInterval, TimeUnit.HOURS)
                .setConstraints(Builder().setRequiredNetworkType(if (isAllowMobileFeedRefresh) NetworkType.CONNECTED else NetworkType.UNMETERED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_ID_FEED_UPDATE,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, workRequest)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun runOnce(context: Context, feed: Feed? = null, nextPage: Boolean = false, fullUpdate: Boolean = false) {
        val workRequest: OneTimeWorkRequest.Builder = OneTimeWorkRequest.Builder(FeedUpdateWorker::class.java)
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
            else -> confirmMobileRefresh(context, feed)
        }
    }

    private fun confirmMobileRefresh(context: Context, feed: Feed?) {
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.feed_refresh_title)
            .setPositiveButton(R.string.confirm_mobile_streaming_button_once) { _: DialogInterface?, _: Int -> runOnce(context, feed) }
            .setNeutralButton(R.string.confirm_mobile_streaming_button_always) { _: DialogInterface?, _: Int ->
                isAllowMobileFeedRefresh = true
                runOnce(context, feed)
            }
            .setNegativeButton(R.string.no, null)
        if (isNetworkRestricted && isVpnOverWifi) builder.setMessage(R.string.confirm_mobile_feed_refresh_dialog_message_vpn)
        else builder.setMessage(R.string.confirm_mobile_feed_refresh_dialog_message)

        builder.show()
    }
}
