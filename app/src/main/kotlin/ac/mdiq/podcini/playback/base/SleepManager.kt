package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.storage.database.sleepPrefs
import ac.mdiq.podcini.utils.EventFlow
import ac.mdiq.podcini.utils.FlowEvent
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logt
import android.annotation.SuppressLint
import android.content.Context.SENSOR_SERVICE
import android.content.Context.VIBRATOR_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Vibrator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import ac.mdiq.podcini.storage.utils.nowInMillis

class SleepManager {
    private var sleepTimer: SleepTimer? = null
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
     * Sleeps for a given time and then pauses playback.
     */
    internal inner class SleepTimer(private val waitingTime: Long) {
        var timeLeft = waitingTime

        private var hasVibrated = false
        private var shakeListener: ShakeListener? = null

        fun start(): Job {
            var lastTick = nowInMillis()
            fun postTimeLeft() {
                val now = nowInMillis()
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
                        if (sleepPrefs.Vibrate && !hasVibrated) {
                            val v = getAppContext().getSystemService(VIBRATOR_SERVICE) as? Vibrator
                            if (v != null) {
                                v.vibrate(500)
                                hasVibrated = true
                            }
                        }
                        if (shakeListener == null && sleepPrefs.ShakeToReset) shakeListener = ShakeListener(this@SleepTimer)
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

    internal class ShakeListener(private val mSleepTimer: SleepTimer) : SensorEventListener {
        private var mAccelerometer: Sensor? = null
        private var mSensorMgr: SensorManager? = null

        init {
            resume()
        }

        private fun resume() {
            // only a precaution, the user should actually not be able to activate shake to reset
            // when the accelerometer is not available
            mSensorMgr = getAppContext().getSystemService(SENSOR_SERVICE) as SensorManager
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
        private val TAG: String = SleepManager::class.simpleName ?: "Anonymous"

        private const val SLEEP_TIMER_UPDATE_INTERVAL = 10000L  // in millisoconds
        const val SLEEP_TIMER_ENDING_THRESHOLD: Long = 20000  // in millisoconds

        @SuppressLint("StaticFieldLeak")
        internal var sleepManager: SleepManager? = null

        fun isSleepTimerActive(): Boolean = sleepManager?.isSleepTimerActive == true
    }
}
