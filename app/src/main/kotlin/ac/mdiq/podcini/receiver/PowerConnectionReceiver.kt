package ac.mdiq.podcini.receiver

import ac.mdiq.podcini.automation.autodownload
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.config.ClientConfigurator
import ac.mdiq.podcini.storage.database.appPrefs
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// modified from http://developer.android.com/training/monitoring-device-state/battery-monitoring.html
// and ConnectivityActionReceiver.java
// Updated based on http://stackoverflow.com/questions/20833241/android-charge-intent-has-no-extra-data
// Since the intent doesn't have the EXTRA_STATUS like the android.com article says it does
// (though it used to)
class PowerConnectionReceiver : BroadcastReceiver() {
     override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Logd(TAG, "onReceive charging intent: $action")
        ClientConfigurator.initialize()
        if (Intent.ACTION_POWER_CONNECTED == action) {
            Logd(TAG, "charging, starting auto-download")
            // we're plugged in, this is a great time to auto-download if everything else is
            // right. So, even if the user allows auto-dl on battery, let's still start
            // downloading now. They shouldn't mind.
            // autodownloadUndownloadedItems will make sure we're on the right wifi networks,
            // etc... so we don't have to worry about it.
            autodownload()
        } else {
            // if we're not supposed to be auto-downloading when we're not charging, stop it
            if (!appPrefs.enableAutoDownloadOnBattery) {
                Logd(TAG, "not charging anymore, canceling auto-download")
                DownloadServiceInterface.impl?.cancelAll()
            } else Logd(TAG, "not charging anymore, but the user allows auto-download when on battery so we'll keep going")
        }
    }

    companion object {
        private val TAG: String = PowerConnectionReceiver::class.simpleName ?: "Anonymous"
    }
}
