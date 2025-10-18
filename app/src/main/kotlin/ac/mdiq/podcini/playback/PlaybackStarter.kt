package ac.mdiq.podcini.playback

import ac.mdiq.podcini.playback.base.InTheatre.aController
import ac.mdiq.podcini.playback.base.InTheatre.aCtrlFuture
import ac.mdiq.podcini.playback.base.InTheatre.clearCurTempSpeed
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.setCurEpisode
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.EXTRA_ALLOW_STREAM_THIS_TIME
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.playPause
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.base.TaskManager.Companion.taskManager
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.episodeChangedWhenScreenOff
import ac.mdiq.podcini.preferences.AppPreferences.prefStreamOverDownload
import ac.mdiq.podcini.storage.database.Episodes.checkAndMarkDuplicates
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi


class PlaybackStarter(private val context: Context, private val media: Episode) {
    private val TAG = "PlaybackStarter"

    private var shouldStreamThisTime = false
    val intent: Intent
        @UnstableApi
        get() {
            val launchIntent = Intent(context, PlaybackService::class.java)
            launchIntent.putExtra(EXTRA_ALLOW_STREAM_THIS_TIME, shouldStreamThisTime)
            return launchIntent
        }

    fun shouldStreamThisTime(shouldStreamThisTime: Boolean?): PlaybackStarter {
        if (shouldStreamThisTime == null) {
            this.shouldStreamThisTime = media.feed == null || media.feedId == null || !media.downloaded
                    || media.feed?.type == Feed.FeedType.YOUTUBE.name || (prefStreamOverDownload && media.feed?.prefStreamOverDownload == true)
        } else this.shouldStreamThisTime = shouldStreamThisTime
        return this
    }

    @UnstableApi
    fun start() {
        Logd(TAG, "start PlaybackService.isRunning: ${PlaybackService.isRunning}")
        var media_ = media
        if (curEpisode?.id != media.id) {
            media_ = checkAndMarkDuplicates(media)
            episodeChangedWhenScreenOff = true
            setCurEpisode(media_)
            clearCurTempSpeed()
        }
        Logd(TAG, "start: status: $status")
        aCtrlFuture?.let { future ->
            if (future.isDone && aController?.isConnected == true) {
                Logd(TAG, "aController ready, play")
                mPlayer?.isStreaming = shouldStreamThisTime
                when (status) {
                    PlayerStatus.PLAYING -> playPause()
                    PlayerStatus.PAUSED, PlayerStatus.PREPARED -> mPlayer?.prepareMedia(media_, shouldStreamThisTime, startWhenPrepared = true, prepareImmediately = true)
                    PlayerStatus.STOPPED -> ContextCompat.startForegroundService(context, intent)
                    else -> mPlayer?.reinit()
                }
                taskManager?.restartSleepTimer()
            } else {
                Logd(TAG, "starting PlaybackService")
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
