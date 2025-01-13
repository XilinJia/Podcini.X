package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.storage.model.Feed
import android.content.Context
import androidx.compose.runtime.Composable

class FeedBuilder(context: Context, showError: (String?, String)->Unit): FeedBuilderBase(context, showError) {
    private val ytTabsMap: MutableMap<Int, String> = mutableMapOf()
    private var urlInit: String = ""

    fun isYT(url: String): Boolean = false

    fun isYTChannel(): Boolean = false

    fun ytChannelValidTabs(): Int = 0

    @Composable
    fun ConfirmYTChannelTabsDialog(onDismissRequest: () -> Unit, handleFeed: (Feed, Map<String, String>)->Unit) {}

    suspend fun buildYTPlaylist(handleFeed: (Feed, Map<String, String>)->Unit) {}

    suspend fun buildYTChannel(index: Int, title: String, handleFeed: (Feed, Map<String, String>)->Unit) {}

}