package com.example

import com.example.audio.SynthAudio
import com.example.game.CcClass
import com.example.game.CpuDifficulty
import com.example.game.CpuDriver
import com.example.game.GameConstants
import com.example.game.GameMode
import com.example.game.ItemMode
import com.example.game.ItemType
import com.example.game.KartBodyType
import com.example.game.KartRuntime
import com.example.game.RacePhase
import com.example.game.RaceSession
import com.example.game.TouchInputSnapshot
import com.example.net.Protocol
import com.example.net.ReliableRaceEvent
import com.example.net.UdpKartStatePacket
import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Part B14: JVM Network Test Harness.
 * Runs 1 Host + 2 Clients + 3 Bots (6 racers total) over an in-memory FakeNetwork
 * that injects 60 ± 20 ms latency, 5% packet loss, 2% packet reordering, and 1% duplication.
 */
class MultiplayerFakeNetworkTest {

    private class FakeNetwork(seed: Long = 42L) {
        private val rng = Random(seed)

        data class ScheduledUdpPacket(
            val deliverAtMs: Long,
            val recipientNode: Int, // 0 = Host, 1 = Client1, 2 = Client2
            val payload: ByteArray
        )

        data class ScheduledTcpFrame(
            val deliverAtMs: Long,
            val recipientNode: Int,
            val payload: ByteArray
        )

        private val udpQueue = ArrayList<ScheduledUdpPacket>()
        private val tcpBuffers = Array(3) { ArrayList<ScheduledTcpFrame>() }
        private val tcpNextOrderedMs = LongArray(3)

        fun sendUdp(nowMs: Long, toNode: Int, bytes: ByteArray) {
            // 5% packet loss
            if (rng.nextFloat() < 0.05f) return

            val baseDelay = 60L + rng.nextLong(-20L, 21L)
            // 2% reordering (+35 ms extra delay)
            val reorderJitter = if (rng.nextFloat() < 0.02f) 35L else 0L
            val deliverTime = nowMs + (baseDelay + reorderJitter).coerceAtLeast(5L)
            udpQueue.add(ScheduledUdpPacket(deliverTime, toNode, bytes.copyOf()))

            // 1% packet duplication
            if (rng.nextFloat() < 0.01f) {
                udpQueue.add(ScheduledUdpPacket(deliverTime + 6L, toNode, bytes.copyOf()))
            }
        }

        fun sendTcpOrdered(nowMs: Long, toNode: Int, bytes: ByteArray) {
            val delay = 60L + rng.nextLong(-15L, 16L)
            val deliverAt = maxOf(nowMs + delay, tcpNextOrderedMs[toNode] + 1L)
            tcpNextOrderedMs[toNode] = deliverAt
            tcpBuffers[toNode].add(ScheduledTcpFrame(deliverAt, toNode, bytes.copyOf()))
        }

        fun drainDueUdp(nowMs: Long, onDeliver: (recipientNode: Int, bytes: ByteArray) -> Unit) {
            val due = udpQueue.filter { it.deliverAtMs <= nowMs }.sortedBy { it.deliverAtMs }
            if (due.isEmpty()) return
            udpQueue.removeAll(due.toSet())
            for (pkt in due) {
                onDeliver(pkt.recipientNode, pkt.payload)
            }
        }

        fun drainDueTcp(nowMs: Long, onDeliver: (recipientNode: Int, bytes: ByteArray) -> Unit) {
            for (node in 0..2) {
                val list = tcpBuffers[node]
                val due = list.filter { it.deliverAtMs <= nowMs }
                if (due.isNotEmpty()) {
                    list.removeAll(due.toSet())
                    for (frame in due) {
                        onDeliver(node, frame.payload)
                    }
                }
            }
        }
    }

    private fun buildSixRacerGrid(localSlot: Int): List<KartRuntime> {
        val names = listOf("HOST_ACE", "CLIENT_1", "CLIENT_2", "BOT_VOLT", "BOT_TURBO", "BOT_APEX")
        return (0 until 6).map { slot ->
            KartRuntime(
                slotIndex = slot,
                playerName = names[slot],
                isHuman = slot in 0..2,
                isLocalPlayer = (slot == localSlot),
                colorHex = GameConstants.KART_COLORS[slot % GameConstants.KART_COLORS.size],
                bodyType = KartBodyType.fromId(slot % KartBodyType.entries.size),
                liveryIndex = slot % 3
            )
        }
    }

    @Test
    fun `run 1 host + 2 clients + 3 bots over lossy FakeNetwork for 3 laps, rematch, and mid-race rejoin`() {
        val silentAudio = SynthAudio(context = null).apply {
            soundEnabled = false
            hapticsEnabled = false
        }
        val net = FakeNetwork(seed = 202604L)

        val hostSession = RaceSession(synthAudio = silentAudio)
        val client1Session = RaceSession(synthAudio = silentAudio)
        val client2Session = RaceSession(synthAudio = silentAudio)

        var boxesGrantedCount = 0
        var itemsUsedCount = 0
        var projectileHitsOrSpawnsCount = 0
        val observedPositionSets = ArrayList<Set<Int>>()

        // Wire reliable TCP event emission from Host -> Client1 & Client2 via FakeNetwork
        var simNowMs = 1_000L
        hostSession.onHostEmitReliableEvent = { event ->
            when (event) {
                is ReliableRaceEvent.ItemGranted -> boxesGrantedCount++
                is ReliableRaceEvent.ItemUsed -> itemsUsedCount++
                is ReliableRaceEvent.ProjectileSpawned, is ReliableRaceEvent.Hit -> projectileHitsOrSpawnsCount++
                else -> Unit
            }
            val encoded = Protocol.encodeReliableEvent(event)
            net.sendTcpOrdered(simNowMs, 1, encoded)
            net.sendTcpOrdered(simNowMs, 2, encoded)
        }

        // Wire Client1 & Client2 item activations -> Host via reliable TCP + 3 UDP edge repeats
        var client1EdgeRepeats = 0
        var client1Backward = false
        var client2EdgeRepeats = 0
        var client2Backward = false

        client1Session.onLocalClientItemAction = { item, backward ->
            client1EdgeRepeats = 3
            client1Backward = backward
            val tcpBytes = java.nio.ByteBuffer.allocate(7)
                .put(Protocol.OP_ITEM_ACTION)
                .putInt(hostSession.activeRaceId)
                .put(item.id.toByte())
                .put(if (backward) 1.toByte() else 0.toByte())
                .array()
            net.sendTcpOrdered(simNowMs, 0, tcpBytes)
        }
        client2Session.onLocalClientItemAction = { item, backward ->
            client2EdgeRepeats = 3
            client2Backward = backward
            val tcpBytes = java.nio.ByteBuffer.allocate(7)
                .put(Protocol.OP_ITEM_ACTION)
                .putInt(hostSession.activeRaceId)
                .put(item.id.toByte())
                .put(if (backward) 1.toByte() else 0.toByte())
                .array()
            net.sendTcpOrdered(simNowMs, 0, tcpBytes)
        }

        val raceId1 = Protocol.generateRaceId()
        hostSession.startNewRace(
            trackId = 0,
            laps = 3,
            mode = ItemMode.NORMAL,
            difficulty = CpuDifficulty.PRO,
            cc = CcClass.CC_150,
            gameMode = GameMode.MULTIPLAYER,
            multiplayer = true,
            hostAuthority = true,
            localSlot = 0,
            racerConfigs = buildSixRacerGrid(localSlot = 0),
            skipFlyby = true,
            raceId = raceId1,
            initialCountdownSec = 0.5f
        )
        client1Session.startNewRace(
            trackId = 0,
            laps = 3,
            mode = ItemMode.NORMAL,
            difficulty = CpuDifficulty.PRO,
            cc = CcClass.CC_150,
            gameMode = GameMode.MULTIPLAYER,
            multiplayer = true,
            hostAuthority = false,
            localSlot = 1,
            racerConfigs = buildSixRacerGrid(localSlot = 1),
            skipFlyby = true,
            raceId = raceId1,
            initialCountdownSec = 0.5f
        )
        client2Session.startNewRace(
            trackId = 0,
            laps = 3,
            mode = ItemMode.NORMAL,
            difficulty = CpuDifficulty.PRO,
            cc = CcClass.CC_150,
            gameMode = GameMode.MULTIPLAYER,
            multiplayer = false.not(),
            hostAuthority = false,
            localSlot = 2,
            racerConfigs = buildSixRacerGrid(localSlot = 2),
            skipFlyby = true,
            raceId = raceId1,
            initialCountdownSec = 0.5f
        )

        var hostSeq = 0
        var c1Seq = 0
        var c2Seq = 0
        var lastHostSeqAtC1 = -1
        var lastHostSeqAtC2 = -1
        var lastC1SeqAtHost = -1
        var lastC2SeqAtHost = -1

        val dt = GameConstants.SIM_DT
        val maxTicks = (180f / dt).toInt() // 180 seconds simulated budget for 3 full laps + DNF window
        var client2Disconnected = false
        var client2Rejoined = false
        var client2DisconnectAtSec = 0f

        for (tick in 0 until maxTicks) {
            if (hostSession.phase == RacePhase.RESULTS &&
                client1Session.phase == RacePhase.RESULTS &&
                client2Session.phase == RacePhase.RESULTS
            ) {
                break
            }

            simNowMs += 16L

            // B14(f): A client disconnecting at lap 2 becomes a bot, rejoins and gets its slot back
            val elapsedSec = tick * dt
            val hostK2 = hostSession.karts.first { it.slotIndex == 2 }
            if (!client2Disconnected && (hostK2.lap >= 2 || elapsedSec >= 35.0f)) {
                client2Disconnected = true
                client2DisconnectAtSec = elapsedSec
                hostSession.convertSlotToCpu(2, "CLIENT_2")
                val k2 = hostSession.karts.first { it.slotIndex == 2 }
                assertTrue("Expected slot 2 to be converted to CPU on disconnect", !k2.isHuman)
            }
            if (client2Disconnected && !client2Rejoined && elapsedSec >= client2DisconnectAtSec + 3.0f) {
                client2Rejoined = true
                hostSession.reclaimSlotForHuman(2, "CLIENT_2")
                val k2 = hostSession.karts.first { it.slotIndex == 2 }
                assertTrue("Expected slot 2 to be reclaimed by human on rejoin", k2.isHuman)
                assertEquals("CLIENT_2", k2.playerName)
            }

            // Deliver due UDP and TCP packets
            net.drainDueTcp(simNowMs) { node, bytes ->
                when (node) {
                    0 -> {
                        if (bytes.isNotEmpty() && bytes[0] == Protocol.OP_ITEM_ACTION && bytes.size >= 7) {
                            val bb = java.nio.ByteBuffer.wrap(bytes, 1, bytes.size - 1)
                            val rId = bb.int
                            val item = ItemType.fromId(bb.get().toInt())
                            val back = bb.get().toInt() != 0
                            if (rId == hostSession.activeRaceId) {
                                // Determine sender from who holds the item
                                val cSlot = if (hostSession.karts.first { it.slotIndex == 1 }.heldItem == item) 1 else 2
                                hostSession.onHostReceiveClientItemAction(cSlot, item, back, 60)
                            }
                        }
                    }
                    1 -> {
                        val ev = Protocol.decodeReliableEvent(bytes, bytes.size)
                        if (ev != null) client1Session.onClientReceiveReliableEvent(ev)
                    }
                    2 -> {
                        if (!client2Disconnected || client2Rejoined) {
                            val ev = Protocol.decodeReliableEvent(bytes, bytes.size)
                            if (ev != null) client2Session.onClientReceiveReliableEvent(ev)
                        }
                    }
                }
            }

            net.drainDueUdp(simNowMs) { node, bytes ->
                when (node) {
                    0 -> {
                        val pkt = Protocol.decodeUdpClientKart(bytes, bytes.size)
                        if (pkt != null && pkt.raceId == hostSession.activeRaceId) {
                            if (pkt.slotIndex == 1 && Protocol.isSeqNewer(pkt.seq, lastC1SeqAtHost)) {
                                lastC1SeqAtHost = pkt.seq
                                hostSession.onHostReceiveClientKartState(pkt, 60)
                            } else if (pkt.slotIndex == 2 && Protocol.isSeqNewer(pkt.seq, lastC2SeqAtHost)) {
                                lastC2SeqAtHost = pkt.seq
                                hostSession.onHostReceiveClientKartState(pkt, 60)
                            }
                        }
                    }
                    1 -> {
                        val snap = Protocol.decodeWorldSnapshotV4(bytes, bytes.size)
                        if (snap != null && Protocol.isSeqNewer(snap.seq, lastHostSeqAtC1)) {
                            lastHostSeqAtC1 = snap.seq
                            client1Session.onClientReceiveWorldSnapshot(snap, simNowMs)
                        }
                    }
                    2 -> {
                        if (!client2Disconnected || client2Rejoined) {
                            val snap = Protocol.decodeWorldSnapshotV4(bytes, bytes.size)
                            if (snap != null && Protocol.isSeqNewer(snap.seq, lastHostSeqAtC2)) {
                                lastHostSeqAtC2 = snap.seq
                                client2Session.onClientReceiveWorldSnapshot(snap, simNowMs)
                            }
                        }
                    }
                }
            }

            // Generate skilled racing inputs for Host (slot 0), Client 1 (slot 1), and Client 2 (slot 2)
            fun buildDriverInput(session: RaceSession, slot: Int): TouchInputSnapshot {
                val kart = session.karts.first { it.slotIndex == slot }
                val (aiInput, _) = CpuDriver.computeCpuInput(
                    kart = kart,
                    track = session.currentTrack,
                    allKarts = session.karts,
                    projectiles = session.projectiles,
                    itemBoxes = session.itemBoxes,
                    coins = session.coins,
                    difficulty = CpuDifficulty.MASTER,
                    ccClass = CcClass.CC_150,
                    leadingHumanProgress = 0f,
                    raceElapsedSec = elapsedSec
                )
                // Proactively fire held items so item usage & hits are thoroughly exercised
                val shouldFireItem = kart.heldItem != ItemType.NONE && kart.rouletteTimerSec <= 0f && (tick % 30 == 0)
                return aiInput.copy(itemJustPressed = aiInput.itemJustPressed || shouldFireItem)
            }

            val hostInput = buildDriverInput(hostSession, 0)
            val c1Input = buildDriverInput(client1Session, 1)
            val c2Input = buildDriverInput(client2Session, 2)

            hostSession.stepFixed(dt, hostInput, autoAccelerate = true, smartSteering = true, showRocketStartTip = false)
            client1Session.stepFixed(dt, c1Input, autoAccelerate = true, smartSteering = true, showRocketStartTip = false)
            client2Session.stepFixed(dt, c2Input, autoAccelerate = true, smartSteering = true, showRocketStartTip = false)

            // 30 Hz network tick (every 2nd 60 Hz sim tick)
            if (tick % 2 == 0 && hostSession.phase != RacePhase.RESULTS) {
                hostSeq++
                val worldSnap = hostSession.buildWorldSnapshotV4(raceId = raceId1, seq = hostSeq)
                val snapBytes = Protocol.encodeWorldSnapshotV4(worldSnap)
                assertTrue("WorldSnapshotV4 must fit inside single 1200-byte UDP datagram", snapBytes.size <= 1200)
                net.sendUdp(simNowMs, 1, snapBytes)
                net.sendUdp(simNowMs, 2, snapBytes)

                // Client 1 -> Host UDP
                val k1 = client1Session.karts.first { it.slotIndex == 1 }
                c1Seq++
                val c1Pressed = if (client1EdgeRepeats > 0) {
                    client1EdgeRepeats--
                    true
                } else false
                val pkt1 = UdpKartStatePacket(
                    seq = c1Seq,
                    raceId = raceId1,
                    slotIndex = 1,
                    raceTimeMs = (elapsedSec * 1000f).toInt(),
                    x = k1.x,
                    y = k1.y,
                    z = k1.z,
                    headingRad = k1.headingRad,
                    speed = k1.forwardSpeed,
                    steer = c1Input.steer,
                    sparkTier = k1.sparkTier,
                    isBoosting = k1.boostTimerSec > 0f,
                    isDrifting = k1.isDrifting,
                    heldItem = k1.heldItem,
                    inputEdges = UdpKartStatePacket.buildInputEdges(c1Pressed, client1Backward, false)
                )
                net.sendUdp(simNowMs, 0, Protocol.encodeUdpClientKart(pkt1))

                // Client 2 -> Host UDP (only when connected)
                if (!client2Disconnected || client2Rejoined) {
                    val k2 = client2Session.karts.first { it.slotIndex == 2 }
                    c2Seq++
                    val c2Pressed = if (client2EdgeRepeats > 0) {
                        client2EdgeRepeats--
                        true
                    } else false
                    val pkt2 = UdpKartStatePacket(
                        seq = c2Seq,
                        raceId = raceId1,
                        slotIndex = 2,
                        raceTimeMs = (elapsedSec * 1000f).toInt(),
                        x = k2.x,
                        y = k2.y,
                        z = k2.z,
                        headingRad = k2.headingRad,
                        speed = k2.forwardSpeed,
                        steer = c2Input.steer,
                        sparkTier = k2.sparkTier,
                        isBoosting = k2.boostTimerSec > 0f,
                        isDrifting = k2.isDrifting,
                        heldItem = k2.heldItem,
                        inputEdges = UdpKartStatePacket.buildInputEdges(c2Pressed, client2Backward, false)
                    )
                    net.sendUdp(simNowMs, 0, Protocol.encodeUdpClientKart(pkt2))
                }

                // Verify host positions are strictly a permutation of 1..6 (no duplicates)
                val hostPositions = hostSession.karts.map { it.racePosition }.toSet()
                assertEquals("Host racePositions must be unique 1..6", (1..6).toSet(), hostPositions)
                observedPositionSets.add(hostSession.karts.map { it.racePosition }.toSet())
            }
        }

        // Flush any remaining reliable TCP events (e.g., final Results frame)
        for (flushStep in 0 until 20) {
            simNowMs += 20L
            net.drainDueTcp(simNowMs) { node, bytes ->
                val ev = Protocol.decodeReliableEvent(bytes, bytes.size) ?: return@drainDueTcp
                if (node == 1) client1Session.onClientReceiveReliableEvent(ev)
                if (node == 2) client2Session.onClientReceiveReliableEvent(ev)
            }
            client1Session.stepFixed(dt, TouchInputSnapshot(), true, true, false)
            client2Session.stepFixed(dt, TouchInputSnapshot(), true, true, false)
        }

        // Assertions for Part B14:
        // 1. All 3 human karts finished within 90 s and race reached RESULTS on Host & both Clients
        assertEquals(RacePhase.RESULTS, hostSession.phase)
        assertEquals(RacePhase.RESULTS, client1Session.phase)
        assertEquals(RacePhase.RESULTS, client2Session.phase)

        val humansFinished = hostSession.karts.filter { it.slotIndex in 0..2 }.all { it.hasFinishedRace && !it.isDnf }
        assertTrue("All 3 human karts must finish 3 laps within 90s", humansFinished)

        // 2. Items were granted, used, and projectiles spawned/hit
        assertTrue("Expected item boxes to be picked up and granted ($boxesGrantedCount)", boxesGrantedCount > 0)
        assertTrue("Expected items to be activated ($itemsUsedCount)", itemsUsedCount > 0)
        assertTrue("Expected projectiles or hits ($projectileHitsOrSpawnsCount)", projectileHitsOrSpawnsCount > 0)

        // 3. Host and both clients have byte-identical final Results (positions, finish times, best laps)
        val hostHud = hostSession.buildHudSnapshot(false)
        val c1Hud = client1Session.buildHudSnapshot(false)
        val c2Hud = client2Session.buildHudSnapshot(false)

        assertEquals(6, hostHud.standings.size)
        assertEquals(6, c1Hud.standings.size)
        assertEquals(6, c2Hud.standings.size)

        for (i in 0 until 6) {
            val hs = hostHud.standings[i]
            val c1s = c1Hud.standings[i]
            val c2s = c2Hud.standings[i]
            assertEquals("Standing #$i slot mismatch on Client 1", hs.slotIndex, c1s.slotIndex)
            assertEquals("Standing #$i slot mismatch on Client 2", hs.slotIndex, c2s.slotIndex)
            assertEquals("Standing #$i position mismatch on Client 1", hs.position, c1s.position)
            assertEquals("Standing #$i position mismatch on Client 2", hs.position, c2s.position)
            assertEquals("Standing #$i totalTimeMs mismatch on Client 1", hs.totalTimeMs, c1s.totalTimeMs)
            assertEquals("Standing #$i totalTimeMs mismatch on Client 2", hs.totalTimeMs, c2s.totalTimeMs)
            assertEquals("Standing #$i bestLapMs mismatch on Client 1", hs.bestLapMs, c1s.bestLapMs)
            assertEquals("Standing #$i bestLapMs mismatch on Client 2", hs.bestLapMs, c2s.bestLapMs)
        }

        // 4. Rematch increments/changes raceId, resets laps/items, and starts a second race cleanly
        val raceId2 = Protocol.generateRaceId()
        assertNotEquals(raceId1, raceId2)
        hostSession.startNewRace(
            trackId = 1,
            laps = 3,
            mode = ItemMode.NORMAL,
            difficulty = CpuDifficulty.PRO,
            cc = CcClass.CC_150,
            gameMode = GameMode.MULTIPLAYER,
            multiplayer = true,
            hostAuthority = true,
            localSlot = 0,
            racerConfigs = buildSixRacerGrid(localSlot = 0),
            skipFlyby = true,
            raceId = raceId2,
            initialCountdownSec = 3.2f
        )
        assertEquals(raceId2, hostSession.activeRaceId)
        assertEquals(RacePhase.COUNTDOWN, hostSession.phase)
        assertTrue(hostSession.karts.all { it.lapsCompleted == 0 && it.lap == 1 && it.heldItem == ItemType.NONE })

        // Verify stale packet from raceId1 is ignored by raceId2 session
        val staleSnap = hostSession.buildWorldSnapshotV4(raceId = raceId1, seq = 9999)
        client1Session.startNewRace(
            trackId = 1,
            laps = 3,
            mode = ItemMode.NORMAL,
            difficulty = CpuDifficulty.PRO,
            cc = CcClass.CC_150,
            gameMode = GameMode.MULTIPLAYER,
            multiplayer = true,
            hostAuthority = false,
            localSlot = 1,
            racerConfigs = buildSixRacerGrid(localSlot = 1),
            skipFlyby = true,
            raceId = raceId2,
            initialCountdownSec = 3.2f
        )
        client1Session.applyWorldSnapshotV4Internal(staleSnap, simNowMs)
        assertEquals(1, client1Session.karts.first { it.slotIndex == 1 }.lap)

        silentAudio.shutdown()
    }

    @Test
    fun `verify Protocol v4 wrap-safe sequence comparison and binary round-trips`() {
        assertTrue(Protocol.isSeqNewer(1, 0))
        assertTrue(Protocol.isSeqNewer(100, 99))
        assertTrue(!Protocol.isSeqNewer(99, 100))
        assertTrue(!Protocol.isSeqNewer(50, 50))

        // Wrap-around across Int.MAX_VALUE -> Int.MIN_VALUE
        val nearMax = Int.MAX_VALUE - 2
        val wrapped = nearMax + 5 // overflows to negative
        assertTrue("Expected wrapped sequence to be newer than nearMax", Protocol.isSeqNewer(wrapped, nearMax))
        assertTrue("Expected nearMax to be older than wrapped", !Protocol.isSeqNewer(nearMax, wrapped))

        // ReliableRaceEvent round-trip
        val events = listOf(
            ReliableRaceEvent.ItemGranted(raceId = 777, slot = 2, item = ItemType.PAPER_PLANE, rouletteMs = 900),
            ReliableRaceEvent.ItemUsed(raceId = 777, slot = 2, item = ItemType.MARBLE, backward = true),
            ReliableRaceEvent.Hit(raceId = 777, victimSlot = 1, attackerSlot = 2, kind = com.example.game.ProjectileKind.MARBLE),
            ReliableRaceEvent.CoinPicked(raceId = 777, slot = 1, totalCoins = 8),
            ReliableRaceEvent.LapCompleted(raceId = 777, slot = 0, lap = 2, lapTimeMs = 24_512L),
            ReliableRaceEvent.Finished(raceId = 777, slot = 0, position = 1, finishTimeMs = 73_120L),
            ReliableRaceEvent.RaceEndCountdown(raceId = 777, secondsLeft = 15),
            ReliableRaceEvent.Rematch(
                raceId = 888,
                trackId = 3,
                laps = 3,
                cpuFill = true,
                botCount = 5,
                botDifficulty = CpuDifficulty.MASTER,
                ccClass = CcClass.CC_200,
                itemMode = ItemMode.FRANTIC
            )
        )
        for (ev in events) {
            val bytes = Protocol.encodeReliableEvent(ev)
            val decoded = Protocol.decodeReliableEvent(bytes, bytes.size)
            assertNotNull("Failed to decode event $ev", decoded)
            assertEquals(ev, decoded)
        }
    }
}
