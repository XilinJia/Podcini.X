package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.config.ClientConfig
import ac.mdiq.podcini.net.download.DownloadRequest
import ac.mdiq.podcini.net.download.PodciniHttpClient.CONNECTION_TIMEOUT
import ac.mdiq.podcini.net.download.PodciniHttpClient.READ_TIMEOUT
import ac.mdiq.podcini.net.download.PodciniHttpClient.proxyConfig
import ac.mdiq.podcini.net.utils.NetworkUtils.getURIFromRequestUrl
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.ProxyConfig
import ac.mdiq.podcini.utils.Logd
import android.net.TrafficStats
import kotlinx.io.IOException
import okhttp3.Cache
import okhttp3.Credentials.basic
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okio.ByteString
import java.io.File
import java.io.UnsupportedEncodingException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketAddress
import java.util.concurrent.TimeUnit

object OKHTTP {
    private const val TAG = "OKHTTP"
    private const val MAX_CONNECTIONS = 8

    private var okhttpCacheDirectory: File? = null

    class UserAgentInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Chain): Response {
            TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())
            return chain.proceed(chain.request().newBuilder().header("User-Agent", ClientConfig.USER_AGENT?:"").build())
        }
    }

    fun setOKHTTPCacheDirectory(cacheDirectory_: File?) {
        okhttpCacheDirectory = cacheDirectory_
    }

    //        fun resetMemoryBuffer() {
    //            val memoryBufferSize = (128 * 1024 / 8) * BufferDurationSeconds
    //            memoryBuffer = CircularByteBuffer(memoryBufferSize)
    //        }

    private var httpClient: OkHttpClient? = null
    @Synchronized
    fun getOKHttpClient(): OkHttpClient {
        if (httpClient == null) httpClient = newBuilder().build()
        return httpClient!!
    }

    /**
     * Creates a new HTTP client.  Most users should just use
     * getHttpClient() to get the standard Podcini client,
     * but sometimes it's necessary for others to have their own
     * copy so that the clients don't share state.
     * @return http client
     */
    private fun newBuilder(): Builder {
        Logd(TAG, "Creating new instance of HTTP client")
        System.setProperty("http.maxConnections", MAX_CONNECTIONS.toString())

        val builder = Builder()
        builder.interceptors().add(BasicAuthorizationInterceptor())
        builder.addNetworkInterceptor(UserAgentInterceptor())

        //        builder.networkInterceptors().add(UserAgentInterceptor())

        // set cookie handler
        val cm = CookieManager()
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        builder.cookieJar(JavaNetCookieJar(cm))

        // set timeouts
        builder.connectTimeout(CONNECTION_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        builder.readTimeout(READ_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        builder.writeTimeout(READ_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        builder.cache(Cache(okhttpCacheDirectory!!, 20L * 1000000)) // 20MB

        // configure redirects
        builder.followRedirects(true)
        builder.followSslRedirects(true)

        if (proxyConfig != null && proxyConfig!!.type != Proxy.Type.DIRECT && !proxyConfig?.host.isNullOrEmpty()) {
            val port = if (proxyConfig!!.port > 0) proxyConfig!!.port else ProxyConfig.DEFAULT_PORT
            val address: SocketAddress = InetSocketAddress.createUnresolved(proxyConfig!!.host, port)
            builder.proxy(Proxy(proxyConfig!!.type, address))
            if (!proxyConfig!!.username.isNullOrEmpty() && proxyConfig!!.password != null) {
                builder.proxyAuthenticator { _: Route?, response: Response ->
                    val credentials = basic(proxyConfig!!.username!!, proxyConfig!!.password!!)
                    response.request.newBuilder().header("Proxy-Authorization", credentials).build()
                }
            }
        }
        return builder
    }

    fun encodeCredentials(username: String, password: String, charset: String?): String {
        try {
            val credentials = "$username:$password"
            val bytes = credentials.toByteArray(charset(charset!!))
            val encoded: String = ByteString.of(*bytes).base64()
            return "Basic $encoded"
        } catch (e: UnsupportedEncodingException) { throw AssertionError(e) }
    }

    class BasicAuthorizationInterceptor : Interceptor {
        @Throws(IOException::class)
        override fun intercept(chain: Chain): Response {
            TrafficStats.setThreadStatsTag(Thread.currentThread().id.toInt())

            val request: Request = chain.request()
            var response: Response = chain.proceed(request)

            if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) return response

            val newRequest: Request.Builder = request.newBuilder()
            if (response.request.url.toString() != request.url.toString()) {
                // Redirect detected. OkHTTP does not re-add the headers on redirect, so calling the new location directly.
                newRequest.url(response.request.url)

                val authorizationHeaders = request.headers.values(HEADER_AUTHORIZATION)
                if (authorizationHeaders.isNotEmpty() && authorizationHeaders[0].isNotEmpty()) {
                    // Call already had authorization headers. Try again with the same credentials.
                    newRequest.header(HEADER_AUTHORIZATION, authorizationHeaders[0])
                    return chain.proceed(newRequest.build())
                }
            }

            var userInfo = ""
            if (request.tag() is DownloadRequest) {
                val downloadRequest = request.tag() as? DownloadRequest
                if (downloadRequest?.source != null) {
                    userInfo = getURIFromRequestUrl(downloadRequest.source).userInfo
                    if (userInfo.isEmpty() && (!downloadRequest.username.isNullOrEmpty() || !downloadRequest.password.isNullOrEmpty()))
                        userInfo = downloadRequest.username + ":" + downloadRequest.password
                }
            } else userInfo = getImageAuthentication(request.url.toString())

            if (userInfo.isEmpty()) {
                Logd(TAG, "no credentials for '" + request.url + "'")
                return response
            }

            if (!userInfo.contains(":")) {
                Logd(TAG, "Invalid credentials for '" + request.url + "'")
                return response
            }
            val username = userInfo.substringBefore(':')
            val password = userInfo.substring(userInfo.indexOf(':') + 1)

            Logd(TAG, "Authorization failed, re-trying with ISO-8859-1 encoded credentials")
            newRequest.header(HEADER_AUTHORIZATION, encodeCredentials(username, password, "ISO-8859-1"))
            response = chain.proceed(newRequest.build())

            if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) return response

            Logd(TAG, "Authorization failed, re-trying with UTF-8 encoded credentials")
            newRequest.header(HEADER_AUTHORIZATION, encodeCredentials(username, password, "UTF-8"))
            return chain.proceed(newRequest.build())
        }

        /**
         * Returns credentials based on image URL
         * @param imageUrl The URL of the image
         * @return Credentials in format "Username:Password", empty String if no authorization given
         */
        private fun getImageAuthentication(imageUrl: String): String {
            Logd(TAG, "getImageAuthentication() called with: imageUrl = [$imageUrl]")
            val episode = realm.query(Episode::class).query("imageUrl == $0", imageUrl).first().find() ?: return ""
            val username = episode.feed?.username
            val password = episode.feed?.password
            if (username != null && password != null) return "$username:$password"
            return ""
        }

        companion object {
            private val TAG: String = BasicAuthorizationInterceptor::class.simpleName ?: "Anonymous"
            private const val HEADER_AUTHORIZATION = "Authorization"
        }
    }
}