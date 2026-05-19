package ac.mdiq.podcini.net.download

import ac.mdiq.podcini.config.settings.USER_AGENT
import ac.mdiq.podcini.storage.specs.ProxyConfig
import ac.mdiq.podcini.utils.Logd
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.util.AttributeKey
import kotlinx.io.IOException
import okhttp3.Call
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.Credentials.basic
import okhttp3.EventListener
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64

/**
 * Provides access to a HttpClient singleton.
 */
object PodciniHttpClient {
    private val TAG: String = PodciniHttpClient::class.simpleName ?: "Anonymous"

    var proxyConfig: ProxyConfig? = null

    private var ktorClient: HttpClient? = null

    fun getKtorClient(): HttpClient {
        if (ktorClient == null) ktorClient = createKtorClient()
        return ktorClient!!
    }

    fun resetClient() {
        ktorClient = null
    }

    val DownloadRequestKey = AttributeKey<DownloadRequest>("DownloadRequest")

    fun createKtorClient(): HttpClient {
        Logd(TAG, "Creating new instance of HTTP client")
        val client = HttpClient(OkHttp) {
            install(DefaultRequest) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "application/json")
                attributes.getOrNull(DownloadRequestKey)?.let { downloadReq ->
                    val user = downloadReq.username.orEmpty()
                    val pass = downloadReq.password.orEmpty()
                    if (user.isNotEmpty()) header(HttpHeaders.Authorization, "Basic ${Base64.encode("$user:$pass".encodeToByteArray())}")
                }
            }
            install(HttpCookies) { storage = AcceptAllCookiesStorage() }
            install(HttpRedirect) { checkHttpMethod = false }
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
                requestTimeoutMillis = 45_000
            }
            install(HttpCache)
            engine {
                config {
                    followRedirects(true)
                    retryOnConnectionFailure(true)
                    pingInterval(30, TimeUnit.SECONDS)
                    connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))

                    // Optional:
//                    protocols(listOf(Protocol.HTTP_1_1))

                    // Debugging / diagnostics
                    eventListener(object : EventListener() {
                        override fun connectionAcquired(call: Call, connection: Connection) {
                            Logd(TAG, "connectionAcquired: $connection")
                        }
                        override fun connectionReleased(call: Call, connection: Connection) {
                            Logd(TAG, "connectionReleased: $connection")
                        }
                        override fun callFailed(call: Call, ioe: IOException) {
                            Logd(TAG, "callFailed ${ioe.message}")
                        }
                    })

                    proxyConfig?.let { proxy ->
                        if (proxy.type != Proxy.Type.DIRECT && !proxy.host.isNullOrEmpty()) {
                            proxy(Proxy(proxy.type, InetSocketAddress.createUnresolved(proxy.host, if (proxy.port > 0) proxy.port else ProxyConfig.DEFAULT_PORT)))
                            if (!proxy.username.isNullOrEmpty() && proxy.password != null)
                                proxyAuthenticator { _, response -> response.request.newBuilder().header("Proxy-Authorization", basic(proxy.username, proxy.password)).build() }
                        }
                    }
                    /*
                    Optional StrictMode socket tagging
                    addNetworkInterceptor { chain ->
                        TrafficStats.setThreadStatsTag(0xF00D)
                        try { chain.proceed(chain.request()) } finally { TrafficStats.clearThreadStatsTag() }
                    }
                    */
                }
            }
        }
        return client
    }

    fun configProxy(proxyConfig: ProxyConfig?) {
        PodciniHttpClient.proxyConfig = proxyConfig
    }
}
