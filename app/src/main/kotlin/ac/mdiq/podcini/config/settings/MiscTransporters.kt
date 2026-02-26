package ac.mdiq.podcini.config.settings

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.net.sync.SyncService.Companion.isValidGuid
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.model.EpisodeAction.Companion.readFromJsonObject
import ac.mdiq.podcini.net.sync.model.SyncServiceException
import ac.mdiq.podcini.storage.database.episodeByGuidOrUrl
import ac.mdiq.podcini.storage.database.getEpisodes

import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.EpisodeFilter
import ac.mdiq.podcini.storage.specs.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.specs.EpisodeState
import ac.mdiq.podcini.storage.specs.Rating
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import org.apache.commons.io.IOUtils
import org.json.JSONArray
import java.io.IOException
import java.io.Reader
import java.io.Writer

import java.util.TreeMap
import kotlin.collections.get

class EpisodeProgressReader {
    val TAG = "EpisodeProgressReader"

    fun readDocument(reader: Reader) {
        val jsonString = reader.readText()
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val jsonAction = jsonArray.getJSONObject(i)
            Logd(TAG, "Loaded EpisodeActions message: $i $jsonAction")
            val action = readFromJsonObject(jsonAction) ?: continue
            Logd(TAG, "processing action: $action")
            val result = processEpisodeAction(action) ?: continue
//                upsertBlk(result.second) {}
        }
    }
    private fun processEpisodeAction(action: EpisodeAction): Pair<Long, Episode>? {
        val guid = if (isValidGuid(action.guid)) action.guid else null
        var feedItem = episodeByGuidOrUrl(guid, action.episode, false) ?: return null
        var idRemove = 0L
        feedItem = upsertBlk(feedItem) {
            it.startPosition = action.started * 1000
            it.position = action.position * 1000
            it.playedDuration = action.playedDuration * 1000
            it.lastPlayedTime = (action.timestamp!!)
            it.setRating(if (action.isFavorite) Rating.SUPER else Rating.UNRATED)
            it.setPlayState(EpisodeState.fromCode(action.playState))
            if (it.hasAlmostEnded()) {
                Logd(TAG, "Marking as played: $action")
                it.setPlayState(EpisodeState.PLAYED)
//                it.setPosition(0)
                idRemove = it.id
            } else Logd(TAG, "Setting position: $action")
        }
        return Pair(idRemove, feedItem)
    }
}

class EpisodesProgressWriter : ExportWriter {
    val TAG = "EpisodesProgressWriter"

    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    override fun writeDocument(feeds: List<Feed>, writer: Writer) {
        Logd(TAG, "Starting to write document")
        val queuedEpisodeActions: MutableList<EpisodeAction> = mutableListOf()
        val pausedItems = getEpisodes(EpisodeFilter(EpisodeFilter.States.paused.name), EpisodeSortOrder.DATE_DESC)
        val readItems = getEpisodes(EpisodeFilter(EpisodeFilter.States.PLAYED.name), EpisodeSortOrder.DATE_DESC)
        val favoriteItems = getEpisodes(EpisodeFilter(EpisodeFilter.States.superb.name), EpisodeSortOrder.DATE_DESC)
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
                writer.write(list.toString())
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
    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    override fun writeDocument(feeds: List<Feed>, writer: Writer) {
        val context = getAppContext()
        Logd(TAG, "Starting to write document")
        val templateStream = context.assets.open("html-export-template.html")
        var template = IOUtils.toString(templateStream, UTF_8)
        template = template.replace("\\{TITLE\\}".toRegex(), "Favorites")
        val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val favTemplateStream = context.assets.open(FAVORITE_TEMPLATE)
        val favTemplate = IOUtils.toString(favTemplateStream, UTF_8)
        val feedTemplateStream = context.assets.open(FEED_TEMPLATE)
        val feedTemplate = IOUtils.toString(feedTemplateStream, UTF_8)
        val allFavorites = getEpisodes(EpisodeFilter(EpisodeFilter.States.superb.name), EpisodeSortOrder.DATE_DESC)
        val favoritesByFeed = buildFeedMap(allFavorites)
        writer.append(templateParts[0])
        for (feedId in favoritesByFeed.keys) {
            val favorites: List<Episode> = favoritesByFeed[feedId]!!
            if (favorites[0].feed == null) continue
            writer.append("<li><div>\n")
            writeFeed(writer, favorites[0].feed!!, feedTemplate)
            writer.append("<ul>\n")
            for (item in favorites) writeFavoriteItem(writer, item, favTemplate)
            writer.append("</ul></div></li>\n")
        }
        writer.append(templateParts[1])
        Logd(TAG, "Finished writing document")
    }
    /**
     * Group favorite episodes by feed, sorting them by publishing date in descending order.
     * @param favoritesList `List` of all favorite episodes.
     * @return A `Map` favorite episodes, keyed by feed ID.
     */
    private fun buildFeedMap(favoritesList: List<Episode>): Map<Long, MutableList<Episode>> {
        val feedMap: MutableMap<Long, MutableList<Episode>> = TreeMap()
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
    @Throws(IOException::class)
    private fun writeFeed(writer: Writer, feed: Feed, feedTemplate: String) {
        val feedInfo = feedTemplate
            .replace("{FEED_IMG}", feed.imageUrl?:"")
            .replace("{FEED_TITLE}", feed.title?:" No title")
            .replace("{FEED_LINK}", feed.link?: "")
            .replace("{FEED_WEBSITE}", feed.downloadUrl?:"")
        writer.append(feedInfo)
    }
    @Throws(IOException::class)
    private fun writeFavoriteItem(writer: Writer, item: Episode, favoriteTemplate: String) {
        var favItem = favoriteTemplate.replace("{FAV_TITLE}", item.title!!.trim { it <= ' ' })
        favItem = if (item.link != null) favItem.replace("{FAV_WEBSITE}", item.link!!)
        else favItem.replace("{FAV_WEBSITE}", "")
        favItem = if (item.downloadUrl != null) favItem.replace("{FAV_MEDIA}", item.downloadUrl!!) else favItem.replace("{FAV_MEDIA}", "")
        writer.append(favItem)
    }
    override fun fileExtension(): String {
        return "html"
    }
}
class HtmlWriter : ExportWriter {
    val TAG = "HtmlWriter"

    @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
    override fun writeDocument(feeds: List<Feed>, writer: Writer) {
        Logd(TAG, "Starting to write document")
        val templateStream = getAppContext().assets.open("html-export-template.html")
        var template = IOUtils.toString(templateStream, "UTF-8")
        template = template.replace("\\{TITLE\\}".toRegex(), "Subscriptions")
        val templateParts = template.split("\\{FEEDS\\}".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        writer.append(templateParts[0])
        for (feed in feeds) {
            writer.append("<li><div><img src=\"")
            writer.append(feed.imageUrl)
            writer.append("\" /><p>")
            writer.append(feed.title)
            writer.append(" <span><a href=\"")
            writer.append(feed.link)
            writer.append("\">Website</a> • <a href=\"")
            writer.append(feed.downloadUrl)
            writer.append("\">Feed</a></span></p></div></li>\n")
        }
        writer.append(templateParts[1])
        Logd(TAG, "Finished writing document")
    }
    override fun fileExtension(): String {
        return "html"
    }
}
