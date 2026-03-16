package ac.mdiq.podcini.net.sync.transceive

import ac.mdiq.podcini.net.utils.NetworkUtils.getLocalIpAddress
import ac.mdiq.podcini.storage.database.allFeeds
import ac.mdiq.podcini.storage.database.appAttribs
import ac.mdiq.podcini.storage.database.createSynthetic
import ac.mdiq.podcini.storage.database.getEpisodes
import ac.mdiq.podcini.storage.database.getFeed
import ac.mdiq.podcini.storage.database.runOnIOScope
import ac.mdiq.podcini.storage.database.upsert
import ac.mdiq.podcini.storage.database.upsertBlk
import ac.mdiq.podcini.storage.model.CATALOG_VOLUME_ID_START
import ac.mdiq.podcini.storage.model.Episode
import ac.mdiq.podcini.storage.model.EpisodeDTO
import ac.mdiq.podcini.storage.model.FeedDTO
import ac.mdiq.podcini.storage.model.Volume
import ac.mdiq.podcini.storage.model.toBasicDTO
import ac.mdiq.podcini.storage.model.toDTO
import ac.mdiq.podcini.storage.model.toRealm
import ac.mdiq.podcini.storage.model.volumes
import ac.mdiq.podcini.storage.utils.nowInMillis
import ac.mdiq.podcini.utils.Logd
import ac.mdiq.podcini.utils.Loge
import ac.mdiq.podcini.utils.Logt
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.BoundDatagramSocket
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.ServerSocket
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.isClosed
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.writeText
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readInt
import io.ktor.utils.io.writeByteArray
import io.ktor.utils.io.writeInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val TAG = "Transceiver"
val socketSelector = SelectorManager(Dispatchers.IO)

enum class ContentType { Feed, Catalog, Episodes }

abstract class Receiver(private val port: Int) {
    var serverSocket: ServerSocket? = null

    open suspend fun start() {
        try {
            serverSocket = aSocket(socketSelector).tcp().bind("0.0.0.0", port)
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
data class FeedPackage(
    val feed: FeedDTO,
    val episodes: List<EpisodeDTO>
)

class FeedReceiver(port: Int): Receiver(port) {
    override suspend fun start() {
        super.start()

        while (true) {
            try {
                if (serverSocket?.isClosed == true) break
                val clientSocket = serverSocket?.accept() ?: break

                Logd(TAG, "Client connected: ${clientSocket.remoteAddress}")

                try {
                    val input = clientSocket.openReadChannel()
                    val size = input.readInt()
                    val bytes = ByteArray(size)
                    input.readFully(bytes)

                    val json = bytes.decodeToString()
                    val pkg = Json.decodeFromString<FeedPackage>(json)

                    Logd(TAG, "Received ${pkg.feed.eigenTitle} with ${pkg.episodes.size} episodes")

                    val f = pkg.feed.toRealm()
                    upsertBlk(f) { it.freezeFeed(false) }

                    Logd(TAG, "Saved feed: ${f.title}")
                    pkg.episodes.forEach {
                        val e = it.toRealm()
                        Logd(TAG, "Saved episode: ${e.title}")
                    }
                    Logt(TAG, "Received ${pkg.feed.eigenTitle} with ${pkg.episodes.size} episodes")
                } catch (e: Exception) { Logt(TAG, "Receiving feed terminated: ${e.message}")
                } finally { clientSocket.close() }
            } catch (e: Throwable) { }
        }
    }
}

fun sendFeed(host: String, port: Int, feedId: Long, onEnd: ()->Unit): Job? {
    Logd(TAG, "sendFeed host: $host port: $port")
    val feed = getFeed(feedId) ?: return null
    val feedDTO = feed.toDTO()
    val episodesDTO = getEpisodes(null, null, feedId = feedId, copy = false).map { it.toDTO() }
    val pkg = FeedPackage(feedDTO, episodesDTO)
    Logd(TAG, "built package: feed: ${pkg.feed.eigenTitle} ${pkg.episodes.size} episodes")

    var socket: Socket? = null
    return runOnIOScope {
        try {
            socket = aSocket(socketSelector).tcp().connect(host, port)
            Logd(TAG, "got socket")

            val json = Json.encodeToString(pkg)
            Logd(TAG, "built json")
            val bytes = json.toByteArray()
            Logd(TAG, "(${bytes.size} bytes)")

            val output = socket.openWriteChannel()
            output.writeInt(bytes.size)
            output.writeByteArray(bytes)
            output.flush()

            upsert(feed) { it.freezeFeed(true) }
            Logt(TAG, "Sent feed: ${pkg.feed.eigenTitle} ${pkg.episodes.size} episodes (${bytes.size} bytes)")
        } catch (e: Throwable) { Logt(TAG, "Sending feed terminated: ${e.message}")
        } finally {
            socket?.close()
            onEnd()
        }
    }
}

@Serializable
data class EpisodesPackage(
    val syntheticName: String,
    val episodes: List<EpisodeDTO>
)

class EpisodesReceiver(port: Int, val onEnd: ()->Unit): Receiver(port) {
    override suspend fun start() {
        super.start()

        while (true) {
            try {
                if (serverSocket?.isClosed == true) break
                val clientSocket = serverSocket?.accept() ?: break

                Logd(TAG, "Client connected: ${clientSocket.remoteAddress}")

                try {
                    val input = clientSocket.openReadChannel()
                    val size = input.readInt()
                    val bytes = ByteArray(size)
                    input.readFully(bytes)

                    val json = bytes.decodeToString()
                    val pkg = Json.decodeFromString<EpisodesPackage>(json)

                    Logd(TAG, "Received ${pkg.episodes.size} episodes for ${pkg.syntheticName}")

                    val f = allFeeds.find { it.eigenTitle == pkg.syntheticName } ?: run {
                        val f_ = createSynthetic(0, pkg.syntheticName)
                        upsertBlk(f_) {}
                    }

                    Logd(TAG, "Saved feed: ${f.title}")
                    pkg.episodes.forEach {
                        val e = it.toRealm()
                        upsertBlk(e) { e_ -> e_.feedId = f.id}
                        Logd(TAG, "Saved episode: ${e.title}")
                    }
                    Logt(TAG, "Received ${pkg.episodes.size} episodes for ${pkg.syntheticName}")
                } catch (e: Exception) { Logt(TAG, "Receiving feed terminated: ${e.message}")
                } finally {
                    clientSocket.close()
                    onEnd()
                }
            } catch (e: Throwable) { onEnd() }
        }
    }
}

fun sendEpisodes(host: String, port: Int, syntheticName: String, episodes: List<Episode>, onEnd: ()->Unit): Job? {
    val episodesDTO = episodes.map { it.toBasicDTO() }
    val pkg = EpisodesPackage(syntheticName, episodesDTO)
    Logd(TAG, "built package: feed: $syntheticName ${pkg.episodes.size} episodes")

    var socket: Socket? = null
    return runOnIOScope {
        try {
            socket = aSocket(socketSelector).tcp().connect(host, port)
            Logd(TAG, "got socket")

            val json = Json.encodeToString(pkg)
            Logd(TAG, "built json")
            val bytes = json.toByteArray()
            Logd(TAG, "(${bytes.size} bytes)")

            val output = socket.openWriteChannel()
            output.writeInt(bytes.size)
            output.writeByteArray(bytes)
            output.flush()

            Logt(TAG, "Sent feed: $syntheticName ${pkg.episodes.size} episodes (${bytes.size} bytes)")
        } catch (e: Throwable) { Logt(TAG, "Sending feed terminated: ${e.message}")
        } finally {
            socket?.close()
            onEnd()
        }
    }
}

@Serializable
data class CatalogPackage(
    val senderName: String,
    val senderUID: String,
    val feedDTOs: List<FeedDTO>
)

class CatalogReceiver(port: Int, val onEnd: ()->Unit): Receiver(port) {
    override suspend fun start() {
        super.start()

        while (true) {
            try {
                if (serverSocket?.isClosed == true) break
                val clientSocket = serverSocket?.accept() ?: break
                Logd(TAG, "Client connected: ${clientSocket.remoteAddress}")

                try {
                    val input = clientSocket.openReadChannel()
                    val size = input.readInt()
                    val bytes = ByteArray(size)
                    input.readFully(bytes)

                    val json = bytes.decodeToString()
                    val pkg = Json.decodeFromString<CatalogPackage>(json)

                    var v = volumes.find { it.originId == pkg.senderUID }
                    if (v == null) {
                        v = Volume()
                        var id = CATALOG_VOLUME_ID_START-1
                        while (true) {
                            val v0 = volumes.find { it.id == id }
                            if (v0 == null) break
                            id--
                        }
                        v.id = id
                        v.name = pkg.senderName
                        v.originId = pkg.senderUID
                        v.parentId = CATALOG_VOLUME_ID_START
                        upsertBlk(v) {}
                    }

                    Logd(TAG, "CatalogReceiver Received from ${pkg.senderName} ${pkg.feedDTOs.size} feeds")
                    for (fdto in pkg.feedDTOs) {
                        val f = fdto.toRealm()
                        Logd(TAG, "CatalogReceiver got feed ${f.title}")
                        upsertBlk(f) {
                            it.volumeId = v.id
                            it.keepUpdated = false
                        }
                        Logd(TAG, "CatalogReceiver set feed to Volume ${v.name} ${f.title}")
                    }
                    Logt(TAG, "CatalogReceiver Received from ${pkg.senderName} ${pkg.feedDTOs.size} feeds in catalog")
                } catch (e: Exception) { Logt(TAG, "Receiving catalog terminated: ${e.message}")
                } finally {
                    clientSocket.close()
                    onEnd()
                }
            } catch (e: Throwable) { onEnd() }
        }
    }
}

fun sendCatalog(host: String, port: Int, onEnd: ()->Unit): Job {
    val feedsDTO = mutableListOf<FeedDTO>()
    for (f in allFeeds) {
        if (!f.inNormalVolume || f.isSynthetic() || f.isLocalFeed) continue
        feedsDTO.add(f.toDTO())
    }
    val pack = CatalogPackage(appAttribs.name, appAttribs.uniqueId, feedsDTO)
    Logd(TAG, "sendCatalog built package: ${feedsDTO.size} feeds")

    var socket: Socket? = null
    return runOnIOScope {
        try {
            socket = aSocket(socketSelector).tcp().connect(host, port)
            Logd(TAG, "sendCatalog got socket")

            val json = Json.encodeToString(pack)
            Logd(TAG, "sendCatalog built json")
            val bytes = json.toByteArray()
            Logd(TAG, "sendCatalog (${bytes.size} bytes)")

            val output = socket.openWriteChannel()
            output.writeInt(bytes.size)
            output.writeByteArray(bytes)
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
    val socket = aSocket(socketSelector).udp().bind { broadcast = true }

    fun computeBroadcast(ip: String, prefixLength: Int): String {
        val ipParts = ip.split(".").map { it.toInt() }
        val mask = IntArray(4)
        for (i in 0 until 4) {
            val remaining = prefixLength - i * 8
            mask[i] = when {
                remaining >= 8 -> 0xFF
                remaining <= 0 -> 0
                else -> (0xFF shl (8 - remaining)) and 0xFF
            }
        }
        val broadcast = IntArray(4)
        for (i in 0 until 4) broadcast[i] = ipParts[i] or (mask[i].inv() and 0xFF)
        return broadcast.joinToString(".")
    }

    val myIp = getLocalIpAddress() ?: return@withContext
    val mask = computeBroadcast(myIp, 24)

    val message = "PodciniReceiver:$myIp:$tcpPort:${appAttribs.name}:${appAttribs.uniqueId}"

    try {
        while (isActive) {
            listOf("255.255.255.255", mask).forEach { socket.send(Datagram(buildPacket { writeText(message) }, InetSocketAddress(it, udpPort))) }
            Logd(TAG, "broadcastPresence send to udp port: $udpPort $message")
            delay(2000)
        }
    } catch (e: CancellationException) { Logd(TAG, "listener socket is canceled")
    } catch (e: Exception) { Loge(TAG, "broadcastPresence error: ${e.message}")
    } finally { socket.close() }
}

data class DiscoveredReceiver(
    val ip: String,
    val port: Int,
    val name: String,
    val uid: String,
    val lastSeen: Long = nowInMillis()
)

suspend fun listenForUDPBroadcasts(udpPort: Int, onReceiversUpdated: (List<DiscoveredReceiver>) -> Unit) = withContext(Dispatchers.IO) {
    var socket:  BoundDatagramSocket? = null
    val receivers = mutableMapOf<String, DiscoveredReceiver>()
    try {
        socket = aSocket(socketSelector).udp().bind(InetSocketAddress("0.0.0.0", udpPort))
        Logd(TAG, "listenForBroadcasts 1 udpPort: $udpPort")

        while (isActive) {
            try {
                val datagram = withTimeoutOrNull(10_000) { socket.receive() } ?: continue
                val message = datagram.packet.readString()

                if (message.isBlank()) continue
                Logd(TAG, "listenForBroadcasts 5 message: $message")
                if (!message.startsWith("PodciniReceiver:")) continue

                val parts = message.trim().split(":")
                if (parts.size < 5) {
                    Loge(TAG, "listenForBroadcasts Invalid message format: $message")
                    continue
                }

                val ip = parts[1]
                val port = parts[2].toIntOrNull()
                val name = parts[3]
                val uid = parts[4]
                Logd(TAG, "listenForBroadcasts 9")

                if (port == null) {
                    Loge(TAG, "listenForBroadcasts Invalid port in message: $message")
                    continue
                }
                Logd(TAG, "listenForBroadcasts 10")

                val key = "$ip:$port"
                receivers[key] = DiscoveredReceiver(ip, port, name, uid)

                withContext(Dispatchers.Main) { onReceiversUpdated(receivers.values.toList()) }
            } catch (e: SocketTimeoutException) {
                Logt(TAG, "listenForBroadcasts socket receive time out: is the receiver started")
                val cutoff = nowInMillis() - 10_000
                receivers.entries.removeAll { it.value.lastSeen < cutoff }
                withContext(Dispatchers.Main) { onReceiversUpdated(receivers.values.toList()) }
            } catch (e: CancellationException) { Logd(TAG, "listener socket is canceled")
            } catch (e: Throwable) {
                Loge(TAG, "listenForBroadcasts socket exception: ${e.message}")
                break
            }
        }
    } catch (e: Exception) { Loge(TAG, "listenForBroadcasts error: ${e.message}")
    } finally { socket?.close() }
}

