package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.R
import java.util.Locale

const val DAY_MIL = 8.64e+7
const val FOUR_DAY_MIL = 3.456e+8

private const val HOURS_MIL = 3600000
private const val MINUTES_MIL = 60000
private const val SECONDS_MIL = 1000

/**
 * Converts milliseconds to a string containing hours, minutes and seconds.
 */
fun durationStringFull(duration: Int): String {
    fun millisecondsToHms(duration: Long): IntArray {
        val h = (duration / HOURS_MIL).toInt()
        var rest = duration - h * HOURS_MIL
        val m = (rest / MINUTES_MIL).toInt()
        rest -= (m * MINUTES_MIL).toLong()
        val s = (rest / SECONDS_MIL).toInt()
        return intArrayOf(h, m, s)
    }
    if (duration <= 0) return "00:00:00"
    else {
        val hms = millisecondsToHms(duration.toLong())
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hms[0], hms[1], hms[2])
    }
}

fun durationStringAdapt(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = if (totalSeconds > 3600) totalSeconds / 3600 else 0
    val minutes = (if (totalSeconds > 3600) (totalSeconds % 3600) else totalSeconds) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

/**
 * Converts milliseconds to a string containing hours and minutes or minutes and seconds.
 */
fun getDurationStringShort(duration: Long, inHours: Boolean): String {
    val firstPartBase = if (inHours) HOURS_MIL else MINUTES_MIL
    val firstPart = duration / firstPartBase
    val leftoverFromFirstPart = duration - firstPart * firstPartBase
    val secondPart = leftoverFromFirstPart / (if (inHours) MINUTES_MIL else SECONDS_MIL)
    return String.format(Locale.getDefault(), "%02d:%02d", firstPart, secondPart)
}

/**
 * Converts long duration string (HH:MM:SS) to milliseconds.
 */
fun durationStringLongToMs(input: String): Int {
    val parts = input.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    if (parts.size != 3) return 0

    return parts[0].toInt() * 3600 * 1000 + parts[1].toInt() * 60 * 1000 + parts[2].toInt() * 1000
}

/**
 * Converts short duration string (XX:YY) to milliseconds. If durationIsInHours is true then the
 * format is HH:MM, otherwise it's MM:SS.
 */
fun durationStringShortToMs(input: String, inHours: Boolean): Int {
    val parts = input.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    if (parts.size != 2) return 0
    val modifier = if (inHours) 60 else 1
    return (parts[0].toInt() * 60 * 1000 * modifier + parts[1].toInt() * 1000 * modifier)
}

/**
 * Converts milliseconds to a localized string containing hours and minutes.
 */
fun getDurationStringLocalized(duration: Long): String {
    val resources = getAppContext().resources
    var result = ""
    var h = (duration / HOURS_MIL).toInt()
    val d = h / 24
    if (d > 0) {
        val days = resources.getQuantityString(R.plurals.time_days_quantified, d, d)
        result += days.replace(" ", "\u00A0") + " "
        h -= d * 24
    }
    val rest = (duration - (d * 24 + h) * HOURS_MIL).toInt()
    val m = rest / MINUTES_MIL
    if (h > 0) {
        val hours = resources.getQuantityString(R.plurals.time_hours_quantified, h, h)
        result += hours.replace(" ", "\u00A0")
        if (d == 0) result += " "
    }
    if (d == 0) {
        val minutes = resources.getQuantityString(R.plurals.time_minutes_quantified, m, m)
        result += minutes.replace(" ", "\u00A0")
    }
    return result
}

/**
 * Converts seconds to a localized representation.
 * @param time The time in seconds
 * @return "HH:MM hours"
 */
fun durationInHours(time: Long, showHoursText: Boolean = true): String {
    val hours = time.toFloat() / 3600f
    return String.format(Locale.getDefault(), "%.2f ", hours) + if (showHoursText) getAppContext().getString(R.string.time_hours) else ""
}
