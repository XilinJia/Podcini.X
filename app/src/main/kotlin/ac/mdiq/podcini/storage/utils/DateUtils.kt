package ac.mdiq.podcini.storage.utils

import ac.mdiq.podcini.utils.Logd
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.byUnicodePattern
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG: String = "DateUtils"

fun nowInMillis(): Long = Clock.System.now().toEpochMilliseconds()

private val monthMap = mapOf(
    "Jan" to "01",
    "Feb" to "02",
    "Mar" to "03",
    "Apr" to "04",
    "May" to "05",
    "Jun" to "06",
    "Jul" to "07",
    "Aug" to "08",
    "Sep" to "09",
    "Oct" to "10",
    "Nov" to "11",
    "Dec" to "12"
)

fun parseRssPubDate(dateString: String): Instant? {
    return try {
        val trimmed = dateString.substringAfter(", ", dateString).trim()
        val parts = trimmed.split(Regex("\\s+"))
        if (parts.size < 5) return null

        val day = parts[0].padStart(2, '0')
        val month = monthMap[parts[1]] ?: return null
        val year = parts[2]
        val time = parts[3]
        val rawOffset = parts[4]

        val offset = when {
            rawOffset == "GMT" -> "+00:00"
            rawOffset.matches(Regex("[+-]\\d{4}")) -> rawOffset.substring(0, 3) + ":" + rawOffset.substring(3)
            rawOffset.matches(Regex("[+-]\\d{2}:\\d{2}")) -> rawOffset
            else -> return null
        }
        val iso = "${year}-${month}-${day}T${time}${offset}"
        Instant.parse(iso)
    } catch (e: Exception) { null }
}

fun parseDate(input: String?): Instant? {
    if (input == null) return null

    val instant = parseRssPubDate(input)
    if (instant != null) return instant

    var date = input.trim { it <= ' ' }.replace('/', '-').replace("( ){2,}+".toRegex(), " ")
    // remove colon from timezone to avoid differences between Android and Java SimpleDateFormat
    date = date.replace("([+-]\\d\\d):(\\d\\d)$".toRegex(), "$1$2")
    // CEST is widely used but not in the "ISO 8601 Time zone" list. Let's hack around.
    date = date.replace("CEST$".toRegex(), "+0200")
    date = date.replace("CET$".toRegex(), "+0100")
    // some generators use "Sept" for September
    date = date.replace("\\bSept\\b".toRegex(), "Sep")

    // if datetime is more precise than seconds, make sure the value is in ms
    if (date.contains(".")) {
        val start = date.indexOf('.')
        var current = start + 1
        while (current < date.length && Character.isDigit(date[current])) current++
        // even more precise than microseconds: discard further decimal places
        date = when {
            current - start >= 4 -> if (current < date.length - 1) date.take(start + 4) + date.substring(current) else date.take(start + 4)
            // less than 4 decimal places: pad to have a consistent format for the parser
            current - start < 4 -> {
                if (current < date.length - 1) (date.take(current) + "0".repeat(4 - (current - start)) + date.substring(current))
                else date.take(current) + "0".repeat(4 - (current - start))
            }
            else -> ""
        }
    }
    val patterns = arrayOf(
        "dd MMM yy HH:mm:ss Z",
        "dd MMM yy HH:mm Z",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss",
        "EEE, dd MMMM yyyy HH:mm:ss Z",
        "EEE, dd MMMM yyyy HH:mm:ss",
        "EEEE, dd MMM yyyy HH:mm:ss Z",
        "EEEE, dd MMM yy HH:mm:ss Z",
        "EEEE, dd MMM yyyy HH:mm:ss",
        "EEEE, dd MMM yy HH:mm:ss",
        "EEE MMM d HH:mm:ss yyyy",
        "EEE, dd MMM yyyy HH:mm Z",
        "EEE, dd MMM yyyy HH:mm",
        "EEE, dd MMMM yyyy HH:mm Z",
        "EEE, dd MMMM yyyy HH:mm",
        "EEEE, dd MMM yyyy HH:mm Z",
        "EEEE, dd MMM yy HH:mm Z",
        "EEEE, dd MMM yyyy HH:mm",
        "EEEE, dd MMM yy HH:mm",
        "EEE MMM d HH:mm yyyy",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS Z",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-ddZ",
        "yyyy-MM-dd",
        "EEE d MMM yyyy HH:mm:ss 'GMT'Z (z)"
    )

    for (pattern in patterns) {
        try {
            val formatter = DateTimeComponents.Format { byUnicodePattern(pattern) }
            val result = formatter.parseOrNull(date) ?: continue
            return try { result.toInstantUsingOffset() } catch (e: Exception) { null }
        } catch (_: IllegalArgumentException) {
            continue
        }
    }

    // if date string starts with a weekday, try parsing date string without it
    if (date.matches("^\\w+, .*$".toRegex())) return parseDate(date.substring(date.indexOf(',') + 1))

    Logd(TAG, "Could not parse date string \"$input\" [$date], likely an ETag")
    return null
}