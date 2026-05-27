package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.net.download.DownloadRequest
import ac.mdiq.podcini.net.download.PodciniHttpClient.proxyConfig
import ac.mdiq.podcini.net.utils.NetworkUtils.getURIFromRequestUrl
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.specs.ProxyConfig
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import kotlinx.io.IOException
import okhttp3.Call
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.Credentials.basic
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Interceptor.Chain
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okio.ByteString
import java.io.InterruptedIOException
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object OKHTTP {
    private const val TAG = "OKHTTP"
    private const val CONNECTION_TIMEOUT = 15000

    private var httpClient: OkHttpClient? = null
    @Synchronized
    fun getOKHttpClient(): OkHttpClient {
        if (httpClient == null) httpClient = newBuilder().build()
        return httpClient!!
    }

    private fun newBuilder(): Builder {
        Logd(TAG, "Creating optimized HTTP client for Media3 Streaming")
        val builder = Builder()
        builder.retryOnConnectionFailure(true)
        builder.connectTimeout(CONNECTION_TIMEOUT.toLong(), TimeUnit.MILLISECONDS)
        builder.readTimeout(15, TimeUnit.SECONDS)
        builder.writeTimeout(30, TimeUnit.SECONDS)
        builder.callTimeout(0, TimeUnit.SECONDS)
        builder.followRedirects(true)
        builder.followSslRedirects(true)
//        builder.protocols(listOf(Protocol.HTTP_1_1))
        builder.protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        builder.connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        builder.addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("Accept-Encoding", "identity")
                .build()
            chain.proceed(request)
        }
        builder.addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            Logd(TAG,
                """
                host=${chain.request().url.host}
                protocol=${response.protocol}
                code=${response.code}
                contentLength=${response.body.contentLength()}
                transferEncoding=${response.header("Transfer-Encoding")}
                acceptRanges=${response.header("Accept-Ranges")}
                contentEncoding=${response.header("Content-Encoding")}
                connection=${response.header("Connection")}
                """.trimIndent()
            )
            response
        }
        builder.interceptors().add(BasicAuthorizationInterceptor())

        builder.eventListener(object : EventListener() {
            override fun connectionAcquired(call: Call, connection: Connection) {
                Logd(TAG, "acquired: $connection")
            }
            override fun connectionReleased(call: Call, connection: Connection) {
                Logd(TAG, "released: $connection")
            }
            override fun callFailed(call: Call, ioe: IOException) {
                if (call.isCanceled() || ioe is InterruptedIOException && ioe.message?.contains("canceled", ignoreCase = true) == true || ioe.message?.contains("canceled", ignoreCase = true) == true) {
                    Logd(TAG, "Network call was intentionally canceled. Ignoring error logs.")
                    return
                }
                Loge(TAG, "callFailed error ${ioe::class.java.name}: ${ioe.message}")
                var cause = ioe.cause
                while (cause != null) {
                    Loge(TAG, "callFailed Cause: ${cause::class.java.name}: ${cause.message}")
                    cause = cause.cause
                }
            }
        })
        proxyConfig?.let { proxy ->
            if (proxy.type != Proxy.Type.DIRECT && !proxy.host.isNullOrEmpty()) {
                val port = if (proxy.port > 0) proxy.port else ProxyConfig.DEFAULT_PORT
                val address = InetSocketAddress.createUnresolved(proxy.host, port)
                builder.proxy(Proxy(proxy.type, address))
                if (!proxy.username.isNullOrEmpty() && proxy.password != null)
                    builder.proxyAuthenticator { _, response -> response.request.newBuilder().header("Proxy-Authorization", basic(proxy.username, proxy.password)).build() }
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
            fun getImageAuthentication(imageUrl: String): String {
                Logd(TAG, "getImageAuthentication() called with: imageUrl = [$imageUrl]")
                val episode = realm.query(Episode::class).query("imageUrl == $0", imageUrl).first().find() ?: return ""
                val username = episode.feed?.username
                val password = episode.feed?.password
                if (username != null && password != null) return "$username:$password"
                return ""
            }
            fun getUserInfo(request: Request): String {
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
            response = chain.proceed(newRequest.header(HEADER_AUTHORIZATION, encodeCredentials(username, password, "ISO-8859-1")).build())
            if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) return response

            Logd(TAG, "Retrying auth with UTF-8")
            response.close()
            return chain.proceed(newRequest.header(HEADER_AUTHORIZATION, encodeCredentials(username, password, "UTF-8")).build())
        }

        companion object {
            private val TAG: String = BasicAuthorizationInterceptor::class.simpleName ?: "Anonymous"
            private const val HEADER_AUTHORIZATION = "Authorization"
        }
    }
}
