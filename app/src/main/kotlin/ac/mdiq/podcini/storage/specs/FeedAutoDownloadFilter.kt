package ac.mdiq.podcini.storage.specs

import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.util.Logd
import java.io.Serializable
import java.util.Locale
import java.util.regex.Pattern

// We're storing the strings and not the parsed terms because
// 1. It's easier to show the user exactly what they typed in this way (we don't have to recreate it)
// 2. We don't know if we'll actually be asked to parse anything anyways.
class FeedAutoDownloadFilter(
        val includeFilterRaw: String? = "",
        val excludeFilterRaw: String? = "",
        val minDurationFilter: Int = 0,    // in seconds
        val maxDurationFilter: Int = 0,    // in seconds
        val markExcludedPlayed: Boolean = false) : Serializable {

    val includeTerms: List<String> by lazy { parseTerms(includeFilterRaw ?: "") }
    val excludeTerms: List<String> by lazy { parseTerms(excludeFilterRaw ?: "") }

    /**
     * Parses the text in to a list of single words or quoted strings.
     * Example: "One "Two Three"" returns ["One", "Two Three"]
     * @param filter string to parse in to terms
     * @return list of terms
     */
    private fun parseTerms(filter: String): List<String> {
        // from http://stackoverflow.com/questions/7804335/split-string-on-spaces-in-java-except-if-between-quotes-i-e-treat-hello-wor
        val list: MutableList<String> = mutableListOf()
        val m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(filter)
        while (m.find()) if (!m.group(1).isNullOrBlank()) list.add(m.group(1)!!.replace("\"", ""))
        return list
    }

    /**
     * @param item
     * @return true if the item should be downloaded
     */
    fun meetsAutoDLCriteria(item: Episode): Boolean {
        // nothing has been specified, so include everything
        if (includeTerms.isEmpty() && excludeTerms.isEmpty() && minDurationFilter <= 0 && maxDurationFilter <= 0) return true

        // Check if the episode is long enough if minimal duration filter is on, Minimal Duration is stored in seconds
        if (hasMinDurationFilter() && item.duration > 0 && item.duration / 1000 < minDurationFilter) {
            Logd("FeedAutoDownloadFilter", "meetsAutoDLCriteria: no item.duration: ${item.duration} < $minDurationFilter")
            return false
        }

        if (hasMaxDurationFilter() && item.duration > 0 && item.duration / 1000 > maxDurationFilter) {
            Logd("FeedAutoDownloadFilter", "meetsAutoDLCriteria: no item.duration: ${item.duration} > $maxDurationFilter")
            return false
        }

        // check using lowercase so the users don't have to worry about case.
        val title = item.title?.lowercase(Locale.getDefault())?:""

        // if it's explicitly excluded, it shouldn't be autodownloaded
        // even if it has include terms
        for (term in excludeTerms) if (title.contains(term.trim { it <= ' ' }.lowercase(Locale.getDefault()))) {
            Logd("FeedAutoDownloadFilter", "meetsAutoDLCriteria: no title contains: [$term]")
            return false
        }

        for (term in includeTerms) if (title.contains(term.trim { it <= ' ' }.lowercase(Locale.getDefault()))) return true

        // now's the tricky bit
        // if they haven't set an include filter, but they have set an exclude filter
        // default to including, but if they've set both, then exclude
        if (!hasIncludeFilter() && hasExcludeFilter()) return true

        // if they only set min or max duration filter and arrived here, autodownload should happen
        if (hasMinDurationFilter() || hasMaxDurationFilter()) return true

        Logd("FeedAutoDownloadFilter", "meetsAutoDLCriteria: no final")
        return false
    }

    fun queryString(): String {
        if (includeTerms.isEmpty() && excludeTerms.isEmpty() && minDurationFilter <= 0 && maxDurationFilter <= 0) return ""

        val sl = mutableListOf<String>()
        if (hasMinDurationFilter()) sl.add(" duration >= ${minDurationFilter * 1000L} ")
        if (hasMaxDurationFilter()) sl.add(" duration <= ${maxDurationFilter * 1000L} ")

        for (term in excludeTerms) sl.add(" !(title contains[c] '${term.trim { it <= ' ' }.lowercase(Locale.getDefault())}') ")
        for (term in includeTerms) sl.add(" title contains[c] '${term.trim { it <= ' ' }.lowercase(Locale.getDefault())}' ")

        if (sl.isEmpty()) return ""

        val sb = StringBuilder("( ")
        for (i in sl.indices) {
            if (i > 0) sb.append(" AND ")
            sb.append(sl[i])
        }
        sb.append(" )")
        return sb.toString()
    }

    private fun hasIncludeFilter(): Boolean = !includeFilterRaw.isNullOrBlank()

    private fun hasExcludeFilter(): Boolean = !excludeFilterRaw.isNullOrBlank()

    fun hasMinDurationFilter(): Boolean = minDurationFilter > 0

    fun hasMaxDurationFilter(): Boolean = maxDurationFilter > 0
}