package ac.mdiq.podcini.ui.utils

import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.PAFeed
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder.Companion.sortPairOf
import ac.mdiq.podcini.ui.compose.NonlazyGrid
import ac.mdiq.podcini.ui.screens.SearchBy
import ac.mdiq.podcini.utils.Logd
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.xilinjia.krdb.notifications.ResultsChange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class SearchAlgo {
    private val TAG = "SearchAlgo"
    private val searchBIES = SearchBy.entries.toMutableSet()
    fun setSearchByAll() {
        searchBIES.addAll(SearchBy.entries)
    }
    fun isSelected(by: SearchBy) = searchBIES.contains(by)
    private fun setSelected(by: SearchBy, selected: Boolean) {
        if (selected) searchBIES.add(by) else searchBIES.remove(by)
    }

    fun contains(s: String): String {
        return if (s.startsWith('-')) {
            val s1 = s.substring(1).trim()
            "contains[c] '$s1'"
        } else "contains[c] '$s'"
    }

    fun episodesQueryString(feedID: Long, queryWords: List<String>): String {
        Logd("searchEpisodesQuery", "searchEpisodes called")
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            val sb1 = StringBuilder()
            var isStart = true
            val qw = queryWords[i]
            val exl = qw.startsWith('-')
            val command = contains(qw)
            if (isSelected(SearchBy.TITLE)) {
                if (exl) sb1.append("NOT " )
                sb1.append("(title $command)")
                isStart = false
            }
            if (isSelected(SearchBy.DESCRIPTION)) {
                if (!isStart) sb1.append(if (exl) " AND " else " OR ")
                if (exl) sb1.append("NOT ")
                sb1.append("(description $command)")
                if (exl) sb1.append(" AND ") else sb1.append(" OR ")
                if (exl) sb1.append("NOT ")
                sb1.append("(transcript $command)")
                isStart = false
            }
            if (isSelected(SearchBy.COMMENT)) {
                if (!isStart) sb1.append(if (exl) " AND " else " OR ")
                if (exl) sb1.append("NOT ")
                sb1.append("(comment $command)")
            }
            if (sb1.isEmpty()) continue
            sb.append("(")
            sb.append(sb1)
            sb.append(") ")
            if (i != queryWords.size - 1) sb.append("AND ")
        }
        if (sb.isEmpty()) return ""

        var queryString = sb.toString()
        if (feedID != 0L) queryString = "(feedId == $feedID) AND $queryString"
        Logd(TAG, "searchEpisodes queryString: $queryString")

        return queryString
    }

    fun feedQueryString(queryWords: List<String>): String {
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            var isStart = true
            val sb1 = StringBuilder()
            val qw = queryWords[i]
            val exl = qw.startsWith('-')
            val command = contains(qw)
            if (isSelected(SearchBy.TITLE)) {
                if (exl) sb1.append("NOT ")
                sb1.append("(eigenTitle $command)")
                if (exl) sb1.append(" AND ") else sb1.append(" OR ")
                if (exl) sb1.append("NOT ")
                sb1.append("(customTitle $command)")
                isStart = false
            }
            if (isSelected(SearchBy.AUTHOR)) {
                if (!isStart) sb1.append(if (exl) " AND " else " OR ")
                if (exl) sb1.append("NOT ")
                sb1.append("(author $command)")
                isStart = false
            }
            if (isSelected(SearchBy.DESCRIPTION)) {
                if (!isStart) sb1.append(if (exl) " AND " else " OR ")
                if (exl) sb1.append("NOT ")
                sb1.append("(description $command)")
                isStart = false
            }
            if (isSelected(SearchBy.COMMENT)) {
                if (!isStart) sb1.append(if (exl) " AND " else " OR ")
                if (exl) sb1.append("NOT ")
                sb1.append("(comment $command)")
            }
            if (sb1.isEmpty()) continue
            sb.append("(")
            sb.append(sb1)
            sb.append(") ")
            if (i != queryWords.size - 1) sb.append("AND ")
        }
        return sb.toString()
    }

    fun paFeedsQueryString(queryWords: List<String>): String {
        val sb = StringBuilder()
        for (i in queryWords.indices) {
            var isStart = true
            val sb1 = StringBuilder()
            val qw = queryWords[i]
            val exl = qw.startsWith('-')
            val command = contains(qw)
            if (isSelected(SearchBy.TITLE)) {
                if (exl) sb1.append("NOT ")
                sb1.append("(name $command)")
                isStart = false
            }
            if (isSelected(SearchBy.AUTHOR)) {
                if (!isStart) sb1.append(if (exl) " AND " else " OR ")
                if (exl) sb1.append("NOT ")
                sb1.append("(author $command)")
                isStart = false
            }
            if (isSelected(SearchBy.DESCRIPTION)) {
                if (!isStart) sb1.append(if (exl) " AND " else " OR ")
                if (exl) sb1.append("NOT ")
                sb1.append("(description $command)")
            }
            if (sb1.isEmpty()) continue
            sb.append("(")
            sb.append(sb1)
            sb.append(") ")
            if (i != queryWords.size - 1) sb.append("AND ")
        }
        return sb.toString()
    }

    fun searchFeeds(queryWords: List<String>): List<Feed> {
        val queryString = feedQueryString(queryWords)
        if (queryString.isEmpty()) return listOf()
        Logd(TAG, "searchFeeds queryString: $queryString")
        return realm.query(Feed::class).query(queryString).find()
    }
    fun searchPAFeeds(queryWords: List<String>): List<PAFeed> {
        val queryString = paFeedsQueryString(queryWords)
        if (queryString.isEmpty()) return listOf()
        Logd(TAG, "searchFeeds queryString: $queryString")
        return realm.query(PAFeed::class).query(queryString).find()
    }
    fun searchEpisodes(feedID: Long, queryWords: List<String>): Flow<ResultsChange<Episode>> {
        val queryString = episodesQueryString(feedID, queryWords)
        if (queryString.isBlank()) return emptyFlow()
        return realm.query(Episode::class).query(queryString).sort(sortPairOf(EpisodeSortOrder.DATE_DESC)).asFlow()
    }

    @Composable
    fun SearchByGrid(exl: Set<SearchBy> = setOf()) {
        NonlazyGrid(columns = 2, itemCount = SearchBy.entries.size) { index ->
            val c = SearchBy.entries[index]
            if (c !in exl) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 10.dp)) {
                var isChecked by remember { mutableStateOf(isSelected(c)) }
                Checkbox(checked = isChecked,
                    onCheckedChange = { newValue ->
                        setSelected(c, newValue)
                        isChecked = newValue
                    }
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(stringResource(c.nameRes))
            }
        }
    }
}
