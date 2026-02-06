package ac.mdiq.podcini.net.sync.transceive

import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.ComboPackage
import ac.mdiq.podcini.storage.model.EpisodeDTO
import ac.mdiq.podcini.storage.model.toRealm
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "Receiver"

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
            } catch (e: Exception) { Loge(TAG, "Error receiving feed: ${e.message}")
            } finally { clientSocket.close() }
        }
    }

    fun receiveFeed(socket: Socket): ComboPackage {
        val input = DataInputStream(socket.getInputStream())
        val size = input.readInt()
        val bytes = ByteArray(size)
        input.readFully(bytes)

        val json = bytes.decodeToString()
        return Json.decodeFromString<ComboPackage>(json)
    }

    fun receiveUsersInBatches(socket: Socket): List<EpisodeDTO> {
        val input = DataInputStream(socket.getInputStream())

        val totalCount = input.readInt()
        val allUsers = mutableListOf<EpisodeDTO>()

        while (allUsers.size < totalCount) {
            val batchSize = input.readInt()
            val bytes = ByteArray(batchSize)
            input.readFully(bytes)

            val batch = Json.decodeFromString<List<EpisodeDTO>>(bytes.decodeToString())
            allUsers.addAll(batch)

            println("Received ${allUsers.size}/$totalCount users")
        }

        return allUsers
    }
}
