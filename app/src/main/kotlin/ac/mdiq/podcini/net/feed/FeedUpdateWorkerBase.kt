package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.R
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.feed.FeedUpdateManager.EXTRA_EVEN_ON_MOBILE
import ac.mdiq.podcini.net.feed.FeedUpdateManager.EXTRA_FEED_ID
import ac.mdiq.podcini.net.feed.FeedUpdaterBase.Companion.createNotification
import ac.mdiq.podcini.net.utils.NetworkUtils.isFeedRefreshAllowed
import ac.mdiq.podcini.net.utils.NetworkUtils.networkAvailable
import ac.mdiq.podcini.storage.database.Feeds
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.config.ClientConfigurator
import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

open class FeedUpdateWorkerBase(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    protected val TAG = "FeedUpdateWorkerBase"

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override suspend fun doWork(): Result {
        ClientConfigurator.initialize(applicationContext)
        val feedId = inputData.getLong(EXTRA_FEED_ID, -1L)
        val updater = if (feedId == -1L) gearbox.feedUpdater()
        else {
            val feed = Feeds.getFeed(feedId) ?: return Result.success()
            gearbox.feedUpdater(feed)
        }
        val ready = updater.prepare()
        if (!ready) return Result.failure()

        if (!inputData.getBoolean(EXTRA_EVEN_ON_MOBILE, false) && !updater.allAreLocal) {
            if (!networkAvailable() || !isFeedRefreshAllowed) {
                Loge(TAG, "Refresh not performed: network unavailable or isFeedRefreshAllowed: $isFeedRefreshAllowed")
                return Result.retry()
            }
        }
        val success =  updater.doWork()
        return if (success) Result.success() else Result.failure()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return withContext(Dispatchers.Main) { ForegroundInfo(R.id.notification_updating_feeds, createNotification(null)) }
    }
}
