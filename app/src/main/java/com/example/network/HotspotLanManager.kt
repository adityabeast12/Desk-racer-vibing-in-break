package com.example.network

import com.example.game.DiscoveredHost
import com.example.game.KartState
import com.example.game.LanRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

data class RemoteMarbleEvent(
    val id: String,
    val ownerId: String,
    val x: Float,
    val z: Float,
    val vx: Float,
    val vz: Float
)

class HotspotLanManager(
    private val onRemoteMarbleFired: (RemoteMarbleEvent) -> Unit
) {
    companion object {
        const val UDP_DISCOVERY_PORT = 8888
        const val TCP_GAME_PORT = 7777
        const val BROADCAST_HEADER = "DESK_RACER_HOST_HERE"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _role = MutableStateFlow(LanRole.STANDALONE)
    val role: StateFlow<LanRole> = _role.asStateFlow()

    private val _statusText = MutableStateFlow("LAN Ready • Zero-Internet Hotspot")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _localIpAddress = MutableStateFlow(detectLocalHotspotOrLanIp())
    val localIpAddress: StateFlow<String> = _localIpAddress.asStateFlow()

    private val _discoveredHosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val discoveredHosts: StateFlow<List<DiscoveredHost>> = _discoveredHosts.asStateFlow()

    private val _remotePlayers = MutableStateFlow<Map<String, KartState>>(emptyMap())
    val remotePlayers: StateFlow<Map<String, KartState>> = _remotePlayers.asStateFlow()

    private var discoveryListenJob: Job? = null
    private var hostBroadcastJob: Job? = null
    private var tcpServerJob: Job? = null
    private var tcpClientJob: Job? = null

    private var udpListenerSocket: DatagramSocket? = null
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var clientWriter: PrintWriter? = null

    private val connectedClientWriters = CopyOnWriteArrayList<PrintWriter>()

    init {
        startUdpDiscoveryListener()
    }

    fun refreshLocalIp(): String {
        val ip = detectLocalHotspotOrLanIp()
        _localIpAddress.value = ip
        return ip
    }

    fun startUdpDiscoveryListener() {
        if (discoveryListenJob?.isActive == true) return
        discoveryListenJob = scope.launch {
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(UDP_DISCOVERY_PORT))
                    soTimeout = 2500
                }
                udpListenerSocket = socket
                val buffer = ByteArray(512)
                while (isActive) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val msg = String(packet.data, 0, packet.length, Charsets.UTF_8).trim()
                        if (msg.startsWith(BROADCAST_HEADER)) {
                            val senderIp = packet.address?.hostAddress ?: continue
                            val parts = msg.split("|")
                            val room = parts.getOrNull(1)?.removePrefix("room=") ?: "KART1"
                            val port = parts.getOrNull(2)?.removePrefix("port=")?.toIntOrNull() ?: TCP_GAME_PORT
                            val now = System.currentTimeMillis()
                            _discoveredHosts.update { existing ->
                                val filtered = existing.filter {
                                    it.ipAddress != senderIp && (now - it.lastSeenMs) < 8000L
                                }
                                (filtered + DiscoveredHost(room, senderIp, port, now))
                                    .sortedByDescending { it.lastSeenMs }
                            }
                        }
                    } catch (_: Exception) {
                        // Timeout or transient network change; prune stale hosts
                        val now = System.currentTimeMillis()
                        _discoveredHosts.update { list ->
                            list.filter { now - it.lastSeenMs < 8000L }
                        }
                    }
                }
            } catch (_: Exception) {
                // Port 8888 may already be bound in secondary instance
            }
        }
    }

    fun hostHotspotGame(roomCode: String, localPlayerId: String) {
        disconnect()
        val cleanRoom = roomCode.trim().uppercase(Locale.US).ifEmpty { "KART1" }
        val myIp = refreshLocalIp()
        _role.value = LanRole.HOSTING_HOTSPOT
        _statusText.value = "HOSTING [$cleanRoom] @ $myIp:$TCP_GAME_PORT (UDP $UDP_DISCOVERY_PORT)"

        // 1. Start TCP Server on port 7777
        tcpServerJob = scope.launch {
            try {
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(TCP_GAME_PORT))
                }
                serverSocket = server
                while (isActive) {
                    val client = server.accept()
                    client.tcpNoDelay = true
                    handleConnectedClientOnHost(client, localPlayerId)
                }
            } catch (e: Exception) {
                if (_role.value == LanRole.HOSTING_HOTSPOT) {
                    _statusText.value = "Host Server Stopped: ${e.localizedMessage ?: "Closed"}"
                }
            }
        }

        // 2. Start UDP Broadcast on port 8888 every 1 second
        hostBroadcastJob = scope.launch {
            var broadcastSocket: DatagramSocket? = null
            try {
                broadcastSocket = DatagramSocket().apply { broadcast = true }
                val payload = "$BROADCAST_HEADER|room=$cleanRoom|port=$TCP_GAME_PORT"
                val bytes = payload.toByteArray(Charsets.UTF_8)
                val broadcastTargets = listOf(
                    "255.255.255.255",
                    "192.168.43.255",
                    "192.168.137.255"
                )
                while (isActive && _role.value == LanRole.HOSTING_HOTSPOT) {
                    for (target in broadcastTargets) {
                        runCatching {
                            val packet = DatagramPacket(
                                bytes,
                                bytes.size,
                                InetAddress.getByName(target),
                                UDP_DISCOVERY_PORT
                            )
                            broadcastSocket.send(packet)
                        }
                    }
                    delay(1000L)
                }
            } catch (_: Exception) {
            } finally {
                runCatching { broadcastSocket?.close() }
            }
        }
    }

    private fun handleConnectedClientOnHost(socket: Socket, localPlayerId: String) {
        scope.launch {
            var writer: PrintWriter? = null
            var remotePeerId: String? = null
            try {
                writer = PrintWriter(socket.getOutputStream(), true)
                connectedClientWriters.add(writer)
                val peerIp = socket.inetAddress?.hostAddress ?: "Peer"
                _statusText.value = "Racer Connected ($peerIp) • ${connectedClientWriters.size} Peer(s)"

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    remotePeerId = parseIncomingPacket(line, localPlayerId) ?: remotePeerId
                    // Relay to any other connected clients in hotspot room
                    for (otherWriter in connectedClientWriters) {
                        if (otherWriter !== writer) {
                            runCatching { otherWriter.println(line) }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                writer?.let { connectedClientWriters.remove(it) }
                remotePeerId?.let { id ->
                    _remotePlayers.update { map -> map - id }
                }
                runCatching { socket.close() }
            }
        }
    }

    fun joinHotspotGame(targetInput: String, localPlayerId: String) {
        disconnect()
        val trimmed = targetInput.trim()
        // Determine if user entered an IP(:port) or a Room Code matched via UDP auto-discovery
        val matchedDiscovery = _discoveredHosts.value.firstOrNull {
            it.roomCode.equals(trimmed, ignoreCase = true) || it.ipAddress == trimmed
        }
        val hostIp: String
        val hostPort: Int
        if (matchedDiscovery != null) {
            hostIp = matchedDiscovery.ipAddress
            hostPort = matchedDiscovery.tcpPort
        } else if (trimmed.contains(".") || trimmed.contains(":")) {
            val split = trimmed.removePrefix("http://").removePrefix("https://").split(":")
            hostIp = split[0].ifEmpty { "192.168.43.1" }
            hostPort = split.getOrNull(1)?.toIntOrNull() ?: TCP_GAME_PORT
        } else {
            // Default to latest discovered host or Android Hotspot Gateway IP 192.168.43.1
            val fallbackHost = _discoveredHosts.value.firstOrNull()
            hostIp = fallbackHost?.ipAddress ?: "192.168.43.1"
            hostPort = fallbackHost?.tcpPort ?: TCP_GAME_PORT
        }

        _role.value = LanRole.JOINED_CLIENT
        _statusText.value = "Connecting to Hotspot $hostIp:$hostPort..."

        tcpClientJob = scope.launch {
            try {
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(hostIp, hostPort), 3500)
                clientSocket = socket
                val writer = PrintWriter(socket.getOutputStream(), true)
                clientWriter = writer
                _statusText.value = "CONNECTED TO $hostIp:$hostPort"

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    parseIncomingPacket(line, localPlayerId)
                }
            } catch (e: Exception) {
                _role.value = LanRole.STANDALONE
                _statusText.value = "Hotspot unreachable ($hostIp:$hostPort) • Running Local/AI Race"
            }
        }
    }

    fun broadcastLocalKartState(kart: KartState) {
        val roleNow = _role.value
        if (roleNow == LanRole.STANDALONE) return
        val packet = String.format(
            Locale.US,
            "MOVE|%s|%.3f|%.3f|%.3f|%.3f|%.3f|%d|%d",
            kart.id,
            kart.x,
            kart.y,
            kart.z,
            kart.rot,
            kart.speed,
            kart.colorHex,
            kart.lap
        )
        sendRawLine(packet)
    }

    fun broadcastMarbleFired(event: RemoteMarbleEvent) {
        if (_role.value == LanRole.STANDALONE) return
        val packet = String.format(
            Locale.US,
            "MARBLE|%s|%s|%.3f|%.3f|%.3f|%.3f",
            event.id,
            event.ownerId,
            event.x,
            event.z,
            event.vx,
            event.vz
        )
        sendRawLine(packet)
    }

    private fun sendRawLine(line: String) {
        scope.launch {
            when (_role.value) {
                LanRole.HOSTING_HOTSPOT -> {
                    for (writer in connectedClientWriters) {
                        runCatching { writer.println(line) }
                    }
                }
                LanRole.JOINED_CLIENT -> {
                    runCatching { clientWriter?.println(line) }
                }
                LanRole.STANDALONE -> Unit
            }
        }
    }

    private fun parseIncomingPacket(line: String, localPlayerId: String): String? {
        val parts = line.split("|")
        when (parts.firstOrNull()) {
            "MOVE" -> {
                if (parts.size < 9) return null
                val peerId = parts[1]
                if (peerId == localPlayerId) return peerId
                val x = parts[2].toFloatOrNull() ?: return peerId
                val y = parts[3].toFloatOrNull() ?: 0f
                val z = parts[4].toFloatOrNull() ?: return peerId
                val rot = parts[5].toFloatOrNull() ?: 0f
                val speed = parts[6].toFloatOrNull() ?: 0f
                val color = parts[7].toLongOrNull() ?: 0xFF00F5D4L
                val lap = parts[8].toIntOrNull() ?: 1

                _remotePlayers.update { current ->
                    val existing = current[peerId]
                    // Smooth interpolation (0.35 lerp factor)
                    val smoothX = if (existing != null) existing.x + (x - existing.x) * 0.45f else x
                    val smoothY = if (existing != null) existing.y + (y - existing.y) * 0.45f else y
                    val smoothZ = if (existing != null) existing.z + (z - existing.z) * 0.45f else z
                    current + (peerId to KartState(
                        id = peerId,
                        name = "PEER-${peerId.takeLast(3)}",
                        x = smoothX,
                        y = smoothY,
                        z = smoothZ,
                        rot = rot,
                        speed = speed,
                        colorHex = color,
                        lap = lap
                    ))
                }
                return peerId
            }
            "MARBLE" -> {
                if (parts.size < 7) return null
                val marbleId = parts[1]
                val ownerId = parts[2]
                if (ownerId == localPlayerId) return ownerId
                val x = parts[3].toFloatOrNull() ?: return ownerId
                val z = parts[4].toFloatOrNull() ?: return ownerId
                val vx = parts[5].toFloatOrNull() ?: return ownerId
                val vz = parts[6].toFloatOrNull() ?: return ownerId
                onRemoteMarbleFired(RemoteMarbleEvent(marbleId, ownerId, x, z, vx, vz))
                return ownerId
            }
        }
        return null
    }

    fun disconnect() {
        hostBroadcastJob?.cancel()
        tcpServerJob?.cancel()
        tcpClientJob?.cancel()
        connectedClientWriters.forEach { runCatching { it.close() } }
        connectedClientWriters.clear()
        runCatching { clientWriter?.close() }
        runCatching { clientSocket?.close() }
        runCatching { serverSocket?.close() }
        clientWriter = null
        clientSocket = null
        serverSocket = null
        _remotePlayers.value = emptyMap()
        _role.value = LanRole.STANDALONE
        _statusText.value = "LAN Standby • Hotspot Port $TCP_GAME_PORT / UDP $UDP_DISCOVERY_PORT"
    }

    fun shutdownAll() {
        disconnect()
        discoveryListenJob?.cancel()
        runCatching { udpListenerSocket?.close() }
    }

    private fun detectLocalHotspotOrLanIp(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            var candidateIp: String? = null
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        // Prioritize Android Hotspot gateway subnets (192.168.43.x or 192.168.137.x)
                        if (ip.startsWith("192.168.43.") || ip.startsWith("192.168.137.")) {
                            return ip
                        }
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            candidateIp = ip
                        } else if (candidateIp == null) {
                            candidateIp = ip
                        }
                    }
                }
            }
            candidateIp ?: "192.168.43.1"
        } catch (_: Exception) {
            "192.168.43.1"
        }
    }
}
