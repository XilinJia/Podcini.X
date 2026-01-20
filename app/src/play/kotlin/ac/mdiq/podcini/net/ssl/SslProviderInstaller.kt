package ac.mdiq.podcini.net.ssl

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.utils.Logs
import android.content.Context
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import com.google.android.gms.security.ProviderInstaller

object SslProviderInstaller {
    private const val TAG = "SslProviderInstaller"
    fun install() {
        try { ProviderInstaller.installIfNeeded(getAppContext())
        } catch (e: GooglePlayServicesRepairableException) {
            Logs(TAG, e)
            GoogleApiAvailability.getInstance().showErrorNotification(getAppContext(), e.connectionStatusCode)
        } catch (e: GooglePlayServicesNotAvailableException) { Logs(TAG, e)
        } catch (e: Exception) { Logs(TAG, e) }
    }
}
