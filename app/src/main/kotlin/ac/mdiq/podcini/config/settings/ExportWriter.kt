package ac.mdiq.podcini.config.settings

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.model.SyncServiceException
import ac.mdiq.podcini.storage.database.getEpisodes
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.storage.utils.UnifiedFile
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.io.readString
import okio.buffer
import org.json.JSONArray
import kotlin.use

interface ExportWriter {
    suspend fun writeDocument(feeds: List<Feed>, writer: UnifiedFile) {}

    fun fileExtension(): String?
}

class EpisodesProgressWriter : ExportWriter {
    val TAG = "EpisodesProgressWriter"

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override suspend fun writeDocument(feeds: List<Feed>, writer: UnifiedFile) {
        Logd(TAG, "Starting to write document")
        val queuedEpisodeActions: MutableList<EpisodeAction> = mutableListOf()
        val pausedItems = getEpisodes(EpisodeFilter(EpisodeFilter.States.paused.name), EpisodeSortOrder.DATE_DESC, copy = false)
        val readItems = getEpisodes(EpisodeFilter(EpisodeFilter.States.PLAYED.name), EpisodeSortOrder.DATE_DESC, copy = false)
        val favoriteItems = getEpisodes(EpisodeFilter(EpisodeFilter.States.superb.name), EpisodeSortOrder.DATE_DESC, copy = false)
        val comItems = mutableSetOf<Episode>()
        comItems.addAll(pausedItems)
        comItems.addAll(readItems)
        comItems.addAll(favoriteItems)
        Logd(TAG, "Save state for all " + comItems.size + " played episodes")
        for (item in comItems) {
            val played = EpisodeAction.Builder(item, EpisodeAction.PLAY)
                .timestamp(item.lastPlayedTime)
                .started(item.startPosition / 1000)
                .position(item.position / 1000)
                .playedDuration(item.playedDuration / 1000)
                .total(item.duration / 1000)
                .isFavorite(item.rating >= Rating.GOOD.code)
                .playState(item.playState)
                .build()
            queuedEpisodeActions.add(played)
        }
        if (queuedEpisodeActions.isNotEmpty()) {
            try {
                Logd(TAG, "Saving ${queuedEpisodeActions.size} actions: ${queuedEpisodeActions.joinToString(", ")}")
                val list = JSONArray()
                for (episodeAction in queuedEpisodeActions) {
                    val obj = episodeAction.writeToJsonObject()
                    if (obj != null) {
                        Logd(TAG, "saving EpisodeAction: $obj")
                        list.put(obj)
                    }
                }
                writer.writeString(list.toString())
            } catch (e: Exception) {
                Logs(TAG, e)
                throw SyncServiceException(e)
            }
        }
        Logd(TAG, "Finished writing document")
    }
    override fun fileExtension(): String {
        return "json"
    }
}
class FavoritesWriter : ExportWriter {
    val TAG = "FavoritesWriter"

    private val FAVORITE_TEMPLATE = "html-export-favorites-item-template.html"
    private val FEED_TEMPLATE = "html-export-feed-template.html"
    private val UTF_8 = "UTF-8"
    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override suspend fun writeDocument(feeds: List<Feed>, writer: UnifiedFile) {
        val context = getAppContext()
        Logd(TAG, "Starting to write document")
        val template = context.assets.open("html-export-template.html").asSource().buffered().use { it.readString() }.replace("\\{TITLE\\}".toRegex(), "Favorites")
        val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val favTemplate = context.assets.open(FAVORITE_TEMPLATE).asSource().buffered().use { it.readString() }
        val feedTemplate = context.assets.open(FEED_TEMPLATE).asSource().buffered().use { it.readString() }
        val allFavorites = getEpisodes(EpisodeFilter(EpisodeFilter.States.superb.name), EpisodeSortOrder.DATE_DESC, copy = false)
        val favoritesByFeed = buildFeedMap(allFavorites)
        writer.sink(append = false).buffer().use { sink ->
            sink.writeString(templateParts[0], Charsets.UTF_8)
            for (feedId in favoritesByFeed.keys) {
                val favorites: List<Episode> = favoritesByFeed[feedId]!!
                if (favorites[0].feed == null) continue
                sink.writeString("<li><div>\n", Charsets.UTF_8)
                val feed = favorites[0].feed!!
                val feedInfo = feedTemplate
                    .replace("{FEED_IMG}", feed.imageUrl?:"")
                    .replace("{FEED_TITLE}", feed.title?:" No title")
                    .replace("{FEED_LINK}", feed.link?: "")
                    .replace("{FEED_WEBSITE}", feed.downloadUrl?:"")
                sink.writeString(feedInfo, Charsets.UTF_8)
                sink.writeString("<ul>\n", Charsets.UTF_8)
                for (item in favorites) {
                    var favItem = favTemplate.replace("{FAV_TITLE}", item.title!!.trim { it <= ' ' })
                    favItem = if (item.link != null) favItem.replace("{FAV_WEBSITE}", item.link!!)
                    else favItem.replace("{FAV_WEBSITE}", "")
                    favItem = if (item.downloadUrl != null) favItem.replace("{FAV_MEDIA}", item.downloadUrl!!) else favItem.replace("{FAV_MEDIA}", "")
                    sink.writeString(favItem, Charsets.UTF_8)
                }
                sink.writeString("</ul></div></li>\n", Charsets.UTF_8)
            }
            sink.writeString(templateParts[1], Charsets.UTF_8)
        }
        Logd(TAG, "Finished writing document")
    }
    /**
     * Group favorite episodes by feed, sorting them by publishing date in descending order.
     * @param favoritesList `List` of all favorite episodes.
     * @return A `Map` favorite episodes, keyed by feed ID.
     */
    private fun buildFeedMap(favoritesList: List<Episode>): Map<Long, MutableList<Episode>> {
        val feedMap: MutableMap<Long, MutableList<Episode>> = mutableMapOf()
        for (item in favoritesList) {
            var feedEpisodes = feedMap[item.feedId]
            if (feedEpisodes == null) {
                feedEpisodes = mutableListOf()
                if (item.feedId != null) feedMap[item.feedId!!] = feedEpisodes
            }
            feedEpisodes.add(item)
        }
        return feedMap
    }
    override fun fileExtension(): String {
        return "html"
    }
}
class HtmlWriter : ExportWriter {
    val TAG = "HtmlWriter"

    @Throws(IllegalArgumentException::class, IllegalStateException::class)
    override suspend fun writeDocument(feeds: List<Feed>, writer: UnifiedFile) {
        Logd(TAG, "Starting to write document")
        val template =  getAppContext().assets.open("html-export-template.html").asSource().buffered().use { it.readString() }.replace("\\{TITLE\\}".toRegex(), "Subscriptions")
        val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        writer.sink(append = false).buffer().use { sink ->
            sink.writeString(templateParts[0], Charsets.UTF_8)
            for (feed in feeds) {
                sink.writeString("<li><div><img src=\"", Charsets.UTF_8)
                sink.writeString(feed.imageUrl?:"", Charsets.UTF_8)
                sink.writeString("\" /><p>", Charsets.UTF_8)
                sink.writeString(feed.title?:"", Charsets.UTF_8)
                sink.writeString(" <span><a href=\"", Charsets.UTF_8)
                sink.writeString(feed.link?:"", Charsets.UTF_8)
                sink.writeString("\">Website</a> • <a href=\"", Charsets.UTF_8)
                sink.writeString(feed.downloadUrl?:"", Charsets.UTF_8)
                sink.writeString("\">Feed</a></span></p></div></li>\n", Charsets.UTF_8)
            }
            sink.writeString(templateParts[1], Charsets.UTF_8)
        }
        Logd(TAG, "Finished writing document")
    }
    override fun fileExtension(): String {
        return "html"
    }
}
