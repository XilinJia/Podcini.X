package ac.mdiq.podcini.playback.base

import ac.mdiq.podcini.config.ClientConfig
import ac.mdiq.podcini.net.download.PodciniHttpClient.BasicAuthorizationInterceptor
import ac.mdiq.podcini.net.download.PodciniHttpClient.CONNECTION_TIMEOUT
import ac.mdiq.podcini.net.download.PodciniHttpClient.READ_TIMEOUT
import ac.mdiq.podcini.net.download.PodciniHttpClient.installCertificates
import ac.mdiq.podcini.net.download.PodciniHttpClient.proxyConfig
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
import okhttp3.Response
import okhttp3.Route
import java.io.File
import java.net.CookieManager
import java.net.CookiePolicy
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

        installCertificates(builder)
        return builder
    }

}