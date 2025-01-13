package ac.mdiq.podcini.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

import ac.mdiq.podcini.playback.service.PlaybackService
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.service.PlaybackService.Companion.EXTRA_ALLOW_STREAM_THIS_TIME
import ac.mdiq.podcini.storage.model.Episode

import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent


class PlaybackServiceStarter(private val context: Context, private val media: Episode) {

    private var shouldStreamThisTime = false
    private var callEvenIfRunning = false
    val intent: Intent
        get() {
            val launchIntent = Intent(context, PlaybackService::class.java)
//            launchIntent.putExtra(PlaybackServiceConstants.EXTRA_PLAYABLE, media as Parcelable)
            launchIntent.putExtra(EXTRA_ALLOW_STREAM_THIS_TIME, shouldStreamThisTime)
            return launchIntent
        }

    /**
     * Default value: false
     */
    fun callEvenIfRunning(callEvenIfRunning: Boolean): PlaybackServiceStarter {
        this.callEvenIfRunning = callEvenIfRunning
        return this
    }

    fun shouldStreamThisTime(shouldStreamThisTime: Boolean): PlaybackServiceStarter {
        this.shouldStreamThisTime = shouldStreamThisTime
        return this
    }

    fun start() {
        Logd("PlaybackServiceStarter", "starting PlaybackService")
        if (curEpisode != null) EventFlow.postEvent(FlowEvent.PlayEvent(curEpisode!!, FlowEvent.PlayEvent.Action.END))
        curEpisode = media
        curEpisode = media

        if (PlaybackService.isRunning && !callEvenIfRunning) return
        ContextCompat.startForegroundService(context, intent)
    }
}
