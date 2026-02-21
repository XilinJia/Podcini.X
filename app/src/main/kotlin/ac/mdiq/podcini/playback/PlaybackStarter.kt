package ac.mdiq.podcini.playback

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.playback.base.InTheatre.aController
import ac.mdiq.podcini.playback.base.InTheatre.aCtrlFuture
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.setAsCurEpisode
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isPaused
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isPlaying
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isPrepared
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isStopped
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isStreamingCapable
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.playPause
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.shouldRepeat
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.playback.base.SleepManager.Companion.sleepManager
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.episodeChangedWhenScreenOff
import ac.mdiq.podcini.storage.database.checkAndMarkDuplicates
import ac.mdiq.podcini.storage.database.prefStreamOverDownload
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.utils.Logd
import android.content.Intent
import androidx.core.content.ContextCompat


class PlaybackStarter(private val media: Episode) {
    private val TAG = "PlaybackStarter"

    private var shouldStreamThisTime = false
    private var repeat = false

    private var widgetId: String = ""

    fun shouldStreamThisTime(shouldStreamThisTime: Boolean?): PlaybackStarter {
        if (shouldStreamThisTime == null) {
            this.shouldStreamThisTime = media.feed == null || media.feedId == null || (!media.downloaded && media.feed?.isLocalFeed != true)
                    || media.feed?.type == Feed.FeedType.YOUTUBE.name || (prefStreamOverDownload && media.feed?.prefStreamOverDownload == true)
        } else this.shouldStreamThisTime = shouldStreamThisTime
        return this
    }

    fun setToRepeat(repeat_: Boolean): PlaybackStarter {
        repeat = repeat_
        return this
    }

    fun setWidgetId(widgetId: String): PlaybackStarter {
        this.widgetId = widgetId
        return this
    }

    fun start() {
        Logd(TAG, "start PlaybackService.isRunning: ${PlaybackService.isRunning}")
        var media_ = media
        var sameMedia = true
        if (curEpisode?.id != media.id) {
            sameMedia = false
            media_ = checkAndMarkDuplicates(media)
            episodeChangedWhenScreenOff = true
            setAsCurEpisode(media_)
        }
        shouldRepeat = repeat
        Logd(TAG, "start: status: $status sameMedia: $sameMedia")
        mPlayer?.isStreaming = shouldStreamThisTime
        mPlayer?.widgetId = widgetId

        Logd(TAG, "aCtrlFuture: ${aCtrlFuture != null} isPlaying: $isPlaying isPaused: $isPaused isPrepared: $isPrepared isStopped: $isStopped")
        fun processTask() {
            when {
                isPlaying -> {
                    playPause()     // TODO: start new?
//                    if (sameMedia) playPause()
//                    else mPlayer?.prepareMedia(media_, shouldStreamThisTime, startWhenPrepared = true, prepareImmediately = true)
                }
                isPaused || isPrepared -> mPlayer?.prepareMedia(media_, shouldStreamThisTime, startWhenPrepared = true, prepareImmediately = true)
                isStopped -> ContextCompat.startForegroundService(getAppContext(), Intent(getAppContext(), PlaybackService::class.java))
                else -> mPlayer?.reinit()
            }
            sleepManager?.restartSleepTimer()
        }
        aCtrlFuture?.let { future ->
            if (future.isDone && aController?.isConnected == true) {
                Logd(TAG, "aController ready, play, $status $shouldStreamThisTime")
                if (shouldStreamThisTime && !isStreamingCapable(media)) return
                processTask()
            } else {
                Logd(TAG, "starting PlaybackService")
                ContextCompat.startForegroundService(getAppContext(), Intent(getAppContext(), PlaybackService::class.java))
            }
        } ?: run {
            Logd(TAG, "aCtrlFuture is null, starting service")
            processTask()
        }
    }
}
