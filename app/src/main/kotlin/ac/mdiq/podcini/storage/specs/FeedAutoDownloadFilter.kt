package ac.mdiq.podcini.storage.specs

import kotlinx.serialization.Serializable

private val FILTER_REGEX = Regex("""([^"]\S*|".+?")\s*""")

// We're storing the strings and not the parsed terms because
// 1. It's easier to show the user exactly what they typed in this way (we don't have to recreate it)
// 2. We don't know if we'll actually be asked to parse anything anyways.
@Serializable
class FeedAutoDownloadFilter(
        val includeFilterRaw: String? = "",
        val excludeFilterRaw: String? = "",
        val minDurationFilter: Int = 0,    // in seconds
        val maxDurationFilter: Int = 0,    // in seconds
        val markExcludedPlayed: Boolean = false) {

    val includeTerms: List<String> by lazy { parseTerms(includeFilterRaw ?: "") }
    val excludeTerms: List<String> by lazy { parseTerms(excludeFilterRaw ?: "") }

    /**
     * Parses the text in to a list of single words or quoted strings.
     * Example: "One "Two Three"" returns ["One", "Two Three"]
     * @param filter string to parse in to terms
     * @return list of terms
     */
    private fun parseTerms(filter: String): List<String> {
        val list = FILTER_REGEX.findAll(filter)
            .mapNotNull { it.groups[1]?.value }
            .filter { it.isNotBlank() }
            .map { it.replace("\"", "") }
            .toList()
        return list
    }

    fun queryString(): String {
        if (includeTerms.isEmpty() && excludeTerms.isEmpty() && minDurationFilter <= 0 && maxDurationFilter <= 0) return ""

        val sl = mutableListOf<String>()
        if (hasMinDurationFilter()) sl.add(" duration >= ${minDurationFilter * 1000L} ")
        if (hasMaxDurationFilter()) sl.add(" duration <= ${maxDurationFilter * 1000L} ")

        for (term in excludeTerms) sl.add(" !(title contains[c] '${term.trim { it <= ' ' }.lowercase()}') ")
        for (term in includeTerms) sl.add(" title contains[c] '${term.trim { it <= ' ' }.lowercase()}' ")

        if (sl.isEmpty()) return ""

        val sb = StringBuilder("( ")
        for (i in sl.indices) {
            if (i > 0) sb.append(" AND ")
            sb.append(sl[i])
        }
        sb.append(" )")
        return sb.toString()
    }

    fun hasMinDurationFilter(): Boolean = minDurationFilter > 0

    fun hasMaxDurationFilter(): Boolean = maxDurationFilter > 0

    fun queryExcludeString() : String {
        if (excludeTerms.isEmpty()) return ""
        val sl = mutableListOf<String>()
        for (term in excludeTerms) sl.add(" (title contains[c] '${term.trim { it <= ' ' }.lowercase()}') ")
        val sb = StringBuilder("( ")
        for (i in sl.indices) {
            if (i > 0) sb.append(" AND ")
            sb.append(sl[i])
        }
        sb.append(" )")
        return sb.toString()
    }
}