package com.example.net

import com.example.game.GameConstants
import com.example.game.ItemMode
import com.example.game.ItemType
import com.example.game.KartBodyType
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class DiscoveredRoomBeacon(
    val protocolVersion: Byte,
    val roomCode: String,
    val hostName: String,
    val hostIp: String,
    val tcpPort: Int,
    val trackId: Int,
    val playerCount: Int,
    val maxPlayers: Int,
    val raceInProgress: Boolean,
    val lastSeenMs: Long = System.currentTimeMillis(),
    val pingMs: Int = 12
)

data class LobbySlotInfo(
    val slotIndex: Int,
    val playerName: String,
    val colorIndex: Int,
    val bodyType: KartBodyType,
    val isHuman: Boolean,
    val isReady: Boolean,
    val pingMs: Int = 8
)

data class UdpKartStatePacket(
    val seq: Int,
    val slotIndex: Int,
    val raceTimeMs: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val headingRad: Float,
    val speed: Float,
    val sparkTier: Int,
    val isBoosting: Boolean,
    val heldItem: ItemType
)

object Protocol {
    // UDP Discovery Magic Headers
    private val MAGIC_BEACON = byteArrayOf('D'.code.toByte(), 'R'.code.toByte(), 'C'.code.toByte(), 'R'.code.toByte())
    private val MAGIC_PROBE = byteArrayOf('D'.code.toByte(), 'R'.code.toByte(), 'C'.code.toByte(), '?'.code.toByte())

    // TCP Control Message Opcodes
    const val OP_JOIN_REQ: Byte = 1
    const val OP_JOIN_ACCEPT: Byte = 2
    const val OP_JOIN_REJECT: Byte = 3
    const val OP_LOBBY_STATE: Byte = 4
    const val OP_CLIENT_READY: Byte = 5
    const val OP_PING: Byte = 6
    const val OP_PONG: Byte = 7
    const val OP_START_COUNTDOWN: Byte = 8
    const val OP_ITEM_ACTION: Byte = 9
    const val OP_HOST_LEFT: Byte = 10
    const val OP_REMATCH: Byte = 11
    const val OP_RACE_RESULTS: Byte = 12

    // UDP Fast State Opcodes
    const val UDP_CLIENT_KART: Byte = 20
    const val UDP_WORLD_SNAPSHOT: Byte = 21

    fun encodeDiscoveryBeacon(
        roomCode: String,
        hostName: String,
        trackId: Int,
        players: Int,
        maxPlayers: Int,
        tcpPort: Int,
        raceInProgress: Boolean
    ): ByteArray {
        val buf = ByteBuffer.allocate(64)
        buf.put(MAGIC_BEACON)
        buf.put(GameConstants.PROTOCOL_VERSION)
        putShortString(buf, roomCode.take(4))
        putShortString(buf, hostName.take(10))
        buf.put(trackId.toByte())
        buf.put(players.toByte())
        buf.put(maxPlayers.toByte())
        buf.putShort(tcpPort.toShort())
        buf.put(if (raceInProgress) 1.toByte() else 0.toByte())
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    fun encodeDiscoveryProbe(): ByteArray {
        val buf = ByteBuffer.allocate(8)
        buf.put(MAGIC_PROBE)
        buf.put(GameConstants.PROTOCOL_VERSION)
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    fun isDiscoveryProbe(data: ByteArray, length: Int): Boolean {
        if (length < 5) return false
        return data[0] == MAGIC_PROBE[0] &&
            data[1] == MAGIC_PROBE[1] &&
            data[2] == MAGIC_PROBE[2] &&
            data[3] == MAGIC_PROBE[3]
    }

    fun decodeDiscoveryBeacon(data: ByteArray, length: Int, senderIp: String): DiscoveredRoomBeacon? {
        if (length < 12) return null
        if (data[0] != MAGIC_BEACON[0] || data[1] != MAGIC_BEACON[1] ||
            data[2] != MAGIC_BEACON[2] || data[3] != MAGIC_BEACON[3]
        ) {
            return null
        }
        return try {
            val buf = ByteBuffer.wrap(data, 4, length - 4)
            val version = buf.get()
            val roomCode = getShortString(buf)
            val hostName = getShortString(buf)
            val trackId = buf.get().toInt()
            val players = buf.get().toInt()
            val maxPlayers = buf.get().toInt()
            val tcpPort = buf.short.toInt() and 0xFFFF
            val inProgress = buf.get().toInt() != 0
            DiscoveredRoomBeacon(
                protocolVersion = version,
                roomCode = roomCode,
                hostName = hostName,
                hostIp = senderIp,
                tcpPort = tcpPort,
                trackId = trackId,
                playerCount = players,
                maxPlayers = maxPlayers,
                raceInProgress = inProgress
            )
        } catch (_: Exception) {
            null
        }
    }

    fun encodeUdpClientKart(pkt: UdpKartStatePacket): ByteArray {
        val buf = ByteBuffer.allocate(36)
        buf.put(UDP_CLIENT_KART)
        buf.put(pkt.slotIndex.toByte())
        buf.putInt(pkt.seq)
        buf.putInt(pkt.raceTimeMs)
        buf.putFloat(pkt.x)
        buf.putFloat(pkt.y)
        buf.putFloat(pkt.z)
        buf.putFloat(pkt.headingRad)
        buf.putFloat(pkt.speed)
        val flags = (pkt.sparkTier and 0x03) or
            (if (pkt.isBoosting) 0x04 else 0) or
            ((pkt.heldItem.id and 0x0F) shl 3)
        buf.put(flags.toByte())
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    fun decodeUdpClientKart(data: ByteArray, length: Int): UdpKartStatePacket? {
        if (length < 31 || data[0] != UDP_CLIENT_KART) return null
        return try {
            val buf = ByteBuffer.wrap(data, 1, length - 1)
            val slot = buf.get().toInt()
            val seq = buf.int
            val timeMs = buf.int
            val x = buf.float
            val y = buf.float
            val z = buf.float
            val heading = buf.float
            val speed = buf.float
            val flags = buf.get().toInt() and 0xFF
            val sparkTier = flags and 0x03
            val boosting = (flags and 0x04) != 0
            val item = ItemType.fromId((flags ushr 3) and 0x0F)
            UdpKartStatePacket(seq, slot, timeMs, x, y, z, heading, speed, sparkTier, boosting, item)
        } catch (_: Exception) {
            null
        }
    }

    fun putShortString(buf: ByteBuffer, str: String) {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        val len = bytes.size.coerceAtMost(32)
        buf.put(len.toByte())
        buf.put(bytes, 0, len)
    }

    fun getShortString(buf: ByteBuffer): String {
        val len = (buf.get().toInt() and 0xFF).coerceAtMost(buf.remaining())
        val bytes = ByteArray(len)
        buf.get(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun encodeLobbyStateFrame(
        roomCode: String,
        trackId: Int,
        laps: Int,
        cpuFill: Boolean,
        itemMode: ItemMode,
        slots: List<LobbySlotInfo>
    ): ByteArray {
        val buf = ByteBuffer.allocate(320)
        buf.put(OP_LOBBY_STATE)
        putShortString(buf, roomCode)
        buf.put(trackId.toByte())
        buf.put(laps.toByte())
        buf.put(if (cpuFill) 1.toByte() else 0.toByte())
        buf.put(itemMode.id.toByte())
        buf.put(slots.size.toByte())
        for (s in slots) {
            buf.put(s.slotIndex.toByte())
            putShortString(buf, s.playerName)
            buf.put(s.colorIndex.toByte())
            buf.put(s.bodyType.id.toByte())
            buf.put(if (s.isHuman) 1.toByte() else 0.toByte())
            buf.put(if (s.isReady) 1.toByte() else 0.toByte())
            buf.putShort(s.pingMs.toShort())
        }
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    fun encodeRaceResultsFrame(standings: List<com.example.game.RacerStanding>): ByteArray {
        val buf = ByteBuffer.allocate(384)
        buf.put(OP_RACE_RESULTS)
        buf.put(standings.size.toByte())
        for (st in standings) {
            buf.put(st.slotIndex.toByte())
            buf.put(st.position.toByte())
            putShortString(buf, st.name)
            buf.putLong(st.colorHex)
            buf.put(st.bodyType.id.toByte())
            buf.put(if (st.isHuman) 1.toByte() else 0.toByte())
            buf.putLong(st.totalTimeMs)
            buf.putLong(st.bestLapMs)
            buf.put(if (st.isDnf) 1.toByte() else 0.toByte())
        }
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    fun decodeRaceResultsFrame(buf: ByteBuffer, localSlot: Int): List<com.example.game.RacerStanding> {
        val count = (buf.get().toInt() and 0xFF).coerceIn(0, GameConstants.MAX_RACERS)
        val list = ArrayList<com.example.game.RacerStanding>(count)
        for (i in 0 until count) {
            val slot = buf.get().toInt()
            val pos = buf.get().toInt()
            val name = getShortString(buf)
            val color = buf.long
            val body = KartBodyType.fromId(buf.get().toInt())
            val isHuman = buf.get().toInt() != 0
            val totalMs = buf.long
            val bestLapMs = buf.long
            val isDnf = buf.get().toInt() != 0
            list.add(
                com.example.game.RacerStanding(
                    slotIndex = slot,
                    position = pos,
                    name = name,
                    colorHex = color,
                    bodyType = body,
                    isHuman = isHuman,
                    isLocal = (slot == localSlot),
                    totalTimeMs = totalMs,
                    bestLapMs = bestLapMs,
                    isDnf = isDnf
                )
            )
        }
        return list
    }
}
