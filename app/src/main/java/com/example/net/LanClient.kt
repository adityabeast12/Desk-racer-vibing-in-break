package com.example.net

import com.example.game.GameConstants
import com.example.game.ItemMode
import com.example.game.KartBodyType
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
    var onCountdownStartedFromHost: (trackId: Int, laps: Int, cpuFill: Boolean, itemMode: ItemMode, assignedSlot: Int, slots: List<LobbySlotInfo>) -> Unit,
    var onHostLeftRoom: (reason: String) -> Unit,
    var onRemoteSnapshotReceived: (slotIndex: Int, sample: RemoteKartInterpSample) -> Unit,
    var onRaceResultsReceived: (List<com.example.game.RacerStanding>) -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    private val _itemMode = MutableStateFlow(ItemMode.NORMAL)
    val itemMode: StateFlow<ItemMode> = _itemMode.asStateFlow()

    private val _slots = MutableStateFlow<List<LobbySlotInfo>>(emptyList())
    val slots: StateFlow<List<LobbySlotInfo>> = _slots.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private var tcpSocket: Socket? = null
    private var tcpOut: DataOutputStream? = null
    private var udpSocket: DatagramSocket? = null
    private var hostInetAddress: InetAddress? = null
    private var tcpJob: Job? = null
    private var udpJob: Job? = null
    private var pingJob: Job? = null
    private var clientUdpSeq = 0
    private var lastWorldSeq = -1

    fun connectToHost(
        hostIpRaw: String,
        tcpPort: Int = GameConstants.TCP_CONTROL_PORT,
        playerName: String,
        colorIndex: Int,
        bodyType: KartBodyType
    ) {
        disconnect()
        val cleanIp = hostIpRaw.trim().substringBefore(":")
        if (cleanIp.isEmpty()) {
            _statusMessage.value = "Enter a valid Host IP address."
            return
        }
        _statusMessage.value = "Connecting to $cleanIp:$tcpPort..."

        tcpJob = scope.launch {
            try {
                val sock = Socket()
                sock.tcpNoDelay = true
                sock.connect(InetSocketAddress(cleanIp, tcpPort), 3500)
                tcpSocket = sock
                hostInetAddress = sock.inetAddress
                val dis = DataInputStream(sock.getInputStream())
                val dos = DataOutputStream(sock.getOutputStream())
                tcpOut = dos

                // Send JOIN_REQ
                val req = ByteBuffer.allocate(48)
                req.put(Protocol.OP_JOIN_REQ)
                req.put(GameConstants.PROTOCOL_VERSION)
                Protocol.putShortString(req, playerName)
                req.put(colorIndex.toByte())
                req.put(bodyType.id.toByte())
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
                    _assignedSlot.value = resp.getOrNull(1)?.toInt() ?: 1
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
                disconnect()
                if (wasConnected) {
                    onHostLeftRoom("Host left")
                } else {
                    _statusMessage.value = "Not on Wi-Fi: join the host's hotspot first (or verify IP $cleanIp)"
                }
            }
        }
    }

    private fun handleServerControlFrame(frame: ByteArray) {
        if (frame.isEmpty()) return
        val buf = ByteBuffer.wrap(frame)
        when (buf.get()) {
            Protocol.OP_LOBBY_STATE -> {
                _roomCode.value = Protocol.getShortString(buf)
                _trackId.value = buf.get().toInt()
                _laps.value = buf.get().toInt()
                _cpuFill.value = buf.get().toInt() != 0
                _itemMode.value = ItemMode.fromId(buf.get().toInt())
                val count = buf.get().toInt()
                val parsed = ArrayList<LobbySlotInfo>(count)
                for (i in 0 until count) {
                    val slotIdx = buf.get().toInt()
                    val name = Protocol.getShortString(buf)
                    val color = buf.get().toInt()
                    val body = KartBodyType.fromId(buf.get().toInt())
                    val human = buf.get().toInt() != 0
                    val ready = buf.get().toInt() != 0
                    val ping = buf.short.toInt()
                    parsed.add(LobbySlotInfo(slotIdx, name, color, body, human, ready, ping))
                }
                _slots.value = parsed
            }
            Protocol.OP_START_COUNTDOWN -> {
                val startAtHostMs = buf.long
                val tId = buf.get().toInt()
                val lps = buf.get().toInt()
                val cpu = buf.get().toInt() != 0
                val mode = ItemMode.fromId(buf.get().toInt())
                onCountdownStartedFromHost(tId, lps, cpu, mode, _assignedSlot.value, _slots.value)
            }
            Protocol.OP_RACE_RESULTS -> {
                val standings = Protocol.decodeRaceResultsFrame(buf, _assignedSlot.value)
                onRaceResultsReceived(standings)
            }
            Protocol.OP_HOST_LEFT -> {
                disconnect()
                onHostLeftRoom("Host left")
            }
        }
    }

    private fun startUdpClientChannel() {
        udpJob?.cancel()
        udpJob = scope.launch {
            try {
                val udp = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(GameConstants.UDP_STATE_PORT))
                    soTimeout = 1000
                }
                udpSocket = udp
                val buf = ByteArray(384)
                val pkt = DatagramPacket(buf, buf.size)
                while (isActive && !udp.isClosed) {
                    try {
                        pkt.setData(buf, 0, buf.size)
                        udp.receive(pkt)
                        if (pkt.length > 6 && buf[0] == Protocol.UDP_WORLD_SNAPSHOT) {
                            val bb = ByteBuffer.wrap(buf, 1, pkt.length - 1)
                            val seq = bb.int
                            if (seq > lastWorldSeq) {
                                lastWorldSeq = seq
                                val count = bb.get().toInt()
                                val now = System.currentTimeMillis()
                                for (i in 0 until count) {
                                    val slot = bb.get().toInt()
                                    val x = bb.float
                                    val y = bb.float
                                    val z = bb.float
                                    val h = bb.float
                                    val spd = bb.float
                                    val spark = bb.get().toInt()
                                    val boost = bb.get().toInt() != 0
                                    if (slot != _assignedSlot.value) {
                                        onRemoteSnapshotReceived(
                                            slot,
                                            RemoteKartInterpSample(now, x, y, z, h, spd, spark, boost)
                                        )
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun startClockSyncAndPingLoop(dos: DataOutputStream) {
        pingJob?.cancel()
        pingJob = scope.launch {
            // Initial 5 fast pings for clock sync, then 1s heartbeat
            var count = 0
            while (isActive && _isConnected.value) {
                val bb = ByteBuffer.allocate(9)
                bb.put(Protocol.OP_PING)
                bb.putLong(System.currentTimeMillis())
                sendFrame(dos, bb.array())
                count++
                delay(if (count < 5) 180L else GameConstants.HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    fun toggleReady() {
        val next = !_isReady.value
        _isReady.value = next
        val dos = tcpOut ?: return
        scope.launch {
            sendFrame(dos, byteArrayOf(Protocol.OP_CLIENT_READY, if (next) 1 else 0))
        }
    }

    fun sendOwnKartStateUdp(pkt: UdpKartStatePacket) {
        val udp = udpSocket ?: return
        val hostAddr = hostInetAddress ?: return
        clientUdpSeq++
        val bytes = Protocol.encodeUdpClientKart(pkt.copy(seq = clientUdpSeq))
        scope.launch {
            runCatching {
                val dp = DatagramPacket(bytes, bytes.size, hostAddr, GameConstants.UDP_STATE_PORT)
                udp.send(dp)
            }
        }
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

    fun disconnect() {
        _isConnected.value = false
        _isReady.value = false
        tcpJob?.cancel()
        udpJob?.cancel()
        pingJob?.cancel()
        runCatching { tcpSocket?.close() }
        runCatching { udpSocket?.close() }
        tcpSocket = null
        udpSocket = null
        tcpOut = null
    }
}
