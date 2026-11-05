package com.example

import com.example.game.GameConstants
import com.example.game.ItemsEngine
import com.example.game.KartBodyType
import com.example.game.KartPhysics
import com.example.game.KartRuntime
import com.example.game.ProjectileKind
import com.example.game.TouchInputSnapshot
import com.example.game.Track
import com.example.game.Tracks
import com.example.game.WorldProjectile
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v7 handling QA: drift reward/penalty, contact stability, ground smoothness, projectiles on track.
 * Prints a FEEL line per check so CI logs show the numbers, not just pass/fail.
 */
class KartFeelTest {

    private val dt = GameConstants.SIM_DT

    private fun straightestSampleIndex(track: Track): Int {
        val n = track.samples.size
        var best = 0
        var bestScore = Float.MAX_VALUE
        for (i in 0 until n) {
            var score = 0f
            for (k in -20..20) score += abs(track.samples[(i + k + n) % n].curvature)
            for (k in -20..20) score += abs(track.samples[(i + k + n) % n].pos.y - track.samples[i].pos.y) * 0.2f
            if (score < bestScore) { bestScore = score; best = i }
        }
        return best
    }

    private fun kartAt(track: Track, idx: Int, slot: Int = 0, lateral: Float = 0f, speed: Float = 28f): KartRuntime {
        val s = track.samples[idx]
        return KartRuntime(slot, "T$slot", isHuman = true, isLocalPlayer = slot == 0, colorHex = 0xFFFF0000L, bodyType = KartBodyType.CLASSIC).apply {
            x = s.pos.x + s.rightX * lateral
            z = s.pos.z + s.rightZ * lateral
            y = s.pos.y
            headingRad = s.headingRad
            forwardSpeed = speed
            vx = sin(headingRad) * speed
            vz = cos(headingRad) * speed
            lastSampleIndex = idx
            trackS = s.distance
        }
    }

    private fun step(kart: KartRuntime, track: Track, input: TouchInputSnapshot, all: List<KartRuntime> = listOf(kart)) {
        KartPhysics.stepKart(dt, kart, input, track, all, autoAccelerate = true, smartSteering = false)
    }

    @Test
    fun `drifting on a straight is slower than driving, drifting into a turn charges sparks`() {
        val track = Tracks.byId(0)
        val idx = straightestSampleIndex(track)

        // A: plain driving down the straight at top speed
        val a = kartAt(track, idx, speed = 30f)
        repeat(70) { step(a, track, TouchInputSnapshot(gasPressed = true)) }

        // B: hop + drift right, then counter-steer to hold the straight line
        val b = kartAt(track, idx, speed = 30f)
        step(b, track, TouchInputSnapshot(gasPressed = true, steer = 1f, driftPressed = true, driftJustPressed = true))
        repeat(69) { step(b, track, TouchInputSnapshot(gasPressed = true, steer = -0.9f, driftPressed = true)) }

        println("FEEL straight: drive=${"%.2f".format(a.forwardSpeed)} drift-straight=${"%.2f".format(b.forwardSpeed)} charge=${b.driftChargeUnits} drifting=${b.isDrifting}")
        assertTrue("drift should have started", b.isDrifting)
        assertTrue("straight-line drift must cost speed", b.forwardSpeed < a.forwardSpeed * 0.92f)
        assertTrue("straight-line drift must not earn a mini-turbo in ~1 s", b.driftChargeUnits < GameConstants.SPARK_BLUE_UNITS)

        // C: drift INTO the turn — measure the charge rate once the hop has landed (on-road, grounded)
        val c = kartAt(track, idx, speed = 30f)
        step(c, track, TouchInputSnapshot(gasPressed = true, steer = 1f, driftPressed = true, driftJustPressed = true))
        var guard = 0
        while (c.vy != 0f || c.y > track.samples[c.lastSampleIndex].pos.y + 0.2f) {
            step(c, track, TouchInputSnapshot(gasPressed = true, steer = 1f, driftPressed = true))
            if (++guard > 60) break
        }
        val u0 = c.driftChargeUnits
        repeat(10) { step(c, track, TouchInputSnapshot(gasPressed = true, steer = 1f, driftPressed = true)) }
        val intoRate = (c.driftChargeUnits - u0) / 10f
        val ticksToBlue = guard + 1 + (GameConstants.SPARK_BLUE_UNITS / intoRate.coerceAtLeast(0.1f)).toInt()
        println("FEEL into-turn: hopTicks=${guard + 1} chargePerTick=$intoRate (straight≈${"%.2f".format(b.driftChargeUnits / 69f)}) estTicksToBlue=$ticksToBlue")
        assertTrue("carving drift must charge >= 4 units/tick", intoRate >= 4f)
        assertTrue("carving must charge far faster than a straight-line drift", intoRate > 3f * (b.driftChargeUnits / 69f))
        assertTrue("blue sparks within ~1 s including the hop", ticksToBlue <= 60)
    }

    @Test
    fun `drift buffer does not double-hop`() {
        val track = Tracks.byId(0)
        val idx = straightestSampleIndex(track)
        val k = kartAt(track, idx)
        var hops = 0
        // driftJustPressed stays true for ~7 ticks because of the 120 ms input buffer
        repeat(30) { t ->
            step(k, track, TouchInputSnapshot(gasPressed = true, steer = 0.6f, driftPressed = true, driftJustPressed = t < 7))
            if (k.hopJustStarted) { hops++; k.hopJustStarted = false }
        }
        println("FEEL hops for one press: $hops")
        assertTrue(hops == 1)
    }

    @Test
    fun `side by side contact settles without oscillating`() {
        val track = Tracks.byId(0)
        val idx = straightestSampleIndex(track)
        val k1 = kartAt(track, idx, slot = 0, lateral = -0.75f, speed = 25f)
        val k2 = kartAt(track, idx, slot = 1, lateral = 0.75f, speed = 25f) // 1.5 apart: 0.4 overlap
        val all = listOf(k1, k2)
        var maxLatStep = 0f
        var signFlips = 0
        var lastDelta = 0f
        var lastSep = hypot(k1.x - k2.x, k1.z - k2.z)
        repeat(90) {
            val p1x = k1.x; val p1z = k1.z
            step(k1, track, TouchInputSnapshot(gasPressed = true), all)
            step(k2, track, TouchInputSnapshot(gasPressed = true), all)
            val s = track.samples[k1.lastSampleIndex]
            val latStep = abs((k1.x - p1x) * s.rightX + (k1.z - p1z) * s.rightZ)
            maxLatStep = maxOf(maxLatStep, latStep)
            val sep = hypot(k1.x - k2.x, k1.z - k2.z)
            val d = sep - lastSep
            if (abs(d) > 0.002f && abs(lastDelta) > 0.002f && d * lastDelta < 0f) signFlips++
            if (abs(d) > 0.002f) lastDelta = d
            lastSep = sep
        }
        println("FEEL contact: finalSep=${"%.3f".format(lastSep)} maxLatStep=${"%.3f".format(maxLatStep)} flips=$signFlips")
        assertTrue("karts must end separated", lastSep > 1.8f)
        assertTrue("no per-tick lateral pops", maxLatStep < 0.12f)
        assertTrue("separation must not ping-pong", signFlips <= 2)
    }

    @Test
    fun `road height under a moving point is continuous on every track`() {
        for (track in Tracks.all) {
            var worst = 0f
            var prevY = Float.NaN
            var lastIdx = 0
            val stepLen = 0.25f
            var d = 0f
            while (d < track.totalLength) {
                val s = track.sampleAtDistance(d)
                val x = s.pos.x + s.rightX * 2f
                val z = s.pos.z + s.rightZ * 2f
                val near = track.findNearestSampleWindowed(x, s.pos.y, z, lastIdx, 40)
                lastIdx = near.sample.index
                val f = track.surfaceFrame(near.sample, x, z)
                if (!prevY.isNaN()) worst = maxOf(worst, abs(f.y - prevY))
                prevY = f.y
                d += stepLen
            }
            println("FEEL ground ${track.name}: worst dy per 0.25u = ${"%.3f".format(worst)}")
            assertTrue("height step on ${track.name} = $worst", worst < 0.4f)
        }
    }

    @Test
    fun `marbles stay on the road`() {
        for (track in Tracks.all.take(4)) {
            val idx = straightestSampleIndex(track)
            val s = track.samples[idx]
            val ang = s.headingRad + 0.6f // fired at an angle toward the edge
            val p = WorldProjectile(
                id = 1, kind = ProjectileKind.MARBLE, ownerSlot = 7,
                x = s.pos.x, y = s.pos.y + 0.5f, z = s.pos.z,
                vx = sin(ang) * GameConstants.MARBLE_SPEED, vz = cos(ang) * GameConstants.MARBLE_SPEED,
                trackSampleIdx = idx
            )
            val list = mutableListOf(p)
            var worstLat = 0f
            var worstDy = 0f
            var last = idx
            repeat(150) {
                if (list.isEmpty()) return@repeat
                ItemsEngine.stepProjectilesAndPickups(
                    dt, track, emptyList(), emptyList(), emptyList(), list,
                    itemMode = com.example.game.ItemMode.SKILL_ONLY,
                    onHitEvent = { _, _, _ -> }, onBoxPickedUp = { _, _, _ -> }
                )
                if (list.isEmpty()) return@repeat
                val near = track.findNearestSampleWindowed(p.x, p.y, p.z, last, 40)
                last = near.sample.index
                val f = track.surfaceFrame(near.sample, p.x, p.z)
                worstLat = maxOf(worstLat, abs(f.lateral) - track.roadWidth * 0.5f)
                if (it > 10) worstDy = maxOf(worstDy, abs(p.y - (f.y + 0.5f)))
            }
            println("FEEL marble ${track.name}: beyondEdge=${"%.2f".format(worstLat)} dy=${"%.2f".format(worstDy)}")
            assertTrue("marble left the road on ${track.name}", worstLat < 1.6f)
            assertTrue("marble floated on ${track.name}", worstDy < 0.8f)
        }
    }

    @Test
    fun `every item can drop and the leader is not stuck with three items`() {
        val rnd = kotlin.random.Random(7)
        for (pos in listOf(1, 2, 4, 6, 8)) {
            val counts = HashMap<com.example.game.ItemType, Int>()
            var last = com.example.game.ItemType.NONE
            repeat(4000) {
                val it2 = ItemsEngine.rollItemForPosition(pos, com.example.game.ItemMode.NORMAL, rnd, 8, last)
                counts[it2] = (counts[it2] ?: 0) + 1
                last = it2
            }
            println("FEEL items pos$pos: " + counts.entries.sortedByDescending { it.value }.joinToString { "${it.key.name}=${it.value * 100 / 4000}%" })
            val distinct = counts.keys.size
            assertTrue("position $pos only gets $distinct item kinds", distinct >= if (pos >= 6) 5 else 6)
        }
    }

    @Test
    fun `cpu field drives calmly - few hops, no weaving`() {
        val silent = com.example.audio.SynthAudio(context = null).apply { soundEnabled = false; hapticsEnabled = false }
        val session = com.example.game.RaceSession(synthAudio = silent, onNewBestLapRecorded = { _, _ -> false })
        val racers = (0 until GameConstants.MAX_RACERS).map { slot ->
            KartRuntime(slot, "B$slot", isHuman = false, isLocalPlayer = false, colorHex = 0xFFFF0000L,
                bodyType = KartBodyType.fromId(slot % 5))
        }
        session.startNewRace(
            trackId = 0, laps = 1, mode = com.example.game.ItemMode.NORMAL,
            difficulty = com.example.game.CpuDifficulty.CHAMPION, cc = com.example.game.CcClass.CC_150,
            gameMode = com.example.game.GameMode.QUICK_RACE, multiplayer = false, hostAuthority = true,
            localSlot = -1, racerConfigs = racers, skipFlyby = true
        )
        val hops = IntArray(GameConstants.MAX_RACERS)
        val lastVy = FloatArray(GameConstants.MAX_RACERS)
        var latFlips = 0
        val lastLatVel = FloatArray(GameConstants.MAX_RACERS)
        var bigBounces = 0
        val prevLat = FloatArray(GameConstants.MAX_RACERS)
        var steps = 0
        val empty = TouchInputSnapshot()
        while (steps < 60 * 150 && session.phase != com.example.game.RacePhase.RESULTS) {
            session.stepFixed(dt = dt, localInput = empty, autoAccelerate = true, smartSteering = false, showRocketStartTip = false)
            steps++
            if (session.phase != com.example.game.RacePhase.RACING) continue
            for (k in session.karts) {
                val i = k.slotIndex
                if (k.vy > 3.5f && lastVy[i] <= 0.5f && k.vy < 6f) hops[i]++ // drift hop launch
                lastVy[i] = k.vy
                val fx = sin(k.headingRad); val fz = cos(k.headingRad)
                val lat = k.vx * cos(k.headingRad) - k.vz * sin(k.headingRad)
                if (abs(lat - prevLat[i]) > 6f) bigBounces++
                prevLat[i] = lat
                if (abs(lat) > 0.8f && lastLatVel[i] * lat < 0f) latFlips++
                if (abs(lat) > 0.8f) lastLatVel[i] = lat
                if (fx.isNaN() || fz.isNaN()) error("nan")
            }
        }
        val secs = steps / 60f
        val hopsPerMin = hops.sum() / (secs / 60f) / GameConstants.MAX_RACERS
        println("FEEL cpu: raceSec=${"%.0f".format(secs)} hopsPerKartPerMin=${"%.1f".format(hopsPerMin)} hops=${hops.toList()} lateralFlips=$latFlips bigBounces=$bigBounces")
        silent.shutdown()
        assertTrue("bots hop too often ($hopsPerMin/min)", hopsPerMin < 14f)
    }
}
