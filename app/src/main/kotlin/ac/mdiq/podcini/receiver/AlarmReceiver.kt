package ac.mdiq.podcini.receiver

import ac.mdiq.podcini.automation.AlarmHandler
import ac.mdiq.podcini.automation.AlarmHandler.ALARM_TYPE
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.storage.database.Episodes.getEpisode
import ac.mdiq.podcini.util.Logd
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    val TAG = "AlarmReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        val message = intent?.getStringExtra(ALARM_TYPE) ?: "Alarm Fired!"

        Logd(TAG, message)
        if (message.startsWith(AlarmHandler.AlarmTypes.PLAY_EPISODE.name)) {
            val id = message.substringAfter(":").toLong()
            val episode = getEpisode(id, false) ?: return
            PlaybackStarter(context!!, episode).shouldStreamThisTime(null).start()
        }
    }
}