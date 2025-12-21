package ac.mdiq.podcini.receiver

import ac.mdiq.podcini.automation.ALARM_TYPE
import ac.mdiq.podcini.automation.AlarmTypes
import ac.mdiq.podcini.playback.PlaybackStarter
import ac.mdiq.podcini.storage.database.getEpisode
import ac.mdiq.podcini.utils.Logd
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    val TAG = "AlarmReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val message = intent?.getStringExtra(ALARM_TYPE) ?: "Alarm Fired!"

        Logd(TAG, message)
        if (message.startsWith(AlarmTypes.PLAY_EPISODE.name)) {
            val msgs = message.split(':')
            if (msgs.size < 2) return
            val id = msgs[1].toLong()
            val episode = getEpisode(id, false) ?: return
            val repeat = if (msgs.size == 3) msgs[2].toBoolean() else false
            PlaybackStarter(context, episode).shouldStreamThisTime(null).setRepeat(repeat).start()
        }
    }
}