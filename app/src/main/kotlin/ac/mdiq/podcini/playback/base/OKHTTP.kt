package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.net.download.DownloadRequest
import ac.mdiq.podcini.net.download.PodciniHttpClient.CONNECTION_TIMEOUT
import ac.mdiq.podcini.net.download.PodciniHttpClient.READ_TIMEOUT
import ac.mdiq.podcini.net.download.PodciniHttpClient.proxyConfig
import ac.mdiq.podcini.net.utils.NetworkUtils.getURIFromRequestUrl
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.ProxyConfig
import ac.mdiq.podcini.utils.Logd
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

    private val okhttpCacheDirectory: File by lazy { File(getAppContext().cacheDir, "okhttp") }

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

        val builder = Builder().retryOnConnectionFailure(true).connectTimeout(15, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
        builder.interceptors().add(BasicAuthorizationInterceptor())

        // set cookie handler
        val cm = CookieManager()
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        builder.cookieJar(JavaNetCookieJar(cm))

        // set timeouts
        builder.connectTimeout(CONNECTION_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        builder.readTimeout(READ_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        builder.writeTimeout(READ_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        builder.cache(Cache(okhttpCacheDirectory, 20L * 1000000)) // 20MB

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
        override fun intercept(chain: Chain): Response {
            val request = chain.request()
            var response = chain.proceed(request)
            if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) return response

            val newRequest = request.newBuilder()
            if (response.request.url != request.url) {
                val authorizationHeader = request.header(HEADER_AUTHORIZATION)
                if (!authorizationHeader.isNullOrEmpty()) {
                    val redirectUrl = response.request.url
                    response.close()
                    return chain.proceed(newRequest.url(redirectUrl).header(HEADER_AUTHORIZATION, authorizationHeader).build())
                }
            }

            val userInfo = getUserInfo(request)
            if (userInfo.isEmpty()) {
                Logd(TAG, "No credentials for '${request.url}'")
                return response
            }
            val parts = userInfo.split(':', limit = 2)
            if (parts.size != 2) {
                Logd(TAG, "Invalid credentials for '${request.url}'")
                return response
            }

            val username = parts[0]
            val password = parts[1]
            Logd(TAG, "Retrying auth with ISO-8859-1")
            response.close()
            response = retryWithEncoding(chain, newRequest, username, password, "ISO-8859-1")
            if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) return response

            Logd(TAG, "Retrying auth with UTF-8")
            response.close()
            return retryWithEncoding(chain, newRequest, username, password, "UTF-8")
        }

        private fun retryWithEncoding(chain: Chain, requestBuilder: Request.Builder, username: String, password: String, charset: String): Response {
            return chain.proceed(requestBuilder.header(HEADER_AUTHORIZATION, encodeCredentials(username, password, charset)).build())
        }

        private fun getUserInfo(request: Request): String {
            val downloadRequest = request.tag(DownloadRequest::class.java)
            if (downloadRequest != null) {
                if (downloadRequest.source != null) {
                    var userInfo = getURIFromRequestUrl(downloadRequest.source).userInfo
                    if (userInfo.isEmpty() && (!downloadRequest.username.isNullOrEmpty() || !downloadRequest.password.isNullOrEmpty()))
                        userInfo = "${downloadRequest.username}:${downloadRequest.password}"
                    return userInfo
                }
            }
            return getImageAuthentication(request.url.toString())
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