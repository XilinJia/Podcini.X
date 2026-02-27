package ac.mdiq.podcini.gears

import ac.mdiq.podcini.net.feed.CombinedSearcher
import ac.mdiq.podcini.net.feed.FeedBuilderBase
import ac.mdiq.podcini.net.feed.FeedUpdaterBase
import ac.mdiq.podcini.net.feed.PodcastSearchResult
import ac.mdiq.podcini.net.feed.PodcastSearcher
import ac.mdiq.podcini.net.utils.NetworkUtils.getFinalRedirectedUrl
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.model.ShareLog
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.ShownotesCleaner
import androidx.compose.runtime.Composable
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.source.MediaSource
import java.net.URL

open class GearBoxBase {

    open fun init() {}

    open fun supportAudioQualities(): Boolean = false

    @Composable
    open fun ConfirmAddEpisode(sharedUrls: List<String>, onDismissRequest: () -> Unit) {}

    open fun isGearFeed(url: URL): Boolean = false

    open fun isGearUrl(url: URL): Boolean = false

    open fun includeExtraSort(): List<EpisodeSortOrder> = listOf()

    open fun cleanGearData() {}

    open fun loadChapters(episode: Episode) {
        episode.loadChapters(false)
    }

    open fun canCheckMediaSize(episode: Episode): Boolean = true

    fun buildWebviewData(episode: Episode): String? {
        val shownotesCleaner = ShownotesCleaner()
        val webDataPair = buildWebviewPair(episode, shownotesCleaner)
        return webDataPair?.second ?: buildCleanedNotes(episode, shownotesCleaner).second
    }
    open fun buildWebviewPair(episode_: Episode, shownotesCleaner: ShownotesCleaner): Pair<Episode, String>? = null

    open fun buildCleanedNotes(curItem: Episode, shownotesCleaner: ShownotesCleaner?): Pair<Episode, String?> {
        return Pair(curItem, shownotesCleaner?.processShownotes(curItem.description ?: "", curItem.duration))
    }

    @Composable
    open fun PlayerDetailedGearPanel(curItem: Episode, reset: Boolean, cb: (Boolean)->Unit) {}

    open fun gearProviderRes(): Int = 0

    open fun gearSearchProvider(): Class<out PodcastSearcher?> = CombinedSearcher::class.java

    open fun isFeedAutoDownloadable(urlString: String): Boolean = true

    open fun canHandleSharedMedia(urlString: String): Boolean = false

    open fun handleSharedMedia(log: ShareLog?, mediaCB: ()->Unit) {}

    open fun feedFilter(properties: HashSet<String>, statements: MutableList<String>) {}

    open fun formMediaSource(metadata: MediaMetadata, media: Episode): MediaSource? = null

    open fun formCastMediaSource(media: Episode): Boolean = false

    open fun hasSearcher(): Boolean = false

    open fun getSearcher(): PodcastSearcher = CombinedSearcher()

    open fun feedUpdater(feeds: List<Feed>, fullUpdate: Boolean = false, doItAnyway: Boolean = false) : FeedUpdaterBase = FeedUpdaterBase(feeds, fullUpdate, doItAnyway)

    open fun formFeedBuilder(url: String, feedSource: String, showError: (String?, String) -> Unit): FeedBuilderBase {
        return FeedBuilderBase(showError)
    }

    open suspend fun buildFeed(url: String, username: String, password: String, fbb: FeedBuilderBase, handleFeed: (Feed, Map<String, String>)->Unit, showDialog: ()->Unit) {
        fbb.buildPodcast(getFinalRedirectedUrl(url), username, password) { feed_, map -> handleFeed(feed_, map) }
    }

    @Composable
    open fun ShowTabsDialog(fbb: FeedBuilderBase, onDismissRequest: () -> Unit, handleFeed: (Feed, Map<String, String>)->Unit) {}

    open fun isSameFeed(feed: Feed, url: String?, title: String?, author: String?): Boolean = feed.downloadUrl == url

    open fun subscribeFeed(feed: PodcastSearchResult) {
        if (feed.feedUrl == null) return
        runOnIOScope {
            val fbb = FeedBuilderBase { message, details -> Loge("OnineFeedItem", "Subscribe error: $message \n $details") }
            fbb.buildPodcast(feed.feedUrl, "", "") { feed, _ -> runOnIOScope { fbb.subscribe(feed) } }
        }
    }
}