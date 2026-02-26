package ac.mdiq.podcini.config.settings

import ac.mdiq.podcini.storage.database.sleepPrefs

object SleepTimer {
    private val TAG: String = SleepTimer::class.simpleName ?: "Anonymous"

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
