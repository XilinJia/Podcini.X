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
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat


class PlaybackStarter(private val context: Context, private val media: Episode) {
    private val TAG = "PlaybackStarter"

    private var shouldStreamThisTime = false
    private var callEvenIfRunning = false
    val intent: Intent
        get() {
            val launchIntent = Intent(context, PlaybackService::class.java)
            launchIntent.putExtra(EXTRA_ALLOW_STREAM_THIS_TIME, shouldStreamThisTime)
            return launchIntent
        }

    fun callEvenIfRunning(callEvenIfRunning: Boolean): PlaybackStarter {
        this.callEvenIfRunning = callEvenIfRunning
        return this
    }

    fun shouldStreamThisTime(shouldStreamThisTime: Boolean): PlaybackStarter {
        this.shouldStreamThisTime = shouldStreamThisTime
        return this
    }

    fun start() {
        Logd(TAG, "start PlaybackService.isRunning: ${PlaybackService.isRunning}")
        if (curEpisode?.id != media.id) {
            if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlayEvent(curEpisode!!, FlowEvent.PlayEvent.Action.END))
            setCurEpisode(media)
            clearCurTempSpeed()
        }
        Logd(TAG, "start: status: $status")
        aCtrlFuture?.let { future ->
            if (future.isDone && aController?.isConnected == true) {
                Logd(TAG, "aController ready, play")
                mPlayer?.isStreaming = shouldStreamThisTime
                when (status) {
                    PlayerStatus.PLAYING, PlayerStatus.PAUSED, PlayerStatus.PREPARED -> playPause()
//                    PlayerStatus.PLAYING -> mPlayer?.pause(true)
//                    PlayerStatus.PAUSED -> mPlayer!!.resume()
//                    PlayerStatus.PREPARED -> mPlayer!!.playMediaObject(media, shouldStreamThisTime, true, true)
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
