package ac.mdiq.podcini.net.download

import ac.mdiq.podcini.net.download.Downloader.Companion.downloadStates
import ac.mdiq.podcini.net.sync.SynchronizationSettings.isProviderConnected
import ac.mdiq.podcini.net.sync.model.EpisodeAction
import ac.mdiq.podcini.net.sync.queue.SynchronizationQueueSink
import ac.mdiq.podcini.storage.database.realm
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.utils.fetchChapters
import ac.mdiq.podcini.storage.utils.toUF
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import kotlinx.coroutines.runBlocking

abstract class EpisodeDLManager {
    private var currentDownloads: Map<String, DownloadStatus> = mutableMapOf()

    fun setCurrentDownloads(currentDownloads: Map<String, DownloadStatus>) {
        this.currentDownloads = currentDownloads
    }

    fun isDownloading(url: String): Boolean {
        return (currentDownloads.containsKey(url) && currentDownloads[url]!!.state < DownloadStatus.State.COMPLETED.ordinal)
    }

    abstract fun downloadNow(episodes: List<Episode>, ignoreConstraints: Boolean)

    abstract fun download(episodes: List<Episode>)

    abstract suspend fun cancel(media: Episode)

    abstract fun cancelAll()

    companion object {
        const val TAG = "EpisodeDLManager"

        suspend fun updateDB(request: DownloadRequest) {
            downloadStates[request.source!!] = DownloadStatus(DownloadStatus.State.COMPLETED.ordinal, -1)
            var item = realm.query(Episode::class).query("id == ${request.feedfileId}").first().find()
            if (item == null) {
                Loge(TAG, "Could not find downloaded episode object in database")
                return
            }
            //                val broadcastUnreadStateUpdate = item.isNew
            item = upsert(item) {
                it.downloaded = true
                it.fileUrl = request.destination
                Logd(TAG, "run() set request.destination: ${request.destination}")
                if (!request.destination.isNullOrBlank()) {
                    val file = request.destination.toUF()
                    runBlocking { it.size = if (file.exists()) file.size()?: 0 else 0 }
                    Logd(TAG, "run() set size: ${it.size}")
                }
                if (!it.chaptersLoaded) runBlocking { try { it.setChapters(fetchChapters(it)) } catch (e: Exception) { Logs(TAG, e, "Get chapters failed for ${it.title}") } }

                // TODO: this seems not really needed
//                if (!it.fileUrl.isNullOrBlank() && it.size > 0) {
//                    Logd(TAG, "run() it.fileUrl: ${it.fileUrl}")
//                    try {
//                        MediaMetadataRetrieverCompat().use { mmr ->
//                            mmr.setDataSource(it.fileUrl!!)
//                            val image = mmr.embeddedPicture
//                            it.hasEmbeddedPicture = image != null
//                            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull()
//                            if (duration != null) it.duration = duration
//                            val mimeType = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
//                            if (mimeType != null) it.mimeType = mimeType
//                        }
//                    } catch (e: Exception) {
//                        Logs(TAG, e, "Get duration failed. current duration is ${it.duration}. If lower than 30sc or higher than 5h, reset to 30sc")
//                        if (it.duration !in 30000..18000000) it.duration = 30000
//                        it.hasEmbeddedPicture = false
//                    }
//                } else Loge(TAG, "Get metadata failed for ${it.title}: fileUrl: ${it.fileUrl}")
//                Logd(TAG, "run() set duration: ${it.duration}")
                it.isAutoDownloadEnabled = false
            }
            // TODO: need to post two events?
            //                if (broadcastUnreadStateUpdate) EventFlow.postEvent(FlowEvent.EpisodeMediaEvent.updated(item))
            if (isProviderConnected) {
                Logd(TAG, "enqueue synch")
                val action = EpisodeAction.Builder(item, EpisodeAction.DOWNLOAD).currentTimestamp().build()
                SynchronizationQueueSink.enqueueEpisodeActionIfSyncActive(action)
            }
            //                Logd(TAG, "episode.isNew: ${item.isNew} ${item.playState}")
        }

    }
}