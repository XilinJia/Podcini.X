package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.R
import ac.mdiq.podcini.config.ClientConfig
import ac.mdiq.podcini.preferences.AppPreferences.AppPrefs
import ac.mdiq.podcini.preferences.AppPreferences.getPref
import ac.mdiq.podcini.storage.model.DownloadResult
import android.content.Context
import android.net.wifi.WifiManager
import java.util.Date

abstract class Downloader(val downloadRequest: DownloadRequest) {
    @Volatile
    var isFinished: Boolean = false
        private set

    
    @Volatile
    var cancelled: Boolean
    
    var permanentRedirectUrl: String? = null

    
    val result: DownloadResult

    init {
        this.downloadRequest.setStatusMsg(R.string.download_pending)
        this.cancelled = false
        this.result = DownloadResult(this.downloadRequest.title?:"", this.downloadRequest.feedfileId, this.downloadRequest.feedfileType, false, null, Date(), "")
    }

    protected abstract fun download()

//    suspend fun run(): Downloader {
//        download()
//        isFinished = true
//        return this
//    }

    suspend fun run(): Downloader {
        var wifiLock: WifiManager.WifiLock? = null
        if (getPref(AppPrefs.prefDisableWifiLock, false)) {
            val wifiManager = ClientConfig.applicationCallbacks?.getApplicationInstance()?.applicationContext?.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiManager != null) {
                wifiLock = wifiManager.createWifiLock(TAG)
                wifiLock.acquire()
            }
        }
        download()
        wifiLock?.release()
        isFinished = true
        return this
    }

    fun cancel() {
        cancelled = true
    }

    companion object {
        private val TAG: String = Downloader::class.simpleName ?: "Anonymous"
    }
}