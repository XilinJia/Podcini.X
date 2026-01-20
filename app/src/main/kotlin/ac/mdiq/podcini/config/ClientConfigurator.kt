package ac.mdiq.podcini.config

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.download.service.DownloadServiceInterfaceImpl
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.setCacheDirectory
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.setProxyConfig
import ac.mdiq.podcini.net.ssl.SslProviderInstaller
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.net.utils.NetworkUtils
import ac.mdiq.podcini.preferences.AppPreferences
import ac.mdiq.podcini.preferences.AppPreferences.proxyConfig
import ac.mdiq.podcini.storage.database.cancelQueuesJob
import ac.mdiq.podcini.storage.database.getRealmInstance
import ac.mdiq.podcini.storage.database.initAppPrefs
import ac.mdiq.podcini.storage.database.initQueues
import ac.mdiq.podcini.utils.Logd
import android.content.Context
import java.io.File


object ClientConfigurator {
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return
        Logd("ClientConfigurator", "initialize")

        AppPreferences.init()
        getRealmInstance()

        initQueues()
        SslProviderInstaller.install()
        NetworkUtils.init()
        DownloadServiceInterface.impl = DownloadServiceInterfaceImpl()
        SynchronizationQueueSink.setServiceStarterImpl { SyncService.sync() }
        setCacheDirectory(File(getAppContext().cacheDir, "okhttp"))
        setProxyConfig(proxyConfig)
//        SleepTimerPreferences.init(context)
        createChannels()
        initialized = true
    }

    fun destroy() {
        cancelQueuesJob()
    }
}
