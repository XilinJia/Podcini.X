package ac.mdiq.podcini.utils

import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.upsertBlk
import kotlin.math.abs

/**
 * Collects statistics about the app usage. The statistics are used to allow on-demand configuration:
 * "Looks like you stream a lot. Do you want to toggle the 'Prefer streaming' setting?".
 * The data is only stored locally on the device. It is NOT used for analytics/tracking.
 * A private instance of this class must first be instantiated via
 * init() or otherwise every public method will throw an Exception
 * when called.
 */
object UsageStatistics {
    private const val TAG = "UsageStatistics"
    private const val MOVING_AVERAGE_WEIGHT = 0.8f
    private const val MOVING_AVERAGE_BIAS_THRESHOLD = 0.1f
    private const val SUFFIX_HIDDEN = "_hidden"

    val ACTION_STREAM: StatsAction = StatsAction("downloadVsStream", 0)

    val ACTION_DOWNLOAD: StatsAction = StatsAction("downloadVsStream", 1)

    fun logAction(action: StatsAction) {
        val numExecutions = appAttribs.usageCountMap[action.type + action.value] ?: 0
        val movingAverage = appAttribs.usageAverageMap[action.type] ?: 0.5f
        upsertBlk(appAttribs) {
            it.usageCountMap[action.type + action.value] = numExecutions + 1
            it.usageAverageMap[action.type] = MOVING_AVERAGE_WEIGHT * movingAverage + (1 - MOVING_AVERAGE_WEIGHT) * action.value
        }
    }


    fun hasSignificantBiasTo(action: StatsAction): Boolean {
        if (appAttribs.usageHideMap[action.type + SUFFIX_HIDDEN] == true) return false
        else {
            val movingAverage = appAttribs.usageAverageMap[action.type] ?: 0.5f
            return abs((action.value - movingAverage).toDouble()) < MOVING_AVERAGE_BIAS_THRESHOLD
        }
    }

    fun doNotAskAgain(action: StatsAction) {
        upsertBlk(appAttribs) { it.usageHideMap[action.type + SUFFIX_HIDDEN] = true }
    }

    class StatsAction(val type: String, val value: Int)
}