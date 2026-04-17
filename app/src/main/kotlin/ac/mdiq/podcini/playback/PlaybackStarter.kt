package ac.mdiq.podcini.playback

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.playback.base.InTheatre.aController
import ac.mdiq.podcini.playback.base.InTheatre.aCtrlFuture
import ac.mdiq.podcini.playback.base.InTheatre.theatres
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.isStreamingCapable
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

    // TODO: ensure caller pass correct playerId
    fun start(playerId: Int = 0, force: Boolean = false) {
        Logd(TAG, "start PlaybackService.isRunning: ${PlaybackService.isRunning}")
        var media_ = media
        var sameMedia = true
        if (theatres[playerId].mPlayer?.curEpisode?.id != media.id || force) {
            sameMedia = false
            media_ = checkAndMarkDuplicates(media)
            episodeChangedWhenScreenOff = true
            theatres[playerId].mPlayer?.setAsCurEpisode(media_, force)
        }
        theatres[playerId].mPlayer?.shouldRepeat = repeat
        Logd(TAG, "start: status: ${theatres[playerId].mPlayer?.status} sameMedia: $sameMedia")
        theatres[playerId].mPlayer?.isStreaming = shouldStreamThisTime
        theatres[playerId].mPlayer?.widgetId = widgetId

        Logd(TAG, "aCtrlFuture: ${aCtrlFuture != null} player status: ${theatres[playerId].mPlayer?.status}")
        fun processTask() {
            if (theatres[playerId].mPlayer == null) return
            when {
                theatres[playerId].mPlayer!!.isPlaying -> {
                    theatres[playerId].mPlayer?.pause(false)
                    if (!sameMedia) {
                        theatres[playerId].mPlayer?.prepareMedia(media_, shouldStreamThisTime, startWhenPrepared = true, prepareImmediately = true)
                        sleepManager?.restart()
                    }
                }
                theatres[playerId].mPlayer!!.isPaused || theatres[playerId].mPlayer!!.isPrepared -> {
                    if (sameMedia) theatres[playerId].mPlayer?.play()
                    else theatres[playerId].mPlayer?.prepareMedia(media_, shouldStreamThisTime, startWhenPrepared = true, prepareImmediately = true)
                    sleepManager?.restart()
                }
                theatres[playerId].mPlayer!!.isStopped -> {
                    // TODO: test
//                    ContextCompat.startForegroundService(getAppContext(), Intent(getAppContext(), PlaybackService::class.java))
                    theatres[playerId].mPlayer?.prepareMedia(media_, shouldStreamThisTime, startWhenPrepared = true, prepareImmediately = true)
                    sleepManager?.restart()
                }
                // TODO: test
                theatres[playerId].mPlayer!!.isInitialized -> {
                    theatres[playerId].mPlayer?.prepareMedia(media_, shouldStreamThisTime, startWhenPrepared = true, prepareImmediately = true)
                    sleepManager?.restart()
                }
                else -> {
                    theatres[playerId].mPlayer?.reinit()
                    sleepManager?.restart()
                }
            }
        }
        aCtrlFuture?.let { future ->
            if (future.isDone && aController?.isConnected == true) {
                Logd(TAG, "aController ready, play, ${theatres[playerId].mPlayer?.status} $shouldStreamThisTime")
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
