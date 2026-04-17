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
import ac.mdiq.podcini.utils.showStackTrace
import android.content.Context.VIBRATOR_MANAGER_SERVICE
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.VibrationEffect
import android.os.VibratorManager

class SleepManager {
    private var timer: SleepTimer? = null
    private var timerJob: Job? = null

    @get:Synchronized
    val isActive: Boolean
        get() = timerJob != null && (timer?.timeLeft ?: 0) > 0

    /**
     * Returns the current sleep timer time or 0 if the sleep timer is not active.
     */
    @get:Synchronized
    val timeLeft: Long
        get() = if (isActive) timer!!.timeLeft else 0

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Starts a new sleep timer with the given waiting time. If another sleep timer is already active, it will be
     * cancelled first.
     * After waitingTime has elapsed, onSleepTimerExpired() will be called.
     * @throws java.lang.IllegalArgumentException if waitingTime <= 0
     */
    @Synchronized
    fun setTimer(waitingTime: Long) {
        require(waitingTime > 0) { "Waiting time <= 0" }
//        showStackTrace()
        Logt(TAG, "Setting sleep timer to ${waitingTime/1000} seconds")
        if (isActive) timerJob!!.cancel()
        timer = SleepTimer(waitingTime)
        timerJob = timer!!.start()
    }

    @Synchronized
    fun disable() {
        Logt(TAG, "Sleep timer disabled")
        timer?.cancel()
        timer = null
    }

    @Synchronized
    fun restart() {
        if (isActive) {
            Logt(TAG, "Sleep timer restarted")
            timer!!.restart()
        }
    }

    /**
     * Sleeps for a given time and then pauses playback.
     */
    inner class SleepTimer(private val waitingTime: Long) {
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
                            val vibrator = if (Build.VERSION.SDK_INT >= VERSION_CODES.S) (getAppContext().getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                            else {
                                @Suppress("DEPRECATION")
                                getAppContext().getSystemService(VIBRATOR_SERVICE) as Vibrator
                            }
                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                            hasVibrated = true
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
            setTimer(waitingTime)
            shakeListener?.pause()
            shakeListener = null
        }

        fun cancel() {
            timerJob?.cancel()
            timerJob = null
            shakeListener?.pause()
            EventFlow.postEvent(FlowEvent.SleepTimerUpdatedEvent.cancelled())
        }
    }

    class ShakeListener(private val mSleepTimer: SleepTimer) : SensorEventListener {
        private var mAccelerometer: Sensor? = null
        private var mSensorMgr: SensorManager? = null

        init {
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

        fun isSleepTimerActive(): Boolean = sleepManager?.isActive == true

        val lastTimerValue: Long
            get() = sleepPrefs.LastValue.takeIf { it != 0L } ?: 15L    // in minutes

        val autoEnableFrom: Int
            get() = sleepPrefs.AutoEnableFrom.takeIf { it != 0 } ?: 22

        val autoEnableTo: Int
            get() = sleepPrefs.AutoEnableTo.takeIf { it != 0 } ?: 6

        fun isInTimeRange(from: Int, to: Int, current: Int): Boolean {
            return when {
                from < to -> current in from..<to   // Range covers one day
                from <= current -> true     // Range covers two days
                else -> current < to
            }
        }
    }
}
