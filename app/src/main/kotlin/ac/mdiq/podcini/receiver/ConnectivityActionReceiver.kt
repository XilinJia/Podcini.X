package ac.mdiq.podcini.receiver

import ac.mdiq.podcini.automation.AutoDownloads.autodownloadEpisodeMedia
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.utils.NetworkUtils.isAutoDownloadAllowed
import ac.mdiq.podcini.net.utils.NetworkUtils.isNetworkRestricted
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logt
import ac.mdiq.podcini.util.config.ClientConfigurator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager


class ConnectivityActionReceiver : BroadcastReceiver() {
     override fun onReceive(context: Context, intent: Intent) {
        Logd(TAG, "onReceive called with action: ${intent.action}")
        if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            Logd(TAG, "Received intent")
            ClientConfigurator.initialize(context)
            networkChangedDetected(context)
        }
    }

    private fun networkChangedDetected(context: Context) {
        if (isAutoDownloadAllowed) {
            Logd(TAG, "auto-dl network available, starting auto-download")
            autodownloadEpisodeMedia(context)
        } else { // if new network is Wi-Fi, finish ongoing downloads,
            // otherwise cancel all downloads
            if (isNetworkRestricted) {
                Logt(TAG, "Device is no longer connected to Wi-Fi. Cancelling ongoing downloads")
                DownloadServiceInterface.impl?.cancelAll(context)
            }
        }
    }

    companion object {
        private val TAG: String = ConnectivityActionReceiver::class.simpleName ?: "Anonymous"
    }
}
