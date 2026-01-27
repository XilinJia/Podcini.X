package ac.mdiq.podcini.net.ssl

import org.conscrypt.Conscrypt
import java.security.Security

object SslProviderInstaller {
    fun install() {
        // Insert bundled conscrypt as highest security provider (overrides OS version).
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }
}
