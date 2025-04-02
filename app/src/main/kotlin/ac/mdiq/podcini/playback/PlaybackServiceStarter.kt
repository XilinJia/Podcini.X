package ac.mdiq.podcini.playback

import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.setCurEpisode
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.status
import ac.mdiq.podcini.playback.base.PlayerStatus
import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.EXTRA_ALLOW_STREAM_THIS_TIME
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat


class PlaybackServiceStarter(private val context: Context, private val media: Episode) {

    private var shouldStreamThisTime = false
    private var callEvenIfRunning = false
    val intent: Intent
        get() {
            val launchIntent = Intent(context, PlaybackService::class.java)
            launchIntent.putExtra(EXTRA_ALLOW_STREAM_THIS_TIME, shouldStreamThisTime)
            return launchIntent
        }

    fun callEvenIfRunning(callEvenIfRunning: Boolean): PlaybackServiceStarter {
        this.callEvenIfRunning = callEvenIfRunning
        return this
    }

    fun shouldStreamThisTime(shouldStreamThisTime: Boolean): PlaybackServiceStarter {
        this.shouldStreamThisTime = shouldStreamThisTime
        return this
    }

    fun start() {
        Logd("PlaybackServiceStarter", "start PlaybackService.isRunning: ${PlaybackService.isRunning}")
        if (curEpisode?.id != media.id) {
            if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlayEvent(curEpisode!!, FlowEvent.PlayEvent.Action.END))
            setCurEpisode(media)
            PlaybackService.clearCurTempSpeed()
        }
        if (!PlaybackService.isRunning || status < PlayerStatus.PREPARED || callEvenIfRunning) ContextCompat.startForegroundService(context, intent)
        else {
            Logd("PlaybackServiceStarter", "start: status: $status")
            PlaybackService.playbackService?.mPlayer?.isStreaming = shouldStreamThisTime
            when (status) {
                PlayerStatus.PLAYING -> PlaybackService.playbackService?.mPlayer?.pause(true)
                else -> PlaybackService.playbackService?.mPlayer?.reinit()
            }
            PlaybackService.playbackService?.taskManager?.restartSleepTimer()
        }
    }
}
