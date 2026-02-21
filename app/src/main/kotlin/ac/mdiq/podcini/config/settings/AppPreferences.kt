package ac.mdiq.podcini.config.settings

import ac.mdiq.podcini.PodciniApp.Companion.getAppContext
import ac.mdiq.podcini.storage.database.appPrefs
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.utils.createNoMediaFile
import ac.mdiq.podcini.ui.compose.DefaultPages
import ac.mdiq.podcini.ui.screens.prefscreens.EpisodeCleanupOptions
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.timeIt
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.KeyEvent
import androidx.preference.PreferenceManager
import io.github.xilinjia.krdb.ext.toRealmSet
import java.net.Proxy

/**
 * Provides access to preferences set by the user in the settings screen. A
 * private instance of this class must first be instantiated via
 * init() or otherwise every public method will throw an Exception
 * when called.
 */
@SuppressLint("StaticFieldLeak")
object AppPreferences {
    private val TAG: String = AppPreferences::class.simpleName ?: "Anonymous"

    lateinit var appSPrefs: SharedPreferences
    val cachedPrefs = mutableMapOf<String, Any?>()

    /**
     * Sets up the UserPreferences class.
     * @throws IllegalArgumentException if context is null
     */
    fun init() {
        timeIt("$TAG start of init")
        Logd(TAG, "Creating new instance of UserPreferences")
        if (!appPrefs.migrationDone) {
            appSPrefs = PreferenceManager.getDefaultSharedPreferences(getAppContext())
            AppSPrefs.entries.map { it.name }.forEach { key -> cachedPrefs[key] = appSPrefs.all[key] }
        }
        createNoMediaFile()
        timeIt("$TAG end of init")
    }

    private inline fun <reified T> getPref(key: AppSPrefs, hintValue: T, useHintValue: Boolean = false): T {
        return when (val value = cachedPrefs[key.name]) {
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

    private inline fun <reified T> getPrefOrNull(key: AppSPrefs, hintValue: T?): T? {
        return when (val value = cachedPrefs[key.name]) {
            is T -> value
            else -> {
                when (key.default) {
                    is T -> key.default
                    else -> null
                }
            }
        }
    }

    @Suppress("EnumEntryName")
    private enum class AppSPrefs(val default: Any?) {
        lastVersion("0"),

        prefOPMLBackup(true),
        prefOPMLRestore(false),
        prefOPMLFeedsToRestore(0),

        // User Interface
        prefTheme("system"),
        prefThemeBlack(false),
        prefTintedColors(false),
        prefEpisodeCover(false),
        prefShowSkip(true),
        prefShowDownloadReport(true),
        prefDefaultPage(DefaultPages.Library.name),
        prefBackButtonOpensDrawer(false),
        prefShowErrorToasts(true),
        prefPrintDebugLogs(false),

        dont_ask_again_unrestricted_background(false),

        // Playback
        prefPauseOnHeadsetDisconnect(true),
        prefUnpauseOnHeadsetReconnect(true),
        prefUnpauseOnBluetoothReconnect(false),
        prefHardwareForwardButton(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD.toString()),
        prefHardwarePreviousButton(KeyEvent.KEYCODE_MEDIA_REWIND.toString()),
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
        prefStreamOverDownload(false),
        prefLowQualityOnMobile(false),
        prefSpeedforwardSpeed("0.00"),
        prefSkipforwardSpeed("0.00"),
        prefUseAdaptiveProgressUpdate(true),

        // Network
        prefEnqueueDownloaded(true),

        prefDisableWifiLock(false),

        prefAutoUpdateIntervalMinutes("360"),

        prefMobileUpdateTypes(hashSetOf("images")),
        prefEpisodeCleanup(EpisodeCleanupOptions.Never.num.toString()),
        prefEpisodeCacheSize("25"),
        prefEnableAutoDl(false),
        prefEnableAutoDownloadOnBattery(false),

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
        prefVideoPlaybackMode("1"),
    }

    fun migrateSharedPrefs() {
        if (appPrefs.migrationDone) return

        upsertBlk(appPrefs) {
            it.lastVersion = getPref(AppSPrefs.lastVersion, "0")

            it.OPMLBackup = getPref(AppSPrefs.prefOPMLBackup, true)
            it.OPMLRestored = getPref(AppSPrefs.prefOPMLRestore, false)
            it.OPMLFeedsToRestore = getPref(AppSPrefs.prefOPMLFeedsToRestore, 0)

            // User Interface
            it.theme = getPref(AppSPrefs.prefTheme, "system")
            it.themeBlack = getPref(AppSPrefs.prefThemeBlack, false)
            it.tintedColors = getPref(AppSPrefs.prefTintedColors, false)
            it.useEpisodeCover = getPref(AppSPrefs.prefEpisodeCover, false)
            it.showSkip = getPref(AppSPrefs.prefShowSkip, true)
            it.showDownloadReport = getPref(AppSPrefs.prefShowDownloadReport, true)
            it.defaultPage = getPref(AppSPrefs.prefDefaultPage, DefaultPages.Library.name)
            it.backButtonOpensDrawer = getPref(AppSPrefs.prefBackButtonOpensDrawer, false)
            it.showErrorToasts = getPref(AppSPrefs.prefShowErrorToasts, true)
            it.printDebugLogs = getPref(AppSPrefs.prefPrintDebugLogs, false)

            it.dont_ask_again_unrestricted_background = getPref(AppSPrefs.dont_ask_again_unrestricted_background, false)

            // Playback
            it.pauseOnHeadsetDisconnect = getPref(AppSPrefs.prefPauseOnHeadsetDisconnect, true)
            it.unpauseOnHeadsetReconnect = getPref(AppSPrefs.prefUnpauseOnHeadsetReconnect, true)
            it.unpauseOnBluetoothReconnect = getPref(AppSPrefs.prefUnpauseOnBluetoothReconnect, false)
            it.hardwareForwardButton = getPref(AppSPrefs.prefHardwareForwardButton, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD.toString())
            it.hardwarePreviousButton = getPref(AppSPrefs.prefHardwarePreviousButton, KeyEvent.KEYCODE_MEDIA_REWIND.toString())
            it.skipKeepsEpisode = getPref(AppSPrefs.prefSkipKeepsEpisode, true)
            it.removeFromQueueMarkPlayed = getPref(AppSPrefs.prefRemoveFromQueueMarkedPlayed, true)
            it.favoriteKeepsEpisode = getPref(AppSPrefs.prefFavoriteKeepsEpisode, true)

            it.autoBackup = getPref(AppSPrefs.prefAutoBackup, false)
            it.autoBackupIntervall = getPref(AppSPrefs.prefAutoBackupIntervall, 24)
            it.autoBackupFolder = getPref(AppSPrefs.prefAutoBackupFolder, "")
            it.autoBackupLimit = getPref(AppSPrefs.prefAutoBackupLimit, 2)
            it.autoBackupTimeStamp = getPref(AppSPrefs.prefAutoBackupTimeStamp, 0L)

            it.useCustomMediaFolder = getPref(AppSPrefs.prefUseCustomMediaFolder, false)
            it.customMediaUri = getPref(AppSPrefs.prefCustomMediaUri, "")

            it.autoDelete = getPref(AppSPrefs.prefAutoDelete, false)
            it.autoDeleteLocal = getPref(AppSPrefs.prefAutoDeleteLocal, false)
            it.playbackSpeedArray = getPrefOrNull<String>(AppSPrefs.prefPlaybackSpeedArray, null)
            it.fallbackSpeed = getPref(AppSPrefs.prefFallbackSpeed, "0.00")
            it.streamOverDownload = getPref(AppSPrefs.prefStreamOverDownload, false)
            it.lowQualityOnMobile = getPref(AppSPrefs.prefLowQualityOnMobile, false)
            it.speedforwardSpeed = getPref(AppSPrefs.prefSpeedforwardSpeed, "0.00")
            it.skipforwardSpeed = getPref(AppSPrefs.prefSkipforwardSpeed, "0.00")
            it.useAdaptiveProgressUpdate = getPref(AppSPrefs.prefUseAdaptiveProgressUpdate, true)

            // Network
            it.enqueueDownloaded = getPref(AppSPrefs.prefEnqueueDownloaded, true)

            it.disableWifiLock = getPref(AppSPrefs.prefDisableWifiLock, false)

            it.autoUpdateInterval = getPref(AppSPrefs.prefAutoUpdateIntervalMinutes, "360").toInt()

            it.mobileUpdateTypes = getPref(AppSPrefs.prefMobileUpdateTypes, hashSetOf("images")).toRealmSet()
            it.episodeCleanup = getPref(AppSPrefs.prefEpisodeCleanup, EpisodeCleanupOptions.Never.num.toString())
            it.episodeCacheSize = getPref(AppSPrefs.prefEpisodeCacheSize, "25").toInt()
            it.enableAutoDl = getPref(AppSPrefs.prefEnableAutoDl, false)
            it.enableAutoDownloadOnBattery = getPref(AppSPrefs.prefEnableAutoDownloadOnBattery, false)

            it.proxyType = getPref(AppSPrefs.prefProxyType, Proxy.Type.DIRECT.name)
            it.proxyHost = getPrefOrNull<String>(AppSPrefs.prefProxyHost, null)
            it.proxyPort = getPref(AppSPrefs.prefProxyPort, 0)
            it.proxyUser = getPrefOrNull<String>(AppSPrefs.prefProxyUser, null)
            it.proxyPassword = getPrefOrNull<String>(AppSPrefs.prefProxyPassword, null)

            // Services
            it.gpodnet_notifications = getPref(AppSPrefs.pref_gpodnet_notifications, true)
            it.nextcloud_server_address = getPref(AppSPrefs.pref_nextcloud_server_address, "")

            // Other
            it.deleteRemovesFromQueue = getPref(AppSPrefs.prefDeleteRemovesFromQueue, true)

            // Mediaplayer
            it.playbackSpeed = getPref(AppSPrefs.prefPlaybackSpeed, "1.00")
            it.skipSilence = getPref(AppSPrefs.prefSkipSilence, false)
            it.fastForwardSecs = getPref(AppSPrefs.prefFastForwardSecs, 30)
            it.rewindSecs = getPref(AppSPrefs.prefRewindSecs, 10)
            it.streamingCacheSizeMB = getPref(AppSPrefs.prefStreamingCacheSizeMB, 100)
            it.videoPlaybackMode = getPref(AppSPrefs.prefVideoPlaybackMode, "1").toInt()

            it.migrationDone = true
        }
    }
}

