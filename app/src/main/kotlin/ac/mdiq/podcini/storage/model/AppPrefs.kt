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
    var customMediaUri: String = ("")

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


    var migrationDone: Boolean = false
    var sharedPrefsDeleted: Boolean = false
}