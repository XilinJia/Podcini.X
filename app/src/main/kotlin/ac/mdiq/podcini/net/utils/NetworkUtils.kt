package ac.mdiq.podcini.net.utils

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.net.download.PodciniHttpClient.getKtorClient
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.utils.toSafeUri
import ac.mdiq.podcini.ui.screens.prefscreens.MobileUpdateOptions
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.xilinjia.krdb.ext.toRealmSet
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.MalformedURLException
import java.net.NetworkInterface
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.util.regex.Pattern

@SuppressLint("StaticFieldLeak")
object NetworkUtils {
    private const val TAG = "NetworkUtils"

    private const val REGEX_PATTERN_IP_ADDRESS = "([0-9]{1,3}[\\.]){3}[0-9]{1,3}"

    var mobileAllowStreaming: Boolean
        get() = isAllowedOnMobile(MobileUpdateOptions.streaming.name)
        set(allow) {
            setAllowMobileFor(MobileUpdateOptions.streaming.name, allow)
        }

    private val mobileAllowAutoDownload: Boolean
        get() = isAllowedOnMobile(MobileUpdateOptions.auto_download.name)

    var mobileAllowFeedRefresh: Boolean
        get() = isAllowedOnMobile(MobileUpdateOptions.feed_refresh.name)
        set(allow) {
            setAllowMobileFor(MobileUpdateOptions.feed_refresh.name, allow)
        }

    val mobileAllowEpisodeDownload: Boolean
        get() = isAllowedOnMobile(MobileUpdateOptions.episode_download.name)

    val isImageDownloadAllowed: Boolean
        get() = isAllowedOnMobile(MobileUpdateOptions.images.name) || !networkMonitor.isNetworkRestricted

    val isStreamingAllowed: Boolean
        get() = mobileAllowStreaming || !networkMonitor.isNetworkRestricted

    val isFeedRefreshAllowed: Boolean
        get() = mobileAllowFeedRefresh || !networkMonitor.isNetworkRestricted

    fun isNetworkUrl(source: String?): Boolean {
        if (source.isNullOrBlank()) return false
        return try {
            val url = Url(source)
            url.protocol == URLProtocol.HTTP || url.protocol == URLProtocol.HTTPS
        } catch (e: Exception) {
            false
        }
    }

    fun isAllowedOnMobile(type: String): Boolean {
        val defaultValue = HashSet<String>()
        defaultValue.add("images")
        val allowed = appPrefs.mobileUpdateTypes
        return allowed.contains(type)
    }

    fun setAllowMobileFor(type: String, allow: Boolean) {
        val defaultValue = HashSet<String>()
        defaultValue.add("images")
        val getValueStringSet = appPrefs.mobileUpdateTypes
        val allowed: MutableSet<String> = HashSet(getValueStringSet)
        if (allow) allowed.add(type)
        else allowed.remove(type)
        upsertBlk(appPrefs) { it.mobileUpdateTypes = allowed.toRealmSet() }
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
        val url = try { URL(urlString) } catch (e: MalformedURLException) {
            Loge(TAG, "fetchHtmlSource urlString invalid: $urlString")
            return@withContext ""
        }
        getKtorClient().get(url).bodyAsText()
    }

    fun getURIFromRequestUrl(source: String): URI {
        // try without encoding the URI
        try { return URI(source) } catch (e: URISyntaxException) { Logs(TAG, e, "Source is not encoded, encoding now") }
        try {
            val url = URL(source)
            return URI(url.protocol, url.userInfo, url.host, url.port, url.path, url.query, url.ref)
        } catch (e: MalformedURLException) {
            Logs(TAG, e, "source: $source")
            throw IllegalArgumentException(e)
        } catch (e: URISyntaxException) {
            Logs(TAG, e, "source: $source")
            throw IllegalArgumentException(e)
        }
    }

    fun getFinalRedirectedUrl(url: String): String {
        return try {
            val response = runBlocking { getKtorClient().get(url) }
            if (response.status.isSuccess()) response.call.request.url.toString() else url
        } catch (e: Exception) { url }
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
//                Logd(TAG, "Removing $AP_SUBSCRIBE_DEEPLINK")
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
        val urlUri = url.toSafeUri()
        val baseUri = base.toSafeUri()
        return if (urlUri.isRelative && baseUri.isAbsolute) urlUri.buildUpon().scheme(baseUri.scheme).build().toString() else prepareUrl(url)
    }

    fun containsUrl(list: List<String>, url: String?): Boolean {
        for (item in list) if (urlEquals(item, url)) return true
        return false
    }

    fun urlEquals(string1: String?, string2: String?): Boolean {
        if (string1 == null || string2 == null) return false
        val url1 = runCatching { URLBuilder(string1) }.getOrNull() ?: return false
        val url2 = runCatching { URLBuilder(string2) }.getOrNull() ?: return false
        if (url1.host != url2.host) return false

        val pathSegments1 = normalizePathSegments(url1.pathSegments)
        val pathSegments2 = normalizePathSegments(url2.pathSegments)
        if (pathSegments1 != pathSegments2) return false

        if (url1.parameters.isEmpty()) return url2.parameters.isEmpty()
        return url1.parameters == url2.parameters
    }

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            for (intf in interfaces) {
                val addrs = intf.inetAddresses.toList()
                for (addr in addrs) if (!addr.isLoopbackAddress && addr is Inet4Address) return addr.hostAddress
            }
        } catch (ex: Exception) { ex.printStackTrace() }
        return null
    }

    /**
     * Removes empty segments and converts all to lower case.
     * @param input List of path segments
     * @return Normalized list of path segments
     */
    private fun normalizePathSegments(input: List<String>): List<String> {
        val result: MutableList<String> = mutableListOf()
        for (string in input) if (string.isNotEmpty()) result.add(string.lowercase())
        return result
    }

    fun networkChangedDetected(isConnected: Boolean) {
//        if (networkMonitor.networkAllowAutoDownload) {
//            Logd(TAG, "auto-dl network available, starting auto-download")
//            if (appAttribs.dlCanceledWhenDisconnected) {
//                upsertBlk(appAttribs) { it.dlCanceledWhenDisconnected = false }
//                autodownload()
//            }
//        } else {
//            if (networkMonitor.isNetworkRestricted) {
//                Logt(TAG, "Device is no longer connected to Wi-Fi. Cancelling ongoing downloads")
//                upsertBlk(appAttribs) { it.dlCanceledWhenDisconnected = true }
//                EpisodeAdrDLManager.manager?.cancelAll()
//            }
//        }
    }

    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor() }

    class NetworkMonitor {
        private val connectivityManager = getAppContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        var isConnected: Boolean = true
            private set

        var isNetworkRestricted: Boolean = false
            private set

        var networkAllowAutoDownload: Boolean = false
            private set

        var isVpnOverWifi: Boolean = false
            private set

        val networkFlow: Flow<Boolean> = callbackFlow {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    isConnected = true
                    trySend(true)
                }
                override fun onLost(network: Network) {
                    isConnected = false
                    isNetworkRestricted = false
                    trySend(false)
                }
                override fun onCapabilitiesChanged(n: Network, nc: NetworkCapabilities) {
                    val connected = nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    val isMetered = connectivityManager.isActiveNetworkMetered
                    val isCellular = nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    isNetworkRestricted = isMetered || isCellular
                    networkAllowAutoDownload = when {
                        nc == null -> false
                        nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                            if (nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) true
                            else mobileAllowAutoDownload
                        }
                        nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                        else -> mobileAllowAutoDownload || nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    }
                    isVpnOverWifi = (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                    trySend(connected)
                }
            }
            connectivityManager.registerDefaultNetworkCallback(callback)
//            trySend(false)
            trySend(connectivityManager.activeNetwork != null)
            awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
        }.distinctUntilChanged()
    }
}
