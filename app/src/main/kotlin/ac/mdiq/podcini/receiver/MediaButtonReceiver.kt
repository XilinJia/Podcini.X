package ac.mdiq.podcini.receiver

import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.config.ClientConfigurator
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import androidx.core.content.ContextCompat


// TODO: likely not used
/**
 * Receives media button events.
 */
class MediaButtonReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        Logd(TAG, "onReceive Received intent: $intent Action: ${intent.action}")
        val extras = intent.extras
        Logd(TAG, "onReceive Extras: $extras")
        if (extras == null) return
        Logd(TAG, "onReceive Extras: ${extras.keySet()}")
        for (key in extras.keySet()) Logd(TAG, "onReceive Extra[$key] = ${extras[key]}")

//        val event = extras.getParcelable(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= 33) extras.getParcelable(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        else extras.getParcelable(Intent.EXTRA_KEY_EVENT) as KeyEvent?
        Logd(TAG, "onReceive keyEvent = $keyEvent" )

        if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
            ClientConfigurator.initialize(context.applicationContext)
            val serviceIntent = Intent(PLAYBACK_SERVICE_INTENT)
            serviceIntent.setPackage(context.packageName)
            serviceIntent.putExtra(EXTRA_KEYCODE, keyEvent.keyCode)
            serviceIntent.putExtra(EXTRA_SOURCE, keyEvent.source)
            serviceIntent.putExtra(EXTRA_HARDWAREBUTTON, keyEvent.eventTime > 0 || keyEvent.downTime > 0)
            try { ContextCompat.startForegroundService(context, serviceIntent) } catch (e: Exception) { Logs(TAG, e) }
        }
    }

    companion object {
        private val TAG: String = MediaButtonReceiver::class.simpleName ?: "Anonymous"
        const val EXTRA_KEYCODE: String = "ac.mdiq.podcini.service.extra.MediaButtonReceiver.KEYCODE"
        const val EXTRA_CUSTOM_ACTION: String = "ac.mdiq.podcini.service.extra.MediaButtonReceiver.CUSTOM_ACTION"
        const val EXTRA_SOURCE: String = "ac.mdiq.podcini.service.extra.MediaButtonReceiver.SOURCE"
        const val EXTRA_HARDWAREBUTTON: String = "ac.mdiq.podcini.service.extra.MediaButtonReceiver.HARDWAREBUTTON"
        const val NOTIFY_BUTTON_RECEIVER: String = "ac.mdiq.podcini.NOTIFY_BUTTON_RECEIVER"
        const val PLAYBACK_SERVICE_INTENT: String = "ac.mdiq.podcini.intents.PLAYBACK_SERVICE"

        @JvmStatic
        fun createIntent(context: Context, eventCode: Int): Intent {
            val event = KeyEvent(KeyEvent.ACTION_DOWN, eventCode)
            val startingIntent = Intent(context, MediaButtonReceiver::class.java)
            startingIntent.setAction(NOTIFY_BUTTON_RECEIVER)
            startingIntent.putExtra(Intent.EXTRA_KEY_EVENT, event)
            return startingIntent
        }
    }
}
