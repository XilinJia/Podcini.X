package ac.mdiq.podcini.config

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.AppPreferences
import android.app.NotificationManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationChannelGroupCompat
import androidx.core.app.NotificationManagerCompat

@Suppress("ClassName", "EnumEntryName")
enum class CHANNEL_ID {
    user_action,
    refreshing,
    downloading,
    //        playing,
    error,
    sync_error,
    episode_notifications
}

@Suppress("EnumEntryName", "ClassName")
enum class GROUP_ID {
    group_errors,
    group_news
}

fun createChannels() {
    val c = getAppContext()
    val mNotificationManager = NotificationManagerCompat.from(c)

    val channelGroups = listOf(
        createGroupErrors()
        //            createGroupNews(context)
    )
    mNotificationManager.createNotificationChannelGroupsCompat(channelGroups)

    val channels = listOf(
        createChannelUserAction(),
        createChannelFeedUpdate(),
        createChannelDownloading(),
        //            createChannelPlaying(context),
        createChannelError(),
        createChannelSyncError()
        //            createChannelEpisodeNotification(context)
    )
    mNotificationManager.createNotificationChannelsCompat(channels)

    mNotificationManager.deleteNotificationChannelGroup(GROUP_ID.group_news.name)
    mNotificationManager.deleteNotificationChannel(CHANNEL_ID.episode_notifications.name)
}

private fun createChannelUserAction(): NotificationChannelCompat {
    val c = getAppContext()
    return NotificationChannelCompat.Builder(
        CHANNEL_ID.user_action.name, NotificationManagerCompat.IMPORTANCE_HIGH)
        .setName(c.getString(R.string.notification_channel_user_action))
        .setDescription(c.getString(R.string.notification_channel_user_action_description))
        .setGroup(GROUP_ID.group_errors.name)
        .build()
}

private fun createChannelFeedUpdate(): NotificationChannelCompat {
    val c = getAppContext()
    return NotificationChannelCompat.Builder(CHANNEL_ID.refreshing.name, NotificationManager.IMPORTANCE_LOW)
        .setName(c.getString(R.string.notification_channel_refreshing))
        .setDescription(c.getString(R.string.notification_channel_refreshing_description))
        .setShowBadge(false)
        .build()
}

private fun createChannelDownloading(): NotificationChannelCompat {
    val c = getAppContext()
    return NotificationChannelCompat.Builder(
        CHANNEL_ID.downloading.name, NotificationManagerCompat.IMPORTANCE_LOW)
        .setName(c.getString(R.string.notification_channel_downloading))
        .setDescription(c.getString(R.string.notification_channel_downloading_description))
        .setShowBadge(false)
        .build()
}

private fun createChannelError(): NotificationChannelCompat {
    val c = getAppContext()
    val notificationChannel = NotificationChannelCompat.Builder(
        CHANNEL_ID.error.name, NotificationManagerCompat.IMPORTANCE_HIGH)
        .setName(c.getString(R.string.notification_channel_download_error))
        .setDescription(c.getString(R.string.notification_channel_download_error_description))
        .setGroup(GROUP_ID.group_errors.name)

    // Migration from app managed setting: disable notification
    if (!AppPreferences.getPref(AppPreferences.AppPrefs.prefShowDownloadReport, true)) notificationChannel.setImportance(NotificationManagerCompat.IMPORTANCE_NONE)
    return notificationChannel.build()
}

private fun createChannelSyncError(): NotificationChannelCompat {
    val c = getAppContext()
    val notificationChannel = NotificationChannelCompat.Builder(
        CHANNEL_ID.sync_error.name, NotificationManagerCompat.IMPORTANCE_HIGH)
        .setName(c.getString(R.string.notification_channel_sync_error))
        .setDescription(c.getString(R.string.notification_channel_sync_error_description))
        .setGroup(GROUP_ID.group_errors.name)

    // Migration from app managed setting: disable notification
    if (!AppPreferences.getPref(AppPreferences.AppPrefs.pref_gpodnet_notifications, true)) notificationChannel.setImportance(NotificationManagerCompat.IMPORTANCE_NONE)
    return notificationChannel.build()
}

private fun createGroupErrors(): NotificationChannelGroupCompat {
    val c = getAppContext()
    return NotificationChannelGroupCompat.Builder(GROUP_ID.group_errors.name).setName(c.getString(R.string.notification_group_errors)).build()
}

//    private fun createChannelPlaying(): NotificationChannelCompat {
//        return NotificationChannelCompat.Builder(
//            CHANNEL_ID.playing.name, NotificationManagerCompat.IMPORTANCE_LOW)
//            .setName(c.getString(R.string.notification_channel_playing))
//            .setDescription(c.getString(R.string.notification_channel_playing_description))
//            .setShowBadge(false)
//            .build()
//    }

