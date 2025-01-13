package ac.mdiq.podcini.util.config

import ac.mdiq.podcini.net.download.service.DownloadServiceInterfaceImpl
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.setCacheDirectory
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.setProxyConfig
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.ssl.SslProviderInstaller
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.preferences.SleepTimerPreferences
import ac.mdiq.podcini.preferences.UsageStatistics
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.proxyConfig
import ac.mdiq.podcini.ui.utils.NotificationUtils
import ac.mdiq.podcini.util.Logd
import android.content.Context

import java.io.File


object ClientConfigurator {
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        Logd("ClientConfigurator", "initialize")

        AppPreferences.init(context)
        UsageStatistics.init(context)
        SslProviderInstaller.install(context)
        NetworkUtils.init(context)
        DownloadServiceInterface.setImpl(DownloadServiceInterfaceImpl())
        SynchronizationQueueSink.setServiceStarterImpl { SyncService.sync(context) }
        setCacheDirectory(File(context.cacheDir, "okhttp"))
        setProxyConfig(proxyConfig)
        SleepTimerPreferences.init(context)
        NotificationUtils.createChannels(context)
        initialized = true
    }
}
