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
import java.net.Socket
import java.nio.ByteBuffer

data class RemoteKartInterpSample(
    val timestampMs: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val headingRad: Float,
    val speed: Float,
    val sparkTier: Int,
    val isBoosting: Boolean
)

class LanClient(
    var onCountdownStartedFromHost: (
        raceId: Int,
        startDelayMs: Long,
        trackId: Int,
        laps: Int,
        cpuFill: Boolean,
        botCount: Int,
        botDifficulty: CpuDifficulty,
        ccClass: CcClass,
        itemMode: ItemMode,
        assignedSlot: Int,
        slots: List<LobbySlotInfo>
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
    var onHostLeftRoom: (reason: String) -> Unit = {},
    var onWorldSnapshotReceived: (WorldSnapshotV4, receiptTimeMs: Long) -> Unit = { _, _ -> },
    var onReliableEventReceived: (ReliableRaceEvent) -> Unit = {},
    var onRaceResultsReceived: (List<RacerStanding>) -> Unit = {},
    var onRematchReturnToLobby: () -> Unit = {}
) {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _statusMessage = MutableStateFlow("Select a room or enter the Host IP to join.")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _assignedSlot = MutableStateFlow(1)
    val assignedSlot: StateFlow<Int> = _assignedSlot.asStateFlow()

    private val _roomCode = MutableStateFlow("")
    val roomCode: StateFlow<String> = _roomCode.asStateFlow()

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

    private val _slots = MutableStateFlow<List<LobbySlotInfo>>(emptyList())
    val slots: StateFlow<List<LobbySlotInfo>> = _slots.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    @Volatile
    var activeRaceId: Int = 0
        private set

    // Part B8: 7-sample median RTT clock sync (offset = hostTime - clientTime)
    private data class ClockSyncSample(val rttMs: Long, val offsetMs: Long)
    private val clockSyncSamples = ArrayList<ClockSyncSample>(8)

    @Volatile
    var clockOffsetMs: Long = 0L
        private set

    @Volatile
    var medianRttMs: Int = 12
        private set

    // Part B2: Repeat itemPressed / itemBackward edges across 3 consecutive UDP packets
    @Volatile
    private var pendingItemEdgeRepeats = 0

    @Volatile
    private var pendingItemBackwardFlag = false

    @Volatile
    private var latestItemHeldFlag = false

    private var tcpSocket: Socket? = null
    private var tcpOut: DataOutputStream? = null
    private var udpSocket: DatagramSocket? = null
    private var hostInetAddress: InetAddress? = null
    private var tcpJob: Job? = null
    private var udpRecvJob: Job? = null
    private var udpSendJob: Job? = null
    private var pingJob: Job? = null

    @Volatile
    private var clientUdpSeq = 0

    @Volatile
    private var lastWorldSeq = -1

    // Part B11: Single long-lived UDP sender coroutine draining a Channel(capacity = 4, DROP_OLDEST)
    private var udpSendChannel = Channel<ByteArray>(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private fun runOnMain(block: () -> Unit) {
        runCatching {
            mainScope.launch { block() }
        }.onFailure {
            block()
        }
    }

    fun connectToHost(
        hostIpRaw: String,
        tcpPort: Int = GameConstants.TCP_CONTROL_PORT,
        playerName: String,
        colorIndex: Int,
        bodyType: KartBodyType,
        liveryIndex: Int = 0,
        playerId: String = ""
    ) {
        disconnect(sendLeaveFrame = false)
        val cleanIp = hostIpRaw.trim().substringBefore(":")
        if (cleanIp.isEmpty()) {
            _statusMessage.value = "Enter a valid Host IP address."
            return
        }
        _statusMessage.value = "Connecting to $cleanIp:$tcpPort..."
        resetSequenceNumbers(0)
        synchronized(clockSyncSamples) { clockSyncSamples.clear() }
        udpSendChannel = Channel(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        tcpJob = ioScope.launch {
            try {
                val sock = Socket()
                sock.tcpNoDelay = true
                sock.connect(InetSocketAddress(cleanIp, tcpPort), 3500)
                tcpSocket = sock
                hostInetAddress = sock.inetAddress
                val dis = DataInputStream(sock.getInputStream())
                val dos = DataOutputStream(sock.getOutputStream())
                tcpOut = dos

                // Send JOIN_REQ with v4 protocol, liveryIndex, and stable playerId
                val req = ByteBuffer.allocate(96)
                req.put(Protocol.OP_JOIN_REQ)
                req.put(GameConstants.PROTOCOL_VERSION)
                Protocol.putShortString(req, playerName)
                req.put(colorIndex.toByte())
                req.put(bodyType.id.toByte())
                req.put(liveryIndex.toByte())
                Protocol.putShortString(req, playerId)
                val reqBytes = ByteArray(req.position())
                req.flip()
                req.get(reqBytes)
                sendFrame(dos, reqBytes)

                // Read JOIN response
                val respLen = dis.readUnsignedShort()
                val resp = ByteArray(respLen)
                dis.readFully(resp)
                if (resp[0] == Protocol.OP_JOIN_REJECT) {
                    val code = resp.getOrNull(1)?.toInt() ?: 0
                    _statusMessage.value = when (code) {
                        1 -> "Version mismatch: update both phones"
                        2 -> "Race already started"
                        3 -> "Room full"
                        else -> "Join rejected by host"
                    }
                    sock.close()
                    return@launch
                }
                if (resp[0] == Protocol.OP_JOIN_ACCEPT) {
                    val acceptBuf = ByteBuffer.wrap(resp, 1, resp.size - 1)
                    _assignedSlot.value = acceptBuf.get().toInt() and 0xFF
                    if (acceptBuf.remaining() >= 4) {
                        resetSequenceNumbers(acceptBuf.int)
                    }
                    _isConnected.value = true
                    _isReady.value = false
                    _statusMessage.value = "Connected to Host ($cleanIp)"
                    startUdpClientChannel()
                    startClockSyncAndPingLoop(dos)
                }

                // Read ongoing control frames
                while (isActive && !sock.isClosed) {
                    val fLen = dis.readUnsignedShort()
                    val frame = ByteArray(fLen)
                    dis.readFully(frame)
                    handleServerControlFrame(frame)
                }
            } catch (_: Exception) {
                val wasConnected = _isConnected.value
                disconnect(sendLeaveFrame = false)
                if (wasConnected) {
                    runOnMain { onHostLeftRoom("Host left") }
                } else {
                    _statusMessage.value = "Not on Wi-Fi: join the host's hotspot first (or verify IP $cleanIp)"
                }
            }
        }
    }

    private fun resetSequenceNumbers(newRaceId: Int) {
        activeRaceId = newRaceId
        clientUdpSeq = 0
        lastWorldSeq = -1
        pendingItemEdgeRepeats = 0
    }

    private fun handleServerControlFrame(frame: ByteArray) {
        if (frame.isEmpty()) return
        val buf = ByteBuffer.wrap(frame)
        when (buf.get()) {
            Protocol.OP_LOBBY_STATE -> {
                val lobby = Protocol.decodeLobbyStateFrame(buf) ?: return
                if (lobby.raceId != 0 && lobby.raceId != activeRaceId) {
                    resetSequenceNumbers(lobby.raceId)
                }
                _roomCode.value = lobby.roomCode
                _trackId.value = lobby.trackId
                _laps.value = lobby.laps
                _cpuFill.value = lobby.cpuFill
                _botCount.value = lobby.botCount
                _botDifficulty.value = lobby.botDifficulty
                _ccClass.value = lobby.ccClass
                _itemMode.value = lobby.itemMode
                _slots.value = lobby.slots
            }

            Protocol.OP_PONG -> {
                if (buf.remaining() >= 16) {
                    val clientSentMs = buf.long
                    val hostTimeMs = buf.long
                    val now = System.currentTimeMillis()
                    val rtt = (now - clientSentMs).coerceIn(1L, 2000L)
                    val offset = hostTimeMs - (clientSentMs + rtt / 2L)
                    synchronized(clockSyncSamples) {
                        if (clockSyncSamples.size >= 7) {
                            clockSyncSamples.removeAt(0)
                        }
                        clockSyncSamples.add(ClockSyncSample(rtt, offset))
                        val sortedByRtt = clockSyncSamples.sortedBy { it.rttMs }
                        val median = sortedByRtt[sortedByRtt.size / 2]
                        medianRttMs = median.rttMs.toInt()
                        clockOffsetMs = median.offsetMs
                    }
                }
            }

            Protocol.OP_START_COUNTDOWN -> {
                val startPayload = Protocol.decodeStartCountdownFrame(buf) ?: return
                resetSequenceNumbers(startPayload.raceId)
                _trackId.value = startPayload.trackId
                _laps.value = startPayload.laps
                _cpuFill.value = startPayload.cpuFill
                _botCount.value = startPayload.botCount
                _botDifficulty.value = startPayload.botDifficulty
                _ccClass.value = startPayload.ccClass
                _itemMode.value = startPayload.itemMode
                _slots.value = startPayload.slots

                // Part B8: Countdown starts at startAtHostMs - clockOffsetMs locally
                val localStartTargetMs = startPayload.startAtHostMs - clockOffsetMs
                val startDelayMs = (localStartTargetMs - System.currentTimeMillis()).coerceIn(0L, 4000L)

                runOnMain {
                    onCountdownStartedFromHost(
                        startPayload.raceId,
                        startDelayMs,
                        startPayload.trackId,
                        startPayload.laps,
                        startPayload.cpuFill,
                        startPayload.botCount,
                        startPayload.botDifficulty,
                        startPayload.ccClass,
                        startPayload.itemMode,
                        _assignedSlot.value,
                        startPayload.slots
                    )
                }
            }

            Protocol.OP_RELIABLE_EVENT -> {
                val evt = Protocol.decodeReliableEvent(frame, _assignedSlot.value) ?: return
                if (evt is ReliableRaceEvent.Rematch) {
                    resetSequenceNumbers(evt.raceId)
                    _isReady.value = false
                    _trackId.value = evt.trackId
                    _laps.value = evt.laps
                    _cpuFill.value = evt.cpuFill
                    _botCount.value = evt.botCount
                    _botDifficulty.value = evt.botDifficulty
                    _ccClass.value = evt.ccClass
                    _itemMode.value = evt.itemMode
                    runOnMain { onRematchReturnToLobby() }
                    return
                }
                if (evt.raceId != 0 && activeRaceId != 0 && evt.raceId != activeRaceId) return
                onReliableEventReceived(evt)
                if (evt is ReliableRaceEvent.Results) {
                    runOnMain { onRaceResultsReceived(evt.standings) }
                }
            }

            Protocol.OP_RACE_RESULTS -> {
                if (buf.remaining() < 5) return
                val ver = buf.get()
                if (ver != GameConstants.PROTOCOL_VERSION) return
                val raceId = buf.int
                if (raceId != 0 && activeRaceId != 0 && raceId != activeRaceId) return
                val standings = Protocol.decodeRaceResultsFrame(buf, _assignedSlot.value)
                runOnMain { onRaceResultsReceived(standings) }
            }

            Protocol.OP_HOST_LEFT -> {
                disconnect(sendLeaveFrame = false)
                runOnMain { onHostLeftRoom("Host left") }
            }
        }
    }

    private fun startUdpClientChannel() {
        udpRecvJob?.cancel()
        udpSendJob?.cancel()
        udpRecvJob = ioScope.launch {
            try {
                val udp = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(GameConstants.UDP_STATE_PORT))
                    soTimeout = 1000
                }
                udpSocket = udp

                // Part B11: Single long-lived UDP sender coroutine draining udpSendChannel
                udpSendJob = ioScope.launch {
                    val reusablePacket = DatagramPacket(ByteArray(1), 1)
                    for (payload in udpSendChannel) {
                        val hostAddr = hostInetAddress ?: continue
                        if (!isActive || udp.isClosed) break
                        runCatching {
                            reusablePacket.setData(payload, 0, payload.size)
                            reusablePacket.address = hostAddr
                            reusablePacket.port = GameConstants.UDP_STATE_PORT
                            udp.send(reusablePacket)
                        }
                    }
                }

                val buf = ByteArray(1280)
                val pkt = DatagramPacket(buf, buf.size)
                while (isActive && !udp.isClosed) {
                    try {
                        pkt.setData(buf, 0, buf.size)
                        udp.receive(pkt)
                        val snap = Protocol.decodeWorldSnapshotV4(buf, pkt.length)
                        if (snap != null) {
                            // Part B1: Drop packets from different raceId and check wrap-safe sequence
                            if (activeRaceId != 0 && snap.raceId != 0 && snap.raceId != activeRaceId) continue
                            if (Protocol.isSeqNewer(snap.seq, lastWorldSeq)) {
                                lastWorldSeq = snap.seq
                                onWorldSnapshotReceived(snap, System.currentTimeMillis())
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Part B8: Exchange 7 fast ping/pong samples in lobby, take median RTT, then 1s heartbeat.
     */
    private fun startClockSyncAndPingLoop(dos: DataOutputStream) {
        pingJob?.cancel()
        pingJob = ioScope.launch {
            var count = 0
            while (isActive && _isConnected.value) {
                val bb = ByteBuffer.allocate(11)
                bb.put(Protocol.OP_PING)
                bb.putLong(System.currentTimeMillis())
                bb.putShort(medianRttMs.coerceIn(1, 999).toShort())
                sendFrame(dos, bb.array())
                count++
                delay(if (count < 7) 120L else GameConstants.HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    fun toggleReady() {
        val next = !_isReady.value
        _isReady.value = next
        val dos = tcpOut ?: return
        ioScope.launch {
            sendFrame(dos, byteArrayOf(Protocol.OP_CLIENT_READY, if (next) 1 else 0))
        }
    }

    /**
     * Part B2 & B5: Records an item press edge to be sent in the next 3 UDP packets + reliable TCP frame.
     */
    fun notifyLocalItemActivated(item: ItemType, fireBackward: Boolean) {
        pendingItemBackwardFlag = fireBackward
        pendingItemEdgeRepeats = 3
        val dos = tcpOut ?: return
        val buf = ByteBuffer.allocate(7)
        buf.put(Protocol.OP_ITEM_ACTION)
        buf.putInt(activeRaceId)
        buf.put(item.id.toByte())
        buf.put(if (fireBackward) 1.toByte() else 0.toByte())
        ioScope.launch {
            sendFrame(dos, buf.array())
        }
    }

    fun updateItemHeldState(held: Boolean) {
        latestItemHeldFlag = held
    }

    /**
     * Part H9: Notify host when client minimizes/resumes the app during multiplayer.
     */
    fun setAwayStatus(away: Boolean) {
        val dos = tcpOut ?: return
        ioScope.launch {
            sendFrame(dos, byteArrayOf(Protocol.OP_CLIENT_AWAY, if (away) 1 else 0))
        }
    }

    /**
     * Part B2 & B11: Sends ClientKartState at 30 Hz via the long-lived UDP sender channel.
     */
    fun sendOwnKartStateUdp(pkt: UdpKartStatePacket) {
        if (!_isConnected.value) return
        clientUdpSeq++
        val itemPressedNow = if (pendingItemEdgeRepeats > 0) {
            pendingItemEdgeRepeats--
            true
        } else {
            pkt.itemPressedEdge
        }
        val edges = UdpKartStatePacket.buildInputEdges(
            itemPressed = itemPressedNow,
            itemBackward = if (itemPressedNow) pendingItemBackwardFlag else pkt.itemBackwardEdge,
            itemHeld = latestItemHeldFlag || pkt.itemHeldEdge
        )
        val outgoing = pkt.copy(
            seq = clientUdpSeq,
            raceId = activeRaceId,
            inputEdges = edges
        )
        val bytes = Protocol.encodeUdpClientKart(outgoing)
        udpSendChannel.trySend(bytes)
    }

    private fun sendFrame(dos: DataOutputStream, bytes: ByteArray) {
        synchronized(dos) {
            runCatching {
                dos.writeShort(bytes.size)
                dos.write(bytes)
                dos.flush()
            }
        }
    }

    fun disconnect(sendLeaveFrame: Boolean = true) {
        val dos = tcpOut
        if (sendLeaveFrame && dos != null && _isConnected.value) {
            runCatching {
                sendFrame(dos, byteArrayOf(Protocol.OP_CLIENT_LEAVE))
            }
        }
        _isConnected.value = false
        _isReady.value = false
        tcpJob?.cancel()
        udpRecvJob?.cancel()
        udpSendJob?.cancel()
        pingJob?.cancel()
        udpSendChannel.close()
        runCatching { tcpSocket?.close() }
        runCatching { udpSocket?.close() }
        tcpSocket = null
        udpSocket = null
        tcpOut = null
    }
}
