package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.net.download.DownloadRequest.Companion.requestFor
import ac.mdiq.podcini.net.download.Downloader
import ac.mdiq.podcini.net.download.Downloader.Companion.downloaderFor
import ac.mdiq.podcini.net.download.PodciniHttpClient.getKtorClient
import ac.mdiq.podcini.net.utils.NetworkUtils.prepareUrl
import ac.mdiq.podcini.storage.database.addNewFeed
import ac.mdiq.podcini.storage.database.feedByIdentityOrID
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import ac.mdiq.podcini.utils.Logt
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.network.parseGetRequest
import io.github.xilinjia.krdb.ext.toRealmList
import io.ktor.client.request.head
import io.ktor.client.statement.HttpResponse
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

open class FeedBuilderBase(val showError: (String?, String)->Unit) {
    private val TAG = "FeedBuilderBase"

    var feedSource: String = ""
    var selectedDownloadUrl: String? = null
    private var downloader: Downloader? = null

    suspend fun buildPodcast(url: String, username: String?, password: String?, handleFeed: (Feed, Map<String, String>)->Unit) {
        Logd(TAG, "buildPodcast: $url")
        val urlType = try {
            val response: HttpResponse = getKtorClient().head(url)
            val type = response.contentType()?.toString()
            Logd(TAG, "htmlOrXml connection type: $type")
            when {
                type == null -> null
                type.contains("html", ignoreCase = true) -> "HTML"
                type.contains("xml", ignoreCase = true) -> "XML"
                else -> type
            }
        } catch (e: Exception) {
            Loge(TAG, "htmlOrXml Error connecting to URL. ${e.message}")
            null
        }
        when (urlType) {
            "HTML" -> {
                try {
                    val doc = Ksoup.parseGetRequest(url)
                    val linkElements = doc.select("link[type=application/rss+xml]")
                    //                TODO: should show all as options
                    for (element in linkElements) {
                        val rssUrl = element.attr("href")
                        Logd(TAG, "buildPodcast RSS URL: $rssUrl")
                        buildPodcast(rssUrl, username, password) { feed, map -> handleFeed(feed, map) }
                    }
                    return
                } catch (e: Throwable) { Loge(TAG, "buildPodcast error: ${e.message}")}
            }
            "XML" -> {}
            else -> {
                Loge(TAG, "buildPodcast unknown url type $urlType")
                showError("buildPodcast unknown url type $urlType", "")
                return
            }
        }
        selectedDownloadUrl = prepareUrl(url)
        CoroutineScope(Dispatchers.IO).launch {
            val request = requestFor(Feed(selectedDownloadUrl, null)).withAuthentication(username, password).withInitiatedByUser(true).build()
            try {
                downloader = downloaderFor(request)
                downloader?.download { source ->
                    //                        Logd(TAG, "buildPodcast destination: $destination")
                    val feed = Feed(selectedDownloadUrl, null)
                    feed.isBuilding = true
                    val result = FeedHandler.parseFeed(source, feed)
                    feed.isBuilding = false
                    if (result != null) withContext(Dispatchers.Main) { handleFeed(result.feed, result.alternateFeedUrls) }
                }
            } catch (e: Throwable) {
                Logs(TAG, e)
                withContext(Dispatchers.Main) { showError("buildPodcast ${e.message}", "") }
            }
        }
    }

    suspend fun subscribe(feed: Feed) {
        while (feed.isBuilding) delay(200)
        feed.id = 0L
        if (feed.limitEpisodesCount > 0) {
            val sz = feed.episodes.size
            if (sz > 0) feed.episodes = feed.episodes.subList(0, min(sz, feed.limitEpisodesCount)).toRealmList()
        }
        for (item in feed.episodes) {
            item.id = 0L
            item.feedId = null
            item.origFeedlink = null
            item.origFeeddownloadUrl = null
            item.origFeedTitle = null
        }
        if (feedByIdentityOrID(feed) == null) addNewFeed(feed)
        else Logt(TAG, "feed already exists: ${feed.title}")
    }
}