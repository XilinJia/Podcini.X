package ac.mdiq.podcini.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import ac.mdiq.podcini.config.ClientConfigurator
import ac.mdiq.podcini.net.feed.FeedUpdateManager
import ac.mdiq.podcini.utils.Logd

/**
 * Refreshes all feeds when it receives an intent
 */
class FeedUpdateReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Logd(TAG, "Received intent")
        ClientConfigurator.initialize()
        FeedUpdateManager.runOnce()
    }

    companion object {
        private val TAG: String = FeedUpdateReceiver::class.simpleName ?: "Anonymous"
    }
}
