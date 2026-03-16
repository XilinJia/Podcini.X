package ac.mdiq.podcini.config

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.config.settings.AppPreferences
import ac.mdiq.podcini.gears.gearbox
import ac.mdiq.podcini.net.download.EpisodeAdrDLManager
import ac.mdiq.podcini.net.download.PodciniHttpClient.configProxy
import ac.mdiq.podcini.net.ssl.SslProviderInstaller
import ac.mdiq.podcini.net.sync.SyncService
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.playback.base.OKHTTP.setOKHTTPCacheDirectory
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
import ac.mdiq.podcini.storage.utils.createCacheDir
import ac.mdiq.podcini.storage.utils.createNoMediaFile
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.timeIt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


object ClientConfigurator {
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return
        getRealmInstance()

        initAppPrefs()

        CoroutineScope(Dispatchers.IO).launch { createCacheDir() }

        Logd("ClientConfigurator", "initialize")
        timeIt("ClientConfigurator Init started ")

        AppPreferences.init()
        createNoMediaFile()

        monitorFeeds()
        monitorVolumes()
        initQueues()
        SslProviderInstaller.install()
        EpisodeAdrDLManager.manager = EpisodeAdrDLManager()
        SynchronizationQueueSink.setServiceStarterImpl { SyncService.sync() }
        setOKHTTPCacheDirectory(File(getAppContext().cacheDir, "okhttp"))
        configProxy(proxyConfig)
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
