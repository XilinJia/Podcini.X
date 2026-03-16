package ac.mdiq.podcini.storage.model

import ac.mdiq.podcini.ui.screens.DefaultPages
import ac.mdiq.podcini.ui.screens.prefscreens.EpisodeCleanupOptions
import android.view.KeyEvent
import io.github.xilinjia.krdb.ext.realmSetOf
import io.github.xilinjia.krdb.types.RealmObject
import io.github.xilinjia.krdb.types.RealmSet
import io.github.xilinjia.krdb.types.annotations.PrimaryKey
import java.net.Proxy

class AppPrefs: RealmObject {
    @PrimaryKey
    var id: Long = 0L
    var lastVersion: String = "0"

    var OPMLRestored: Boolean = false

    var OPMLFeedsToRestore: Int = 0

    var OPMLBackup: Boolean = true

    // User Interface
    var theme: String = ("system")
    var themeBlack: Boolean = false

    var useDynamicThemes: Boolean = false
    var tintedColors: Boolean = false
    var useEpisodeCover: Boolean = false
    var showSkip: Boolean = true
    var showDownloadReport: Boolean = true

    var defaultPage: String = DefaultPages.Library.name

    var backButtonOpensDrawer: Boolean = false
    var showErrorToasts: Boolean = true
    var printDebugLogs: Boolean = false

    var dont_ask_again_unrestricted_background: Boolean = false

    // Playback
    var pauseOnHeadsetDisconnect: Boolean = true
    var unpauseOnHeadsetReconnect: Boolean = true
    var unpauseOnBluetoothReconnect: Boolean = false
    var hardwareForwardButton: String = (KeyEvent.KEYCODE_MEDIA_FAST_FORWARD.toString())
    var hardwarePreviousButton: String = (KeyEvent.KEYCODE_MEDIA_REWIND.toString())
    var skipKeepsEpisode: Boolean = true
    var removeFromQueueMarkPlayed: Boolean = true
    var favoriteKeepsEpisode: Boolean = true

    var autoBackup: Boolean = false
    var autoBackupIntervall: Int = (24)
    var autoBackupFolder: String? = null
    var autoBackupLimit: Int = (2)
    var autoBackupTimeStamp: Long = (0L)

    var useCustomMediaFolder: Boolean = false
    var customMediaUri: String = ""

    var customFolderUnavailable: Boolean = false

    var autoDelete: Boolean = false
    var autoDeleteLocal: Boolean = false
    var playbackSpeedArray: String? = null
    var fallbackSpeed: String = ("0.00")
    var streamOverDownload: Boolean = false
    var lowQualityOnMobile: Boolean = false
    var speedforwardSpeed: String = ("0.00")
    var skipforwardSpeed: String = ("0.00")
    var useAdaptiveProgressUpdate: Boolean = true

    // Network
    var enqueueDownloaded: Boolean = true

    var disableWifiLock: Boolean = false

    var checkAvailableSpace: Boolean = false

    var autoUpdateInterval: Int = 360       // in minutes

    var mobileUpdateTypes: RealmSet<String> = realmSetOf("images")

    var episodeCleanup: String = (EpisodeCleanupOptions.Never.num.toString())
    var episodeCacheSize: Int = 25
    var enableAutoDl: Boolean = false
    var enableAutoDownloadOnBattery: Boolean = false

    var proxyType: String = (Proxy.Type.DIRECT.name)
    var proxyHost: String? = null
    var proxyPort: Int = (0)
    var proxyUser: String? = null
    var proxyPassword: String? = null

    // Services
    var gpodnet_notifications: Boolean = true
    var nextcloud_server_address: String = ("")

    // Other
    var deleteRemovesFromQueue: Boolean = true

    // Mediaplayer
    var playbackSpeed: String = ("1.00")
    var skipSilence: Boolean = false
    var fastForwardSecs: Int = (30)
    var rewindSecs: Int = (10)
    var streamingCacheSizeMB: Int = (100)
    var videoPlaybackMode: Int = (1)

    var recaptcha_cookies: String = ""
    var content_country: String? = null

    var restrictedModeEnabled: Boolean = false

//    =====================
    var migrationDone: Boolean = false
    var sharedPrefsDeleted: Boolean = false

    var clipsMoved: Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AppPrefs

        if (id != other.id) return false
        if (OPMLRestored != other.OPMLRestored) return false
        if (OPMLFeedsToRestore != other.OPMLFeedsToRestore) return false
        if (OPMLBackup != other.OPMLBackup) return false
        if (themeBlack != other.themeBlack) return false
        if (useDynamicThemes != other.useDynamicThemes) return false
        if (tintedColors != other.tintedColors) return false
        if (useEpisodeCover != other.useEpisodeCover) return false
        if (showSkip != other.showSkip) return false
        if (showDownloadReport != other.showDownloadReport) return false
        if (backButtonOpensDrawer != other.backButtonOpensDrawer) return false
        if (showErrorToasts != other.showErrorToasts) return false
        if (printDebugLogs != other.printDebugLogs) return false
        if (dont_ask_again_unrestricted_background != other.dont_ask_again_unrestricted_background) return false
        if (pauseOnHeadsetDisconnect != other.pauseOnHeadsetDisconnect) return false
        if (unpauseOnHeadsetReconnect != other.unpauseOnHeadsetReconnect) return false
        if (unpauseOnBluetoothReconnect != other.unpauseOnBluetoothReconnect) return false
        if (skipKeepsEpisode != other.skipKeepsEpisode) return false
        if (removeFromQueueMarkPlayed != other.removeFromQueueMarkPlayed) return false
        if (favoriteKeepsEpisode != other.favoriteKeepsEpisode) return false
        if (autoBackup != other.autoBackup) return false
        if (autoBackupIntervall != other.autoBackupIntervall) return false
        if (autoBackupLimit != other.autoBackupLimit) return false
        if (autoBackupTimeStamp != other.autoBackupTimeStamp) return false
        if (useCustomMediaFolder != other.useCustomMediaFolder) return false
        if (customFolderUnavailable != other.customFolderUnavailable) return false
        if (autoDelete != other.autoDelete) return false
        if (autoDeleteLocal != other.autoDeleteLocal) return false
        if (streamOverDownload != other.streamOverDownload) return false
        if (lowQualityOnMobile != other.lowQualityOnMobile) return false
        if (useAdaptiveProgressUpdate != other.useAdaptiveProgressUpdate) return false
        if (enqueueDownloaded != other.enqueueDownloaded) return false
        if (disableWifiLock != other.disableWifiLock) return false
        if (checkAvailableSpace != other.checkAvailableSpace) return false
        if (autoUpdateInterval != other.autoUpdateInterval) return false
        if (episodeCacheSize != other.episodeCacheSize) return false
        if (enableAutoDl != other.enableAutoDl) return false
        if (enableAutoDownloadOnBattery != other.enableAutoDownloadOnBattery) return false
        if (proxyPort != other.proxyPort) return false
        if (gpodnet_notifications != other.gpodnet_notifications) return false
        if (deleteRemovesFromQueue != other.deleteRemovesFromQueue) return false
        if (skipSilence != other.skipSilence) return false
        if (fastForwardSecs != other.fastForwardSecs) return false
        if (rewindSecs != other.rewindSecs) return false
        if (streamingCacheSizeMB != other.streamingCacheSizeMB) return false
        if (videoPlaybackMode != other.videoPlaybackMode) return false
        if (restrictedModeEnabled != other.restrictedModeEnabled) return false
        if (migrationDone != other.migrationDone) return false
        if (sharedPrefsDeleted != other.sharedPrefsDeleted) return false
        if (clipsMoved != other.clipsMoved) return false
        if (lastVersion != other.lastVersion) return false
        if (theme != other.theme) return false
        if (defaultPage != other.defaultPage) return false
        if (hardwareForwardButton != other.hardwareForwardButton) return false
        if (hardwarePreviousButton != other.hardwarePreviousButton) return false
        if (autoBackupFolder != other.autoBackupFolder) return false
        if (customMediaUri != other.customMediaUri) return false
        if (playbackSpeedArray != other.playbackSpeedArray) return false
        if (fallbackSpeed != other.fallbackSpeed) return false
        if (speedforwardSpeed != other.speedforwardSpeed) return false
        if (skipforwardSpeed != other.skipforwardSpeed) return false
        if (mobileUpdateTypes.size != other.mobileUpdateTypes.size) return false
        if (episodeCleanup != other.episodeCleanup) return false
        if (proxyType != other.proxyType) return false
        if (proxyHost != other.proxyHost) return false
        if (proxyUser != other.proxyUser) return false
        if (proxyPassword != other.proxyPassword) return false
        if (nextcloud_server_address != other.nextcloud_server_address) return false
        if (playbackSpeed != other.playbackSpeed) return false
        if (recaptcha_cookies != other.recaptcha_cookies) return false
        if (content_country != other.content_country) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + OPMLRestored.hashCode()
        result = 31 * result + OPMLFeedsToRestore
        result = 31 * result + OPMLBackup.hashCode()
        result = 31 * result + themeBlack.hashCode()
        result = 31 * result + useDynamicThemes.hashCode()
        result = 31 * result + tintedColors.hashCode()
        result = 31 * result + useEpisodeCover.hashCode()
        result = 31 * result + showSkip.hashCode()
        result = 31 * result + showDownloadReport.hashCode()
        result = 31 * result + backButtonOpensDrawer.hashCode()
        result = 31 * result + showErrorToasts.hashCode()
        result = 31 * result + printDebugLogs.hashCode()
        result = 31 * result + dont_ask_again_unrestricted_background.hashCode()
        result = 31 * result + pauseOnHeadsetDisconnect.hashCode()
        result = 31 * result + unpauseOnHeadsetReconnect.hashCode()
        result = 31 * result + unpauseOnBluetoothReconnect.hashCode()
        result = 31 * result + skipKeepsEpisode.hashCode()
        result = 31 * result + removeFromQueueMarkPlayed.hashCode()
        result = 31 * result + favoriteKeepsEpisode.hashCode()
        result = 31 * result + autoBackup.hashCode()
        result = 31 * result + autoBackupIntervall
        result = 31 * result + autoBackupLimit
        result = 31 * result + autoBackupTimeStamp.hashCode()
        result = 31 * result + useCustomMediaFolder.hashCode()
        result = 31 * result + customFolderUnavailable.hashCode()
        result = 31 * result + autoDelete.hashCode()
        result = 31 * result + autoDeleteLocal.hashCode()
        result = 31 * result + streamOverDownload.hashCode()
        result = 31 * result + lowQualityOnMobile.hashCode()
        result = 31 * result + useAdaptiveProgressUpdate.hashCode()
        result = 31 * result + enqueueDownloaded.hashCode()
        result = 31 * result + disableWifiLock.hashCode()
        result = 31 * result + checkAvailableSpace.hashCode()
        result = 31 * result + autoUpdateInterval
        result = 31 * result + episodeCacheSize
        result = 31 * result + enableAutoDl.hashCode()
        result = 31 * result + enableAutoDownloadOnBattery.hashCode()
        result = 31 * result + proxyPort
        result = 31 * result + gpodnet_notifications.hashCode()
        result = 31 * result + deleteRemovesFromQueue.hashCode()
        result = 31 * result + skipSilence.hashCode()
        result = 31 * result + fastForwardSecs
        result = 31 * result + rewindSecs
        result = 31 * result + streamingCacheSizeMB
        result = 31 * result + videoPlaybackMode
        result = 31 * result + restrictedModeEnabled.hashCode()
        result = 31 * result + migrationDone.hashCode()
        result = 31 * result + sharedPrefsDeleted.hashCode()
        result = 31 * result + clipsMoved.hashCode()
        result = 31 * result + lastVersion.hashCode()
        result = 31 * result + theme.hashCode()
        result = 31 * result + defaultPage.hashCode()
        result = 31 * result + hardwareForwardButton.hashCode()
        result = 31 * result + hardwarePreviousButton.hashCode()
        result = 31 * result + (autoBackupFolder?.hashCode() ?: 0)
        result = 31 * result + customMediaUri.hashCode()
        result = 31 * result + (playbackSpeedArray?.hashCode() ?: 0)
        result = 31 * result + fallbackSpeed.hashCode()
        result = 31 * result + speedforwardSpeed.hashCode()
        result = 31 * result + skipforwardSpeed.hashCode()
        result = 31 * result + mobileUpdateTypes.size
        result = 31 * result + episodeCleanup.hashCode()
        result = 31 * result + proxyType.hashCode()
        result = 31 * result + (proxyHost?.hashCode() ?: 0)
        result = 31 * result + (proxyUser?.hashCode() ?: 0)
        result = 31 * result + (proxyPassword?.hashCode() ?: 0)
        result = 31 * result + nextcloud_server_address.hashCode()
        result = 31 * result + playbackSpeed.hashCode()
        result = 31 * result + recaptcha_cookies.hashCode()
        result = 31 * result + (content_country?.hashCode() ?: 0)
        return result
    }
}