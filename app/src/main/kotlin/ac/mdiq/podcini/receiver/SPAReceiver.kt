package ac.mdiq.podcini.receiver

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.feed.FeedUpdateManager.runOnce
import ac.mdiq.podcini.storage.database.Feeds.updateFeedFull
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.config.ClientConfigurator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives intents from Podcini Single Purpose apps
 */
class SPAReceiver : BroadcastReceiver() {
     override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SP_APPS_QUERY_FEEDS_REPSONSE) return
        Logd(TAG, "onReceive called with action: ${intent.action}")

        Logd(TAG, "Received SP_APPS_QUERY_RESPONSE")
        if (!intent.hasExtra(ACTION_SP_APPS_QUERY_FEEDS_REPSONSE_FEEDS_EXTRA)) {
            Loge(TAG, "Received invalid SP_APPS_QUERY_RESPONSE: Contains no extra")
            return
        }
        val feedUrls = intent.getStringArrayExtra(ACTION_SP_APPS_QUERY_FEEDS_REPSONSE_FEEDS_EXTRA)
        if (feedUrls == null) {
            Loge(TAG, "Received invalid SP_APPS_QUERY_REPSONSE: extra was null")
            return
        }
        Logd(TAG, "Received feeds list: " + feedUrls.contentToString())
        ClientConfigurator.initialize(context.applicationContext)
        for (url in feedUrls) {
            val feed = Feed(url, null, "Unknown podcast")
            feed.episodes.clear()
            updateFeedFull(context, feed, removeUnlistedItems = false)
        }
         Logt(TAG, context.getString(R.string.sp_apps_importing_feeds_msg))
        runOnce(context)
    }

    companion object {
        private val TAG: String = SPAReceiver::class.simpleName ?: "Anonymous"

        const val ACTION_SP_APPS_QUERY_FEEDS: String = "de.danoeh.antennapdsp.intent.SP_APPS_QUERY_FEEDS"
        private const val ACTION_SP_APPS_QUERY_FEEDS_REPSONSE = "de.danoeh.antennapdsp.intent.SP_APPS_QUERY_FEEDS_RESPONSE"
        private const val ACTION_SP_APPS_QUERY_FEEDS_REPSONSE_FEEDS_EXTRA = "feeds"
    }
}
