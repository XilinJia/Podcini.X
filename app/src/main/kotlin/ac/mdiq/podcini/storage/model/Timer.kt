package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.automation.ALARM_TYPE
import ac.mdiq.podcini.automation.AlarmTypes
import ac.mdiq.podcini.automation.requestExactAlarmPermission
import ac.mdiq.podcini.receiver.AlarmReceiver
import ac.mdiq.podcini.utils.Logd
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.xilinjia.krdb.types.EmbeddedRealmObject
import io.github.xilinjia.krdb.types.annotations.Index

const val TAG = "Timer"

class Timer: EmbeddedRealmObject {
    @Index
    var id: Long = 0L

    var episodeId: Long = 0L

    var alarmId: Int = 0

    var triggerTime: Long = 0L

    fun reset() {
        val context = getAppContext()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                requestExactAlarmPermission()
                return
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(ALARM_TYPE, AlarmTypes.PLAY_EPISODE.name + ":" + episodeId.toString())
        }
        val pendingIntent = PendingIntent.getBroadcast(context, alarmId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }

    fun cancel() {
        val context = getAppContext()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(context, alarmId, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel() // Good practice to also cancel the PendingIntent
            Logd(TAG, "Timer cancelled.")
        }
    }
}