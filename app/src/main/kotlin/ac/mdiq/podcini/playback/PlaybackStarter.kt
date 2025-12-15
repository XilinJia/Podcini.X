package ac.mdiq.podcini.playback

import ac.mdiq.podcini.playback.base.InTheatre.aController
import ac.mdiq.podcini.playback.base.InTheatre.aCtrlFuture
import ac.mdiq.podcini.playback.base.InTheatre.clearCurTempSpeed
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.setCurEpisode
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isPaused
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isPlaying
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isPrepared
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isStopped
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isStreamingCapable
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.playPause
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.playback.base.SleepManager.Companion.sleepManager
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.episodeChangedWhenScreenOff
import ac.mdiq.podcini.preferences.AppPreferences.prefStreamOverDownload
import ac.mdiq.podcini.storage.database.checkAndMarkDuplicates
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.utils.Logd
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi


class PlaybackStarter(private val context: Context, private val media: Episode) {
    private val TAG = "PlaybackStarter"

    private var shouldStreamThisTime = false

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
                Logd(TAG, "aController ready, play, $status $shouldStreamThisTime")
                if (shouldStreamThisTime && !isStreamingCapable(media)) return

                mPlayer?.isStreaming = shouldStreamThisTime
                when {
                    isPlaying -> playPause()
                    isPaused || isPrepared -> mPlayer?.prepareMedia(media_, shouldStreamThisTime, startWhenPrepared = true, prepareImmediately = true)
                    isStopped -> ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
                    else -> mPlayer?.reinit()
                }
                sleepManager?.restartSleepTimer()
            } else {
                Logd(TAG, "starting PlaybackService")
                ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
            }
        }
    }
}
