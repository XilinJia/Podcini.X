package ac.mdiq.podcini.net.sync

import io.ktor.http.Url

class HostnameParser(hosturl: String?) {
    
    var scheme: String? = null
    
    var port: Int = 0
    
    var host: String? = null
    
    var subfolder: String? = null

    private val URL_PATTERN = Regex("""(?:(https?)://)?([^:/]+)(?::(\d+))?(.+)?""")

    init {
        val input = hosturl ?: ""
        val match = URL_PATTERN.matchEntire(input)

        if (match != null) {
            val groups = match.groups
            scheme = groups[1]?.value
//            host = IDN.toASCII(groups[2]?.value ?: "")
            val url = Url("https://${groups[2]?.value ?: ""}")
            host = url.host
            port = groups[3]?.value?.toIntOrNull() ?: -1
            val path = groups[4]?.value
            subfolder = path?.trimEnd('/') ?: ""
        } else {
            scheme = "https"
//            host = IDN.toASCII(input)
            val url = Url("https://$input")
            host = url.host
            port = 443
            subfolder = ""
        }

        when (scheme) {
            null if port == 80 -> scheme = "http"
            null -> scheme = "https" // assume https
        }
        when (scheme) {
            "https" if port == -1 -> port = 443
            "http" if port == -1 -> port = 80
        }
    }
}
