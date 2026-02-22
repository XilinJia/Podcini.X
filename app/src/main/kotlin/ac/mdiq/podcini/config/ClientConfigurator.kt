package ac.mdiq.podcini.config

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.config.settings.AppPreferences
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.service.DownloadServiceInterface
import ac.mdiq.podcini.net.download.service.DownloadServiceInterfaceImpl
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.setCacheDirectory
import ac.mdiq.podcini.net.download.service.PodciniHttpClient.setProxyConfig
import ac.mdiq.podcini.net.ssl.SslProviderInstaller
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.storage.database.cancelAppPrefs
import ac.mdiq.podcini.storage.database.cancelMonitorFeeds
import ac.mdiq.podcini.storage.database.cancelQueuesJob
import ac.mdiq.podcini.storage.database.getRealmInstance
import ac.mdiq.podcini.storage.database.initAppPrefs
import ac.mdiq.podcini.storage.database.initQueues
import ac.mdiq.podcini.storage.database.monitorFeeds
import ac.mdiq.podcini.storage.database.proxyConfig
import ac.mdiq.podcini.storage.model.cancelMonitorVolumes
import ac.mdiq.podcini.storage.model.monitorVolumes
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.timeIt
import java.io.File


object ClientConfigurator {
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return
        getRealmInstance()

        initAppPrefs()

        Logd("ClientConfigurator", "initialize")
        timeIt("ClientConfigurator Init started ")

        AppPreferences.init()

        monitorFeeds()
        monitorVolumes()
        initQueues()
        SslProviderInstaller.install()
        DownloadServiceInterface.impl = DownloadServiceInterfaceImpl()
        SynchronizationQueueSink.setServiceStarterImpl { SyncService.sync() }
        setCacheDirectory(File(getAppContext().cacheDir, "okhttp"))
        setProxyConfig(proxyConfig)
        createChannels()
        gearbox.init()

        timeIt("ClientConfigurator Init ends ")
        initialized = true
    }

    fun destroy() {
        cancelQueuesJob()
        cancelMonitorFeeds()
        cancelMonitorVolumes()
        cancelAppPrefs()
    }
}
