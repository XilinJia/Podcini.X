package ac.mdiq.podcini.net.feed

import ac.mdiq.podcini.BuildConfig
import ac.mdiq.podcini.config.ClientConfig
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.service.PodciniHttpClient
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Logs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

interface PodcastSearcher {

    fun urlNeedsLookup(url: String): Boolean = false

    suspend fun search(query: String): List<PodcastSearchResult>

    suspend fun lookupUrl(url: String): String = url

    val name: String?
}

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
}

class PodcastIndexPodcastSearcher : PodcastSearcher {
    override suspend fun search(query: String): List<PodcastSearchResult> {
        fun fromPodcastIndex(json: JSONObject): PodcastSearchResult {
            val title = json.optString("title", "")
            val imageUrl: String? = json.optString("image").takeIf { it.isNotEmpty() }
            val feedUrl: String? = json.optString("url").takeIf { it.isNotEmpty() }
            val author: String? = json.optString("author").takeIf { it.isNotEmpty() }
            var count: Int? = json.optInt("episodeCount", -1)
            if (count != null && count < 0) count = null
            val updateInt: Int = json.optInt("lastUpdateTime", -1)
            var update: String? = null
            if (updateInt > 0) update = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(updateInt.toLong() * 1000)
            return PodcastSearchResult(title, imageUrl, feedUrl, author, count, update, -1, "PodcastIndex")
        }

        val encodedQuery = try {
            withContext(Dispatchers.IO) { URLEncoder.encode(query, "UTF-8") }
        } catch (e: UnsupportedEncodingException) {
            Logs("PodcastIndexPodcastSearcher", e)
            query
        }
        val formattedUrl = String.format(SEARCH_API_URL, encodedQuery)
        val podcasts: MutableList<PodcastSearchResult> = mutableListOf()
        try {
            val client = PodciniHttpClient.getHttpClient()
            val response = client.newCall(buildAuthenticatedRequest(formattedUrl)).execute()
            if (response.isSuccessful) {
                val resultString = response.body.string()
                val result = JSONObject(resultString)
                val j = result.getJSONArray("feeds")

                for (i in 0 until j.length()) {
                    val podcastJson = j.getJSONObject(i)
                    val podcast = fromPodcastIndex(podcastJson)
                    if (podcast.feedUrl != null) podcasts.add(podcast)
                }
            } else throw IOException(response.toString())
        } catch (e: IOException) { throw e
        } catch (e: JSONException) { throw e }
        return podcasts
    }

    override val name: String
        get() = "Podcast Index"

    private fun buildAuthenticatedRequest(url: String): Request {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        val now = Date()
        calendar.time = now
        val secondsSinceEpoch = calendar.timeInMillis / 1000L
        val apiHeaderTime = secondsSinceEpoch.toString()
        val data4Hash = BuildConfig.PODCASTINDEX_API_KEY + BuildConfig.PODCASTINDEX_API_SECRET + apiHeaderTime
        val hashString = sha1(data4Hash) ?:""

        val httpReq: Request.Builder = Request.Builder()
            .addHeader("X-Auth-Date", apiHeaderTime)
            .addHeader("X-Auth-Key", BuildConfig.PODCASTINDEX_API_KEY)
            .addHeader("Authorization", hashString)
            .addHeader("User-Agent", ClientConfig.USER_AGENT ?:"")
            .url(url)
        return httpReq.build()
    }

    companion object {
        private const val SEARCH_API_URL = "https://api.podcastindex.org/api/1.0/search/byterm?q=%s"

        private fun sha1(clearString: String): String? {
            try {
                val messageDigest = MessageDigest.getInstance("SHA-1")
                messageDigest.update(clearString.toByteArray(charset("UTF-8")))
                return toHex(messageDigest.digest())
            } catch (ignored: Exception) {
                Logd("PodcastIndexPodcastSearcher", ignored.message ?: "sha1 error")
                return null
            }
        }

        private fun toHex(bytes: ByteArray): String {
            val buffer = StringBuilder()
            for (b in bytes) buffer.append(String.format(Locale.getDefault(), "%02x", b))
            return buffer.toString()
        }
    }
}

class ItunesPodcastSearcher : PodcastSearcher {
    private val TAG = "ItunesPodcastSearcher"
    override suspend fun search(query: String): List<PodcastSearchResult> {
        /**
         * Constructs a Podcast instance from a iTunes search result
         * @param json object holding the podcast information
         * @throws JSONException
         */
        fun fromItunes(json: JSONObject): PodcastSearchResult {
            val title = json.optString("collectionName", "")
            val imageUrl: String? = json.optString("artworkUrl100").takeIf { it.isNotEmpty() }
            val feedUrl: String? = json.optString("feedUrl").takeIf { it.isNotEmpty() }
            val author: String? = json.optString("artistName").takeIf { it.isNotEmpty() }
            return PodcastSearchResult(title, imageUrl, feedUrl, author, null, null, -1, "Itunes")
        }

        val encodedQuery = try {
            withContext(Dispatchers.IO) { URLEncoder.encode(query, "UTF-8") }
        } catch (e: UnsupportedEncodingException) {
            Logs(TAG, e)
            query
        }
        val formattedUrl = String.format(ITUNES_API_URL, encodedQuery)

        val client = PodciniHttpClient.getHttpClient()
        val httpReq: Request.Builder = Request.Builder().url(formattedUrl)
        val podcasts: MutableList<PodcastSearchResult> = mutableListOf()
        try {
            val response = client.newCall(httpReq.build()).execute()

            if (response.isSuccessful) {
                val resultString = response.body.string()
                val result = JSONObject(resultString)
                val j = result.getJSONArray("results")

                for (i in 0 until j.length()) {
                    val podcastJson = j.getJSONObject(i)
                    val podcast = fromItunes(podcastJson)
                    if (podcast.feedUrl != null) podcasts.add(podcast)
                }
            } else throw IOException(response.toString())
        } catch (e: IOException) { throw e
        } catch (e: JSONException) { throw e }
        return podcasts
    }

    override suspend fun lookupUrl(url: String): String {
        val pattern = Pattern.compile(PATTERN_BY_ID)
        val matcher = pattern.matcher(url)
        val lookupUrl = if (matcher.find()) "https://itunes.apple.com/lookup?id=" + matcher.group(1) else url
        val client = PodciniHttpClient.getHttpClient()
        val httpReq = Request.Builder().url(lookupUrl).build()
        val response = client.newCall(httpReq).execute()
        if (!response.isSuccessful) throw IOException(response.toString())
        val resultString = response.body.string()
        if (resultString.trim().startsWith('<')) return url     // XML already

        Logd(TAG, "lookupUrl resultString: $resultString")
        val result = JSONObject(resultString)
        val results = result.getJSONArray("results").getJSONObject(0)
        val feedUrlName = "feedUrl"
        if (!results.has(feedUrlName)) {
            val artistName = results.getString("artistName")
            val trackName = results.getString("trackName")
            throw FeedUrlNotFoundException(artistName, trackName)
        }
        return results.getString(feedUrlName)
    }

    override fun urlNeedsLookup(url: String): Boolean {
        Logd(TAG, "urlNeedsLookup url: $url")
        // TODO: may also need to check podcasts.apple.com?
        return url.contains("//itunes.apple.com") || url.matches(PATTERN_BY_ID.toRegex())
    }

    override val name: String
        get() = "Apple"

    companion object {
        private const val ITUNES_API_URL = "https://itunes.apple.com/search?media=podcast&term=%s"
        private const val PATTERN_BY_ID = ".*/podcasts\\.apple\\.com/.*/podcast/.*/id(\\d+).*"
    }
}

class CombinedSearcher : PodcastSearcher {
    override suspend fun search(query: String): List<PodcastSearchResult> {
        val searchProviders = PodcastSearcherRegistry.searchProviders
        val searchResults = MutableList<List<PodcastSearchResult>>(searchProviders.size) { listOf() }

        // Using a supervisor scope to ensure that one failing child does not cancel others
        supervisorScope {
            val searchJobs = searchProviders.mapIndexed { index, searchProviderInfo ->
                val searcher = searchProviderInfo.searcher
                if (searchProviderInfo.weight > 0.00001f && searcher.javaClass != CombinedSearcher::class.java) {
                    async(Dispatchers.IO) {
                        try {
                            searchResults[index] = searcher.search(query)
                        } catch (e: Throwable) {
                            Logs(TAG, e)
                        }
                    }
                } else null
            }.filterNotNull()
            searchJobs.awaitAll()
        }
        return weightSearchResults(searchResults)
    }

    private fun weightSearchResults(singleResults: List<List<PodcastSearchResult>>): List<PodcastSearchResult> {
        val resultRanking = mutableMapOf<String?, Float>()
        val urlToResult = mutableMapOf<String?, PodcastSearchResult>()
        for (i in singleResults.indices) {
            val providerPriority = PodcastSearcherRegistry.searchProviders[i].weight
            val providerResults = singleResults[i]
            for (position in providerResults.indices) {
                val result = providerResults[position]
                urlToResult[result.feedUrl] = result
                var ranking = 0f
                if (resultRanking.containsKey(result.feedUrl)) ranking = resultRanking[result.feedUrl]!!
                ranking += 1f / (position + 1f)
                resultRanking[result.feedUrl] = ranking * providerPriority
            }
        }
        //        val sortedResults = mutableListOf<MutableMap.MutableEntry<String?, Float>>(resultRanking.entries)
        val sortedResults = resultRanking.entries.toMutableList()
        sortedResults.sortWith { o1: Map.Entry<String?, Float>, o2: Map.Entry<String?, Float> -> o2.value.toDouble().compareTo(o1.value.toDouble()) }

        val results: MutableList<PodcastSearchResult> = mutableListOf()
        for ((key) in sortedResults) {
            val v = urlToResult[key] ?: continue
            results.add(v)
        }
        return results
    }

    override suspend fun lookupUrl(url: String): String = PodcastSearcherRegistry.lookupUrl1(url)

    override fun urlNeedsLookup(url: String): Boolean = PodcastSearcherRegistry.urlNeedsLookup(url)

    override val name: String
        get() {
            val names = mutableListOf<String?>()
            for (i in PodcastSearcherRegistry.searchProviders.indices) {
                val searchProviderInfo = PodcastSearcherRegistry.searchProviders[i]
                val searcher = searchProviderInfo.searcher
                if (searchProviderInfo.weight > 0.00001f && searcher.javaClass != CombinedSearcher::class.java) names.add(searcher.name)
            }
            return names.joinToString()
        }

    companion object {
        private val TAG: String = CombinedSearcher::class.simpleName ?: "Anonymous"
    }
}

object PodcastSearcherRegistry {
    @get:Synchronized
    var searchProviders: MutableList<SearcherInfo> = mutableListOf()
        get() {
            if (field.isEmpty()) {
                field = mutableListOf()
                field.add(SearcherInfo(CombinedSearcher(), 1.0f))
                if (gearbox.hasSearcher()) field.add(SearcherInfo(gearbox.getSearcher(), 1.0f))
                field.add(SearcherInfo(ItunesPodcastSearcher(), 1.0f))
                field.add(SearcherInfo(PodcastIndexPodcastSearcher(), 1.0f))
            }
            return field
        }
        private set

    suspend fun lookupUrl1(url: String): String {
        for (searchProviderInfo in searchProviders) {
            if (searchProviderInfo.searcher.javaClass != CombinedSearcher::class.java && searchProviderInfo.searcher.urlNeedsLookup(url))
                return searchProviderInfo.searcher.lookupUrl(url)
        }
        return url
    }

    fun urlNeedsLookup(url: String): Boolean {
        for (searchProviderInfo in searchProviders) {
            if (searchProviderInfo.searcher.javaClass != CombinedSearcher::class.java && searchProviderInfo.searcher.urlNeedsLookup(url)) return true
        }
        return false
    }

    class SearcherInfo(val searcher: PodcastSearcher, val weight: Float)
}