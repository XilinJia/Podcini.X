package ac.mdiq.podcini.net.feed.searcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue

class PodcastSearchResult internal constructor(
        val title: String,
        val imageUrl: String?,
        val feedUrl: String?,
        val author: String?,
        val count: Int?,
        val update: String?,
        val subscriberCount: Int,
        val source: String) {

    // feedId will be positive if already subscribed
    var feedId by mutableLongStateOf(0L)

    companion object {
        fun dummy(): PodcastSearchResult {
            return PodcastSearchResult("", "", "", "", 0, "", -1, "dummy")
        }
    }
}
