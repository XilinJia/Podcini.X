package ac.mdiq.podcini.utils

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.math.*

fun formatRfc822Date(date: Instant?): String {
    val formatter = LocalDateTime.Format { byUnicodePattern("dd MMM yy HH:mm:ss Z") }
    val localDateTime = (date?: Instant.fromEpochSeconds(0)).toLocalDateTime(TimeZone.currentSystemDefault())
    return localDateTime.format(formatter)
}

fun fullDateTimeString(time: Long? = null): String {
    val localDateTime = (if (time == null) Clock.System.now() else Instant.fromEpochMilliseconds(time)).toLocalDateTime(TimeZone.currentSystemDefault())
    val formatter = LocalDateTime.Format {
        year()
        char('-')
        monthNumber()
        char('-')
        day()
        char(' ')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }
    return localDateTime.format(formatter)
}

fun stripDateTimeLines(input: String): String {
    val regex = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}:?$""")  // "yyyy-MM-dd HH:mm:ss"
    return input.lines().filterNot { regex.matches(it) }.joinToString("\n")
}

fun formatAbbrev(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val tz = TimeZone.currentSystemDefault()
    val dateTime = instant.toLocalDateTime(tz)
    val now = Clock.System.now().toLocalDateTime(tz)

    val withinLastYear = dateTime.year == now.year

    val withYearFormat = LocalDateTime.Format {
        yearTwoDigits(1970) // or year() for 4 digits
        char('-')
        monthNumber()
        char('-')
        day(padding = Padding.ZERO)
    }
    val noYearFormat = LocalDateTime.Format {
        monthNumber()
        char('-')
        day(padding = Padding.ZERO)
    }
    val format = if (withinLastYear) noYearFormat else withYearFormat
    return dateTime.format(format)
}

fun formatDateTimeFlex(epochMillis: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(tz)
    val now = Clock.System.now().toLocalDateTime(tz)

    val sameDayFormat = LocalDateTime.Format { hour(); char(':'); minute() }
    val sameYearFormat = LocalDateTime.Format { monthNumber(); char('-'); day(); char(' '); hour(); char(':'); minute() }
    val defaultFormat = LocalDateTime.Format { year(); char('-'); monthNumber(); char('-'); day() }

    return when (date.year) {
        now.year if date.dayOfYear == now.dayOfYear -> date.format(sameDayFormat)
        now.year -> date.format(sameYearFormat)
        else -> date.format(defaultFormat)
    }
}

fun formatLargeInteger(n: Int): String {
    return when {
        n < 1000 -> n.toString()
        n < 1_000_000 -> "${(n / 1000.0).toTwoDecimalString()}K"
        n < 1_000_000_000 -> "${(n / 1_000_000.0).toTwoDecimalString()}M"
        else -> "${(n / 1_000_000_000.0).toTwoDecimalString()}B"
    }
}

fun Double.toTwoDecimalString(): String {
    val rounded = round(this * 100) / 100.0
    val s = rounded.toString()
    val parts = s.split(".")
    return when {
        parts.size == 1 -> "$s.00"
        parts[1].length == 1 -> "${s}0"
        else -> s
    }
}

fun dateStampFilename(template: String): String {
    val FileDateFormat = LocalDate.Format { year(); char('-'); monthNumber(); char('-'); day() }
    return template.replace("%s", Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.format(FileDateFormat))
}

fun formatWithGrouping(value: Long, separator: Char = ','): String {
    if (value == 0L) return "0"
    var v = value
    val negative = v < 0
    if (negative) v = -v
    val sb = StringBuilder()
    var count = 0
    if (v == 0L) sb.append('0')
    while (v > 0) {
        if (count == 3) {
            sb.append(separator)
            count = 0
        }
        val digit = (v % 10).toInt()
        sb.append('0' + digit)
        v /= 10
        count++
    }
    if (negative) sb.append('-')
    return sb.reverse().toString()
}

fun formatNumberKmp(value: Double, fractionDigits: Int = 2, useGrouping: Boolean = false, groupingSeparator: Char = ',', decimalSeparator: Char = '.'): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) "Infinity" else "-Infinity"

    val negative = value < 0.0
    val abs = abs(value)

    // scale and round
    val factor = 10.0.pow(fractionDigits)
    val scaled = round(abs * factor).toLong()

    val intPart = scaled / (10.0.pow(fractionDigits).toLong())
    val fracPart = (scaled % (10.0.pow(fractionDigits).toLong())).toInt()

    val intStr = if (useGrouping) group(intPart.toString(), groupingSeparator) else intPart.toString()
    val result = if (fractionDigits == 0) intStr else intStr + decimalSeparator + fracPart.toString().padStart(fractionDigits, '0')
    return if (negative) "-$result" else result
}

private fun group(s: String, sep: Char): String {
    if (s.length <= 3) return s
    val sb = StringBuilder()
    val firstLen = s.length % 3
    var i = 0
    if (firstLen != 0) {
        sb.append(s.substring(0, firstLen))
        i = firstLen
    } else {
        sb.append(s.substring(0, 3))
        i = 3
    }
    while (i < s.length) {
        if (sb.isNotEmpty()) sb.append(sep)
        sb.append(s.substring(i, min(i + 3, s.length)))
        i += 3
    }
    return sb.toString()
}

fun formatNumberKmp(value: Float, fractionDigits: Int = 2, useGrouping: Boolean = false) = formatNumberKmp(value.toDouble(), fractionDigits, useGrouping)

// Parse timestamps for simple patterns with tokens: yyyy, MM, dd, HH, mm, ss
// Example patterns:
//   "yyyyMMdd'T'HHmmss" -> "20260226T153045"
//   "yyyy-MM-dd HH:mm:ss" -> "2026-02-26 15:30:45"
//   "yyyy/MM/dd" -> "2026/02/26"
// Returns epoch millis (UTC) or null if parsing fails
fun parsePatternTimestampToMillis(pattern: String, input: String): Long? {
    // tokenize pattern into sequence of tokens and literals
    val tokens = mutableListOf<String>()
    var i = 0
    while (i < pattern.length) {
        // recognize tokens (longest-first)
        when {
            pattern.startsWith("yyyy", i) -> { tokens += "yyyy"; i += 4 }
            pattern.startsWith("MM", i) -> { tokens += "MM"; i += 2 }
            pattern.startsWith("dd", i) -> { tokens += "dd"; i += 2 }
            pattern.startsWith("HH", i) -> { tokens += "HH"; i += 2 }
            pattern.startsWith("mm", i) -> { tokens += "mm"; i += 2 }
            pattern.startsWith("ss", i) -> { tokens += "ss"; i += 2 }
            else -> {
                // literal run — collect consecutive non-token chars as single literal
                val start = i
                i++
                while (i < pattern.length && !pattern.startsWith("yyyy", i)
                    && !pattern.startsWith("MM", i)
                    && !pattern.startsWith("dd", i)
                    && !pattern.startsWith("HH", i)
                    && !pattern.startsWith("mm", i)
                    && !pattern.startsWith("ss", i)
                ) {
                    i++
                }
                tokens += pattern.substring(start, i)
            }
        }
    }

    // parse input by walking tokens
    var pos = 0
    var year = 1970
    var month = 1
    var day = 1
    var hour = 0
    var minute = 0
    var second = 0

    for (tok in tokens) {
        when (tok) {
            "yyyy" -> {
                if (pos + 4 > input.length) return null
                year = input.substring(pos, pos + 4).toIntOrNull() ?: return null
                pos += 4
            }
            "MM" -> {
                if (pos + 2 > input.length) return null
                month = input.substring(pos, pos + 2).toIntOrNull() ?: return null
                pos += 2
            }
            "dd" -> {
                if (pos + 2 > input.length) return null
                day = input.substring(pos, pos + 2).toIntOrNull() ?: return null
                pos += 2
            }
            "HH" -> {
                if (pos + 2 > input.length) return null
                hour = input.substring(pos, pos + 2).toIntOrNull() ?: return null
                pos += 2
            }
            "mm" -> {
                if (pos + 2 > input.length) return null
                minute = input.substring(pos, pos + 2).toIntOrNull() ?: return null
                pos += 2
            }
            "ss" -> {
                if (pos + 2 > input.length) return null
                second = input.substring(pos, pos + 2).toIntOrNull() ?: return null
                pos += 2
            }
            else -> {
                // literal: must match exactly in input
                val lit = tok
                if (pos + lit.length > input.length) return null
                if (input.substring(pos, pos + lit.length) != lit) return null
                pos += lit.length
            }
        }
    }

    // entire pattern must consume whole input or allow trailing/leading? we require full match
    if (pos != input.length) return null

    // basic validation
    if (month !in 1..12) return null
    if (day !in 1..31) return null
    if (hour !in 0..23) return null
    if (minute !in 0..59) return null
    if (second !in 0..59) return null

    // convert to epoch millis (UTC)
    val days = daysSinceEpochUtc(year, month, day)
    val secondsOfDay = hour * 3600L + minute * 60L + second
    return days * 86400_000L + secondsOfDay * 1000L
}

// helper: compute days since 1970-01-01 (UTC) using proleptic Gregorian calendar
private fun daysSinceEpochUtc(year: Int, month: Int, day: Int): Long {
    var y = year
    var m = month
    if (m <= 2) { y -= 1; m += 12 }
    val era = y / 400
    val yoe = y - era * 400
    val doy = (153 * (m - 3) + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era * 146097 + doe - 719468
    return days.toLong()
}

/**
 * Format epoch milliseconds using a simple pattern.
 * Supported tokens: yyyy, MM, dd, HH, mm, ss, SSS
 * Example patterns: "yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm:ss", "yyyyMMdd"
 *
 * This implementation formats in UTC for portability. If you need local-time formatting,
 * use expect/actual to convert epoch millis to local fields per-platform.
 */
fun formatEpochMillisSimple(epochMillis: Long, pattern: String): String {
    val (y, mo, d, h, mi, s, ms) = utcFieldsFromEpochMillis(epochMillis)
    // Replace tokens — replace longer tokens first (SSS before ss, yyyy before yy if added later)
    return pattern
        .replace("yyyy", y.toString().padStart(4, '0'))
        .replace("MM", mo.toString().padStart(2, '0'))
        .replace("dd", d.toString().padStart(2, '0'))
        .replace("HH", h.toString().padStart(2, '0'))
        .replace("mm", mi.toString().padStart(2, '0'))
        .replace("ss", s.toString().padStart(2, '0'))
        .replace("SSS", ms.toString().padStart(3, '0'))
}

// --- Internals -----------------------------------------------------------------

private data class DateTimeFields(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int, val second: Int, val milli: Int)

/** Convert epoch millis to UTC fields (year,month,day,hour,minute,second,millis). */
private fun utcFieldsFromEpochMillis(ms: Long): DateTimeFields {
    val dayMs = 86_400_000L
    var days = floorDiv(ms, dayMs)
    var rem = ms - days * dayMs
    if (rem < 0) { rem += dayMs; days -= 1 } // normalize negative millis

    val secondsOfDay = rem / 1000L
    val hour = (secondsOfDay / 3600).toInt()
    val minute = ((secondsOfDay % 3600) / 60).toInt()
    val second = (secondsOfDay % 60).toInt()
    val milli = (rem % 1000).toInt()

    val (year, month, day) = ymdFromDaysSinceEpoch(days)
    return DateTimeFields(year, month, day, hour, minute, second, milli)
}

/**
 * Convert days since 1970-01-01 (UTC) to year, month, day in proleptic Gregorian calendar.
 * Algorithm adapted to be compact and correct for wide range of years.
 */
private fun ymdFromDaysSinceEpoch(daysSinceEpoch: Long): Triple<Int, Int, Int> {
    // days since 0000-03-01 proleptic Gregorian
    var z = daysSinceEpoch + 719468L
    val era = floorDiv(z, 146097L).toInt()
    val doe = (z - era * 146097L).toInt() // [0, 146096]
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365 // [0,399]
    var y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100) // [0,365]
    val mp = (5 * doy + 2) / 153 // [0,11]
    val d = doy - ((153 * mp + 2) / 5) + 1
    var m = (mp + 3)
    if (m > 12) {
        m -= 12
        y += 1
    }
    return Triple(y, m, d)
}

private fun floorDiv(x: Long, y: Long): Long = if (x >= 0) x / y else -(((-x) + y - 1) / y)