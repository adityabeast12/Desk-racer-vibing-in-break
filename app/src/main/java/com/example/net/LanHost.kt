package com.example.net

import com.example.game.GameConstants
import com.example.game.ItemMode
import com.example.game.KartBodyType
import com.example.game.WorldRenderSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random

class LanHost(
    private val discovery: LanDiscovery,
    private val onClientDisconnectedDuringRace: (slotIndex: Int, playerName: String) -> Unit,
    private val onRemoteKartUdpUpdate: (UdpKartStatePacket) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var roomCode: String = generateRoomCode()
        private set

    private val _slots = MutableStateFlow<List<LobbySlotInfo>>(emptyList())
    val slots: StateFlow<List<LobbySlotInfo>> = _slots.asStateFlow()

    private val _trackId = MutableStateFlow(0)
    val trackId: StateFlow<Int> = _trackId.asStateFlow()

    private val _laps = MutableStateFlow(3)
    val laps: StateFlow<Int> = _laps.asStateFlow()

    private val _cpuFill = MutableStateFlow(true)
    val cpuFill: StateFlow<Boolean> = _cpuFill.asStateFlow()

    private val _itemMode = MutableStateFlow(ItemMode.NORMAL)
    val itemMode: StateFlow<ItemMode> = _itemMode.asStateFlow()

    private var raceInProgress = false
    private var tcpServerSocket: ServerSocket? = null
    private var udpStateSocket: DatagramSocket? = null
    private var acceptJob: Job? = null
    private var udpJob: Job? = null
    private var heartbeatJob: Job? = null
    private var worldSeq = 0

    private class ConnectedClient(
        val slotIndex: Int,
        var playerName: String,
        var colorIndex: Int,
        var bodyType: KartBodyType,
        val socket: Socket,
        val input: DataInputStream,
        val output: DataOutputStream,
        val ipAddress: InetAddress,
        @Volatile var isReady: Boolean = false,
        @Volatile var lastHeardMs: Long = System.currentTimeMillis(),
        @Volatile var pingMs: Int = 10,
        @Volatile var lastUdpSeq: Int = -1
    )

    private val clients = CopyOnWriteArrayList<ConnectedClient>()

    fun startHosting(hostName: String, hostColorIdx: Int, hostBody: KartBodyType) {
        stopHosting()
        roomCode = generateRoomCode()
        raceInProgress = false
        _slots.value = listOf(
            LobbySlotInfo(
                slotIndex = 0,
                playerName = hostName,
                colorIndex = hostColorIdx,
                bodyType = hostBody,
                isHuman = true,
                isReady = true,
                pingMs = 0
            )
        )

        // 1. Start TCP Control Server on 47778
        acceptJob = scope.launch {
            try {
                val server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(GameConstants.TCP_CONTROL_PORT))
                }
                tcpServerSocket = server
                while (isActive && !server.isClosed) {
                    val clientSock = server.accept()
                    clientSock.tcpNoDelay = true
                    handleNewClientConnection(clientSock)
                }
            } catch (_: Exception) {
            }
        }

        // 2. Start UDP State Server on 47779
        udpJob = scope.launch {
            try {
                val udp = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(GameConstants.UDP_STATE_PORT))
                    soTimeout = 1000
                }
                udpStateSocket = udp
                val buf = ByteArray(128)
                val pkt = DatagramPacket(buf, buf.size)
                while (isActive && !udp.isClosed) {
                    try {
                        pkt.setData(buf, 0, buf.size)
                        udp.receive(pkt)
                        val decoded = Protocol.decodeUdpClientKart(buf, pkt.length)
                        if (decoded != null) {
                            val client = clients.firstOrNull { it.slotIndex == decoded.slotIndex }
                            if (client != null && decoded.seq > client.lastUdpSeq) {
                                client.lastUdpSeq = decoded.seq
                                client.lastHeardMs = System.currentTimeMillis()
                                onRemoteKartUdpUpdate(decoded)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 3. Start 1s Heartbeat & 5s Timeout Monitor
        heartbeatJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val timedOut = clients.filter { now - it.lastHeardMs > GameConstants.PEER_TIMEOUT_MS }
                for (dead in timedOut) {
                    removeClient(dead, notifyDisconnect = true)
                }
                broadcastLobbyStateToAll()
                delay(GameConstants.HEARTBEAT_INTERVAL_MS)
            }
        }

        // 4. Start UDP 47777 Beacons every 500ms
        discovery.startHostBeacons {
            Protocol.encodeDiscoveryBeacon(
                roomCode = roomCode,
                hostName = hostName,
                trackId = _trackId.value,
                players = _slots.value.size,
                maxPlayers = GameConstants.MAX_RACERS,
                tcpPort = GameConstants.TCP_CONTROL_PORT,
                raceInProgress = raceInProgress
            )
        }
    }

    private fun handleNewClientConnection(socket: Socket) {
        scope.launch {
            try {
                val dis = DataInputStream(socket.getInputStream())
                val dos = DataOutputStream(socket.getOutputStream())
                val frameLen = dis.readUnsignedShort()
                val payload = ByteArray(frameLen)
                dis.readFully(payload)
                val buf = ByteBuffer.wrap(payload)
                val op = buf.get()
                if (op != Protocol.OP_JOIN_REQ) {
                    socket.close()
                    return@launch
                }
                val version = buf.get()
                if (version != GameConstants.PROTOCOL_VERSION) {
                    sendTcpFrame(dos, byteArrayOf(Protocol.OP_JOIN_REJECT, 1)) // Version mismatch
                    socket.close()
                    return@launch
                }
                if (raceInProgress) {
                    sendTcpFrame(dos, byteArrayOf(Protocol.OP_JOIN_REJECT, 2)) // Race already started
                    socket.close()
                    return@launch
                }
                val usedSlots = _slots.value.map { it.slotIndex }.toSet()
                val assignedSlot = (1 until GameConstants.MAX_RACERS).firstOrNull { it !in usedSlots }
                if (assignedSlot == null) {
                    sendTcpFrame(dos, byteArrayOf(Protocol.OP_JOIN_REJECT, 3)) // Room full
                    socket.close()
                    return@launch
                }

                val name = Protocol.getShortString(buf)
                val colorIdx = buf.get().toInt()
                val bodyType = KartBodyType.fromId(buf.get().toInt())

                sendTcpFrame(dos, byteArrayOf(Protocol.OP_JOIN_ACCEPT, assignedSlot.toByte()))

                val client = ConnectedClient(
                    slotIndex = assignedSlot,
                    playerName = name,
                    colorIndex = colorIdx,
                    bodyType = bodyType,
                    socket = socket,
                    input = dis,
                    output = dos,
                    ipAddress = socket.inetAddress
                )
                clients.add(client)
                rebuildSlotsState()
                broadcastLobbyStateToAll()

                // Read client control messages
                while (isActive && !socket.isClosed) {
                    val len = dis.readUnsignedShort()
                    val msg = ByteArray(len)
                    dis.readFully(msg)
                    client.lastHeardMs = System.currentTimeMillis()
                    handleClientControlFrame(client, msg)
                }
            } catch (_: Exception) {
                clients.firstOrNull { it.socket === socket }?.let {
                    removeClient(it, notifyDisconnect = true)
                }
            }
        }
    }

    private fun handleClientControlFrame(client: ConnectedClient, msg: ByteArray) {
        if (msg.isEmpty()) return
        val buf = ByteBuffer.wrap(msg)
        when (buf.get()) {
            Protocol.OP_CLIENT_READY -> {
                client.isReady = buf.get().toInt() != 0
                rebuildSlotsState()
                broadcastLobbyStateToAll()
            }
            Protocol.OP_PING -> {
                val clientSentMs = buf.long
                val pong = ByteBuffer.allocate(17)
                pong.put(Protocol.OP_PONG)
                pong.putLong(clientSentMs)
                pong.putLong(System.currentTimeMillis())
                sendTcpFrame(client.output, pong.array())
            }
        }
    }

    fun kickSlot(slotIndex: Int) {
        if (slotIndex == 0) return
        val target = clients.firstOrNull { it.slotIndex == slotIndex } ?: return
        removeClient(target, notifyDisconnect = false)
    }

    private fun removeClient(client: ConnectedClient, notifyDisconnect: Boolean) {
        if (clients.remove(client)) {
            runCatching { client.socket.close() }
            rebuildSlotsState()
            broadcastLobbyStateToAll()
            if (notifyDisconnect && raceInProgress) {
                onClientDisconnectedDuringRace(client.slotIndex, client.playerName)
            }
        }
    }

    private fun rebuildSlotsState() {
        val hostSlot = _slots.value.firstOrNull { it.slotIndex == 0 } ?: return
        val clientSlots = clients.map {
            LobbySlotInfo(
                slotIndex = it.slotIndex,
                playerName = it.playerName,
                colorIndex = it.colorIndex,
                bodyType = it.bodyType,
                isHuman = true,
                isReady = it.isReady,
                pingMs = it.pingMs
            )
        }
        _slots.value = (listOf(hostSlot) + clientSlots).sortedBy { it.slotIndex }
    }

    fun updateTrack(trackId: Int) {
        _trackId.value = trackId.coerceIn(0, 7)
        broadcastLobbyStateToAll()
    }

    fun updateLaps(laps: Int) {
        _laps.value = laps
        broadcastLobbyStateToAll()
    }

    fun updateCpuFill(enabled: Boolean) {
        _cpuFill.value = enabled
        broadcastLobbyStateToAll()
    }

    fun updateItemMode(mode: ItemMode) {
        _itemMode.value = mode
        broadcastLobbyStateToAll()
    }

    fun notifyCountdownStart() {
        raceInProgress = true
        val startAtHostMs = System.currentTimeMillis() + 3000L
        val buf = ByteBuffer.allocate(13)
        buf.put(Protocol.OP_START_COUNTDOWN)
        buf.putLong(startAtHostMs)
        buf.put(_trackId.value.toByte())
        buf.put(_laps.value.toByte())
        buf.put(if (_cpuFill.value) 1.toByte() else 0.toByte())
        buf.put(_itemMode.value.id.toByte())
        val bytes = buf.array()
        for (c in clients) {
            scope.launch { sendTcpFrame(c.output, bytes) }
        }
    }

    fun broadcastRaceResults(standings: List<com.example.game.RacerStanding>) {
        raceInProgress = false
        val bytes = Protocol.encodeRaceResultsFrame(standings)
        for (c in clients) {
            scope.launch { sendTcpFrame(c.output, bytes) }
        }
    }

    fun broadcastWorldSnapshotUdp(snap: WorldRenderSnapshot) {
        if (!raceInProgress || clients.isEmpty()) return
        val udp = udpStateSocket ?: return
        worldSeq++
        val buf = ByteBuffer.allocate(340)
        buf.put(Protocol.UDP_WORLD_SNAPSHOT)
        buf.putInt(worldSeq)
        buf.put(snap.karts.size.toByte())
        for (k in snap.karts) {
            buf.put(k.slotIndex.toByte())
            buf.putFloat(k.x)
            buf.putFloat(k.y)
            buf.putFloat(k.z)
            buf.putFloat(k.headingRad)
            buf.putFloat(k.speed)
            buf.put(k.sparkTier.toByte())
            buf.put(if (k.isBoosting) 1.toByte() else 0.toByte())
        }
        val payload = ByteArray(buf.position())
        buf.flip()
        buf.get(payload)

        for (c in clients) {
            runCatching {
                val pkt = DatagramPacket(payload, payload.size, c.ipAddress, GameConstants.UDP_STATE_PORT)
                udp.send(pkt)
            }
        }
    }

    private fun broadcastLobbyStateToAll() {
        val frame = Protocol.encodeLobbyStateFrame(
            roomCode = roomCode,
            trackId = _trackId.value,
            laps = _laps.value,
            cpuFill = _cpuFill.value,
            itemMode = _itemMode.value,
            slots = _slots.value
        )
        for (c in clients) {
            scope.launch { sendTcpFrame(c.output, frame) }
        }
    }

    private fun sendTcpFrame(dos: DataOutputStream, bytes: ByteArray) {
        synchronized(dos) {
            runCatching {
                dos.writeShort(bytes.size)
                dos.write(bytes)
                dos.flush()
            }
        }
    }

    fun stopHosting() {
        discovery.stopHostBeacons()
        for (c in clients) {
            runCatching {
                sendTcpFrame(c.output, byteArrayOf(Protocol.OP_HOST_LEFT))
                c.socket.close()
            }
        }
        clients.clear()
        acceptJob?.cancel()
        udpJob?.cancel()
        heartbeatJob?.cancel()
        runCatching { tcpServerSocket?.close() }
        runCatching { udpStateSocket?.close() }
        tcpServerSocket = null
        udpStateSocket = null
        raceInProgress = false
    }

    companion object {
        private const val CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        fun generateRoomCode(): String {
            return (1..4).map { CHARS[Random.nextInt(CHARS.length)] }.joinToString("")
        }
    }
}
