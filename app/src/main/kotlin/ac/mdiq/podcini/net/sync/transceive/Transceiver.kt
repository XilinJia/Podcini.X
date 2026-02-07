package ac.mdiq.podcini.net.sync.transceive

import ac.mdiq.podcini.storage.database.getEpisodes
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.ComboPackage
import ac.mdiq.podcini.storage.model.FROZEN_VOLUME_ID
import ac.mdiq.podcini.storage.model.toDTO
import ac.mdiq.podcini.storage.model.toRealm
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "Transceiver"

class Receiver(private val port: Int) {

    fun start() {
        val serverSocket = ServerSocket(port)
        Logd(TAG, "Server listening on port $port")

        while (true) {
            val clientSocket = serverSocket.accept()
            Logd(TAG, "Client connected: ${clientSocket.inetAddress}")

            try {
                val combo = receiveFeed(clientSocket)
                Logd(TAG, "Received ${combo.feed.eigenTitle} with ${combo.episodes.size} episodes")

                val f = combo.feed.toRealm()
                upsertBlk(f) { it.freezeFeed(false) }

                Logd(TAG, "Saved feed: ${f.title}")
                combo.episodes.forEach {
                    val e = it.toRealm()
                    Logd(TAG, "Saved episode: ${e.title}")
                }
                Logt(TAG, "Received ${combo.feed.eigenTitle} with ${combo.episodes.size} episodes")
            } catch (e: Exception) { Loge(TAG, "Error receiving feed: ${e.message}")
            } finally { clientSocket.close() }
        }
    }

    private fun receiveFeed(socket: Socket): ComboPackage {
        val input = DataInputStream(socket.getInputStream())
        val size = input.readInt()
        val bytes = ByteArray(size)
        input.readFully(bytes)

        val json = bytes.decodeToString()
        return Json.decodeFromString<ComboPackage>(json)
    }
}

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

                upsert(feed) { it.freezeFeed(true) }
                Logt(TAG, "Sent feed: ${combo.feed.eigenTitle} ${combo.episodes.size} episodes (${bytes.size} bytes)")
            } catch (e: Throwable) { Loge(TAG, "Error sending feed: ${e.message}")
            } finally {
                socket?.close()
                onEnd()
            }
        }
    }
}
