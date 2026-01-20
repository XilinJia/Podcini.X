package ac.mdiq.podcini.net.download.service

import ac.mdiq.podcini.net.download.DownloadStatus
import ac.mdiq.podcini.storage.model.Episode

abstract class DownloadServiceInterface {
    private var currentDownloads: Map<String, DownloadStatus> = mutableMapOf()

    fun setCurrentDownloads(currentDownloads: Map<String, DownloadStatus>) {
        this.currentDownloads = currentDownloads
    }

    /**
     * Download immediately after user action.
     */
    abstract fun downloadNow(item: Episode, ignoreConstraints: Boolean)

    /**
     * Download when device seems fit.
     */
    abstract fun download(item: Episode)

    abstract fun cancel(media: Episode)

    abstract fun cancelAll()

    fun isDownloadingEpisode(url: String): Boolean {
        return (currentDownloads.containsKey(url) && currentDownloads[url]!!.state < DownloadStatus.State.COMPLETED.ordinal)
    }

//    fun isEpisodeQueued(url: String): Boolean {
//        return (currentDownloads.containsKey(url) && currentDownloads[url]!!.state == DownloadStatus.State.QUEUED.ordinal)
//    }
//
//    fun getProgress(url: String): Int {
//        return if (isDownloadingEpisode(url)) currentDownloads[url]!!.progress else -1
//    }

    companion object {
        const val WORK_TAG: String = "episodeDownload"
        const val WORK_TAG_EPISODE_URL: String = "episodeUrl:"
        const val WORK_DATA_PROGRESS: String = "progress"
        const val WORK_DATA_MEDIA_ID: String = "media_id"
        const val WORK_DATA_WAS_QUEUED: String = "was_queued"

        var impl: DownloadServiceInterface? = null
    }
}
