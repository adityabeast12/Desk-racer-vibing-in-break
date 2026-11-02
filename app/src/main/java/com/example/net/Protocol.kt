package com.example.net

import com.example.game.CcClass
import com.example.game.CpuDifficulty
import com.example.game.GameConstants
import com.example.game.ItemMode
import com.example.game.ItemType
import com.example.game.KartBodyType
import com.example.game.KartPhysics
import com.example.game.ProjectileKind
import com.example.game.RacePhase
import com.example.game.RacerStanding
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.random.Random

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
    val pingMs: Int = 8,
    val liveryIndex: Int = 0,
    val playerId: String = "",
    val isAway: Boolean = false
)

data class LobbyStatePayload(
    val roomCode: String,
    val raceId: Int,
    val trackId: Int,
    val laps: Int,
    val cpuFill: Boolean,
    val botCount: Int,
    val botDifficulty: CpuDifficulty,
    val ccClass: CcClass,
    val itemMode: ItemMode,
    val slots: List<LobbySlotInfo>
)

data class StartCountdownPayload(
    val raceId: Int,
    val startAtHostMs: Long,
    val trackId: Int,
    val laps: Int,
    val cpuFill: Boolean,
    val botCount: Int,
    val botDifficulty: CpuDifficulty,
    val ccClass: CcClass,
    val itemMode: ItemMode,
    val slots: List<LobbySlotInfo>
)

/**
 * Part B2: ClientKartState v4 sent at 30 Hz via UDP.
 * Includes inputEdges byte (bit 0 = itemPressed, bit 1 = itemBackward, bit 2 = itemHeld),
 * repeated across 3 consecutive packets so single packet loss never drops an item press.
 */
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
    val heldItem: ItemType,
    val raceId: Int = 0,
    val isDrifting: Boolean = false,
    val steer: Float = 0f,
    val inputEdges: Int = 0
) {
    val itemPressedEdge: Boolean get() = (inputEdges and EDGE_ITEM_PRESSED) != 0
    val itemBackwardEdge: Boolean get() = (inputEdges and EDGE_ITEM_BACKWARD) != 0
    val itemHeldEdge: Boolean get() = (inputEdges and EDGE_ITEM_HELD) != 0

    companion object {
        const val EDGE_ITEM_PRESSED = 0x01
        const val EDGE_ITEM_BACKWARD = 0x02
        const val EDGE_ITEM_HELD = 0x04

        fun buildInputEdges(itemPressed: Boolean, itemBackward: Boolean, itemHeld: Boolean): Int {
            var bits = 0
            if (itemPressed) bits = bits or EDGE_ITEM_PRESSED
            if (itemBackward) bits = bits or EDGE_ITEM_BACKWARD
            if (itemHeld) bits = bits or EDGE_ITEM_HELD
            return bits
        }
    }
}

/**
 * Part B3: World Snapshot v4 per-kart state (host -> client, UDP, 30 Hz).
 */
data class WorldSnapshotKartV4(
    val slot: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val headingRad: Float,
    val speed: Float,
    val boosting: Boolean,
    val drifting: Boolean,
    val sparkTier: Int,
    val spinningOut: Boolean,
    val invulnerable: Boolean,
    val finished: Boolean,
    val respawning: Boolean,
    val shield: Boolean,
    val orbitingTacksCount: Int = 0,
    val heldItem: ItemType,
    val rouletteRemainingMs: Int,
    val coins: Int,
    val lap: Int,
    val racePosition: Int,
    val raceDistance: Float
)

data class WorldSnapshotProjectileV4(
    val id: Int,
    val kind: ProjectileKind,
    val ownerSlot: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val headingRad: Float
)

/**
 * Part B3: World Snapshot v4 (<= 1200 bytes).
 */
data class WorldSnapshotV4(
    val protocolVersion: Byte = GameConstants.PROTOCOL_VERSION,
    val raceId: Int,
    val seq: Int,
    val hostRaceTimeMs: Int,
    val phase: RacePhase,
    val karts: List<WorldSnapshotKartV4>,
    val activeBoxesMask: Int,
    val activeCoinsMask: Int,
    val projectiles: List<WorldSnapshotProjectileV4>,
    val hazardPhaseSec: Float
)

/**
 * Part B4: Ordered reliable events sent over TCP (and mirrored in WorldSnapshotV4).
 */
sealed class ReliableRaceEvent {
    abstract val raceId: Int

    data class ItemGranted(
        override val raceId: Int,
        val slot: Int,
        val item: ItemType,
        val rouletteMs: Int
    ) : ReliableRaceEvent()

    data class ItemUsed(
        override val raceId: Int,
        val slot: Int,
        val item: ItemType,
        val backward: Boolean
    ) : ReliableRaceEvent()

    data class ProjectileSpawned(
        override val raceId: Int,
        val id: Int,
        val kind: ProjectileKind,
        val ownerSlot: Int,
        val x: Float,
        val y: Float,
        val z: Float,
        val vx: Float,
        val vz: Float,
        val headingRad: Float
    ) : ReliableRaceEvent()

    data class Hit(
        override val raceId: Int,
        val victimSlot: Int,
        val attackerSlot: Int,
        val kind: ProjectileKind
    ) : ReliableRaceEvent()

    data class CoinPicked(
        override val raceId: Int,
        val slot: Int,
        val totalCoins: Int
    ) : ReliableRaceEvent()

    data class LapCompleted(
        override val raceId: Int,
        val slot: Int,
        val lap: Int,
        val lapTimeMs: Long
    ) : ReliableRaceEvent()

    data class Finished(
        override val raceId: Int,
        val slot: Int,
        val position: Int,
        val finishTimeMs: Long
    ) : ReliableRaceEvent()

    data class RaceEndCountdown(
        override val raceId: Int,
        val secondsLeft: Int
    ) : ReliableRaceEvent()

    data class Results(
        override val raceId: Int,
        val standings: List<RacerStanding>
    ) : ReliableRaceEvent()

    data class Rematch(
        override val raceId: Int,
        val trackId: Int,
        val laps: Int,
        val cpuFill: Boolean,
        val botCount: Int,
        val botDifficulty: CpuDifficulty,
        val ccClass: CcClass,
        val itemMode: ItemMode
    ) : ReliableRaceEvent()
}

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
    const val OP_CLIENT_LEAVE: Byte = 13
    const val OP_CLIENT_AWAY: Byte = 14
    const val OP_RELIABLE_EVENT: Byte = 15

    // Reliable Event Sub-opcodes (Part B4)
    private const val EVT_ITEM_GRANTED: Byte = 1
    private const val EVT_ITEM_USED: Byte = 2
    private const val EVT_PROJECTILE_SPAWNED: Byte = 3
    private const val EVT_HIT: Byte = 4
    private const val EVT_COIN: Byte = 5
    private const val EVT_LAP: Byte = 6
    private const val EVT_FINISHED: Byte = 7
    private const val EVT_RACE_END_COUNTDOWN: Byte = 8
    private const val EVT_RESULTS: Byte = 9
    private const val EVT_REMATCH: Byte = 10

    // UDP Fast State Opcodes
    const val UDP_CLIENT_KART: Byte = 20
    const val UDP_WORLD_SNAPSHOT: Byte = 21

    /**
     * Part B1: Wrap-safe sequence comparison ((a - b) > 0).
     */
    fun isSeqNewer(incoming: Int, lastAccepted: Int): Boolean {
        return lastAccepted == -1 || (incoming - lastAccepted) > 0
    }

    /**
     * Part B1: Generates a non-zero random 32-bit raceId.
     */
    fun generateRaceId(random: Random = Random.Default): Int {
        var id = random.nextInt()
        while (id == 0) {
            id = random.nextInt()
        }
        return id
    }

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
        putShortString(buf, hostName.take(12))
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

    /**
     * Part B1 & B2: Encode ClientKartState v4 with protocolVersion, raceId, seq, pose, steer, and inputEdges.
     */
    fun encodeUdpClientKart(pkt: UdpKartStatePacket, targetBuf: ByteArray? = null): ByteArray {
        val out = if (targetBuf != null && targetBuf.size >= 40) targetBuf else ByteArray(40)
        val buf = ByteBuffer.wrap(out)
        buf.put(UDP_CLIENT_KART)
        buf.put(GameConstants.PROTOCOL_VERSION)
        buf.putInt(pkt.raceId)
        buf.put(pkt.slotIndex.toByte())
        buf.putInt(pkt.seq)
        buf.putInt(pkt.raceTimeMs)
        buf.putFloat(pkt.x)
        buf.putFloat(pkt.y)
        buf.putFloat(pkt.z)
        buf.putFloat(pkt.headingRad)
        buf.putFloat(pkt.speed)
        buf.putShort((pkt.steer.coerceIn(-1f, 1f) * 10000f).toInt().toShort())
        val flags = (pkt.sparkTier and 0x03) or
            (if (pkt.isBoosting) 0x04 else 0) or
            (if (pkt.isDrifting) 0x08 else 0) or
            ((pkt.heldItem.id and 0x0F) shl 4)
        buf.put(flags.toByte())
        buf.put((pkt.inputEdges and 0xFF).toByte())
        return if (out.size == 40) out else out.copyOf(buf.position())
    }

    fun decodeUdpClientKart(data: ByteArray, length: Int): UdpKartStatePacket? {
        if (length < 40 || data[0] != UDP_CLIENT_KART) return null
        return try {
            val buf = ByteBuffer.wrap(data, 1, length - 1)
            val version = buf.get()
            if (version != GameConstants.PROTOCOL_VERSION) return null
            val raceId = buf.int
            val slot = buf.get().toInt() and 0xFF
            val seq = buf.int
            val timeMs = buf.int
            val x = buf.float
            val y = buf.float
            val z = buf.float
            val heading = buf.float
            val speed = buf.float
            val steer = (buf.short.toFloat() / 10000f).coerceIn(-1f, 1f)
            val flags = buf.get().toInt() and 0xFF
            val edges = buf.get().toInt() and 0xFF
            val sparkTier = flags and 0x03
            val boosting = (flags and 0x04) != 0
            val drifting = (flags and 0x08) != 0
            val item = ItemType.fromId((flags ushr 4) and 0x0F)
            UdpKartStatePacket(
                seq = seq,
                slotIndex = slot,
                raceTimeMs = timeMs,
                x = x,
                y = y,
                z = z,
                headingRad = heading,
                speed = speed,
                sparkTier = sparkTier,
                isBoosting = boosting,
                heldItem = item,
                raceId = raceId,
                isDrifting = drifting,
                steer = steer,
                inputEdges = edges
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Part B3: Encode WorldSnapshotV4 (<= 1200 bytes).
     */
    fun encodeWorldSnapshotV4(snap: WorldSnapshotV4): ByteArray {
        val buf = ByteBuffer.allocate(1024)
        buf.put(UDP_WORLD_SNAPSHOT)
        buf.put(snap.protocolVersion)
        buf.putInt(snap.raceId)
        buf.putInt(snap.seq)
        buf.putInt(snap.hostRaceTimeMs)
        buf.put(snap.phase.ordinal.toByte())

        val kartCount = snap.karts.size.coerceAtMost(GameConstants.MAX_RACERS)
        buf.put(kartCount.toByte())
        for (i in 0 until kartCount) {
            val k = snap.karts[i]
            buf.put(k.slot.toByte())
            buf.putFloat(k.x)
            buf.putFloat(k.y)
            buf.putFloat(k.z)
            val scaledHeading = (KartPhysics.wrapAngle(k.headingRad) * 10000f)
                .toInt().coerceIn(-32767, 32767).toShort()
            val scaledSpeed = (k.speed * 100f)
                .toInt().coerceIn(-32767, 32767).toShort()
            buf.putShort(scaledHeading)
            buf.putShort(scaledSpeed)

            var flags = 0
            if (k.boosting) flags = flags or (1 shl 0)
            if (k.drifting) flags = flags or (1 shl 1)
            flags = flags or ((k.sparkTier and 0x03) shl 2)
            if (k.spinningOut) flags = flags or (1 shl 4)
            if (k.invulnerable) flags = flags or (1 shl 5)
            if (k.finished) flags = flags or (1 shl 6)
            if (k.respawning) flags = flags or (1 shl 7)
            if (k.shield) flags = flags or (1 shl 8)
            flags = flags or ((k.orbitingTacksCount.coerceIn(0, 3) and 0x03) shl 9)
            buf.putShort(flags.toShort())

            buf.put(k.heldItem.id.toByte())
            buf.putShort(k.rouletteRemainingMs.coerceIn(0, 65535).toShort())
            buf.put(k.coins.coerceIn(0, 99).toByte())
            buf.put(k.lap.coerceIn(1, 99).toByte())
            buf.put(k.racePosition.coerceIn(1, GameConstants.MAX_RACERS).toByte())
            buf.putFloat(k.raceDistance)
        }

        buf.putInt(snap.activeBoxesMask)
        buf.putInt(snap.activeCoinsMask)

        val projCount = snap.projectiles.size.coerceAtMost(16)
        buf.put(projCount.toByte())
        for (i in 0 until projCount) {
            val p = snap.projectiles[i]
            buf.putShort((p.id and 0xFFFF).toShort())
            buf.put(p.kind.id.toByte())
            buf.put(p.ownerSlot.toByte())
            buf.putFloat(p.x)
            buf.putFloat(p.y)
            buf.putFloat(p.z)
            val scaledH = (KartPhysics.wrapAngle(p.headingRad) * 10000f)
                .toInt().coerceIn(-32767, 32767).toShort()
            buf.putShort(scaledH)
        }

        buf.putFloat(snap.hazardPhaseSec)

        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    fun decodeWorldSnapshotV4(data: ByteArray, length: Int): WorldSnapshotV4? {
        if (length < 25 || data[0] != UDP_WORLD_SNAPSHOT) return null
        return try {
            val buf = ByteBuffer.wrap(data, 1, length - 1)
            val version = buf.get()
            if (version != GameConstants.PROTOCOL_VERSION) return null
            val raceId = buf.int
            val seq = buf.int
            val hostRaceTimeMs = buf.int
            val phaseOrd = (buf.get().toInt() and 0xFF).coerceIn(0, RacePhase.entries.lastIndex)
            val phase = RacePhase.entries[phaseOrd]

            val kartCount = (buf.get().toInt() and 0xFF).coerceIn(0, GameConstants.MAX_RACERS)
            val karts = ArrayList<WorldSnapshotKartV4>(kartCount)
            for (i in 0 until kartCount) {
                val slot = buf.get().toInt() and 0xFF
                val x = buf.float
                val y = buf.float
                val z = buf.float
                val headingRad = buf.short.toFloat() / 10000f
                val speed = buf.short.toFloat() / 100f
                val flags = buf.short.toInt() and 0xFFFF
                val heldItem = ItemType.fromId(buf.get().toInt() and 0xFF)
                val rouletteMs = buf.short.toInt() and 0xFFFF
                val coins = buf.get().toInt() and 0xFF
                val lap = buf.get().toInt() and 0xFF
                val pos = buf.get().toInt() and 0xFF
                val raceDist = buf.float
                karts.add(
                    WorldSnapshotKartV4(
                        slot = slot,
                        x = x,
                        y = y,
                        z = z,
                        headingRad = headingRad,
                        speed = speed,
                        boosting = (flags and (1 shl 0)) != 0,
                        drifting = (flags and (1 shl 1)) != 0,
                        sparkTier = (flags ushr 2) and 0x03,
                        spinningOut = (flags and (1 shl 4)) != 0,
                        invulnerable = (flags and (1 shl 5)) != 0,
                        finished = (flags and (1 shl 6)) != 0,
                        respawning = (flags and (1 shl 7)) != 0,
                        shield = (flags and (1 shl 8)) != 0,
                        orbitingTacksCount = (flags ushr 9) and 0x03,
                        heldItem = heldItem,
                        rouletteRemainingMs = rouletteMs,
                        coins = coins,
                        lap = lap,
                        racePosition = pos,
                        raceDistance = raceDist
                    )
                )
            }

            val boxesMask = buf.int
            val coinsMask = buf.int

            val projCount = (buf.get().toInt() and 0xFF).coerceIn(0, 16)
            val projs = ArrayList<WorldSnapshotProjectileV4>(projCount)
            for (i in 0 until projCount) {
                val id = buf.short.toInt() and 0xFFFF
                val kind = ProjectileKind.fromId(buf.get().toInt() and 0xFF)
                val owner = buf.get().toInt() and 0xFF
                val px = buf.float
                val py = buf.float
                val pz = buf.float
                val ph = buf.short.toFloat() / 10000f
                projs.add(WorldSnapshotProjectileV4(id, kind, owner, px, py, pz, ph))
            }

            val hazardPhase = if (buf.remaining() >= 4) buf.float else 0f

            WorldSnapshotV4(
                protocolVersion = version,
                raceId = raceId,
                seq = seq,
                hostRaceTimeMs = hostRaceTimeMs,
                phase = phase,
                karts = karts,
                activeBoxesMask = boxesMask,
                activeCoinsMask = coinsMask,
                projectiles = projs,
                hazardPhaseSec = hazardPhase
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Part B4: Reliable TCP Events encoding and decoding.
     */
    fun encodeReliableEvent(event: ReliableRaceEvent): ByteArray {
        val buf = ByteBuffer.allocate(512)
        buf.put(OP_RELIABLE_EVENT)
        buf.put(GameConstants.PROTOCOL_VERSION)
        buf.putInt(event.raceId)
        when (event) {
            is ReliableRaceEvent.ItemGranted -> {
                buf.put(EVT_ITEM_GRANTED)
                buf.put(event.slot.toByte())
                buf.put(event.item.id.toByte())
                buf.putShort(event.rouletteMs.coerceIn(0, 65535).toShort())
            }
            is ReliableRaceEvent.ItemUsed -> {
                buf.put(EVT_ITEM_USED)
                buf.put(event.slot.toByte())
                buf.put(event.item.id.toByte())
                buf.put(if (event.backward) 1.toByte() else 0.toByte())
            }
            is ReliableRaceEvent.ProjectileSpawned -> {
                buf.put(EVT_PROJECTILE_SPAWNED)
                buf.putShort((event.id and 0xFFFF).toShort())
                buf.put(event.kind.id.toByte())
                buf.put(event.ownerSlot.toByte())
                buf.putFloat(event.x)
                buf.putFloat(event.y)
                buf.putFloat(event.z)
                buf.putFloat(event.vx)
                buf.putFloat(event.vz)
                buf.putFloat(event.headingRad)
            }
            is ReliableRaceEvent.Hit -> {
                buf.put(EVT_HIT)
                buf.put(event.victimSlot.toByte())
                buf.put(event.attackerSlot.toByte())
                buf.put(event.kind.id.toByte())
            }
            is ReliableRaceEvent.CoinPicked -> {
                buf.put(EVT_COIN)
                buf.put(event.slot.toByte())
                buf.put(event.totalCoins.toByte())
            }
            is ReliableRaceEvent.LapCompleted -> {
                buf.put(EVT_LAP)
                buf.put(event.slot.toByte())
                buf.put(event.lap.toByte())
                buf.putLong(event.lapTimeMs)
            }
            is ReliableRaceEvent.Finished -> {
                buf.put(EVT_FINISHED)
                buf.put(event.slot.toByte())
                buf.put(event.position.toByte())
                buf.putLong(event.finishTimeMs)
            }
            is ReliableRaceEvent.RaceEndCountdown -> {
                buf.put(EVT_RACE_END_COUNTDOWN)
                buf.put(event.secondsLeft.coerceIn(0, 120).toByte())
            }
            is ReliableRaceEvent.Results -> {
                buf.put(EVT_RESULTS)
                encodeStandingsIntoBuffer(buf, event.standings)
            }
            is ReliableRaceEvent.Rematch -> {
                buf.put(EVT_REMATCH)
                buf.put(event.trackId.toByte())
                buf.put(event.laps.toByte())
                buf.put(if (event.cpuFill) 1.toByte() else 0.toByte())
                buf.put(event.botCount.toByte())
                buf.put(event.botDifficulty.id.toByte())
                buf.put(event.ccClass.id.toByte())
                buf.put(event.itemMode.id.toByte())
            }
        }
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    fun decodeReliableEvent(data: ByteArray, localSlot: Int = 0): ReliableRaceEvent? {
        if (data.size < 7 || data[0] != OP_RELIABLE_EVENT) return null
        return try {
            val buf = ByteBuffer.wrap(data, 1, data.size - 1)
            val version = buf.get()
            if (version != GameConstants.PROTOCOL_VERSION) return null
            val raceId = buf.int
            when (buf.get()) {
                EVT_ITEM_GRANTED -> ReliableRaceEvent.ItemGranted(
                    raceId = raceId,
                    slot = buf.get().toInt() and 0xFF,
                    item = ItemType.fromId(buf.get().toInt() and 0xFF),
                    rouletteMs = buf.short.toInt() and 0xFFFF
                )
                EVT_ITEM_USED -> ReliableRaceEvent.ItemUsed(
                    raceId = raceId,
                    slot = buf.get().toInt() and 0xFF,
                    item = ItemType.fromId(buf.get().toInt() and 0xFF),
                    backward = buf.get().toInt() != 0
                )
                EVT_PROJECTILE_SPAWNED -> ReliableRaceEvent.ProjectileSpawned(
                    raceId = raceId,
                    id = buf.short.toInt() and 0xFFFF,
                    kind = ProjectileKind.fromId(buf.get().toInt() and 0xFF),
                    ownerSlot = buf.get().toInt() and 0xFF,
                    x = buf.float,
                    y = buf.float,
                    z = buf.float,
                    vx = buf.float,
                    vz = buf.float,
                    headingRad = buf.float
                )
                EVT_HIT -> ReliableRaceEvent.Hit(
                    raceId = raceId,
                    victimSlot = buf.get().toInt() and 0xFF,
                    attackerSlot = buf.get().toInt() and 0xFF,
                    kind = ProjectileKind.fromId(buf.get().toInt() and 0xFF)
                )
                EVT_COIN -> ReliableRaceEvent.CoinPicked(
                    raceId = raceId,
                    slot = buf.get().toInt() and 0xFF,
                    totalCoins = buf.get().toInt() and 0xFF
                )
                EVT_LAP -> ReliableRaceEvent.LapCompleted(
                    raceId = raceId,
                    slot = buf.get().toInt() and 0xFF,
                    lap = buf.get().toInt() and 0xFF,
                    lapTimeMs = buf.long
                )
                EVT_FINISHED -> ReliableRaceEvent.Finished(
                    raceId = raceId,
                    slot = buf.get().toInt() and 0xFF,
                    position = buf.get().toInt() and 0xFF,
                    finishTimeMs = buf.long
                )
                EVT_RACE_END_COUNTDOWN -> ReliableRaceEvent.RaceEndCountdown(
                    raceId = raceId,
                    secondsLeft = buf.get().toInt() and 0xFF
                )
                EVT_RESULTS -> ReliableRaceEvent.Results(
                    raceId = raceId,
                    standings = decodeRaceResultsFrame(buf, localSlot)
                )
                EVT_REMATCH -> ReliableRaceEvent.Rematch(
                    raceId = raceId,
                    trackId = buf.get().toInt() and 0xFF,
                    laps = buf.get().toInt() and 0xFF,
                    cpuFill = buf.get().toInt() != 0,
                    botCount = buf.get().toInt() and 0xFF,
                    botDifficulty = CpuDifficulty.fromId(buf.get().toInt() and 0xFF),
                    ccClass = CcClass.fromId(buf.get().toInt() and 0xFF),
                    itemMode = ItemMode.fromId(buf.get().toInt() and 0xFF)
                )
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun putShortString(buf: ByteBuffer, str: String) {
        val bytes = str.toByteArray(StandardCharsets.UTF_8)
        val len = bytes.size.coerceAtMost(36)
        buf.put(len.toByte())
        buf.put(bytes, 0, len)
    }

    fun getShortString(buf: ByteBuffer): String {
        val len = (buf.get().toInt() and 0xFF).coerceAtMost(buf.remaining())
        val bytes = ByteArray(len)
        buf.get(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    /**
     * Part B9: Lobby state carries raceId, trackId, laps, cpuFill, botCount, botDifficulty, ccClass, itemMode, and slots (including liveryIndex & playerId).
     */
    fun encodeLobbyStateFrame(
        roomCode: String,
        raceId: Int = 0,
        trackId: Int,
        laps: Int,
        cpuFill: Boolean,
        botCount: Int = 7,
        botDifficulty: CpuDifficulty = CpuDifficulty.PRO,
        ccClass: CcClass = CcClass.CC_150,
        itemMode: ItemMode,
        slots: List<LobbySlotInfo>
    ): ByteArray {
        val buf = ByteBuffer.allocate(512)
        buf.put(OP_LOBBY_STATE)
        buf.put(GameConstants.PROTOCOL_VERSION)
        buf.putInt(raceId)
        putShortString(buf, roomCode)
        buf.put(trackId.toByte())
        buf.put(laps.toByte())
        buf.put(if (cpuFill) 1.toByte() else 0.toByte())
        buf.put(botCount.toByte())
        buf.put(botDifficulty.id.toByte())
        buf.put(ccClass.id.toByte())
        buf.put(itemMode.id.toByte())
        buf.put(slots.size.toByte())
        for (s in slots) {
            buf.put(s.slotIndex.toByte())
            putShortString(buf, s.playerName)
            buf.put(s.colorIndex.toByte())
            buf.put(s.bodyType.id.toByte())
            buf.put(s.liveryIndex.toByte())
            putShortString(buf, s.playerId)
            buf.put(if (s.isHuman) 1.toByte() else 0.toByte())
            buf.put(if (s.isReady) 1.toByte() else 0.toByte())
            buf.put(if (s.isAway) 1.toByte() else 0.toByte())
            buf.putShort(s.pingMs.toShort())
        }
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    fun decodeLobbyStateFrame(buf: ByteBuffer): LobbyStatePayload? {
        return try {
            val ver = buf.get()
            if (ver != GameConstants.PROTOCOL_VERSION) return null
            val raceId = buf.int
            val roomCode = getShortString(buf)
            val trackId = buf.get().toInt() and 0xFF
            val laps = buf.get().toInt() and 0xFF
            val cpuFill = buf.get().toInt() != 0
            val botCount = buf.get().toInt() and 0xFF
            val botDifficulty = CpuDifficulty.fromId(buf.get().toInt() and 0xFF)
            val ccClass = CcClass.fromId(buf.get().toInt() and 0xFF)
            val itemMode = ItemMode.fromId(buf.get().toInt() and 0xFF)
            val count = (buf.get().toInt() and 0xFF).coerceIn(0, GameConstants.MAX_RACERS)
            val slots = ArrayList<LobbySlotInfo>(count)
            for (i in 0 until count) {
                val slotIdx = buf.get().toInt() and 0xFF
                val name = getShortString(buf)
                val color = buf.get().toInt() and 0xFF
                val body = KartBodyType.fromId(buf.get().toInt() and 0xFF)
                val livery = buf.get().toInt() and 0xFF
                val playerId = getShortString(buf)
                val human = buf.get().toInt() != 0
                val ready = buf.get().toInt() != 0
                val away = buf.get().toInt() != 0
                val ping = buf.short.toInt()
                slots.add(LobbySlotInfo(slotIdx, name, color, body, human, ready, ping, livery, playerId, away))
            }
            LobbyStatePayload(roomCode, raceId, trackId, laps, cpuFill, botCount, botDifficulty, ccClass, itemMode, slots)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeStartCountdownFrame(payload: StartCountdownPayload): ByteArray {
        val buf = ByteBuffer.allocate(512)
        buf.put(OP_START_COUNTDOWN)
        buf.put(GameConstants.PROTOCOL_VERSION)
        buf.putInt(payload.raceId)
        buf.putLong(payload.startAtHostMs)
        buf.put(payload.trackId.toByte())
        buf.put(payload.laps.toByte())
        buf.put(if (payload.cpuFill) 1.toByte() else 0.toByte())
        buf.put(payload.botCount.toByte())
        buf.put(payload.botDifficulty.id.toByte())
        buf.put(payload.ccClass.id.toByte())
        buf.put(payload.itemMode.id.toByte())
        buf.put(payload.slots.size.toByte())
        for (s in payload.slots) {
            buf.put(s.slotIndex.toByte())
            putShortString(buf, s.playerName)
            buf.put(s.colorIndex.toByte())
            buf.put(s.bodyType.id.toByte())
            buf.put(s.liveryIndex.toByte())
            putShortString(buf, s.playerId)
            buf.put(if (s.isHuman) 1.toByte() else 0.toByte())
            buf.put(if (s.isReady) 1.toByte() else 0.toByte())
            buf.put(if (s.isAway) 1.toByte() else 0.toByte())
            buf.putShort(s.pingMs.toShort())
        }
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    fun decodeStartCountdownFrame(buf: ByteBuffer): StartCountdownPayload? {
        return try {
            val ver = buf.get()
            if (ver != GameConstants.PROTOCOL_VERSION) return null
            val raceId = buf.int
            val startAtHostMs = buf.long
            val trackId = buf.get().toInt() and 0xFF
            val laps = buf.get().toInt() and 0xFF
            val cpuFill = buf.get().toInt() != 0
            val botCount = buf.get().toInt() and 0xFF
            val botDifficulty = CpuDifficulty.fromId(buf.get().toInt() and 0xFF)
            val ccClass = CcClass.fromId(buf.get().toInt() and 0xFF)
            val itemMode = ItemMode.fromId(buf.get().toInt() and 0xFF)
            val count = (buf.get().toInt() and 0xFF).coerceIn(0, GameConstants.MAX_RACERS)
            val slots = ArrayList<LobbySlotInfo>(count)
            for (i in 0 until count) {
                val slotIdx = buf.get().toInt() and 0xFF
                val name = getShortString(buf)
                val color = buf.get().toInt() and 0xFF
                val body = KartBodyType.fromId(buf.get().toInt() and 0xFF)
                val livery = buf.get().toInt() and 0xFF
                val playerId = getShortString(buf)
                val human = buf.get().toInt() != 0
                val ready = buf.get().toInt() != 0
                val away = buf.get().toInt() != 0
                val ping = buf.short.toInt()
                slots.add(LobbySlotInfo(slotIdx, name, color, body, human, ready, ping, livery, playerId, away))
            }
            StartCountdownPayload(raceId, startAtHostMs, trackId, laps, cpuFill, botCount, botDifficulty, ccClass, itemMode, slots)
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeStandingsIntoBuffer(buf: ByteBuffer, standings: List<RacerStanding>) {
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
    }

    fun encodeRaceResultsFrame(standings: List<RacerStanding>, raceId: Int = 0): ByteArray {
        val buf = ByteBuffer.allocate(384)
        buf.put(OP_RACE_RESULTS)
        buf.put(GameConstants.PROTOCOL_VERSION)
        buf.putInt(raceId)
        encodeStandingsIntoBuffer(buf, standings)
        val out = ByteArray(buf.position())
        buf.flip()
        buf.get(out)
        return out
    }

    fun decodeRaceResultsFrame(buf: ByteBuffer, localSlot: Int): List<RacerStanding> {
        val count = (buf.get().toInt() and 0xFF).coerceIn(0, GameConstants.MAX_RACERS)
        val list = ArrayList<RacerStanding>(count)
        for (i in 0 until count) {
            val slot = buf.get().toInt() and 0xFF
            val pos = buf.get().toInt() and 0xFF
            val name = getShortString(buf)
            val color = buf.long
            val body = KartBodyType.fromId(buf.get().toInt() and 0xFF)
            val isHuman = buf.get().toInt() != 0
            val totalMs = buf.long
            val bestLapMs = buf.long
            val isDnf = buf.get().toInt() != 0
            list.add(
                RacerStanding(
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
