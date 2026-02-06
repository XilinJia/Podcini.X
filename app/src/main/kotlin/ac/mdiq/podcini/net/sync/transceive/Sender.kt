package ac.mdiq.podcini.net.sync.transceive

import ac.mdiq.podcini.storage.database.getEpisodes
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.model.ComboPackage
import ac.mdiq.podcini.storage.model.EpisodeDTO
import ac.mdiq.podcini.storage.model.toDTO
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import java.io.DataOutputStream
import java.net.Socket

private const val TAG = "Sender"

class Sender(private val host: String, private val port: Int) {

    fun sendFeed(feedId: Long, onEnd: ()->Unit): Job? {
        val feed = getFeed(feedId) ?: return null
        val feedDTO = feed.toDTO()
        val episodesDTO = getEpisodes(null, null, feedId = feedId).map { it.toDTO() }
        val combo = ComboPackage(feedDTO, episodesDTO)
        Logd(TAG, "build package: feed: ${combo.feed.eigenTitle} ${combo.episodes.size} episodes")

        var socket: Socket? = null
        return runOnIOScope {
            try {
                socket = Socket(host, port)
                Logd(TAG, "got socket")

                val json = Json.encodeToString(combo)
                Logd(TAG, "built json")
                val bytes = json.toByteArray()
                Logd(TAG, "(${bytes.size} bytes)")

                val output = DataOutputStream(socket.getOutputStream())
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()

                upsert(feed) {  it.freezeFeed(true) }
                Logt(TAG, "Sent feed: ${combo.feed.eigenTitle} ${combo.episodes.size} episodes (${bytes.size} bytes)")
            } catch (e: Throwable) { Loge(TAG, "Error sending feed: ${e.message}")
            } finally {
                socket?.close()
                onEnd()
            }
        }
    }

    fun sendUsersInBatches(users: List<EpisodeDTO>, socket: Socket, batchSize: Int = 100) {
        val output = DataOutputStream(socket.getOutputStream())

        // Send total count first
        output.writeInt(users.size)

        // Send in chunks
        users.chunked(batchSize).forEach { batch ->
            val json = Json.encodeToString(batch)
            val bytes = json.toByteArray()

            output.writeInt(bytes.size)  // Batch size
            output.write(bytes)
            output.flush()
        }
    }
}
