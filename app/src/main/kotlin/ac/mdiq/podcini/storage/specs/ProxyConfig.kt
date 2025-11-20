package ac.mdiq.podcini.storage.specs

import java.net.Proxy

class ProxyConfig( val type: Proxy.Type,
                   val host: String?,
                   val port: Int,
                   val username: String?,
                   val password: String?) {

    companion object {
        const val DEFAULT_PORT: Int = 8080
    }
}