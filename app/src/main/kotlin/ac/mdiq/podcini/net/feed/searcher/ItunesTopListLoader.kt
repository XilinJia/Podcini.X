package ac.mdiq.podcini.net.feed.searcher

import ac.mdiq.podcini.R
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.getHttpClient
import ac.mdiq.podcini.utils.Logd
import android.content.Context
import okhttp3.CacheControl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class ItunesTopListLoader(private val context: Context) {

    @Throws(JSONException::class, IOException::class)
    fun loadToplist(country: String, genre: Int, limit: Int): List<PodcastSearchResult> {
        val client = getHttpClient()
        var loadCountry = country
        if (COUNTRY_CODE_UNSET == country) loadCountry = Locale.getDefault().country

        val url0 = "https://itunes.apple.com/%s/rss/toppodcasts/limit=$NUM_LOADED/json"
        val url = "https://itunes.apple.com/%s/rss/toppodcasts/limit=$NUM_LOADED/genre=%d/json"
        val reqStr = if (genre > 0) String.format(url, loadCountry, genre) else String.format(url0, loadCountry)
        Logd(TAG, "getTopListFeed reqStr: $reqStr")
        val httpReq: Request.Builder = Request.Builder().cacheControl(CacheControl.Builder().maxStale(1, TimeUnit.DAYS).build()).url(reqStr)
        val feedString: String
        client.newCall(httpReq.build()).execute().use { response ->
            if (response.isSuccessful) feedString = response.body.string()
            else {
                if (response.code == 400) throw IOException("iTunes does not have data for the selected country.")
                throw IOException(context.getString(R.string.error_msg_prefix) + response)
            }
        }
        return parseFeed(feedString).take(limit)
    }

    @Throws(JSONException::class)
    private fun parseFeed(jsonString: String): List<PodcastSearchResult> {
        /**
         * Constructs a Podcast instance from iTunes toplist entry
         * @param json object holding the podcast information
         * @throws JSONException
         */
        @Throws(JSONException::class)
        fun fromItunesToplist(json: JSONObject): PodcastSearchResult {
            val title = json.getJSONObject("title").getString("label")
            var imageUrl: String? = null
            val images = json.getJSONArray("im:image")
            var i = 0
            while (imageUrl == null && i < images.length()) {
                val image = images.getJSONObject(i)
                val height = image.getJSONObject("attributes").getString("height")
                if (height.toInt() >= 100) imageUrl = image.getString("label")
                i++
            }
            val feedUrl = "https://itunes.apple.com/lookup?id=" + json.getJSONObject("id").getJSONObject("attributes").getString("im:id")

            var author: String? = null
            try { author = json.getJSONObject("im:artist").getString("label")
            } catch (e: Exception) {/* Some feeds have empty artist */ }
            return PodcastSearchResult(title, imageUrl, feedUrl, author, null, null, -1, "Toplist")
        }

        val result = JSONObject(jsonString)
        val feed: JSONObject
        val entries: JSONArray
        try {
            feed = result.getJSONObject("feed")
            entries = feed.getJSONArray("entry")
        } catch (_: JSONException) { return mutableListOf() }
        val results: MutableList<PodcastSearchResult> = mutableListOf()
        for (i in 0 until entries.length()) {
            val json = entries.getJSONObject(i)
            results.add(fromItunesToplist(json))
        }
        return results
    }

    companion object {
        private val TAG: String = ItunesTopListLoader::class.simpleName ?: "Anonymous"
        const val PREF_KEY_COUNTRY_CODE: String = "country_code"
        const val PREF_KEY_NEEDS_CONFIRM: String = "needs_confirm"
        const val PREFS: String = "CountryRegionPrefs"
        const val COUNTRY_CODE_UNSET: String = "99"
        private const val NUM_LOADED = 100
    }
}
