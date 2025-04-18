package ac.mdiq.podcini.ui.utils

import ac.mdiq.podcini.net.feed.searcher.PodcastSearcher
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.ui.screens.FeedScreenMode
import ac.mdiq.podcini.util.Logd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

var episodeOnDisplay by mutableStateOf(Episode())

var feedScreenMode by mutableStateOf(FeedScreenMode.List)
var feedOnDisplay by mutableStateOf(Feed())

var curSearchString by mutableStateOf("")
var feedToSearchIn by mutableStateOf<Feed?>(null)
fun setSearchTerms(query: String? = null, feed: Feed? = null) {
    Logd("setSearchTerms", "query: $query feed: ${feed?.id}")
    if (query != null) curSearchString = query
    feedToSearchIn = feed
}

var onlineSearchText by mutableStateOf("")
var onlineSearcherName by mutableStateOf("")
fun setOnlineSearchTerms(searchProvider: Class<out PodcastSearcher?>, query: String? = null) {
    onlineSearchText = query ?: ""
    onlineSearcherName = searchProvider.name
}

var onlineFeedUrl by mutableStateOf("")
var onlineFeedSource by mutableStateOf("")
var isOnlineFeedShared by mutableStateOf(false)
fun setOnlineFeedUrl(url: String, source: String = "", shared: Boolean = false) {
    onlineFeedUrl = url
    onlineFeedSource = source
    isOnlineFeedShared = shared
}
