package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.DownloadRequestCreator.create
import ac.mdiq.podcini.net.download.service.Downloader
import ac.mdiq.podcini.net.download.service.HttpDownloader
import ac.mdiq.podcini.net.feed.parser.FeedHandler
import ac.mdiq.podcini.net.utils.NetworkUtils.prepareUrl
import ac.mdiq.podcini.storage.database.Feeds.updateFeed
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.util.Logd
import ac.mdiq.podcini.util.Loge
import ac.mdiq.podcini.util.Logs
import ac.mdiq.podcini.util.error.DownloadErrorLabel.from
import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

open class FeedBuilderBase(val context: Context, val showError: (String?, String)->Unit) {
    protected val TAG = "FeedBuilder"

    var feedSource: String = ""
    var selectedDownloadUrl: String? = null
    private var downloader: Downloader? = null

    fun buildPodcast(url: String, username: String?, password: String?, handleFeed: (Feed, Map<String, String>)->Unit) {
        when (val urlType = htmlOrXml(url)) {
            "HTML" -> {
                val doc = Jsoup.connect(url).get()
                val linkElements = doc.select("link[type=application/rss+xml]")
//                TODO: should show all as options
                for (element in linkElements) {
                    val rssUrl = element.attr("href")
                    Logd(TAG, "RSS URL: $rssUrl")
                    buildPodcast(rssUrl, username, password) {feed, map -> handleFeed(feed, map) }
                }
            }
            "XML" -> {}
            else -> {
                Loge(TAG, "unknown url type $urlType")
                showError("unknown url type $urlType", "")
                return
            }
        }
        selectedDownloadUrl = prepareUrl(url)
        val request = create(Feed(selectedDownloadUrl, null)).withAuthentication(username, password).withInitiatedByUser(true).build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                downloader = HttpDownloader(request)
                downloader?.call()
                val status = downloader?.result
                when {
                    request.destination == null || status == null -> return@launch
                    status.isSuccessful -> {
                        try {
                            val result = doParseFeed(request.destination)
                            if (result != null) withContext(Dispatchers.Main) { handleFeed(result.feed, result.alternateFeedUrls) }
                        } catch (e: Throwable) {
                            Logs(TAG, e, "Feed parser exception:")
                            withContext(Dispatchers.Main) { showError(e.message, "") }
                        }
                    }
                    else -> withContext(Dispatchers.Main) { showError(context.getString(from(status.reason)), status.reasonDetailed) }
                }
            } catch (e: Throwable) {
                Logs(TAG, e)
                withContext(Dispatchers.Main) { showError(e.message, "") }
            }
        }
    }

    /**
     * Try to parse the feed.
     * @return  The FeedHandlerResult if successful.
     * Null if unsuccessful but we started another attempt.
     * @throws Exception If unsuccessful but we do not know a resolution.
     */
    @Throws(Exception::class)
    private fun doParseFeed(destination: String): FeedHandler.FeedHandlerResult? {
        val destinationFile = File(destination)
        return try {
            val feed = Feed(selectedDownloadUrl, null)
            feed.isBuilding = true
            feed.fileUrl = destination
            val result = FeedHandler().parseFeed(feed)
            feed.isBuilding = false
            result
        } catch (e: FeedHandler.UnsupportedFeedtypeException) {
            Logd(TAG, "Unsupported feed type detected")
            if ("html".equals(e.rootElement, ignoreCase = true)) {
                if (selectedDownloadUrl != null) {
//                    val doc = Jsoup.connect(selectedDownloadUrl).get()
//                    val linkElements = doc.select("link[type=application/rss+xml]")
//                    for (element in linkElements) {
//                        val rssUrl = element.attr("href")
//                        Logd(TAG, "RSS URL: $rssUrl")
//                        val rc = destinationFile.delete()
//                        Logd(TAG, "Deleted feed source file. Result: $rc")
//                        startFeedDownload(rssUrl)
//                        return null
//                    }
//                    val dialogShown = showFeedDiscoveryDialog(destinationFile, selectedDownloadUrl!!)
//                    if (dialogShown) null // Should not display an error message
//                    else throw FeedHandler.UnsupportedFeedtypeException(getString(R.string.download_error_unsupported_type_html))
                    throw FeedHandler.UnsupportedFeedtypeException(context.getString(R.string.download_error_unsupported_type_html))
                } else null
            } else throw e
        } catch (e: Exception) {
            Logs(TAG, e)
            throw e
        } finally {
            val rc = destinationFile.delete()
            Logd(TAG, "Deleted feed source file. Result: $rc")
        }
    }

    private fun htmlOrXml(url: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        var type: String? = null
        try { type = connection.contentType } catch (e: IOException) {
            Loge(TAG, "Error connecting to URL. ${e.message}")
            showError(e.message, "")
        } finally { connection.disconnect() }
        if (type == null) return null
        Logd(TAG, "connection type: $type")
        return when {
            type.contains("html", ignoreCase = true) -> "HTML"
            type.contains("xml", ignoreCase = true) -> "XML"
            else -> type
        }
    }

    fun subscribe(feed: Feed) {
        while (feed.isBuilding) runBlocking { delay(200) }
        feed.id = 0L
        for (item in feed.episodes) {
            item.id = 0L
            item.feedId = null
            item.feed = feed
        }
        val fo = updateFeed(context, feed, false)
//        if (fo?.downloadUrl != null || fo?.link != null) {
//            val fLog = SubscriptionLog(fo.id, fo.title?:"", fo.downloadUrl?:"", fo.link?:"", SubscriptionLog.Type.Feed.name)
//            upsertBlk(fLog) {}
//        }
        Logd(TAG, "fo.id: ${fo?.id} feed.id: ${feed.id}")
    }

    /**
     *
     * @return true if a FeedDiscoveryDialog is shown, false otherwise (e.g., due to no feed found).
     */
//    private fun showFeedDiscoveryDialog(feedFile: File, baseUrl: String): Boolean {
//        val fd = FeedDiscoverer()
//        val urlsMap: Map<String, String>
//        try {
//            urlsMap = fd.findLinks(feedFile, baseUrl)
//            if (urlsMap.isEmpty()) return false
//        } catch (e: IOException) {
//            Logs(TAG, e)
//            return false
//        }
//
//        if (isRemoving || isPaused) return false
//        val titles: MutableList<String?> = ArrayList()
//        val urls: List<String> = ArrayList(urlsMap.keys)
//        for (url in urls) {
//            titles.add(urlsMap[url])
//        }
//        if (urls.size == 1) {
//            // Skip dialog and display the item directly
//            feeds = getFeedList()
//            subscribe.startFeedBuilding(urls[0]) {feed, map -> showFeedInformation(feed, map) }
//            return true
//        }
//        val adapter = ArrayAdapter(requireContext(), R.layout.ellipsize_start_listitem, R.id.txtvTitle, titles)
//        val onClickListener = DialogInterface.OnClickListener { dialog: DialogInterface, which: Int ->
//            val selectedUrl = urls[which]
//            dialog.dismiss()
//            feeds = getFeedList()
//            subscribe.startFeedBuilding(selectedUrl) {feed, map -> showFeedInformation(feed, map) }
//        }
//        val ab = MaterialAlertDialogBuilder(requireContext())
//            .setTitle(R.string.feeds_label)
//            .setCancelable(true)
//            .setOnCancelListener { _: DialogInterface? ->/*                finish() */ }
//            .setAdapter(adapter, onClickListener)
//        requireActivity().runOnUiThread {
//            if (dialog != null && dialog!!.isShowing) dialog!!.dismiss()
//            dialog = ab.show()
//        }
//        return true
//    }

    /**
     * Finds RSS/Atom URLs in a HTML document using the auto-discovery techniques described here:
     * http://www.rssboard.org/rss-autodiscovery
     * http://blog.whatwg.org/feed-autodiscovery
     */
//    class FeedDiscoverer {
//        /**
//         * Discovers links to RSS and Atom feeds in the given File which must be a HTML document.
//         * @return A map which contains the feed URLs as keys and titles as values (the feed URL is also used as a title if
//         * a title cannot be found).
//         */
//        @Throws(IOException::class)
//        fun findLinks(inVal: File, baseUrl: String): Map<String, String> {
//            return findLinks(Jsoup.parse(inVal), baseUrl)
//        }
//        /**
//         * Discovers links to RSS and Atom feeds in the given File which must be a HTML document.
//         * @return A map which contains the feed URLs as keys and titles as values (the feed URL is also used as a title if
//         * a title cannot be found).
//         */
//        fun findLinks(inVal: String, baseUrl: String): Map<String, String> {
//            return findLinks(Jsoup.parse(inVal), baseUrl)
//        }
//        private fun findLinks(document: Document, baseUrl: String): Map<String, String> {
//            val res: MutableMap<String, String> = ArrayMap()
//            val links = document.head().getElementsByTag("link")
//            for (link in links) {
//                val rel = link.attr("rel")
//                val href = link.attr("href")
//                if (href.isNotEmpty() && (rel == "alternate" || rel == "feed")) {
//                    val type = link.attr("type")
//                    if (type == MIME_RSS || type == MIME_ATOM) {
//                        val title = link.attr("title")
//                        val processedUrl = processURL(baseUrl, href)
//                        if (processedUrl != null) res[processedUrl] = title.ifEmpty { href }
//                    }
//                }
//            }
//            return res
//        }
//        private fun processURL(baseUrl: String, strUrl: String): String? {
//            val uri = Uri.parse(strUrl)
//            if (uri.isRelative) {
//                val res = Uri.parse(baseUrl).buildUpon().path(strUrl).build()
//                return res?.toString()
//            } else return strUrl
//        }
//        companion object {
//            private const val MIME_RSS = "application/rss+xml"
//            private const val MIME_ATOM = "application/atom+xml"
//        }
//    }

}