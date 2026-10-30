package com.example.net

import android.content.Context
import android.net.wifi.WifiManager
import com.example.game.GameConstants
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
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

class LanDiscovery(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _discoveredRooms = MutableStateFlow<List<DiscoveredRoomBeacon>>(emptyList())
    val discoveredRooms: StateFlow<List<DiscoveredRoomBeacon>> = _discoveredRooms.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var beaconJob: Job? = null
    private var listenJob: Job? = null
    private var probeJob: Job? = null
    private var udpSocket: DatagramSocket? = null

    @Volatile
    private var hostingBeaconProvider: (() -> ByteArray)? = null

    fun startHostBeacons(beaconSupplier: () -> ByteArray) {
        hostingBeaconProvider = beaconSupplier
        ensureDiscoverySocketRunning()
        beaconJob?.cancel()
        beaconJob = scope.launch {
            while (isActive) {
                val payload = hostingBeaconProvider?.invoke()
                if (payload != null) {
                    val targets = NetUtils.getAllBroadcastTargets()
                    for (addr in targets) {
                        runCatching {
                            val pkt = DatagramPacket(payload, payload.size, addr, GameConstants.UDP_DISCOVERY_PORT)
                            udpSocket?.send(pkt)
                        }
                    }
                }
                delay(GameConstants.BEACON_INTERVAL_MS)
            }
        }
    }

    fun stopHostBeacons() {
        hostingBeaconProvider = null
        beaconJob?.cancel()
        beaconJob = null
    }

    fun startClientDiscovery() {
        ensureDiscoverySocketRunning()
        probeJob?.cancel()
        probeJob = scope.launch {
            val probeBytes = Protocol.encodeDiscoveryProbe()
            while (isActive) {
                val gw = NetUtils.detectWifiGatewayIp(context)
                val targets = NetUtils.getAllBroadcastTargets(gw)
                for (addr in targets) {
                    runCatching {
                        val pkt = DatagramPacket(probeBytes, probeBytes.size, addr, GameConstants.UDP_DISCOVERY_PORT)
                        udpSocket?.send(pkt)
                    }
                }
                // Prune rooms not heard from for 3 seconds
                val now = System.currentTimeMillis()
                _discoveredRooms.update { list ->
                    list.filter { now - it.lastSeenMs <= GameConstants.ROOM_EXPIRY_MS }
                }
                delay(GameConstants.PROBE_INTERVAL_MS)
            }
        }
    }

    fun stopClientDiscovery() {
        probeJob?.cancel()
        probeJob = null
        _discoveredRooms.value = emptyList()
    }

    private fun ensureDiscoverySocketRunning() {
        if (multicastLock == null) {
            multicastLock = NetUtils.acquireMulticastLock(context)
        }
        if (listenJob?.isActive == true && udpSocket?.isClosed == false) return

        listenJob = scope.launch {
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(GameConstants.UDP_DISCOVERY_PORT))
                    soTimeout = 1000
                }
                udpSocket = socket
                val buffer = ByteArray(256)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive && !socket.isClosed) {
                    try {
                        packet.setData(buffer, 0, buffer.size)
                        socket.receive(packet)
                        val senderIp = packet.address?.hostAddress ?: continue
                        val myIps = NetUtils.detectActiveSubnets().map { it.ipAddress }.toSet()
                        if (senderIp in myIps) continue // Ignore our own packets

                        val len = packet.length
                        if (Protocol.isDiscoveryProbe(buffer, len)) {
                            // Unicast our host beacon back to the probing client immediately
                            val replyBytes = hostingBeaconProvider?.invoke()
                            if (replyBytes != null) {
                                runCatching {
                                    val replyPkt = DatagramPacket(
                                        replyBytes,
                                        replyBytes.size,
                                        InetAddress.getByName(senderIp),
                                        GameConstants.UDP_DISCOVERY_PORT
                                    )
                                    socket.send(replyPkt)
                                }
                            }
                        } else {
                            val beacon = Protocol.decodeDiscoveryBeacon(buffer, len, senderIp)
                            if (beacon != null) {
                                val now = System.currentTimeMillis()
                                _discoveredRooms.update { current ->
                                    val filtered = current.filter {
                                        it.hostIp != beacon.hostIp && (now - it.lastSeenMs) <= GameConstants.ROOM_EXPIRY_MS
                                    }
                                    (filtered + beacon.copy(lastSeenMs = now))
                                        .sortedByDescending { it.lastSeenMs }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Socket timeout; loop continues
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun shutdown() {
        stopHostBeacons()
        stopClientDiscovery()
        listenJob?.cancel()
        runCatching { udpSocket?.close() }
        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }
        multicastLock = null
    }
}
