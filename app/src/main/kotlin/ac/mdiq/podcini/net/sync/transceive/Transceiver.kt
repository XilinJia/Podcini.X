package ac.mdiq.podcini.net.sync.transceive

import ac.mdiq.podcini.net.utils.NetworkUtils.getLocalIpAddress
import ac.mdiq.podcini.storage.database.allFeeds
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.getEpisodes
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.EpisodeDTO
import ac.mdiq.podcini.storage.model.FeedDTO
import ac.mdiq.podcini.storage.model.Volume
import ac.mdiq.podcini.storage.model.toDTO
import ac.mdiq.podcini.storage.model.toRealm
import ac.mdiq.podcini.storage.model.volumes
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import io.github.xilinjia.krdb.ext.isFrozen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

private const val TAG = "Transceiver"

enum class ContentType { Feed, Catalog }

abstract class Receiver(private val port: Int) {
    var serverSocket: ServerSocket? = null
    open fun start() {
        try {
            serverSocket = ServerSocket(port)
            Logd(TAG, "Server listening on port $port")
        } catch (e: Exception) {
            Loge(TAG, "Error starting server socket: ${e.message}")
            return
        }
    }

    fun stop() {
        serverSocket?.close()
    }
}

@Serializable
data class FeedComboPackage(
    val feed: FeedDTO,
    val episodes: List<EpisodeDTO>
)

class FeedReceiver(port: Int): Receiver(port) {
    override fun start() {
        super.start()

        while (true) {
            try {
                if (serverSocket?.isClosed == true) break
                val clientSocket = serverSocket?.accept() ?: break

                Logd(TAG, "Client connected: ${clientSocket.inetAddress}")

                try {
                    val input = DataInputStream(clientSocket.getInputStream())
                    val size = input.readInt()
                    val bytes = ByteArray(size)
                    input.readFully(bytes)

                    val json = bytes.decodeToString()
                    val combo = Json.decodeFromString<FeedComboPackage>(json)

                    Logd(TAG, "Received ${combo.feed.eigenTitle} with ${combo.episodes.size} episodes")

                    val f = combo.feed.toRealm()
                    upsertBlk(f) { it.freezeFeed(false) }

                    Logd(TAG, "Saved feed: ${f.title}")
                    combo.episodes.forEach {
                        val e = it.toRealm()
                        Logd(TAG, "Saved episode: ${e.title}")
                    }
                    Logt(TAG, "Received ${combo.feed.eigenTitle} with ${combo.episodes.size} episodes")
                } catch (e: Exception) { Logt(TAG, "Receiving feed terminated: ${e.message}")
                } finally { clientSocket.close() }
            } catch (e: Throwable) { }
        }
    }
}

fun sendFeed(host: String, port: Int, feedId: Long, onEnd: ()->Unit): Job? {
    val feed = getFeed(feedId) ?: return null
    val feedDTO = feed.toDTO()
    val episodesDTO = getEpisodes(null, null, feedId = feedId).map { it.toDTO() }
    val combo = FeedComboPackage(feedDTO, episodesDTO)
    Logd(TAG, "built package: feed: ${combo.feed.eigenTitle} ${combo.episodes.size} episodes")

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
        } catch (e: Throwable) { Logt(TAG, "Sending feed terminated: ${e.message}")
        } finally {
            socket?.close()
            onEnd()
        }
    }
}

@Serializable
data class CatalogPackage(
    val identifier: String,
    val feedDTOs: List<FeedDTO>
)

class CatalogReceiver(port: Int): Receiver(port) {
    override fun start() {
        super.start()

        while (true) {
            try {
                if (serverSocket?.isClosed == true) break
                val clientSocket = serverSocket?.accept() ?: break
                Logd(TAG, "Client connected: ${clientSocket.inetAddress}")

                try {
                    val input = DataInputStream(clientSocket.getInputStream())
                    val size = input.readInt()
                    val bytes = ByteArray(size)
                    input.readFully(bytes)

                    val json = bytes.decodeToString()
                    val pack = Json.decodeFromString<CatalogPackage>(json)

                    var v = volumes.find { it.name == pack.identifier }
                    if (v == null) {
                        v = Volume()
                        v.id = System.currentTimeMillis()
                        v.name = pack.identifier
                        v.parentId = -1L
                        upsertBlk(v) {}
                    }

                    Logd(TAG, "CatalogReceiver Received from ${pack.identifier} ${pack.feedDTOs.size} feeds")
                    for (fdto in pack.feedDTOs) {
                        val f = fdto.toRealm()
                        Logd(TAG, "CatalogReceiver got feed ${f.title}")
                        upsertBlk(f) { it.volumeId = v.id }
                        Logd(TAG, "CatalogReceiver set feed to Volume ${v.name} ${f.title}")
                    }
                    Logt(TAG, "CatalogReceiver Received from ${pack.identifier} ${pack.feedDTOs.size} feeds in catalog")
                } catch (e: Exception) { Logt(TAG, "Receiving catalog terminated: ${e.message}")
                } finally { clientSocket.close() }
            } catch (e: Throwable) { }
        }
    }
}

fun sendCatalog(host: String, port: Int, onEnd: ()->Unit): Job {
    val feedsDTO = mutableListOf<FeedDTO>()
    for (f in allFeeds) {
        if (!f.inNormalVolume || f.isSynthetic() || f.isLocalFeed) continue
        feedsDTO.add(f.toDTO())
    }
    val pack = CatalogPackage(appAttribs.name, feedsDTO)
    Logd(TAG, "sendCatalog built package: ${feedsDTO.size} feeds")

    var socket: Socket? = null
    return runOnIOScope {
        try {
            socket = Socket(host, port)
            Logd(TAG, "sendCatalog got socket")

            val json = Json.encodeToString(pack)
            Logd(TAG, "sendCatalog built json")
            val bytes = json.toByteArray()
            Logd(TAG, "sendCatalog (${bytes.size} bytes)")

            val output = DataOutputStream(socket.getOutputStream())
            output.writeInt(bytes.size)
            output.write(bytes)
            output.flush()

            Logt(TAG, "sendCatalog Sent ${feedsDTO.size} feeds (${bytes.size} bytes)")
        } catch (e: Throwable) { Logt(TAG, "Sending catalog terminated: ${e.message}")
        } finally {
            socket?.close()
            onEnd()
        }
    }
}

suspend fun broadcastPresence(udpPort: Int, tcpPort: Int) = withContext(Dispatchers.IO) {
    val socket = DatagramSocket()
    socket.broadcast = true

    val myIp = getLocalIpAddress()
    val message = "PodciniReceiver:$myIp:$tcpPort"

    try {
        while (isActive) {
            val sendData = message.toByteArray()
            val packet = DatagramPacket(sendData, sendData.size, InetAddress.getByName("255.255.255.255"), udpPort)
            socket.send(packet)
            Logd(TAG, "broadcastPresence send to udp port: $udpPort $message")
            delay(2000)
        }
    } catch (e: Exception) { Loge(TAG, "broadcastPresence error: ${e.message}")
    } finally { socket.close() }
}

data class DiscoveredReceiver(
    val ip: String,
    val port: Int,
    val lastSeen: Long = System.currentTimeMillis()
)

suspend fun listenForUDPBroadcasts(udpPort: Int, onReceiversUpdated: (List<DiscoveredReceiver>) -> Unit) = withContext(Dispatchers.IO) {
    var socket: DatagramSocket? = null
    val receivers = mutableMapOf<String, DiscoveredReceiver>()
    try {
        socket = DatagramSocket(udpPort)
        socket.soTimeout = 10000
        Logd(TAG, "listenForBroadcasts 1 udpPort: $udpPort")
        val buffer = ByteArray(1024)

        while (isActive) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                if (packet.length == 0) continue
                val message = String(packet.data, 0, packet.length).trim()
                if (message.isBlank()) continue
                Logd(TAG, "listenForBroadcasts 5 message: $message")
                if (!message.startsWith("PodciniReceiver:")) continue

                val parts = message.split(":")

                if (parts.size != 3) {
                    Loge(TAG, "listenForBroadcasts Invalid message format: $message")
                    continue
                }

                val ip = parts[1]
                val port = parts[2].toIntOrNull()
                Logd(TAG, "listenForBroadcasts 9")

                if (port == null) {
                    Loge(TAG, "listenForBroadcasts Invalid port in message: $message")
                    continue
                }
                Logd(TAG, "listenForBroadcasts 10")

                val key = "$ip:$port"
                receivers[key] = DiscoveredReceiver(ip, port)

                withContext(Dispatchers.Main) { onReceiversUpdated(receivers.values.toList()) }
            } catch (e: SocketTimeoutException) {
                Logt(TAG, "listenForBroadcasts socket receive time out: is the receiver started")
                val cutoff = System.currentTimeMillis() - 10_000
                receivers.entries.removeAll { it.value.lastSeen < cutoff }
                withContext(Dispatchers.Main) { onReceiversUpdated(receivers.values.toList()) }
            } catch (e: Throwable) {
                Loge(TAG, "listenForBroadcasts socket exception: ${e.message}")
                break
            }
        }
    } catch (e: Exception) { Loge(TAG, "listenForBroadcasts error: ${e.message}")
    } finally { socket?.close() }
}

