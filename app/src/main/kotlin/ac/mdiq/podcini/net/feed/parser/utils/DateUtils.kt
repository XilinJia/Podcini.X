package ac.mdiq.podcini.net.feed.parser.utils

import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import org.apache.commons.lang3.StringUtils
import java.text.ParseException
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Parses several date formats.
 */
private const val TAG: String = "DateUtils"

private val TIME_ZONE_GMT: TimeZone = TimeZone.getTimeZone("GMT")
private val RFC822_DATE_FORMAT: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
        dateFormat.timeZone = TIME_ZONE_GMT
        return dateFormat
    }
}

fun parseDate(input: String?): Date? {
    requireNotNull(input) { "Date must not be null" }
    try { return RFC822_DATE_FORMAT.get()?.parse(input) } catch (ignored: ParseException) { Logd(TAG, ignored.message ?: "parse date error") }

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
                if (current < date.length - 1) (date.take(current) + StringUtils.repeat("0", 4 - (current - start)) + date.substring(current))
                else date.take(current) + StringUtils.repeat("0", 4 - (current - start))
            }
            else -> ""
        }
    }
    val patterns = arrayOf("dd MMM yy HH:mm:ss Z",
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

    val parser = SimpleDateFormat("", Locale.US)
    parser.isLenient = false
    parser.timeZone = TIME_ZONE_GMT

    val pos = ParsePosition(0)
    for (pattern in patterns) {
        parser.applyPattern(pattern)
        pos.index = 0
        try {
            val result = parser.parse(date, pos)
            if (result != null && pos.index == date.length) return result
        } catch (e: Exception) { Logs(TAG, e) }
    }

    // if date string starts with a weekday, try parsing date string without it
    if (date.matches("^\\w+, .*$".toRegex())) return parseDate(date.substring(date.indexOf(',') + 1))

    Logd(TAG, "Could not parse date string \"$input\" [$date], likely an ETag")
    return null
}
