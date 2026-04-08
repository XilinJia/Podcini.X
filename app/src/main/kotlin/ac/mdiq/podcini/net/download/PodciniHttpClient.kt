package ac.mdiq.podcini.net.download

import ac.mdiq.podcini.config.ClientConfig
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
import okhttp3.Credentials.basic
import java.net.InetSocketAddress
import java.net.Proxy
import kotlin.io.encoding.Base64

/**
 * Provides access to a HttpClient singleton.
 */
object PodciniHttpClient {
    private val TAG: String = PodciniHttpClient::class.simpleName ?: "Anonymous"
    const val CONNECTION_TIMEOUT = 15000
    const val READ_TIMEOUT = 30000
    const val SOCKET_TIMEOUT = 15000

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
        return HttpClient(OkHttp) {
            install(DefaultRequest) {
                header(HttpHeaders.UserAgent, ClientConfig.USER_AGENT)
                header(HttpHeaders.Accept, "application/json")
                if (!proxyConfig?.username.isNullOrEmpty() && proxyConfig?.password != null)
                    header(HttpHeaders.Authorization, basic(proxyConfig!!.username!!, proxyConfig!!.password!!))
                val downloadReq = attributes.getOrNull(DownloadRequestKey)
                if (downloadReq != null) {
                    val user = downloadReq.username ?: ""
                    val pass = downloadReq.password ?: ""
                    if (user.isNotEmpty()) header(HttpHeaders.Authorization, "Basic ${Base64.encode("$user:$pass".encodeToByteArray())}")
                }
            }
            install(HttpCookies) { storage = AcceptAllCookiesStorage() }
            install(HttpRedirect) { checkHttpMethod = false }
            install(HttpTimeout) {
                connectTimeoutMillis = CONNECTION_TIMEOUT.toLong()
                socketTimeoutMillis = SOCKET_TIMEOUT.toLong()
                requestTimeoutMillis = READ_TIMEOUT.toLong()
            }
            install(HttpCache)
            engine {
                config {
                    followRedirects(true)
                    proxyConfig?.let { proxy ->
                        if (proxy.type != Proxy.Type.DIRECT && !proxy.host.isNullOrEmpty()) {
                            proxy(Proxy(proxy.type, InetSocketAddress.createUnresolved(proxy.host, if (proxy.port > 0) proxy.port else ProxyConfig.DEFAULT_PORT)))
                            if (!proxy.username.isNullOrEmpty() && proxy.password != null)
                                proxyAuthenticator { _, response -> response.request.newBuilder().header("Proxy-Authorization", basic(proxy.username, proxy.password)).build() }
                        }
                    }
                }
            }
        }
    }

    fun configProxy(proxyConfig: ProxyConfig?) {
        PodciniHttpClient.proxyConfig = proxyConfig
    }
}
