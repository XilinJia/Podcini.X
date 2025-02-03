package ac.mdiq.podcini.net.utils

import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.preferences.AppPreferences.putPref
import ac.mdiq.podcini.preferences.screens.MobileUpdateOptions
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.util.regex.Pattern

@SuppressLint("StaticFieldLeak")
object NetworkUtils {
    private const val TAG = "NetworkUtils"

    private const val REGEX_PATTERN_IP_ADDRESS = "([0-9]{1,3}[\\.]){3}[0-9]{1,3}"

    private lateinit var context: Context

    var isAllowMobileStreaming: Boolean
        get() = isAllowMobileFor(MobileUpdateOptions.streaming.name)
        set(allow) {
            setAllowMobileFor(MobileUpdateOptions.streaming.name, allow)
        }

    var isAllowMobileAutoDownload: Boolean
        get() = isAllowMobileFor(MobileUpdateOptions.auto_download.name)
        set(allow) {
            setAllowMobileFor(MobileUpdateOptions.auto_download.name, allow)
        }

    val isAutoDownloadAllowed: Boolean
        get() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val networkCapabilities = cm.getNetworkCapabilities(network) ?: return false
            return when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) true
                    else isAllowMobileAutoDownload
                }
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> {
                    isAllowMobileAutoDownload || networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                }
            }
        }

    var isAllowMobileFeedRefresh: Boolean
        get() = isAllowMobileFor(MobileUpdateOptions.feed_refresh.name)
        set(allow) {
            setAllowMobileFor(MobileUpdateOptions.feed_refresh.name, allow)
        }

    var isAllowMobileEpisodeDownload: Boolean
        get() = isAllowMobileFor(MobileUpdateOptions.episode_download.name)
        set(allow) {
            setAllowMobileFor(MobileUpdateOptions.episode_download.name, allow)
        }

    var isAllowMobileImages: Boolean
        get() = isAllowMobileFor(MobileUpdateOptions.images.name)
        set(allow) {
            setAllowMobileFor(MobileUpdateOptions.images.name, allow)
        }

    val isImageAllowed: Boolean
        get() = isAllowMobileImages || !isNetworkRestricted

    val isStreamingAllowed: Boolean
        get() = isAllowMobileStreaming || !isNetworkRestricted

    val isFeedRefreshAllowed: Boolean
        get() = isAllowMobileFeedRefresh || !isNetworkRestricted

    val isNetworkRestricted: Boolean
        get() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false // Nothing connected
            if (cm.isActiveNetworkMetered) return true
            val capabilities = cm.getNetworkCapabilities(network) ?: return true // Better be safe than sorry
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        }

    val isVpnOverWifi: Boolean
        get() {
            val connManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val capabilities = connManager.getNetworkCapabilities(connManager.activeNetwork)
            return (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
        }

    @JvmStatic
    fun init(context: Context) {
        NetworkUtils.context = context
    }

    fun isAllowMobileFor(type: String): Boolean {
        val defaultValue = HashSet<String>()
        defaultValue.add("images")
        val allowed = getPref(AppPrefs.prefMobileUpdateTypes, defaultValue)
        return allowed.contains(type)
    }

    fun setAllowMobileFor(type: String, allow: Boolean) {
        val defaultValue = HashSet<String>()
        defaultValue.add("images")
        val getValueStringSet = getPref(AppPrefs.prefMobileUpdateTypes, defaultValue)
        val allowed: MutableSet<String> = HashSet(getValueStringSet)
        if (allow) allowed.add(type)
        else allowed.remove(type)
        putPref(AppPrefs.prefMobileUpdateTypes, allowed)
    }

    fun networkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val networkCapabilities = cm.getNetworkCapabilities(network) ?: return false
        return networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun wasDownloadBlocked(throwable: Throwable?): Boolean {
        val message = throwable!!.message
        if (message != null) {
            val pattern = Pattern.compile(REGEX_PATTERN_IP_ADDRESS)
            val matcher = pattern.matcher(message)
            if (matcher.find()) {
                val ip = matcher.group()
                return ip.startsWith("127.") || ip.startsWith("0.")
            }
        }
        if (throwable.cause != null) return wasDownloadBlocked(throwable.cause)
        return false
    }

    suspend fun fetchHtmlSource(urlString: String): String = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val connection = url.openConnection()
        val inputStream = connection.getInputStream()
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))

        val stringBuilder = StringBuilder()
        var line = ""
        while (bufferedReader.readLine()?.also { line = it } != null) {
            stringBuilder.append(line)
        }

        bufferedReader.close()
        inputStream.close()
        stringBuilder.toString()
    }

    fun getURIFromRequestUrl(source: String): URI {
        // try without encoding the URI
        try { return URI(source) } catch (e: URISyntaxException) { Logd(TAG, "Source is not encoded, encoding now") }
        try {
            val url = URL(source)
            return URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
        } catch (e: MalformedURLException) {
            Logd(TAG, "source: $source")
            throw IllegalArgumentException(e)
        } catch (e: URISyntaxException) {
            Logd(TAG, "source: $source")
            throw IllegalArgumentException(e)
        }
    }

    fun getFinalRedirectedUrl(url: String): String? {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response -> return if (response.isSuccessful) response.request.url.toString() else url }
    }

    private const val AP_SUBSCRIBE = "podcini-subscribe://"
//    private const val AP_SUBSCRIBE_DEEPLINK = "podcini.org/deeplink/subscribe"

    /**
     * Checks if URL is valid and modifies it if necessary.
     * @param url_ The url which is going to be prepared
     * @return The prepared url
     */
    fun prepareUrl(url_: String): String {
        var url = url_
        url = url.trim { it <= ' ' }
        val lowerCaseUrl = url.lowercase() // protocol names are case insensitive
//        Logd(TAG, "prepareUrl lowerCaseUrl: $lowerCaseUrl")
        return when {
            lowerCaseUrl.startsWith("feed://") ->  prepareUrl(url.substring("feed://".length))
            lowerCaseUrl.startsWith("pcast://") ->  prepareUrl(url.substring("pcast://".length))
            lowerCaseUrl.startsWith("pcast:") ->  prepareUrl(url.substring("pcast:".length))
            lowerCaseUrl.startsWith("itpc") ->  prepareUrl(url.substring("itpc://".length))
            lowerCaseUrl.startsWith(AP_SUBSCRIBE) ->  prepareUrl(url.substring(AP_SUBSCRIBE.length))
//            lowerCaseUrl.contains(AP_SUBSCRIBE_DEEPLINK) -> {
//                Log.d(TAG, "Removing $AP_SUBSCRIBE_DEEPLINK")
//                val removedWebsite = url.substring(url.indexOf("?url=") + "?url=".length)
//                return try {
//                    prepareUrl(URLDecoder.decode(removedWebsite, "UTF-8"))
//                } catch (e: UnsupportedEncodingException) {
//                    prepareUrl(removedWebsite)
//                }
//            }
//            TODO: test
//            !(lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("https://")) ->  "http://$url"
            !(lowerCaseUrl.startsWith("http://") || lowerCaseUrl.startsWith("https://")) ->  "https://$url"
            else ->  url
        }
    }

    /**
     * Checks if URL is valid and modifies it if necessary.
     * This method also handles protocol relative URLs.
     * @param url_  The url which is going to be prepared
     * @param base_ The url against which the (possibly relative) url is applied. If this is null,
     * the result of prepareURL(url) is returned instead.
     * @return The prepared url
     */
    fun prepareUrl(url_: String, base_: String?): String {
        var url = url_
        var base = base_ ?: return prepareUrl(url)
        url = url.trim { it <= ' ' }
        base = prepareUrl(base)
        val urlUri = Uri.parse(url)
        val baseUri = Uri.parse(base)
        return if (urlUri.isRelative && baseUri.isAbsolute) urlUri.buildUpon().scheme(baseUri.scheme).build().toString() else prepareUrl(url)
    }

    fun containsUrl(list: List<String>, url: String?): Boolean {
        for (item in list) if (urlEquals(item, url)) return true
        return false
    }

    fun urlEquals(string1: String?, string2: String?): Boolean {
        if (string1 == null || string2 == null) return false
        val url1 = string1.toHttpUrlOrNull() ?: return false
        val url2 = string2.toHttpUrlOrNull() ?: return false
        if (url1.host != url2.host) return false

        val pathSegments1 = normalizePathSegments(url1.pathSegments)
        val pathSegments2 = normalizePathSegments(url2.pathSegments)
        if (pathSegments1 != pathSegments2) return false

        if (url1.query.isNullOrEmpty()) return url2.query.isNullOrEmpty()
        return url1.query == url2.query
    }

    /**
     * Removes empty segments and converts all to lower case.
     * @param input List of path segments
     * @return Normalized list of path segments
     */
    private fun normalizePathSegments(input: List<String>): List<String> {
        val result: MutableList<String> = ArrayList()
        for (string in input) if (string.isNotEmpty()) result.add(string.lowercase())
        return result
    }
}
