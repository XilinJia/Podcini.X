package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PositionSaver(private val context: Context) {

    private var positionSaverJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @OptIn(UnstableApi::class)
    @Synchronized
    fun startPositionSaver(delayInterval: Long) {
        @OptIn(UnstableApi::class)
        fun positionSaverTick() {
            if (mPlayer == null) {
                Loge(TAG, "positionSaverTick mPlayer is null")
                return
            }
            val curPosition = mPlayer!!.getPosition()
            val curDuration = mPlayer!!.getDuration()
            Logd(TAG, "positionSaverTick currentPosition: $curPosition")
            if (curPosition != mPlayer!!.prevPosition) {
                // skip ending
                val remainingTime = curDuration - curPosition
                val item = curEpisode ?: return
                val skipEnd = item.feed?.endingSkip?:0
                val skipEndMS = skipEnd * 1000
                //                  Logd(TAG, "skipEndingIfNecessary: checking " + remainingTime + " " + skipEndMS + " speed " + currentPlaybackSpeed)
                if (skipEnd > 0 && skipEndMS < curDuration && (remainingTime - skipEndMS < 0)) {
                    Logd(TAG, "skipEndingIfNecessary: Skipping the remaining $remainingTime $skipEndMS")
                    Logt(TAG, context.getString(R.string.pref_feed_skip_ending_toast, skipEnd))
                    mPlayer?.autoSkippedFeedMediaId = item.identifyingValue
                    mPlayer?.skip()
                }
                mPlayer?.persistCurrentPosition(true, null, Episode.INVALID_TIME)
                mPlayer!!.prevPosition = curPosition
            }
        }

        cancelPositionSaver()
        positionSaverJob = scope.launch {
            while (isActive) {
                delay(delayInterval)
                positionSaverTick()
                mPlayer?.invokeBufferListener()
            }
        }
        Logd(TAG, "Started PositionSaver with interval: $delayInterval")
    }

    @Synchronized
    fun cancelPositionSaver() {
        Logd(TAG, "canelling PositionSaver")
        positionSaverJob?.cancel()
        positionSaverJob = null
    }

    companion object {
        private val TAG: String = PositionSaver::class.simpleName ?: "Anonymous"

        @SuppressLint("StaticFieldLeak")
        internal var positionSaver: PositionSaver? = null
    }
}
