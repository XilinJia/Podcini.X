package ac.mdiq.podcini.gears

import ac.mdiq.podcini.net.feed.FeedBuilderBase
import ac.mdiq.podcini.net.feed.FeedUpdateWorkerBase
import ac.mdiq.podcini.net.feed.searcher.PodcastSearchResult
import ac.mdiq.podcini.net.feed.searcher.PodcastSearcher
import ac.mdiq.podcini.net.utils.NetworkUtils.getFinalRedirectedUrl
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.ui.screens.AudioPlayerVM
import ac.mdiq.podcini.ui.utils.ShownotesCleaner
import ac.mdiq.podcini.util.Logd
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URL
import kotlin.jvm.java

open class GearBoxBase {

    open fun init() {}

    @Composable
    open fun ConfirmAddEpisode(sharedUrls: List<String>, showDialog: Boolean, onDismissRequest: () -> Unit) {}

    open fun isGearFeed(url: URL): Boolean = false

    open fun isGearUrl(url: URL): Boolean = false

    open fun clearGearData() {}
    open fun buildWebviewData(episode_: Episode, shownotesCleaner: ShownotesCleaner): Pair<Episode, String>? = null

    open fun buildCleanedNotes(curItem_: Episode, shownotesCleaner: ShownotesCleaner?): Pair<Episode, String?> {
        var curItem = curItem_
        var cleanedNotes: String? = null
        cleanedNotes = shownotesCleaner?.processShownotes(curItem.description ?: "", curItem.duration)
        return Pair(curItem, cleanedNotes)
    }

    @Composable
    open fun PlayerDetailedGearPanel(vm: AudioPlayerVM) {}

    @Composable
    open fun GearSearchText() {}

    open fun canHandleShared(url: URL): Boolean = false

    open fun handleShared(log: ShareLog?, mediaCB: ()->Unit) {}

    open fun feedFilter(properties: HashSet<String>, statements: MutableList<String>) {}

    open fun formMediaSource(metadata: MediaMetadata, media: Episode, context: Context): MediaSource? = null

    open fun formCastMediaSource(media: Episode): Boolean = false

    open fun hasSearcher(): Boolean = false

    open fun getSearcher(): PodcastSearcher? = null

    open fun feedUpdateWorkerClass(): Class<out FeedUpdateWorkerBase> = FeedUpdateWorkerBase::class.java

    open fun formFeedBuilder(url: String, feedSource: String, context: Context, showError: (String?, String) -> Unit): FeedBuilderBase {
        return FeedBuilderBase(context, showError)
    }

    open suspend fun buildFeed(url: String, username: String, password: String, fbb: FeedBuilderBase, handleFeed: (Feed, Map<String, String>)->Unit, showDialog: ()->Unit) {
        val urlFinal = getFinalRedirectedUrl(url)?:""
        fbb.buildPodcast(urlFinal, username, password) { feed_, map -> handleFeed(feed_, map) }
    }

    @Composable
    open fun ShowTabsDialog(fbb: FeedBuilderBase, onDismissRequest: () -> Unit, handleFeed: (Feed, Map<String, String>)->Unit) {}

    open fun subscribeFeed(feed: PodcastSearchResult, context: Context) {
        if (feed.feedUrl == null) return
        val url = feed.feedUrl
        CoroutineScope(Dispatchers.IO).launch {
            val fbb = FeedBuilderBase(context) { message, details -> Logd("OnineFeedItem", "Subscribe error: $message \n $details") }
            fbb.buildPodcast(url, "", "") { feed, _ -> fbb.subscribe(feed) }
        }
    }
}