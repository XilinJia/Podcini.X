package ac.mdiq.podcini.preferences

import ac.mdiq.podcini.R
import ac.mdiq.podcini.preferences.screens.EpisodeCleanupOptions
import ac.mdiq.podcini.storage.database.Queues.EnqueueLocation
import ac.mdiq.podcini.storage.model.EpisodeFilter
import ac.mdiq.podcini.storage.model.EpisodeSortOrder
import ac.mdiq.podcini.storage.model.ProxyConfig
import ac.mdiq.podcini.storage.utils.StorageUtils.createNoMediaFile
import ac.mdiq.podcini.util.Logd
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.preference.PreferenceManager
import java.net.Proxy
import androidx.core.content.edit

/**
 * Provides access to preferences set by the user in the settings screen. A
 * private instance of this class must first be instantiated via
 * init() or otherwise every public method will throw an Exception
 * when called.
 */
@SuppressLint("StaticFieldLeak")
object AppPreferences {
    private val TAG: String = AppPreferences::class.simpleName ?: "Anonymous"

    const val EPISODE_CACHE_SIZE_UNLIMITED: Int = 0

    lateinit var appPrefs: SharedPreferences
    val cachedPrefs = mutableMapOf<String, Any?>()

    var theme: ThemePreference
        get() = when (getPref(AppPrefs.prefTheme, "system")) {
            "0" -> ThemePreference.LIGHT
            "1" -> ThemePreference.DARK
            else -> ThemePreference.SYSTEM
        }
        set(theme) {
            when (theme) {
                ThemePreference.LIGHT -> putPref(AppPrefs.prefTheme, "0")
                ThemePreference.DARK -> putPref(AppPrefs.prefTheme, "1")
                else -> putPref(AppPrefs.prefTheme, "system")
            }
        }

    val videoPlayMode: Int
        get() = getPref(AppPrefs.prefVideoPlaybackMode, "1").toInt()

    val isSkipSilence: Boolean
        get() = getPref(AppPrefs.prefSkipSilence, false)

    val isAutodownloadEnabled: Boolean
        get() = getPref(AppPrefs.prefEnableAutoDl, false)

    var speedforwardSpeed: Float
        get() = getPref(AppPrefs.prefSpeedforwardSpeed, "0.00").toFloat()
        set(speed) {
            putPref(AppPrefs.prefSpeedforwardSpeed, speed.toString())
        }

    var fallbackSpeed: Float
        get() = getPref(AppPrefs.prefFallbackSpeed, "0.00").toFloat()
        set(speed) {
            putPref(AppPrefs.prefFallbackSpeed, speed.toString())
        }

    var fastForwardSecs: Int
        get() = getPref(AppPrefs.prefFastForwardSecs, 30)
        set(secs) {
            putPref(AppPrefs.prefFastForwardSecs, secs)
        }

    var rewindSecs: Int
        get() = getPref(AppPrefs.prefRewindSecs, 10)
        set(secs) {
            putPref(AppPrefs.prefRewindSecs, secs)
        }

    var streamingCacheSizeMB: Int
        get() = getPref(AppPrefs.prefStreamingCacheSizeMB, 100)
        set(size) {
            val size_ = if (size < 10) 10 else size
            putPref(AppPrefs.prefStreamingCacheSizeMB, size_)
        }

    var proxyConfig: ProxyConfig
        get() {
            val type = Proxy.Type.valueOf(getPref(AppPrefs.prefProxyType, Proxy.Type.DIRECT.name))
            val host = getPrefOrNull<String>(AppPrefs.prefProxyHost, null)
            val port = getPref(AppPrefs.prefProxyPort, 0)
            val username = getPrefOrNull<String>(AppPrefs.prefProxyUser, null)
            val password = getPrefOrNull<String>(AppPrefs.prefProxyPassword, null)
            return ProxyConfig(type, host, port, username, password)
        }
        set(config) {
            putPref(AppPrefs.prefProxyType, config.type.name)
            if (config.host.isNullOrEmpty()) removePref(AppPrefs.prefProxyHost.name)
            else putPref(AppPrefs.prefProxyHost.name, config.host)
            if (config.port <= 0 || config.port > 65535) removePref(AppPrefs.prefProxyPort.name)
            else putPref(AppPrefs.prefProxyPort.name, config.port)
            if (config.username.isNullOrEmpty()) removePref(AppPrefs.prefProxyUser.name)
            else putPref(AppPrefs.prefProxyUser.name, config.username)
            if (config.password.isNullOrEmpty()) removePref(AppPrefs.prefProxyPassword.name)
            else putPref(AppPrefs.prefProxyPassword.name, config.password)
        }

    var prefStreamOverDownload: Boolean
        get() = getPref(AppPrefs.prefStreamOverDownload, false)
        set(stream) {
            putPref(AppPrefs.prefStreamOverDownload, stream)
        }

    /**
     * Sets up the UserPreferences class.
     * @throws IllegalArgumentException if context is null
     */
    fun init(context: Context) {
        Logd(TAG, "Creating new instance of UserPreferences")
        appPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        AppPrefs.entries.map { it.name }.forEach { key -> cachedPrefs[key] = appPrefs.all[key] }
        createNoMediaFile()
    }

    inline fun <reified T> getPref(key: String, defaultValue: T): T {
        val value = cachedPrefs[key]
        return when (value) {
            is T -> value
            else -> defaultValue
        }
    }

    inline fun <reified T> getPref(key: AppPrefs, hintValue: T, useHintValue: Boolean = false): T {
        val value = cachedPrefs[key.name]
        return when (value) {
            is T -> value
            else -> {
                if (useHintValue) hintValue
                else when (key.default) {
                    is T -> key.default
                    else -> throw IllegalArgumentException("Unsupported type")
                }
            }
        }
    }

    inline fun <reified T> getPrefOrNull(key: AppPrefs, hintValue: T?): T? {
        val value = cachedPrefs[key.name]
        return when (value) {
            is T -> value
            else -> {
                when (key.default) {
                    is T -> key.default
                    else -> null
                }
            }
        }
    }

    inline fun <reified T> putPref(key: String, value: T) {
        Logd("AppPreferences", "putPref key: $key value: $value")
        cachedPrefs[key] = value
        appPrefs.edit {
            when (value) {
                is String -> putString(key, value)
                is Int -> putInt(key, value)
                is Boolean -> putBoolean(key, value)
                is Float -> putFloat(key, value)
                is Long -> putLong(key, value)
                is Set<*> -> {
                    val stringSet = value.filterIsInstance<String>().toSet()
                    if (stringSet.size == value.size) putStringSet(key, stringSet)
                }
                else -> throw IllegalArgumentException("Unsupported type")
            }
        }
    }

    inline fun <reified T> putPref(key: AppPrefs, value: T) {
        putPref(key.name, value)
    }

    fun removePref(key: String) {
        cachedPrefs.remove(key)
        appPrefs.edit { remove(key) }
    }

    enum class DefaultPages(val res: Int) {
        Subscriptions(R.string.subscriptions_label),
        Queues(R.string.queue_label),
        Facets(R.string.facets),
        OnlineSearch(R.string.add_feed_label),
        Statistics(R.string.statistics_label),
        Remember(R.string.remember_last_page);
    }

    @Suppress("EnumEntryName")
    enum class AppPrefs(val default: Any?) {
        prefOPMLBackup(true),
        prefOPMLRestore(false),
        prefOPMLFeedsToRestore(0),

        // User Interface
        prefTheme("system"),
        prefThemeBlack(false),
        prefTintedColors(false),
        prefFeedGridLayout(false),
        prefSwipeToRefreshAll(true),
        prefEpisodeCover(false),
        showTimeLeft(false),
        prefShowSkip(true),
        prefShowDownloadReport(true),
        prefDefaultPage(DefaultPages.Subscriptions.name),
        prefBackButtonOpensDrawer(false),
        prefQueueKeepSorted(false),
        prefQueueKeepSortedOrder("use-default"),
        prefShowErrorToasts(true),
        prefPrintDebugLogs(false),

        prefLastScreen(""),
        prefLastScreenArg(""),

        // Episodes
        prefEpisodesSort("" + EpisodeSortOrder.DATE_NEW_OLD.code),
        prefEpisodesFilter(""),
        prefFacetsCurIndex(0),
        prefDownloadsFilter(EpisodeFilter.States.downloaded.name),

        // Playback
        prefPauseOnHeadsetDisconnect(true),
        prefUnpauseOnHeadsetReconnect(true),
        prefUnpauseOnBluetoothReconnect(false),
        prefHardwareForwardButton(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD.toString()),
        prefHardwarePreviousButton(KeyEvent.KEYCODE_MEDIA_REWIND.toString()),
        prefFollowQueue(true),
        prefSkipKeepsEpisode(true),
        prefRemoveFromQueueMarkedPlayed(true),
        prefFavoriteKeepsEpisode(true),

        prefAutoBackup(false),
        prefAutoBackupIntervall(24),
        prefAutoBackupFolder(""),
        prefAutoBackupLimit(2),
        prefAutoBackupTimeStamp(0L),

        prefUseCustomMediaFolder(false),
        prefCustomMediaUri(""),

        prefAutoDelete(false),
        prefAutoDeleteLocal(false),
        prefPlaybackSpeedArray(null),
        prefFallbackSpeed("0.00"),
        prefPlaybackTimeRespectsSpeed(false),
        prefStreamOverDownload(false),
        prefLowQualityOnMobile(false),
        prefSpeedforwardSpeed("0.00"),
        prefUseAdaptiveProgressUpdate(true),

        // Network
        prefEnqueueDownloaded(true),
        prefEnqueueLocation(EnqueueLocation.BACK.name),
        prefAutoUpdateStartTime(":"),
        prefAutoUpdateInterval("12"),
        prefLastFullUpdateTime(0L),
        prefMobileUpdateTypes(hashSetOf("images")),
        prefEpisodeCleanup(EpisodeCleanupOptions.Never.num.toString()),
        prefEpisodeCacheSize("25"),
        prefEnableAutoDl(false),
        prefEnableAutoDownloadOnBattery(false),

        prefAutoDLIncludeQueues(setOf<String>()),   // special
        prefAutoDLOnEmptyIncludeQueues(setOf<String>()),   // special

        prefProxyType(Proxy.Type.DIRECT.name),
        prefProxyHost(null),
        prefProxyPort(0),
        prefProxyUser(null),
        prefProxyPassword(null),

        // Services
        pref_gpodnet_notifications(true),
        pref_nextcloud_server_address(""),

        // Other
        prefDeleteRemovesFromQueue(true),

        // Mediaplayer
        prefPlaybackSpeed("1.00"),
        prefSkipSilence(false),
        prefFastForwardSecs(30),
        prefRewindSecs(10),
        prefStreamingCacheSizeMB(100),
        prefQueueLocked(true),
        prefVideoPlaybackMode("1"),
    }

    enum class ThemePreference {
        LIGHT, DARK, BLACK, SYSTEM
    }
}
