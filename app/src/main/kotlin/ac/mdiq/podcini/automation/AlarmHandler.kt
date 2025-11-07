package ac.mdiq.podcini.automation

import ac.mdiq.podcini.receiver.AlarmReceiver
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object AlarmHandler {
    private const val TAG = "AlarmHandler"
    const val ALARM_TYPE = "AlarmType"

    fun idFromTriggerTime(millis: Long): Int {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).let {
            val y = it.year % 10
            "%d%02d%02d%02d%02d".format(y, it.monthValue, it.dayOfMonth, it.hour, it.minute).toInt()
        }
    }

    fun playEpisodeAtTime(context: Context, triggerTime: Long, episode: Episode) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                requestExactAlarmPermission(context)
                return
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(ALARM_TYPE, AlarmTypes.PLAY_EPISODE.name + ":" + episode.id.toString())
        }
        // ... (rest of PendingIntent creation and scheduling) ...

        val id = idFromTriggerTime(triggerTime)
        val pendingIntent = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }

    fun setOneTimeAlarm(context: Context, triggerTime: Long, message: AlarmTypes) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                requestExactAlarmPermission(context)
                return
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(ALARM_TYPE, message.name)
        }
        // ... (rest of PendingIntent creation and scheduling) ...

        val id = idFromTriggerTime(triggerTime)
        val pendingIntent = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
    }

    fun setCountdownMinutes(context: Context, countdown: Int, message: AlarmTypes) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                requestExactAlarmPermission(context)
                return
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(ALARM_TYPE, message.name)
        }
        // ... (rest of PendingIntent creation and scheduling) ...

        val triggerAt = SystemClock.elapsedRealtime() + countdown * 60 * 1000
        val id = idFromTriggerTime(triggerAt)
        val pendingIntent = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_IMMUTABLE)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
    }

    // Function to open the permission settings screen
    private fun requestExactAlarmPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }

            if (context is ComponentActivity) {
                context.startActivity(intent)
            } else {
                // Handle cases where context is not an Activity (e.g., in a background service)
                Loge(TAG, "Cannot request permission without an Activity context.")
            }
        }
    }

    fun cancelAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)

        // code mUST match the request code used for scheduling, Use NO_CREATE to check existence
        val pendingIntent = PendingIntent.getBroadcast(context, 42, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel() // Good practice to also cancel the PendingIntent
            Logd(TAG, "Alarm cancelled.")
        }
    }

    enum class AlarmTypes {
        PLAY_EPISODE
    }
}
