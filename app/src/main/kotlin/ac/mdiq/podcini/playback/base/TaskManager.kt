package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.R
import ac.mdiq.podcini.playback.base.InTheatre.curEpisode
import ac.mdiq.podcini.playback.base.MediaPlayerBase.Companion.mPlayer
import ac.mdiq.podcini.preferences.SleepTimerPreferences
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.util.EventFlow
import ac.mdiq.podcini.util.FlowEvent
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.Logt
import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.SENSOR_SERVICE
import android.content.Context.VIBRATOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Vibrator
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * Manages the background tasks of PlaybackSerivce, i.e. the sleep timer, the position saver, and the queue loader.
 * The PlaybackServiceTaskManager(PSTM) uses a callback object (PSTMCallback) to notify the PlaybackService about updates from the running tasks.
 */
class TaskManager(private val context: Context) {
    private var sleepTimer: SleepTimer? = null

    private var positionSaverJob: Job? = null
    private var sleepTimerJob: Job? = null

    @get:Synchronized
    val isSleepTimerActive: Boolean
        get() = sleepTimerJob != null && (sleepTimer?.timeLeft ?: 0) > 0

    /**
     * Returns the current sleep timer time or 0 if the sleep timer is not active.
     */
    @get:Synchronized
    val sleepTimerTimeLeft: Long
        get() = if (isSleepTimerActive) sleepTimer!!.timeLeft else 0

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @OptIn(UnstableApi::class)
    private fun positionSaverTick() {
        if (mPlayer == null) return
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

    @OptIn(UnstableApi::class)
    @Synchronized
    fun startPositionSaver(delayInterval: Long) {
        cancelPositionSaver()
        positionSaverJob = scope.launch {
            delay(delayInterval)
            while (isActive) {
                positionSaverTick()
                mPlayer?.invokeBufferListener()
                delay(delayInterval)
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

    fun onChapterLoaded(media: Episode?) {
//            sendNotificationBroadcast(NOTIFICATION_TYPE_RELOAD, 0)
    }

    /**
     * Starts a new sleep timer with the given waiting time. If another sleep timer is already active, it will be
     * cancelled first.
     * After waitingTime has elapsed, onSleepTimerExpired() will be called.
     * @throws java.lang.IllegalArgumentException if waitingTime <= 0
     */
    @Synchronized
    fun setSleepTimer(waitingTime: Long) {
        require(waitingTime > 0) { "Waiting time <= 0" }
        Logd(TAG, "Setting sleep timer to $waitingTime milliseconds")
        if (isSleepTimerActive) sleepTimerJob!!.cancel()
        sleepTimer = SleepTimer(waitingTime)
        sleepTimerJob = sleepTimer!!.start()
    }

    @Synchronized
    fun disableSleepTimer() {
        sleepTimer?.cancel()
        sleepTimer = null
    }

    @Synchronized
    fun restartSleepTimer() {
        if (isSleepTimerActive) {
            Logd(TAG, "Restarting sleep timer")
            sleepTimer!!.restart()
        }
    }

    /**
     * Starts a new thread that loads the chapter marks from a playable object. If another chapter loader is already active,
     * it will be cancelled first.
     * On completion, the callback's onChapterLoaded method will be called.
     */
    @Synchronized
    fun startChapterLoader(media: Episode) {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch(Dispatchers.IO) {
            try {
                media.loadChapters(context, false)
                withContext(Dispatchers.Main) { onChapterLoaded(media) }
            } catch (e: Throwable) { Logs(TAG, e, "Error loading chapters:") }
        }
    }

    @Synchronized
    private fun cancelAllTasks() {
        cancelPositionSaver()
        disableSleepTimer()
    }

    fun shutdown() {
        cancelAllTasks()
    }

    /**
     * Sleeps for a given time and then pauses playback.
     */
    internal inner class SleepTimer(private val waitingTime: Long) {
        var timeLeft = waitingTime

        private var hasVibrated = false
        private var shakeListener: ShakeListener? = null

        fun start(): Job {
            var lastTick = System.currentTimeMillis()
            fun postTimeLeft() {
                val now = System.currentTimeMillis()
                timeLeft -= now - lastTick
                lastTick = now
                Logd(TAG, "timeLeft: $timeLeft")
                EventFlow.postEvent(FlowEvent.SleepTimerUpdatedEvent.updated(timeLeft))
            }
            EventFlow.postEvent(FlowEvent.SleepTimerUpdatedEvent.updated(timeLeft))
            return scope.launch {
                try {
                    while (timeLeft > SLEEP_TIMER_ENDING_THRESHOLD) {
                        delay(SLEEP_TIMER_UPDATE_INTERVAL)
                        postTimeLeft()
                    }
                    while (timeLeft > 0) {
                        delay(1000L)
                        postTimeLeft()
                        Logd(TAG, "Sleep timer is about to expire")
                        if (SleepTimerPreferences.vibrate() && !hasVibrated) {
                            val v = context.getSystemService(VIBRATOR_SERVICE) as? Vibrator
                            if (v != null) {
                                v.vibrate(500)
                                hasVibrated = true
                            }
                        }
                        if (shakeListener == null && SleepTimerPreferences.shakeToReset()) shakeListener = ShakeListener(context, this@SleepTimer)
                        if (timeLeft <= 0) {
                            Logd(TAG, "Sleep timer expired")
                            shakeListener?.pause()
                            shakeListener = null
                            hasVibrated = false
                        }
                    }
                    Logd(TAG, "Sleep timer expired")
                    shakeListener?.pause()
                    shakeListener = null
                    hasVibrated = false
                } catch (e: CancellationException) {  }
            }
        }

        fun restart() {
            EventFlow.postEvent(FlowEvent.SleepTimerUpdatedEvent.cancelled())
            setSleepTimer(waitingTime)
            shakeListener?.pause()
            shakeListener = null
        }

        fun cancel() {
            sleepTimerJob?.cancel()
            sleepTimerJob = null
            shakeListener?.pause()
            EventFlow.postEvent(FlowEvent.SleepTimerUpdatedEvent.cancelled())
        }
    }

    internal class ShakeListener(private val mContext: Context, private val mSleepTimer: SleepTimer) : SensorEventListener {
        private var mAccelerometer: Sensor? = null
        private var mSensorMgr: SensorManager? = null

        init {
            resume()
        }

        private fun resume() {
            // only a precaution, the user should actually not be able to activate shake to reset
            // when the accelerometer is not available
            mSensorMgr = mContext.getSystemService(SENSOR_SERVICE) as SensorManager
            if (mSensorMgr == null) throw UnsupportedOperationException("Sensors not supported")

            mAccelerometer = mSensorMgr!!.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (!mSensorMgr!!.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI)) { // if not supported
                mSensorMgr!!.unregisterListener(this)
//                throw UnsupportedOperationException("Accelerometer not supported")
                Logt(TAG, "Shaking and Accelerometer not supported on device")
            }
        }

        fun pause() {
            mSensorMgr?.unregisterListener(this)
            mSensorMgr = null
        }

        override fun onSensorChanged(event: SensorEvent) {
            val gX = event.values[0] / SensorManager.GRAVITY_EARTH
            val gY = event.values[1] / SensorManager.GRAVITY_EARTH
            val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
            val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble())
            if (gForce > 2.25) {
                Logd(TAG, "Detected shake $gForce")
                mSleepTimer.restart()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    companion object {
        private val TAG: String = TaskManager::class.simpleName ?: "Anonymous"

        private const val SLEEP_TIMER_UPDATE_INTERVAL = 10000L  // in millisoconds
        const val SLEEP_TIMER_ENDING_THRESHOLD: Long = 20000  // in millisoconds

        @SuppressLint("StaticFieldLeak")
        internal var taskManager: TaskManager? = null

        fun isSleepTimerActive(): Boolean = taskManager?.isSleepTimerActive == true
    }
}
