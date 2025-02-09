package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.util.Loge
import android.webkit.URLUtil

interface DownloaderFactory {
    fun create(request: DownloadRequest): Downloader?
}

class DefaultDownloaderFactory : DownloaderFactory {
    override fun create(request: DownloadRequest): Downloader? {
        if (!URLUtil.isHttpUrl(request.source) && !URLUtil.isHttpsUrl(request.source)) {
            Loge(TAG, "Could not find appropriate downloader for " + request.source)
            return null
        }
        return HttpDownloader(request)
    }

    companion object {
        private val TAG: String = DefaultDownloaderFactory::class.simpleName ?: "Anonymous"
    }
}