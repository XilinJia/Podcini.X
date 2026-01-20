package ac.mdiq.podcini.receiver

import ac.mdiq.podcini.PodciniApp.Companion.getApp
import ac.mdiq.podcini.automation.autodownload
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logt
import ac.mdiq.podcini.config.ClientConfigurator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager


class ConnectivityActionReceiver : BroadcastReceiver() {
     override fun onReceive(context: Context, intent: Intent) {
        Logd(TAG, "onReceive called with action: ${intent.action}")
        if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            Logd(TAG, "Received intent")
            ClientConfigurator.initialize()
            networkChangedDetected()
        }
    }

    private fun networkChangedDetected() {
        if (getApp().networkMonitor.networkAllowAutoDownload) {
            Logd(TAG, "auto-dl network available, starting auto-download")
            autodownload()
        } else { // if new network is Wi-Fi, finish ongoing downloads,
            // otherwise cancel all downloads
            if (getApp().networkMonitor.isNetworkRestricted) {
                Logt(TAG, "Device is no longer connected to Wi-Fi. Cancelling ongoing downloads")
                DownloadServiceInterface.impl?.cancelAll()
            }
        }
    }

    companion object {
        private val TAG: String = ConnectivityActionReceiver::class.simpleName ?: "Anonymous"
    }
}
