package ac.mdiq.podcini.storage.database

import ac.mdiq.podcini.config.settings.AppPreferences.migrateSharedPrefs
import ac.mdiq.podcini.storage.model.AppAttribs
import ac.mdiq.podcini.storage.model.AppPrefs
import ac.mdiq.podcini.storage.model.SleepPrefs
import ac.mdiq.podcini.storage.model.SyncPrefs
import ac.mdiq.podcini.storage.specs.ProxyConfig
import ac.mdiq.podcini.utils.Logd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.xilinjia.krdb.notifications.DeletedObject
import io.github.xilinjia.krdb.notifications.InitialObject
import io.github.xilinjia.krdb.notifications.PendingObject
import io.github.xilinjia.krdb.notifications.SingleQueryChange
import io.github.xilinjia.krdb.notifications.UpdatedObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.net.Proxy
import java.util.UUID.randomUUID

private const val TAG = "AppPrefs"

var appPrefs: AppPrefs by mutableStateOf(realm.query(AppPrefs::class).query("id == 0").first().find() ?: AppPrefs() )

var appAttribs: AppAttribs by mutableStateOf(realm.query(AppAttribs::class).query("id == 0").first().find() ?: AppAttribs() )
    private set

var syncPrefs: SyncPrefs by mutableStateOf(SyncPrefs() )

var sleepPrefs: SleepPrefs = SleepPrefs()
    private set

var appPrefsJob: Job? = null

var appAttribsJob: Job? = null
var sleepPrefsJob: Job? = null

var syncPrefsJob: Job? = null

fun initAppPrefs() {
    if (appPrefsJob == null) appPrefsJob = CoroutineScope(Dispatchers.IO).launch {
        val flow = realm.query(AppPrefs::class).query("id == 0").first().asFlow()
        flow.collect { changes: SingleQueryChange<AppPrefs> ->
            when (changes) {
                is InitialObject -> {
                    Logd(TAG, "appPrefsJob InitialObject ")
                    appPrefs = changes.obj
                }
                is UpdatedObject -> {
                    Logd(TAG, "appPrefsJob UpdatedObject ")
                    appPrefs = changes.obj
                }
                is DeletedObject -> {}
                is PendingObject -> {}
            }
        }
    }

    migrateSharedPrefs()

    if (appAttribsJob == null) appAttribsJob = CoroutineScope(Dispatchers.IO).launch {
        if (appAttribs.uniqueId.isEmpty()) appAttribs = upsert(appAttribs) { it.uniqueId = randomUUID().toString() }

        val flow = realm.query(AppAttribs::class).query("id == 0").first().asFlow()
        flow.collect { changes: SingleQueryChange<AppAttribs> ->
            when (changes) {
                is InitialObject -> {
                    Logd(TAG, "appAttribsJob InitialObject prefLastScreen: ${changes.obj.prefLastScreen}")
                    appAttribs = changes.obj
                }
                is UpdatedObject -> {
                    Logd(TAG, "appAttribsJob UpdatedObject prefLastScreen: ${changes.obj.prefLastScreen}")
                    appAttribs = changes.obj
                }
                is DeletedObject -> {}
                is PendingObject -> {}
            }
        }
    }
    if (sleepPrefsJob == null) sleepPrefsJob = CoroutineScope(Dispatchers.IO).launch {
        val flow = realm.query(SleepPrefs::class).query("id == 0").first().asFlow()
        flow.collect { changes: SingleQueryChange<SleepPrefs> ->
            Logd(TAG, "sleepPrefsJob flow.collect")
            when (changes) {
                is UpdatedObject -> sleepPrefs = changes.obj
                is InitialObject -> sleepPrefs = changes.obj
                is DeletedObject -> {}
                is PendingObject -> {}
            }
        }
    }
    if (syncPrefsJob == null) syncPrefsJob = CoroutineScope(Dispatchers.IO).launch {
        val flow = realm.query(SyncPrefs::class).query("id == 0").first().asFlow()
        flow.collect { changes: SingleQueryChange<SyncPrefs> ->
            Logd(TAG, "syncPrefsJob flow.collect")
            when (changes) {
                is UpdatedObject -> syncPrefs = changes.obj
                is InitialObject -> syncPrefs = changes.obj
                is DeletedObject -> {}
                is PendingObject -> {}
            }
        }
    }
}

fun cancelAppPrefs() {
    sleepPrefsJob?.cancel()
    appAttribsJob?.cancel()
    appPrefsJob?.cancel()
    syncPrefsJob?.cancel()
}

const val EPISODE_CACHE_SIZE_UNLIMITED: Int = 0

var isSkipSilence: Boolean
    get() = appPrefs.skipSilence
    set(value) {
        upsertBlk(appPrefs) { it.skipSilence = value }
    }

var speedforwardSpeed: Float
    get() = appPrefs.speedforwardSpeed.toFloat()
    set(speed) {
        upsertBlk(appPrefs) { it.speedforwardSpeed = speed.toString() }
    }

var skipforwardSpeed: Float
    get() = appPrefs.skipforwardSpeed.toFloat()
    set(speed) {
        upsertBlk(appPrefs) { it.skipforwardSpeed = speed.toString() }
    }

var fallbackSpeed: Float
    get() = appPrefs.fallbackSpeed.toFloat()
    set(speed) {
        upsertBlk(appPrefs) { it.fallbackSpeed = speed.toString() }
    }

var fastForwardSecs: Int
    get() = appPrefs.fastForwardSecs
    set(secs) {
        upsertBlk(appPrefs) { it.fastForwardSecs = secs }
    }

var rewindSecs: Int
    get() = appPrefs.rewindSecs
    set(secs) {
        upsertBlk(appPrefs) { it.rewindSecs = secs }
    }

var streamingCacheSizeMB: Int
    get() = appPrefs.streamingCacheSizeMB
    set(size) {
        val size_ = if (size < 10) 10 else size
        upsertBlk(appPrefs) { it.streamingCacheSizeMB = size_ }
    }

var proxyConfig: ProxyConfig
    get() {
        val type = Proxy.Type.valueOf(appPrefs.proxyType)
        val host = appPrefs.proxyHost
        val port = appPrefs.proxyPort
        val username = appPrefs.proxyUser
        val password = appPrefs.proxyPassword
        return ProxyConfig(type, host, port, username, password)
    }
    set(config) {
        upsertBlk(appPrefs) {
            it.proxyType = config.type.name
            it.proxyHost = if (config.host.isNullOrEmpty()) null else config.host
            it.proxyPort = if (config.port !in 1..65535) 0 else config.port
            it.proxyUser = if (config.username.isNullOrEmpty()) null else config.username
            it.proxyPassword = if (config.password.isNullOrEmpty()) null else config.password
        }
    }

var prefStreamOverDownload: Boolean
    get() = appPrefs.streamOverDownload
    set(stream) {
        upsertBlk(appPrefs) { it.streamOverDownload = stream }
    }
