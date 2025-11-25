package ac.mdiq.podcini.net.download.service


import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.Feed
import ac.mdiq.podcini.storage.utils.feedfilePath
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logs
import java.io.File

/**
 * Creates download requests that can be sent to the DownloadService.
 */
object DownloadRequestCreator {
    private val TAG: String = DownloadRequestCreator::class.simpleName ?: "Anonymous"

    
    fun create(feed: Feed): DownloadRequest.Builder {
        val dest = File(feedfilePath, feed.getFeedfileName())
        if (dest.exists()) dest.delete()
        Logd(TAG, "Requesting download feed from url " + feed.downloadUrl)
        val username = feed.username
        val password = feed.password
        return DownloadRequest.Builder(dest.toString(), feed).withAuthentication(username, password).lastModified(feed.lastUpdate)
    }

    
    fun create(media: Episode): DownloadRequest.Builder {
        Logd(TAG, "create: ${media.fileUrl} ${media.title}")
        val destUriString = try { media.getMediaFileUriString() } catch (e: Throwable ) {
            Logs(TAG, e)
            ""
        }
        Logd(TAG, "create destUriString: $destUriString")
        if (destUriString.isBlank()) Loge(TAG, "destUriString is empty")
        val feed = media.feed
        val username = feed?.username
        val password = feed?.password
        return DownloadRequest.Builder(destUriString, media).withAuthentication(username, password)
    }
}
