package com.example.net

import com.example.game.CcClass
import com.example.game.CpuDifficulty
import com.example.game.GameConstants
import com.example.game.ItemMode
import com.example.game.ItemType
import com.example.game.KartBodyType
import com.example.game.RacerStanding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
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
    private val onRemoteKartUdpUpdate: (pkt: UdpKartStatePacket, clientPingMs: Int) -> Unit,
    var onClientRejoinedDuringRace: (slotIndex: Int, playerName: String) -> Unit = { _, _ -> },
    var onClientReliableItemAction: (slotIndex: Int, item: ItemType, backward: Boolean, clientPingMs: Int) -> Unit = { _, _, _, _ -> }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var roomCode: String = generateRoomCode()
        private set

    @Volatile
    var currentRaceId: Int = Protocol.generateRaceId()
        private set

    private val _slots = MutableStateFlow<List<LobbySlotInfo>>(emptyList())
    val slots: StateFlow<List<LobbySlotInfo>> = _slots.asStateFlow()

    private val _trackId = MutableStateFlow(0)
    val trackId: StateFlow<Int> = _trackId.asStateFlow()

    private val _laps = MutableStateFlow(3)
    val laps: StateFlow<Int> = _laps.asStateFlow()

    private val _cpuFill = MutableStateFlow(true)
    val cpuFill: StateFlow<Boolean> = _cpuFill.asStateFlow()

    private val _botCount = MutableStateFlow(7)
    val botCount: StateFlow<Int> = _botCount.asStateFlow()

    private val _botDifficulty = MutableStateFlow(CpuDifficulty.PRO)
    val botDifficulty: StateFlow<CpuDifficulty> = _botDifficulty.asStateFlow()

    private val _ccClass = MutableStateFlow(CcClass.CC_150)
    val ccClass: StateFlow<CcClass> = _ccClass.asStateFlow()

    private val _itemMode = MutableStateFlow(ItemMode.NORMAL)
    val itemMode: StateFlow<ItemMode> = _itemMode.asStateFlow()

    @Volatile
    var raceInProgress: Boolean = false
        private set

    private var tcpServerSocket: ServerSocket? = null
    private var udpStateSocket: DatagramSocket? = null
    private var acceptJob: Job? = null
    private var udpRecvJob: Job? = null
    private var udpSendJob: Job? = null
    private var heartbeatJob: Job? = null

    @Volatile
    private var worldSeq = 0

    // Part B11: Single long-lived UDP sender channel with DROP_OLDEST (capacity = 4)
    private var udpSendChannel = Channel<ByteArray>(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private class ConnectedClient(
        val slotIndex: Int,
        var playerId: String,
        var playerName: String,
        var colorIndex: Int,
        var bodyType: KartBodyType,
        var liveryIndex: Int,
        val socket: Socket,
        val input: DataInputStream,
        val output: DataOutputStream,
        val ipAddress: InetAddress,
        @Volatile var isReady: Boolean = false,
        @Volatile var isAway: Boolean = false,
        @Volatile var awaySinceMs: Long = 0L,
        @Volatile var lastHeardMs: Long = System.currentTimeMillis(),
        @Volatile var pingMs: Int = 12,
        @Volatile var lastUdpSeq: Int = -1
    )

    private data class DisconnectedSlotRecord(
        val slotIndex: Int,
        val playerId: String,
        val playerName: String,
        val colorIndex: Int,
        val bodyType: KartBodyType,
        val liveryIndex: Int,
        val disconnectedAtMs: Long
    )

    private val clients = CopyOnWriteArrayList<ConnectedClient>()
    private val reclaimableSlots = CopyOnWriteArrayList<DisconnectedSlotRecord>()

    fun startHosting(
        hostName: String,
        hostColorIdx: Int,
        hostBody: KartBodyType,
        hostLiveryIdx: Int = 0,
        hostPlayerId: String = "host"
    ) {
        stopHosting()
        roomCode = generateRoomCode()
        currentRaceId = Protocol.generateRaceId()
        worldSeq = 0
        raceInProgress = false
        reclaimableSlots.clear()
        _slots.value = listOf(
            LobbySlotInfo(
                slotIndex = 0,
                playerName = hostName,
                colorIndex = hostColorIdx,
                bodyType = hostBody,
                isHuman = true,
                isReady = true,
                pingMs = 0,
                liveryIndex = hostLiveryIdx,
                playerId = hostPlayerId,
                isAway = false
            )
        )

        udpSendChannel = Channel(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

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

        // 2. Start UDP State Server on 47779 (receive + long-lived sender coroutine per Part B11)
        udpRecvJob = scope.launch {
            try {
                val udp = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(GameConstants.UDP_STATE_PORT))
                    soTimeout = 1000
                }
                udpStateSocket = udp

                // Long-lived sender coroutine (Part B11)
                udpSendJob = scope.launch {
                    val reusablePacket = DatagramPacket(ByteArray(1), 1)
                    for (payload in udpSendChannel) {
                        if (!isActive || udp.isClosed) break
                        for (c in clients) {
                            if (c.isAway) continue
                            runCatching {
                                reusablePacket.setData(payload, 0, payload.size)
                                reusablePacket.address = c.ipAddress
                                reusablePacket.port = GameConstants.UDP_STATE_PORT
                                udp.send(reusablePacket)
                            }
                        }
                    }
                }

                val buf = ByteArray(128)
                val pkt = DatagramPacket(buf, buf.size)
                while (isActive && !udp.isClosed) {
                    try {
                        pkt.setData(buf, 0, buf.size)
                        udp.receive(pkt)
                        val decoded = Protocol.decodeUdpClientKart(buf, pkt.length)
                        if (decoded != null) {
                            // Part B1: Drop packets from different raceId and check wrap-safe sequence
                            if (decoded.raceId != 0 && decoded.raceId != currentRaceId) continue
                            val client = clients.firstOrNull { it.slotIndex == decoded.slotIndex }
                            if (client != null && Protocol.isSeqNewer(decoded.seq, client.lastUdpSeq)) {
                                client.lastUdpSeq = decoded.seq
                                client.lastHeardMs = System.currentTimeMillis()
                                onRemoteKartUdpUpdate(decoded, client.pingMs)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 3. Start 1s Heartbeat, 5s Timeout & 30s Rejoin/Away Expiry Monitor
        heartbeatJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                reclaimableSlots.removeAll { now - it.disconnectedAtMs > GameConstants.REJOIN_WINDOW_MS }

                val timedOut = clients.filter { client ->
                    val timeoutLimit = if (client.isAway) GameConstants.REJOIN_WINDOW_MS else GameConstants.PEER_TIMEOUT_MS
                    now - client.lastHeardMs > timeoutLimit
                }
                for (dead in timedOut) {
                    removeClient(dead, notifyDisconnect = true, allowReclaim = true)
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

                val name = Protocol.getShortString(buf)
                val colorIdx = buf.get().toInt() and 0xFF
                val bodyType = KartBodyType.fromId(buf.get().toInt() and 0xFF)
                val liveryIdx = if (buf.hasRemaining()) buf.get().toInt() and 0xFF else 0
                val playerId = if (buf.hasRemaining()) Protocol.getShortString(buf) else ""

                // Part B12: Check if this player is rejoining within 30s
                val now = System.currentTimeMillis()
                val reclaim = if (playerId.isNotEmpty()) {
                    reclaimableSlots.firstOrNull {
                        it.playerId == playerId && (now - it.disconnectedAtMs) <= GameConstants.REJOIN_WINDOW_MS
                    }
                } else null

                if (raceInProgress && reclaim == null) {
                    sendTcpFrame(dos, byteArrayOf(Protocol.OP_JOIN_REJECT, 2)) // Race already started
                    socket.close()
                    return@launch
                }

                val usedSlots = _slots.value.map { it.slotIndex }.toSet()
                val assignedSlot = reclaim?.slotIndex
                    ?: (1 until GameConstants.MAX_RACERS).firstOrNull { it !in usedSlots }
                if (assignedSlot == null) {
                    sendTcpFrame(dos, byteArrayOf(Protocol.OP_JOIN_REJECT, 3)) // Room full
                    socket.close()
                    return@launch
                }
                if (reclaim != null) {
                    reclaimableSlots.remove(reclaim)
                }

                val acceptBuf = ByteBuffer.allocate(8)
                acceptBuf.put(Protocol.OP_JOIN_ACCEPT)
                acceptBuf.put(assignedSlot.toByte())
                acceptBuf.putInt(currentRaceId)
                sendTcpFrame(dos, acceptBuf.array().copyOf(acceptBuf.position()))

                val client = ConnectedClient(
                    slotIndex = assignedSlot,
                    playerId = playerId,
                    playerName = name,
                    colorIndex = colorIdx,
                    bodyType = bodyType,
                    liveryIndex = liveryIdx,
                    socket = socket,
                    input = dis,
                    output = dos,
                    ipAddress = socket.inetAddress,
                    lastUdpSeq = -1
                )
                clients.add(client)
                rebuildSlotsState()
                broadcastLobbyStateToAll()

                if (raceInProgress && reclaim != null) {
                    onClientRejoinedDuringRace(assignedSlot, name)
                    // Send start frame with current raceId so client immediately enters race
                    val startPayload = StartCountdownPayload(
                        raceId = currentRaceId,
                        startAtHostMs = System.currentTimeMillis(),
                        trackId = _trackId.value,
                        laps = _laps.value,
                        cpuFill = _cpuFill.value,
                        botCount = _botCount.value,
                        botDifficulty = _botDifficulty.value,
                        ccClass = _ccClass.value,
                        itemMode = _itemMode.value,
                        slots = _slots.value
                    )
                    sendTcpFrame(dos, Protocol.encodeStartCountdownFrame(startPayload))
                }

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
                    removeClient(it, notifyDisconnect = true, allowReclaim = true)
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
                if (buf.remaining() >= 2) {
                    client.pingMs = (buf.short.toInt() and 0xFFFF).coerceIn(1, 999)
                }
                val pong = ByteBuffer.allocate(17)
                pong.put(Protocol.OP_PONG)
                pong.putLong(clientSentMs)
                pong.putLong(System.currentTimeMillis())
                sendTcpFrame(client.output, pong.array())
            }
            Protocol.OP_ITEM_ACTION -> {
                if (buf.remaining() >= 6) {
                    val raceId = buf.int
                    if (raceId == 0 || raceId == currentRaceId) {
                        val item = ItemType.fromId(buf.get().toInt() and 0xFF)
                        val backward = buf.get().toInt() != 0
                        onClientReliableItemAction(client.slotIndex, item, backward, client.pingMs)
                    }
                }
            }
            Protocol.OP_CLIENT_AWAY -> {
                val away = buf.get().toInt() != 0
                client.isAway = away
                client.awaySinceMs = if (away) System.currentTimeMillis() else 0L
                rebuildSlotsState()
                broadcastLobbyStateToAll()
            }
            Protocol.OP_CLIENT_LEAVE -> {
                removeClient(client, notifyDisconnect = true, allowReclaim = false)
            }
        }
    }

    fun kickSlot(slotIndex: Int) {
        if (slotIndex == 0) return
        val target = clients.firstOrNull { it.slotIndex == slotIndex } ?: return
        removeClient(target, notifyDisconnect = false, allowReclaim = false)
    }

    private fun removeClient(client: ConnectedClient, notifyDisconnect: Boolean, allowReclaim: Boolean) {
        if (clients.remove(client)) {
            if (allowReclaim && client.playerId.isNotEmpty()) {
                reclaimableSlots.removeAll { it.playerId == client.playerId || it.slotIndex == client.slotIndex }
                reclaimableSlots.add(
                    DisconnectedSlotRecord(
                        slotIndex = client.slotIndex,
                        playerId = client.playerId,
                        playerName = client.playerName,
                        colorIndex = client.colorIndex,
                        bodyType = client.bodyType,
                        liveryIndex = client.liveryIndex,
                        disconnectedAtMs = System.currentTimeMillis()
                    )
                )
            }
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
                pingMs = it.pingMs,
                liveryIndex = it.liveryIndex,
                playerId = it.playerId,
                isAway = it.isAway
            )
        }
        _slots.value = (listOf(hostSlot) + clientSlots).sortedBy { it.slotIndex }
    }

    fun updateTrack(trackId: Int) {
        _trackId.value = trackId.coerceIn(0, 7)
        broadcastLobbyStateToAll()
    }

    fun updateLaps(laps: Int) {
        _laps.value = laps.coerceIn(1, 5)
        broadcastLobbyStateToAll()
    }

    fun updateCpuFill(enabled: Boolean) {
        _cpuFill.value = enabled
        broadcastLobbyStateToAll()
    }

    fun updateBotCount(count: Int) {
        _botCount.value = count.coerceIn(0, 7)
        broadcastLobbyStateToAll()
    }

    fun updateBotDifficulty(difficulty: CpuDifficulty) {
        _botDifficulty.value = difficulty
        broadcastLobbyStateToAll()
    }

    fun updateCcClass(cc: CcClass) {
        _ccClass.value = cc
        broadcastLobbyStateToAll()
    }

    fun updateItemMode(mode: ItemMode) {
        _itemMode.value = mode
        broadcastLobbyStateToAll()
    }

    /**
     * Part B1 & B9: Generates a fresh raceId, resets sequence counters, and sends OP_START_COUNTDOWN.
     */
    fun notifyCountdownStart(): Int {
        currentRaceId = Protocol.generateRaceId()
        worldSeq = 0
        for (c in clients) {
            c.lastUdpSeq = -1
        }
        raceInProgress = true
        val startAtHostMs = System.currentTimeMillis() + 3200L
        val payload = StartCountdownPayload(
            raceId = currentRaceId,
            startAtHostMs = startAtHostMs,
            trackId = _trackId.value,
            laps = _laps.value,
            cpuFill = _cpuFill.value,
            botCount = _botCount.value,
            botDifficulty = _botDifficulty.value,
            ccClass = _ccClass.value,
            itemMode = _itemMode.value,
            slots = _slots.value
        )
        val bytes = Protocol.encodeStartCountdownFrame(payload)
        for (c in clients) {
            scope.launch { sendTcpFrame(c.output, bytes) }
        }
        return currentRaceId
    }

    /**
     * Part B12: Rematch resets raceInProgress, generates a new raceId, resets client ready flags,
     * and broadcasts ReliableRaceEvent.Rematch + LobbyState.
     */
    fun notifyRematchToLobby(): Int {
        currentRaceId = Protocol.generateRaceId()
        worldSeq = 0
        raceInProgress = false
        for (c in clients) {
            c.isReady = false
            c.lastUdpSeq = -1
        }
        rebuildSlotsState()
        val rematchEvent = ReliableRaceEvent.Rematch(
            raceId = currentRaceId,
            trackId = _trackId.value,
            laps = _laps.value,
            cpuFill = _cpuFill.value,
            botCount = _botCount.value,
            botDifficulty = _botDifficulty.value,
            ccClass = _ccClass.value,
            itemMode = _itemMode.value
        )
        broadcastReliableEvent(rematchEvent)
        broadcastLobbyStateToAll()
        return currentRaceId
    }

    fun broadcastReliableEvent(event: ReliableRaceEvent) {
        if (clients.isEmpty()) return
        val bytes = Protocol.encodeReliableEvent(event)
        for (c in clients) {
            scope.launch { sendTcpFrame(c.output, bytes) }
        }
    }

    fun broadcastRaceResults(standings: List<RacerStanding>) {
        raceInProgress = false
        broadcastReliableEvent(ReliableRaceEvent.Results(currentRaceId, standings))
        val bytes = Protocol.encodeRaceResultsFrame(standings, currentRaceId)
        for (c in clients) {
            scope.launch { sendTcpFrame(c.output, bytes) }
        }
    }

    /**
     * Part B3 & B11: Encodes WorldSnapshotV4 and offers it to the non-blocking UDP channel.
     */
    fun broadcastWorldSnapshotV4(snap: WorldSnapshotV4) {
        if (!raceInProgress || clients.isEmpty()) return
        worldSeq++
        val tagged = snap.copy(
            raceId = currentRaceId,
            seq = worldSeq
        )
        val payload = Protocol.encodeWorldSnapshotV4(tagged)
        udpSendChannel.trySend(payload)
    }

    private fun broadcastLobbyStateToAll() {
        val frame = Protocol.encodeLobbyStateFrame(
            roomCode = roomCode,
            raceId = currentRaceId,
            trackId = _trackId.value,
            laps = _laps.value,
            cpuFill = _cpuFill.value,
            botCount = _botCount.value,
            botDifficulty = _botDifficulty.value,
            ccClass = _ccClass.value,
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
        reclaimableSlots.clear()
        acceptJob?.cancel()
        udpRecvJob?.cancel()
        udpSendJob?.cancel()
        heartbeatJob?.cancel()
        udpSendChannel.close()
        runCatching { tcpServerSocket?.close() }
        runCatching { udpStateSocket?.close() }
        tcpServerSocket = null
        udpStateSocket = null
        raceInProgress = false
        worldSeq = 0
    }

    companion object {
        private const val CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        fun generateRoomCode(): String {
            return (1..4).map { CHARS[Random.nextInt(CHARS.length)] }.joinToString("")
        }
    }
}
