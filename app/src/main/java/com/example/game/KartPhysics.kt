package com.example.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class KartRuntime(
    val slotIndex: Int,
    var playerName: String,
    var isHuman: Boolean,
    var isLocalPlayer: Boolean,
    var colorHex: Long,
    var bodyType: KartBodyType = KartBodyType.CLASSIC,
    var liveryIndex: Int = 0
) {
    // Spatial & Kinematic State
    var x: Float = 0f
    var y: Float = 0f
    var z: Float = 0f
    var prevX: Float = 0f
    var prevY: Float = 0f
    var prevZ: Float = 0f
    var vx: Float = 0f
    var vy: Float = 0f
    var vz: Float = 0f
    var groundedLastTick: Boolean = true
    var headingRad: Float = 0f
    var forwardSpeed: Float = 0f

    // Visual Body Tilt, Roll & Wheel Animation
    var visualYawOffsetRad: Float = 0f
    var visualRollRad: Float = 0f
    var visualPitchRad: Float = 0f
    var spinOutAngleRad: Float = 0f
    var wheelSpinRad: Float = 0f
    var frontWheelSteerRad: Float = 0f

    // Hop, Drift & Boost State
    var hopTimerSec: Float = 0f
    var isDrifting: Boolean = false
    var driftDirection: Int = 0 // -1 left, +1 right
    var driftChargeUnits: Int = 0
    var boostTimerSec: Float = 0f
    var boostMultiplier: Float = GameConstants.BOOST_SPEED_MULT
    var trickArmedInAir: Boolean = false
    var landingShakeTrigger: Boolean = false

    // v7 feel state
    var steerSmoothed: Float = 0f          // sim-side steering filter (UI writes raw input)
    var hopLockSec: Float = 0f             // stops the 120 ms drift buffer re-triggering hops
    var hopPendingDrift: Boolean = false   // hop without steer: pick drift direction on landing
    var airTimeSec: Float = 0f
    var trickVisualSec: Float = 0f
    var wasOnBoostPad: Boolean = false
    var wallBumpCooldownSec: Float = 0f
    var landingImpact: Float = 0f          // 0..1, for camera dip / suspension squash
    var driftScrubbing: Boolean = false
    var hopJustStarted: Boolean = false
    var visSpeedPrev: Float = 0f
    var draftSec: Float = 0f
    var visAccelSmoothed: Float = 0f

    // Status Timers & Special Zones
    var spinOutTimerSec: Float = 0f
    var invulnerableTimerSec: Float = 0f
    var ghostTimerSec: Float = 0f
    var isOffTrack: Boolean = false
    var isOnSlipperyZone: Boolean = false
    var paintTintHex: Long = 0L
    var paintTintTimerSec: Float = 0f
    var isRespawning: Boolean = false
    var respawnTimerSec: Float = 0f
    var wrongWayTimerSec: Float = 0f
    var wrongWayGraceTimerSec: Float = 0f
    var isWrongWay: Boolean = false
    var farOffTrackTimerSec: Float = 0f

    // Progress Watchdog (6 samples at 0.5s intervals = 3.0s window)
    val watchdogHistory = FloatArray(6)
    var watchdogWriteIdx: Int = 0
    var watchdogSampleCount: Int = 0
    var watchdogSampleTimerSec: Float = 0f
    var lastGoodSampleIndex: Int = 0
    var watchdogRespawnCount: Int = 0

    // Rocket Start Tracking
    var rocketStartHoldSec: Float = 0f
    var rocketStartOverheated: Boolean = false
    var rocketStartStallTimerSec: Float = 0f

    // Item, Coin & Balloon State
    var heldItem: ItemType = ItemType.NONE
    var pendingItem: ItemType = ItemType.NONE
    var lastRolledItem: ItemType = ItemType.NONE
    var rouletteTimerSec: Float = 0f
    var clientBoxPickupPendingSec: Float = 0f
    var clientUsedItemHoldbackSec: Float = 0f
    var clientPreUseItem: ItemType = ItemType.NONE
    var orbitingTacksCount: Int = 0
    var shieldEraserHeld: Boolean = false
    var magnetTimerSec: Float = 0f
    var coinsHeld: Int = 0
    var balloonsRemaining: Int = 3

    // Unwrapped Progress & Lap Timing
    var lastSampleIndex: Int = -1
    var trackS: Float = 0f
    var splineDistance: Float = 0f
    var raceDistance: Float = 0f
    var lapsCompleted: Int = 0
    var lap: Int = 1
    var highestCheckpointPassed: Int = 0
    var racePosition: Int = slotIndex + 1
    var frozenFinishPosition: Int = 0
    var currentLapStartMs: Long = 0L
    var bestLapMs: Long = 0L
    var finishTimeMs: Long = 0L
    var hasFinishedRace: Boolean = false
    var isDnf: Boolean = false

    val sparkTier: Int
        get() = when {
            !isDrifting -> 0
            driftChargeUnits >= GameConstants.SPARK_PURPLE_UNITS -> 3
            driftChargeUnits >= GameConstants.SPARK_ORANGE_UNITS -> 2
            driftChargeUnits >= GameConstants.SPARK_BLUE_UNITS -> 1
            else -> 0
        }

    val totalRaceProgress: Float
        get() = raceDistance
}

private const val GROUND_STICK_MAX_GAP = 0.30f
private const val EDGE_PUSHBACK_PER_TICK = 0.35f

// v7 handling model (see research notes: Mario Kart mini-turbo rules, arcade "exact position" grip,
// sequential-impulse contacts with slop)
private const val STEER_RISE_RATE = 32f          // 1/s, ~30 ms to reach the thumb position
private const val STEER_RETURN_RATE = 16f        // 1/s, ~60 ms return to centre
private const val HOP_LOCK_SEC = 0.22f
private const val DRIFT_STRAIGHT_SCRUB = 7.5f    // u/s^2 lost when drifting without turning into it
private const val CORNER_SCRUB = 4.0f            // u/s^2 lost by hard grip-steering at top speed
private const val KART_CONTACT_RADIUS = 0.95f    // per kart, sum = 1.9 separation
private const val CONTACT_SLOP = 0.04f           // penetration allowed before positional correction
private const val CONTACT_BETA = 0.45f           // fraction of penetration resolved per tick
private const val RESTITUTION_SLOP = 1.2f        // u/s, slower closing speeds don't bounce
private const val KART_RESTITUTION = 0.35f
private const val WALL_BUMP_COOLDOWN_SEC = 0.35f
private const val REAL_AIR_SEC = 0.42f
const val DRAFT_CHARGE_SEC = 1.4f           // longer than a drift hop

object KartPhysics {

    fun resetKartToGrid(kart: KartRuntime, slot: StartGridSlot, track: Track? = null) {
        kart.x = slot.x
        kart.y = slot.y
        kart.z = slot.z
        kart.prevX = slot.x
        kart.prevY = slot.y
        kart.prevZ = slot.z
        kart.vx = 0f
        kart.vy = 0f
        kart.vz = 0f
        kart.headingRad = slot.headingRad
        kart.forwardSpeed = 0f
        kart.visualYawOffsetRad = 0f
        kart.visualRollRad = 0f
        kart.visualPitchRad = 0f
        kart.spinOutAngleRad = 0f
        kart.wheelSpinRad = 0f
        kart.frontWheelSteerRad = 0f
        kart.hopTimerSec = 0f
        kart.isDrifting = false
        kart.driftDirection = 0
        kart.driftChargeUnits = 0
        kart.boostTimerSec = 0f
        kart.spinOutTimerSec = 0f
        kart.invulnerableTimerSec = 0f
        kart.ghostTimerSec = 0f
        kart.isOffTrack = false
        kart.isOnSlipperyZone = false
        kart.paintTintHex = 0L
        kart.paintTintTimerSec = 0f
        kart.isRespawning = false
        kart.respawnTimerSec = 0f
        kart.wrongWayTimerSec = 0f
        kart.wrongWayGraceTimerSec = 2.0f
        kart.isWrongWay = false
        kart.farOffTrackTimerSec = 0f
        kart.watchdogWriteIdx = 0
        kart.watchdogSampleCount = 0
        kart.watchdogSampleTimerSec = 0f
        kart.watchdogRespawnCount = 0
        kart.rocketStartHoldSec = 0f
        kart.rocketStartOverheated = false
        kart.rocketStartStallTimerSec = 0f
        kart.heldItem = ItemType.NONE
        kart.pendingItem = ItemType.NONE
        kart.rouletteTimerSec = 0f
        kart.clientBoxPickupPendingSec = 0f
        kart.clientUsedItemHoldbackSec = 0f
        kart.clientPreUseItem = ItemType.NONE
        kart.orbitingTacksCount = 0
        kart.shieldEraserHeld = false
        kart.magnetTimerSec = 0f
        kart.coinsHeld = 0
        kart.balloonsRemaining = 3

        if (track != null) {
            val res = track.findNearestSampleWindowed(slot.x, slot.y, slot.z, -1)
            kart.lastSampleIndex = res.sample.index
            kart.lastGoodSampleIndex = res.sample.index
            kart.trackS = res.sample.distance
            kart.splineDistance = res.sample.distance
            kart.raceDistance = res.sample.distance - track.totalLength
        } else {
            kart.lastSampleIndex = -1
            kart.lastGoodSampleIndex = 0
            kart.trackS = 0f
            kart.splineDistance = 0f
            kart.raceDistance = -10f
        }
        kart.lapsCompleted = 0
        kart.lap = 1
        kart.highestCheckpointPassed = 0
        kart.racePosition = slot.slotIndex + 1
        kart.frozenFinishPosition = 0
        kart.bestLapMs = 0L
        kart.finishTimeMs = 0L
        kart.hasFinishedRace = false
        kart.isDnf = false
    }

    fun triggerSpinOut(kart: KartRuntime) {
        if (kart.invulnerableTimerSec > 0f || kart.ghostTimerSec > 0f || kart.isRespawning) return
        kart.spinOutTimerSec = GameConstants.SPIN_OUT_DURATION_SEC
        kart.invulnerableTimerSec = GameConstants.SPIN_OUT_DURATION_SEC + GameConstants.INVULNERABLE_AFTER_HIT_SEC
        // Part C7: Ignore wrong-way for 2s after spin-out finishes
        kart.wrongWayGraceTimerSec = GameConstants.SPIN_OUT_DURATION_SEC + 2.0f
        kart.wrongWayTimerSec = 0f
        kart.isWrongWay = false
        kart.forwardSpeed *= GameConstants.SPIN_OUT_SPEED_FACTOR
        kart.isDrifting = false
        kart.driftChargeUnits = 0
        kart.boostTimerSec = 0f
        kart.coinsHeld = (kart.coinsHeld - 2).coerceAtLeast(0)
        if (kart.balloonsRemaining > 0) {
            kart.balloonsRemaining--
        }
        kart.watchdogSampleCount = 0
        kart.watchdogSampleTimerSec = 0f
    }

    fun applyBoost(kart: KartRuntime, durationSec: Float, mult: Float = GameConstants.BOOST_SPEED_MULT) {
        if (durationSec > kart.boostTimerSec) {
            kart.boostTimerSec = durationSec
        }
        kart.boostMultiplier = mult
        val boostedTop = GameConstants.TOP_SPEED * kart.bodyType.topSpeedFactor * mult
        if (kart.forwardSpeed < boostedTop * 0.88f) {
            kart.forwardSpeed = boostedTop * 0.88f
        }
    }

    /**
     * Part C5 & C7: Place kart on the road centreline facing the tangent, 10 u behind the last good sample,
     * with 1.5 s ghost, checked for prop clearance, and 2.0 s wrong-way grace.
     */
    fun respawnKartBehindLastGood(kart: KartRuntime, track: Track) {
        val goodSample = track.samples.getOrElse(kart.lastGoodSampleIndex) { track.samples.first() }
        val baseBack = GameConstants.WATCHDOG_RESPAWN_BACK_OFFSET
        val candidateOffsets = floatArrayOf(baseBack, baseBack + 3.5f, baseBack - 3.5f, baseBack + 7f, baseBack + 11f)
        var chosenSample = track.sampleAtDistance(track.wrapDistance(goodSample.distance - baseBack))

        for (offset in candidateOffsets) {
            val candS = track.wrapDistance(goodSample.distance - offset)
            val cand = track.sampleAtDistance(candS)
            if (isClearOfSolidProps(cand.pos.x, cand.pos.y, cand.pos.z, track)) {
                chosenSample = cand
                break
            }
        }

        kart.x = chosenSample.pos.x
        kart.y = chosenSample.pos.y + 0.15f
        kart.z = chosenSample.pos.z
        kart.prevX = kart.x
        kart.prevY = kart.y
        kart.prevZ = kart.z
        kart.vx = 0f
        kart.vy = 0f
        kart.vz = 0f
        kart.forwardSpeed = 6.0f
        kart.headingRad = chosenSample.headingRad
        kart.isDrifting = false
        kart.driftChargeUnits = 0
        kart.isRespawning = false
        kart.respawnTimerSec = 0f
        kart.ghostTimerSec = GameConstants.GHOST_AFTER_RESPAWN_SEC
        kart.invulnerableTimerSec = GameConstants.GHOST_AFTER_RESPAWN_SEC
        kart.wrongWayTimerSec = 0f
        kart.wrongWayGraceTimerSec = 2.0f
        kart.isWrongWay = false
        kart.farOffTrackTimerSec = 0f
        kart.watchdogSampleCount = 0
        kart.watchdogSampleTimerSec = 0f

        val deltaBack = track.wrapSignedDelta(chosenSample.distance - kart.trackS)
        kart.trackS = chosenSample.distance
        kart.splineDistance = chosenSample.distance
        kart.lastSampleIndex = chosenSample.index
        kart.lastGoodSampleIndex = chosenSample.index
        if (deltaBack < 0f && abs(deltaBack) < 45f) {
            val minLapFloor = kart.lapsCompleted * track.totalLength
            kart.raceDistance = (kart.raceDistance + deltaBack).coerceAtLeast(minLapFloor)
        }
        kart.watchdogRespawnCount++
    }

    private fun isClearOfSolidProps(x: Float, y: Float, z: Float, track: Track): Boolean {
        val clearanceRadius = 1.6f
        for (prop in track.placedProps) {
            if (!prop.solid || prop.isPylon) continue
            if (y > prop.y + 25f || y + 1.5f < prop.y) continue
            val d = hypot(x - prop.x, z - prop.z)
            if (d < prop.radius + clearanceRadius) return false
        }
        for (prop in track.props) {
            if (!prop.isSolidWall) continue
            if (y > prop.y + prop.sy + 0.5f || y + 1.5f < prop.y) continue
            val r = maxOf(prop.sx, prop.sz) * 0.5f + clearanceRadius
            if (hypot(x - prop.x, z - prop.z) < r) return false
        }
        return true
    }

    /**
     * Part B7: Progress Robustness (host & solo).
     * Never silently ignore large progress jumps (|deltaS| > 25 u).
     * Re-seat the kart with a windowed spline search (±60 samples around last index, height-matched)
     * and add the true signed delta when moving forward. Only a confirmed respawn moves raceDistance backwards.
     */
    fun updateKartSplineProgress(kart: KartRuntime, track: Track): NearestSampleResult {
        var nearestRes = track.findNearestSampleWindowed(
            worldX = kart.x,
            worldY = kart.y,
            worldZ = kart.z,
            prevSampleIndex = kart.lastSampleIndex,
            windowRadius = 40
        )
        val oldS = kart.trackS
        var newS = nearestRes.sample.distance
        var deltaS = track.wrapSignedDelta(newS - oldS)

        if (abs(deltaS) > 25f) {
            nearestRes = track.findNearestSampleWindowed(
                worldX = kart.x,
                worldY = kart.y,
                worldZ = kart.z,
                prevSampleIndex = kart.lastSampleIndex,
                windowRadius = 60
            )
            newS = nearestRes.sample.distance
            deltaS = track.wrapSignedDelta(newS - oldS)
        }

        kart.lastSampleIndex = nearestRes.sample.index
        kart.trackS = newS
        kart.splineDistance = newS

        if (deltaS > 0f && deltaS < track.totalLength * 0.45f) {
            kart.raceDistance += deltaS
        }
        return nearestRes
    }

    fun stepPylons(dt: Float, pylons: List<ActivePylon>) {
        for (p in pylons) {
            if (!p.isKnocked) continue
            p.respawnTimerSec -= dt
            if (p.respawnTimerSec <= 0f) {
                p.respawnTimerSec = 0f
                p.x = p.baseX
                p.y = p.baseY
                p.z = p.baseZ
                p.yawRad = p.baseYawRad
                p.pitchRad = 0f
                p.rollRad = 0f
                p.vx = 0f
                p.vy = 0f
                p.vz = 0f
                p.omegaPitch = 0f
                p.omegaRoll = 0f
            } else {
                p.x += p.vx * dt
                p.z += p.vz * dt
                p.y += p.vy * dt
                if (p.y > p.baseY) {
                    p.vy -= 22f * dt
                }
                if (p.y <= p.baseY) {
                    p.y = p.baseY
                    p.vy = 0f
                    p.vx *= (1f - 3.5f * dt).coerceAtLeast(0f)
                    p.vz *= (1f - 3.5f * dt).coerceAtLeast(0f)
                }
                p.pitchRad = (p.pitchRad + p.omegaPitch * dt).coerceAtMost(1.48f)
                p.rollRad += p.omegaRoll * dt * 0.35f
            }
        }
    }

    fun stepKart(
        dt: Float,
        kart: KartRuntime,
        input: TouchInputSnapshot,
        track: Track,
        allKarts: List<KartRuntime>,
        autoAccelerate: Boolean,
        smartSteering: Boolean,
        speedScale: Float = 1.0f,
        raceElapsedSec: Float = 0f,
        activePylons: List<ActivePylon> = emptyList(),
        onWallBump: () -> Unit = {},
        onPylonKnocked: () -> Unit = {},
        onBoostTriggered: () -> Unit = {},
        onLanded: () -> Unit = {},
        onRespawnToast: () -> Unit = {},
        onLapCompleted: (completedLap: Int, subTickFraction: Float) -> Unit = { _, _ -> }
    ) {
        kart.prevX = kart.x
        kart.prevY = kart.y
        kart.prevZ = kart.z

        // v7: steering is filtered here, in sim time, instead of in a UI coroutine (which added a
        // frame of latency and restarted on every touch move). Fast to follow the thumb, a touch
        // slower to re-centre so releases don't snap the kart straight.
        run {
            val raw = input.steer.coerceIn(-1f, 1f)
            val s = kart.steerSmoothed
            val rising = abs(raw) > abs(s) && (raw * s >= 0f)
            val rate = if (rising) STEER_RISE_RATE else STEER_RETURN_RATE
            kart.steerSmoothed = s + (raw - s) * (1f - kotlin.math.exp(-rate * dt))
            if (abs(kart.steerSmoothed) < 0.002f && raw == 0f) kart.steerSmoothed = 0f
        }
        val inp = if (input.steer == kart.steerSmoothed) input else input.copy(steer = kart.steerSmoothed)
        if (kart.hopLockSec > 0f) kart.hopLockSec = (kart.hopLockSec - dt).coerceAtLeast(0f)
        if (kart.wallBumpCooldownSec > 0f) kart.wallBumpCooldownSec = (kart.wallBumpCooldownSec - dt).coerceAtLeast(0f)
        if (kart.trickVisualSec > 0f) kart.trickVisualSec = (kart.trickVisualSec - dt).coerceAtLeast(0f)
        kart.landingImpact = (kart.landingImpact - dt * 4f).coerceAtLeast(0f)

        // 1. Item Roulette Animation Countdown
        if (kart.rouletteTimerSec > 0f) {
            kart.rouletteTimerSec -= dt
            if (inp.itemJustPressed && kart.rouletteTimerSec < GameConstants.ITEM_ROULETTE_SEC - 0.25f) {
                kart.rouletteTimerSec = 0f
            }
            if (kart.rouletteTimerSec <= 0f && kart.pendingItem != ItemType.NONE) {
                kart.heldItem = kart.pendingItem
                kart.pendingItem = ItemType.NONE
                if (kart.heldItem == ItemType.TRIPLE_TACKS) {
                    kart.orbitingTacksCount = 3
                }
            }
        }

        // 2. Lakitu Fall-Off Respawn State
        if (kart.isRespawning) {
            kart.respawnTimerSec -= dt
            if (kart.respawnTimerSec <= 0f) {
                respawnKartBehindLastGood(kart, track)
            }
            return
        }

        if (kart.invulnerableTimerSec > 0f) {
            kart.invulnerableTimerSec = (kart.invulnerableTimerSec - dt).coerceAtLeast(0f)
        }
        if (kart.ghostTimerSec > 0f) {
            kart.ghostTimerSec = (kart.ghostTimerSec - dt).coerceAtLeast(0f)
        }
        if (kart.wrongWayGraceTimerSec > 0f) {
            kart.wrongWayGraceTimerSec = (kart.wrongWayGraceTimerSec - dt).coerceAtLeast(0f)
        }
        if (kart.boostTimerSec > 0f) {
            kart.boostTimerSec = (kart.boostTimerSec - dt).coerceAtLeast(0f)
        }
        if (kart.magnetTimerSec > 0f) {
            kart.magnetTimerSec = (kart.magnetTimerSec - dt).coerceAtLeast(0f)
        }
        if (kart.paintTintTimerSec > 0f) {
            kart.paintTintTimerSec = (kart.paintTintTimerSec - dt).coerceAtLeast(0f)
            if (kart.paintTintTimerSec <= 0f) kart.paintTintHex = 0L
        }
        if (kart.rocketStartStallTimerSec > 0f) {
            kart.rocketStartStallTimerSec = (kart.rocketStartStallTimerSec - dt).coerceAtLeast(0f)
        }

        // 3. Windowed Height-Aware Nearest Spline Sample (Part B7)
        val oldS = kart.trackS
        val nearestRes = updateKartSplineProgress(kart, track)
        val nearestSample = nearestRes.sample
        val surf = track.surfaceFrame(nearestSample, kart.x, kart.z)
        val signedLateral = surf.lateral
        val newS = kart.trackS
        val deltaS = track.wrapSignedDelta(newS - oldS)

        val halfRoad = track.roadWidth * 0.5f
        val verticalDiffFromRoad = abs(kart.y - surf.y)
        kart.isOffTrack = abs(signedLateral) > halfRoad && verticalDiffFromRoad < 1.2f

        if (!kart.isOffTrack && !kart.isWrongWay) {
            kart.lastGoodSampleIndex = nearestSample.index
        }

        // Check special track zones: Art Desk paint puddles & Bathtub Rim soap zones
        for (puddle in track.paintPuddles) {
            val dx = kart.x - puddle.x
            val dz = kart.z - puddle.z
            if (dx * dx + dz * dz <= puddle.radius * puddle.radius) {
                val wasUntinted = kart.paintTintTimerSec <= 0.1f
                kart.paintTintHex = puddle.tintColorHex
                kart.paintTintTimerSec = 5.0f
                if (wasUntinted) {
                    applyBoost(kart, GameConstants.BOOST_MINI_SEC)
                    onBoostTriggered()
                }
            }
        }
        var onSoap = false
        for (soap in track.slipperyZones) {
            val dx = kart.x - soap.x
            val dz = kart.z - soap.z
            if (dx * dx + dz * dz <= soap.radius * soap.radius) {
                onSoap = true
                break
            }
        }
        kart.isOnSlipperyZone = onSoap

        // 4. Spin-Out & Rocket Start Stall Handling
        if (kart.spinOutTimerSec > 0f) {
            kart.spinOutTimerSec -= dt
            val progress = 1f - (kart.spinOutTimerSec / GameConstants.SPIN_OUT_DURATION_SEC).coerceIn(0f, 1f)
            kart.spinOutAngleRad = progress * (PI.toFloat() * 4f)
            kart.forwardSpeed *= (1f - 2.2f * dt).coerceAtLeast(0f)
        } else {
            kart.spinOutAngleRad = 0f
        }

        // 5. Hop + Drift Initiation & Release Boost
        val canControl = kart.spinOutTimerSec <= 0f && kart.rocketStartStallTimerSec <= 0f
        val grounded = kart.y <= surf.y + 0.14f

        if (canControl && inp.driftJustPressed && kart.hopLockSec <= 0f) {
            if (grounded && kart.vy <= 0.01f) {
                kart.hopTimerSec = GameConstants.HOP_DURATION_SEC
                kart.hopLockSec = HOP_LOCK_SEC
                kart.hopJustStarted = true
                kart.vy = GameConstants.HOP_VELOCITY_Y
                kart.hopPendingDrift = false
                if (abs(inp.steer) > 0.12f && kart.forwardSpeed > 8f) {
                    kart.isDrifting = true
                    kart.driftDirection = if (inp.steer > 0f) 1 else -1
                    kart.driftChargeUnits = 0
                } else if (!kart.isDrifting) {
                    // Mario Kart style: hop straight, choose the drift side while in the air
                    kart.hopPendingDrift = true
                }
            } else if (kart.y > surf.y + 0.6f && !kart.trickArmedInAir) {
                kart.trickArmedInAir = true
                kart.trickVisualSec = 0.42f // smooth flip in the visual block (no snap)
            }
        }
        if (kart.hopPendingDrift) {
            if (!inp.driftPressed || !canControl || kart.forwardSpeed < 8f) {
                kart.hopPendingDrift = false
            } else if (abs(inp.steer) > 0.25f) {
                kart.isDrifting = true
                kart.driftDirection = if (inp.steer > 0f) 1 else -1
                kart.driftChargeUnits = 0
                kart.hopPendingDrift = false
            } else if (grounded && kart.hopLockSec <= 0f) {
                kart.hopPendingDrift = false
            }
        }

        // How hard the player is steering INTO the drift: +1 = full lock into, 0 = neutral,
        // -1 = full counter-steer (which straightens the line, i.e. drifting on a straight).
        val driftInto = if (kart.isDrifting) (inp.steer * kart.driftDirection).coerceIn(-1f, 1f) else 0f
        kart.driftScrubbing = false

        if (kart.isDrifting) {
            if (!inp.driftPressed || kart.forwardSpeed < 6f || !canControl) {
                val tier = kart.sparkTier
                val boostDur = when (tier) {
                    3 -> GameConstants.BOOST_ULTRA_SEC
                    2 -> GameConstants.BOOST_SUPER_SEC
                    1 -> GameConstants.BOOST_MINI_SEC
                    else -> 0f
                }
                kart.isDrifting = false
                kart.driftDirection = 0
                kart.driftChargeUnits = 0
                if (boostDur > 0f) {
                    applyBoost(kart, boostDur)
                    onBoostTriggered()
                }
            } else {
                // v7 drift reward: sparks only build while the kart is really carving a turn.
                //  into the drift (hard)  -> fastest charge
                //  neutral                -> slow charge
                //  counter-steer/straight -> almost none, and the slide scrubs speed (see 7.)
                // Airborne or off-road drifting doesn't charge at all.
                val baseInc = when {
                    !grounded || kart.isOffTrack -> 0
                    driftInto > 0.55f -> GameConstants.DRIFT_CHARGE_INTO
                    driftInto > 0.15f -> GameConstants.DRIFT_CHARGE_INTO - 1
                    driftInto > -0.20f -> GameConstants.DRIFT_CHARGE_COUNTER + 1
                    else -> GameConstants.DRIFT_CHARGE_COUNTER - 1
                }
                val increment = if (kart.bodyType == KartBodyType.TURBO_COUPE && baseInc > 0) baseInc + 1 else baseInc
                val maxAllowedUnits = if (smartSteering) {
                    GameConstants.SPARK_PURPLE_UNITS - 1
                } else {
                    600
                }
                kart.driftChargeUnits = (kart.driftChargeUnits + increment).coerceAtMost(maxAllowedUnits)
            }
        }

        // 6. Steering & Smart Steering Assist
        if (canControl) {
            val speedRatio = (abs(kart.forwardSpeed) / GameConstants.TOP_SPEED).coerceIn(0f, 1.2f)
            val speedScaleTurn = 1f - (1f - GameConstants.HIGH_SPEED_STEER_FACTOR) * (speedRatio * 0.8f)
            var steerRate = GameConstants.BASE_STEER_RATE * kart.bodyType.handlingFactor * speedScaleTurn
            if (kart.isDrifting) steerRate *= GameConstants.DRIFT_STEER_BONUS

            val effectiveSteer = if (kart.isDrifting) {
                (kart.driftDirection * 0.55f + inp.steer * 0.65f).coerceIn(-1.25f, 1.25f)
            } else {
                inp.steer
            }

            kart.headingRad -= effectiveSteer * steerRate * dt
            kart.frontWheelSteerRad = (-effectiveSteer * 0.42f).coerceIn(-0.45f, 0.45f)

            if (smartSteering && abs(signedLateral) > halfRoad * 0.82f) {
                val pullError = wrapAngle(surf.headingRad - kart.headingRad)
                kart.headingRad += pullError.coerceIn(-0.6f, 0.6f) * GameConstants.SMART_STEERING_PULL * dt
                if (abs(signedLateral) > halfRoad * 0.96f) {
                    val clampLat = if (signedLateral > 0f) halfRoad * 0.96f else -halfRoad * 0.96f
                    // v6: soft edge correction (was an instant per-tick snap, which made karts
                    // shudder while grinding the track edge at top speed)
                    val excessLat = (signedLateral - clampLat) * EDGE_PUSHBACK_PER_TICK
                    kart.x -= surf.rightX * excessLat
                    kart.z -= surf.rightZ * excessLat
                }
            }
        }

        // 7. Forward Acceleration, Coins Bonus (+0.6%/coin), Off-Track Drag, Boosts
        val gasActive = canControl && (inp.gasPressed || (autoAccelerate && !inp.brakePressed))
        val coinBonusMult = 1.0f + kart.coinsHeld.coerceIn(0, GameConstants.MAX_COINS_HELD) * GameConstants.COIN_SPEED_BONUS_PER_COIN
        var targetTopSpeed = GameConstants.TOP_SPEED * kart.bodyType.topSpeedFactor * coinBonusMult * speedScale
        if (kart.boostTimerSec > 0f) {
            targetTopSpeed *= kart.boostMultiplier
        } else if (kart.isOffTrack) {
            val offTrackMult = if (kart.bodyType == KartBodyType.RALLY_HATCH) 0.72f else GameConstants.OFF_TRACK_SPEED_MULT
            targetTopSpeed *= offTrackMult
        }
        if (kart.magnetTimerSec > 0f) {
            targetTopSpeed *= 1.18f
        }

        // v7: make the drift a decision. Real cars don't slide for free: a drift that isn't carving a
        // turn (neutral / counter-steer on a straight) bleeds top speed, and hard grip-steering at
        // top speed bleeds a little too — so drifting INTO a corner is the fast line, while drifting
        // on a straight is slower than just driving. Applied to the target speed so the throttle
        // can't simply cancel it.
        if (canControl && grounded && kart.forwardSpeed > 10f && kart.boostTimerSec <= 0f) {
            if (kart.isDrifting) {
                val notTurning = ((0.35f - driftInto) / 1.35f).coerceIn(0f, 1f) // 0 when steering into it
                if (notTurning > 0.05f) {
                    kart.driftScrubbing = true
                    targetTopSpeed *= 1f - DRIFT_STRAIGHT_SCRUB * 0.03f * notTurning   // up to -22.5 %
                }
            } else {
                val hard = ((abs(inp.steer) - 0.55f) / 0.45f).coerceIn(0f, 1f)
                val speedRatio = (kart.forwardSpeed / GameConstants.TOP_SPEED).coerceIn(0f, 1.2f)
                targetTopSpeed *= 1f - CORNER_SCRUB * 0.02f * hard * speedRatio        // up to -8 %
            }
        }

        if (canControl) {
            if (inp.brakePressed) {
                if (kart.forwardSpeed > 0f) {
                    kart.forwardSpeed = (kart.forwardSpeed - GameConstants.BRAKE_DECEL * dt).coerceAtLeast(0f)
                } else {
                    kart.forwardSpeed = (kart.forwardSpeed - GameConstants.ACCEL * 0.8f * dt)
                        .coerceAtLeast(GameConstants.REVERSE_MAX_SPEED)
                }
            } else if (gasActive) {
                if (kart.forwardSpeed < targetTopSpeed) {
                    val baseAccel = GameConstants.ACCEL * kart.bodyType.accelFactor
                    val accel = if (kart.boostTimerSec > 0f) baseAccel * 2.4f else baseAccel
                    kart.forwardSpeed = (kart.forwardSpeed + accel * dt).coerceAtMost(targetTopSpeed)
                } else {
                    kart.forwardSpeed = (kart.forwardSpeed - GameConstants.COAST_DECEL * 1.4f * dt)
                        .coerceAtLeast(targetTopSpeed)
                }
            } else {
                if (kart.forwardSpeed > 0f) {
                    kart.forwardSpeed = (kart.forwardSpeed - GameConstants.COAST_DECEL * dt).coerceAtLeast(0f)
                } else if (kart.forwardSpeed < 0f) {
                    kart.forwardSpeed = (kart.forwardSpeed + GameConstants.COAST_DECEL * dt).coerceAtMost(0f)
                }
            }
        } else if (kart.rocketStartStallTimerSec > 0f) {
            kart.forwardSpeed *= (1f - 6f * dt).coerceAtLeast(0f)
        }

        kart.wheelSpinRad = (kart.wheelSpinRad + (kart.forwardSpeed / 0.30f) * dt) % (PI.toFloat() * 20f)

        // 8. Velocity Integration with Lateral Grip Damping
        val fwdX = sin(kart.headingRad)
        val fwdZ = cos(kart.headingRad)
        val rightX = -cos(kart.headingRad)
        val rightZ = sin(kart.headingRad)

        val currentLateralVel = kart.vx * rightX + kart.vz * rightZ
        val gripDamping = when {
            kart.isOnSlipperyZone -> GameConstants.SOAP_LATERAL_DAMPING
            kart.isDrifting -> GameConstants.DRIFT_LATERAL_DAMPING
            else -> GameConstants.NORMAL_LATERAL_DAMPING
        }
        val dampedLatVel = currentLateralVel * (1f - gripDamping * dt).coerceIn(0f, 1f)

        kart.vx = fwdX * kart.forwardSpeed + rightX * dampedLatVel
        kart.vz = fwdZ * kart.forwardSpeed + rightZ * dampedLatVel

        if (kart.magnetTimerSec > 0f) {
            val aheadKart = allKarts.firstOrNull {
                it.slotIndex != kart.slotIndex && it.racePosition == kart.racePosition - 1
            }
            if (aheadKart != null) {
                val dx = aheadKart.x - kart.x
                val dz = aheadKart.z - kart.z
                val d = hypot(dx, dz).coerceAtLeast(1f)
                kart.vx += (dx / d) * GameConstants.MAGNET_PULL_ACCEL * dt
                kart.vz += (dz / d) * GameConstants.MAGNET_PULL_ACCEL * dt
            }
        }

        kart.x += kart.vx * dt
        kart.z += kart.vz * dt

        // 9. Vertical Physics, Elevated Bridges, Guard Walls, Ramps & Sink/Water Hazards
        // v7: continuous surface under the kart's NEW position (no per-sample height steps)
        val ground = track.surfaceFrame(nearestSample, kart.x, kart.z)
        var supportY = ground.y

        if (ground.y > 1.5f) {
            val bridgeWallLimit = halfRoad + 0.4f
            val curLat = ground.lateral
            if (abs(curLat) > bridgeWallLimit) {
                val clampSide = if (curLat > 0f) bridgeWallLimit else -bridgeWallLimit
                val excessLat = curLat - clampSide
                kart.x -= ground.rightX * excessLat
                kart.z -= ground.rightZ * excessLat
                // slide along the guard rail: kill only the velocity going into it
                val outward = if (curLat > 0f) 1f else -1f
                val vInto = (kart.vx * ground.rightX + kart.vz * ground.rightZ) * outward
                if (vInto > 0f) {
                    kart.vx -= ground.rightX * outward * vInto
                    kart.vz -= ground.rightZ * outward * vInto
                }
                val wallHeadingErr = wrapAngle(ground.headingRad - kart.headingRad)
                kart.headingRad += wallHeadingErr.coerceIn(-0.4f, 0.4f) * 4.5f * dt
                if (kart.wallBumpCooldownSec <= 0f) {
                    // one-off scrub on impact, proportional to how head-on it was
                    val headOn = abs(sin(wallHeadingErr)).coerceIn(0f, 1f)
                    kart.forwardSpeed *= 1f - 0.25f * headOn
                    kart.wallBumpCooldownSec = WALL_BUMP_COOLDOWN_SEC
                    if (headOn > 0.15f) onWallBump()
                } else {
                    kart.forwardSpeed *= 1f - 0.4f * dt // light rail friction while grinding
                }
            }
        }

        var onBoostPad = false
        for (prop in track.props) {
            if (!prop.isBoostPad) continue
            val dx = kart.x - prop.x
            val dz = kart.z - prop.z
            val c = cos(-prop.yawRad)
            val s = sin(-prop.yawRad)
            val localX = dx * c - dz * s
            val localZ = dx * s + dz * c
            if (abs(localX) <= prop.sx * 0.5f && abs(localZ) <= prop.sz * 0.5f && abs(kart.y - prop.y) < 3.0f) {
                onBoostPad = true
                applyBoost(kart, GameConstants.STICKY_NOTE_BOOST_SEC, GameConstants.STICKY_NOTE_BOOST_MULT)
                // v7: sound + haptic once per pad, not every tick spent on it (that was a 60 Hz buzz)
                if (!kart.wasOnBoostPad) onBoostTriggered()
            }
        }
        kart.wasOnBoostPad = onBoostPad

        // v7: ramps are real slopes rising along the driving direction; the kart rolls up them and
        // launches off the lip with a speed-scaled kick (was: an instant 0.9 u pop onto a flat slab).
        for ((_, ramp) in track.rampFrames) {
            val dx = kart.x - ramp.centerX
            val dz = kart.z - ramp.centerZ
            val along = dx * ramp.tangentX + dz * ramp.tangentZ
            val lat = dx * ramp.rightX + dz * ramp.rightZ
            if (abs(along) > ramp.halfLength || abs(lat) > ramp.halfWidth) continue
            if (abs(kart.y - (ramp.baseY + ramp.rise * 0.5f)) > 3.0f) continue
            val t = ((along + ramp.halfLength) / (2f * ramp.halfLength)).coerceIn(0f, 1f)
            val rampY = ramp.baseY + ramp.rise * t
            supportY = maxOf(supportY, rampY)
            val travellingUp = kart.vx * ramp.tangentX + kart.vz * ramp.tangentZ
            if (t > 0.80f && travellingUp > 12f && kart.vy <= 1.5f && kart.y <= rampY + 0.25f) {
                val k = (travellingUp / GameConstants.TOP_SPEED).coerceIn(0.6f, 1.15f)
                kart.vy = GameConstants.RAMP_LAUNCH_VY * k
                kart.y = maxOf(kart.y, rampY)
            }
        }

        if (track.isInSinkHole(kart.x, kart.z) && kart.y <= 0.4f) {
            supportY = -12f
        }
        if (track.isInWaterPool(kart.x, kart.z) && kart.y <= 0.4f) {
            supportY = -12f
        }
        if (abs(kart.x) > track.deskHalfExtentX || abs(kart.z) > track.deskHalfExtentZ) {
            supportY = -12f
        }

        // v6: ground-stick. A kart that was on the ground last tick and is moving down a slope stays
        // glued to it instead of micro-hopping every few ticks (visible as shaking at top speed).
        if (kart.groundedLastTick && supportY >= -1f && kart.vy <= 0f) {
            val gap = kart.y - supportY
            if (gap > 0f && gap < GROUND_STICK_MAX_GAP) {
                kart.y = supportY
                kart.vy = 0f
            }
        }
        kart.y += kart.vy * dt
        if (kart.y > supportY + 0.02f) {
            kart.airTimeSec += dt
        }
        if (kart.y > supportY) {
            kart.vy -= GameConstants.GRAVITY * dt
            if (kart.y <= supportY && supportY >= -1f) {
                val impactVy = -kart.vy
                kart.y = supportY
                kart.vy = 0f
                // v7: only a real jump counts as a landing (a drift hop used to fire the camera
                // shake + haptic on every single drift)
                if (kart.airTimeSec >= REAL_AIR_SEC) {
                    kart.landingImpact = (impactVy / 16f).coerceIn(0.25f, 1f)
                    kart.landingShakeTrigger = true
                    onLanded()
                }
                if (kart.trickArmedInAir) {
                    kart.trickArmedInAir = false
                    applyBoost(kart, GameConstants.TRICK_BOOST_SEC)
                    onBoostTriggered()
                }
                kart.airTimeSec = 0f
            }
        } else if (supportY >= -1f) {
            kart.y = supportY
            kart.vy = 0f
            kart.trickArmedInAir = false
            kart.airTimeSec = 0f
        }

        kart.groundedLastTick = supportY >= -1f && kart.vy == 0f && kart.y <= supportY + 0.001f
        if (kart.y < GameConstants.FALL_OFF_Y) {
            kart.isRespawning = true
            kart.respawnTimerSec = GameConstants.LAKITU_RESPAWN_SEC
            onRespawnToast()
            return
        }

        // 10. Collisions
        // Step A: Kart-vs-kart contact (v7: sequential-impulse style with slop)
        //  - positional correction only resolves part of the penetration beyond a small slop each
        //    tick (Baumgarte), instead of shoving both karts 0.25 u past contact — that over-push was
        //    the "bumping into someone makes both karts vibrate" effect.
        //  - restitution only applies above a closing-speed slop, so resting/scraping contacts
        //    don't bounce, while a real T-bone still knocks the lighter kart sideways.
        //  - the impulse is split back into forward speed + lateral slide for BOTH karts so the
        //    shove survives the next tick's grip model instead of being silently overwritten.
        if (kart.ghostTimerSec <= 0f) {
            for (other in allKarts) {
                if (other.slotIndex == kart.slotIndex || other.isRespawning || other.ghostTimerSec > 0f) continue
                val dx = kart.x - other.x
                val dz = kart.z - other.z
                val d = hypot(dx, dz)
                val minSep = KART_CONTACT_RADIUS * 2f
                if (d < minSep && d > 0.001f && abs(kart.y - other.y) < 1.6f) {
                    val nx = dx / d
                    val nz = dz / d
                    val invM1 = 1f / kart.bodyType.mass
                    val invM2 = 1f / other.bodyType.mass
                    val invMassSum = invM1 + invM2

                    val penetration = minSep - d
                    val correction = ((penetration - CONTACT_SLOP).coerceAtLeast(0f) * CONTACT_BETA) / invMassSum
                    kart.x += nx * correction * invM1
                    kart.z += nz * correction * invM1
                    other.x -= nx * correction * invM2
                    other.z -= nz * correction * invM2

                    val rvx = kart.vx - other.vx
                    val rvz = kart.vz - other.vz
                    val closing = rvx * nx + rvz * nz
                    if (closing < 0f) {
                        val e = if (-closing > RESTITUTION_SLOP) KART_RESTITUTION else 0f
                        val j = -(1f + e) * closing / invMassSum
                        kart.vx += nx * j * invM1
                        kart.vz += nz * j * invM1
                        other.vx -= nx * j * invM2
                        other.vz -= nz * j * invM2
                        kart.forwardSpeed = kart.vx * fwdX + kart.vz * fwdZ
                        val ofx = sin(other.headingRad)
                        val ofz = cos(other.headingRad)
                        other.forwardSpeed = other.vx * ofx + other.vz * ofz
                        if (-closing > 4f && kart.wallBumpCooldownSec <= 0f) {
                            kart.wallBumpCooldownSec = WALL_BUMP_COOLDOWN_SEC
                            onWallBump()
                        }
                    }

                    if (other.orbitingTacksCount > 0 && kart.invulnerableTimerSec <= 0f) {
                        other.orbitingTacksCount--
                        if (other.orbitingTacksCount <= 0) other.heldItem = ItemType.NONE
                        triggerSpinOut(kart)
                    }
                }
            }
        }

        // Step A2: Slipstream (drafting). Tuck in behind a kart for a moment and you're slung past it.
        if (grounded && kart.forwardSpeed > 18f && kart.boostTimerSec <= 0f && kart.spinOutTimerSec <= 0f) {
            var inDraft = false
            for (other in allKarts) {
                if (other.slotIndex == kart.slotIndex || other.isRespawning || other.forwardSpeed < 14f) continue
                if (abs(other.y - kart.y) > 1.5f) continue
                val ox = other.x - kart.x
                val oz = other.z - kart.z
                val d = hypot(ox, oz)
                if (d < 2.2f || d > 15f) continue
                if ((ox * fwdX + oz * fwdZ) / d > 0.965f) { inDraft = true; break }
            }
            if (inDraft) {
                kart.draftSec += dt
                if (kart.draftSec >= DRAFT_CHARGE_SEC) {
                    kart.draftSec = 0f
                    applyBoost(kart, 0.9f, 1.20f)
                    onBoostTriggered()
                }
            } else {
                kart.draftSec = (kart.draftSec - dt * 2f).coerceAtLeast(0f)
            }
        } else {
            kart.draftSec = 0f
        }

        // Step B: Knockable Pylons (Part H3: 15% slowdown, tumble away, respawn after 6s)
        var knockedAnyPylon = false
        for (pylon in activePylons) {
            if (pylon.isKnocked) continue
            if (abs(kart.y - pylon.baseY) > 2.5f) continue
            val dx = kart.x - pylon.x
            val dz = kart.z - pylon.z
            val d = hypot(dx, dz)
            val hitR = pylon.radius + 0.85f
            if (d < hitR) {
                kart.forwardSpeed *= 0.85f // 15% speed reduction instead of solid stop
                val pushDirX = if (d > 0.01f) -dx / d else fwdX
                val pushDirZ = if (d > 0.01f) -dz / d else fwdZ
                val launchSpeed = maxOf(abs(kart.forwardSpeed) * 1.2f, 14f)
                pylon.vx = pushDirX * launchSpeed + kart.vx * 0.35f
                pylon.vz = pushDirZ * launchSpeed + kart.vz * 0.35f
                pylon.vy = 3.5f
                pylon.omegaPitch = 7.8f
                pylon.omegaRoll = 5.2f
                pylon.respawnTimerSec = 6.0f
                knockedAnyPylon = true
            }
        }
        if (knockedAnyPylon) {
            onPylonKnocked()
        }

        // Step C: Solid Landmarks & OBB Props
        val hitProp = resolvePropCollisions(kart, track)
        if (hitProp && kart.wallBumpCooldownSec <= 0f) {
            kart.wallBumpCooldownSec = WALL_BUMP_COOLDOWN_SEC
            onWallBump()
        }

        // Step D: Dynamic Hazards
        if (kart.invulnerableTimerSec <= 0f && kart.ghostTimerSec <= 0f) {
            for (h in track.dynamicHazards) {
                val phase = ((raceElapsedSec / h.periodSec) + h.phaseOffset) * (2f * PI.toFloat())
                val offset = sin(phase) * h.amplitude
                val hx = h.centerX + h.axisX * offset
                val hz = h.centerZ + h.axisZ * offset
                val dx = kart.x - hx
                val dz = kart.z - hz
                val hitR = h.radius + 1.0f
                if (dx * dx + dz * dz < hitR * hitR && abs(kart.y - h.centerY) < 2.2f) {
                    triggerSpinOut(kart)
                    onWallBump()
                    break
                }
            }
        }

        // 11. Progress Watchdog
        if (abs(signedLateral) > halfRoad + GameConstants.FAR_OFF_TRACK_MARGIN) {
            kart.farOffTrackTimerSec += dt
        } else {
            kart.farOffTrackTimerSec = 0f
        }

        val dotTangent = fwdX * nearestSample.tangentX + fwdZ * nearestSample.tangentZ
        if (kart.wrongWayGraceTimerSec > 0f || kart.spinOutTimerSec > 0f) {
            // Part C7: Ignore the first 2s after respawn or spin-out
            kart.wrongWayTimerSec = 0f
            kart.isWrongWay = false
        } else if (dotTangent < -0.30f && kart.forwardSpeed > 2.0f) {
            kart.wrongWayTimerSec += dt
            kart.isWrongWay = kart.wrongWayTimerSec > 1.0f
        } else {
            kart.wrongWayTimerSec = 0f
            kart.isWrongWay = false
        }

        if (kart.spinOutTimerSec <= 0f && kart.rocketStartStallTimerSec <= 0f) {
            kart.watchdogSampleTimerSec += dt
            if (kart.watchdogSampleTimerSec >= GameConstants.WATCHDOG_SAMPLE_INTERVAL_SEC) {
                kart.watchdogSampleTimerSec -= GameConstants.WATCHDOG_SAMPLE_INTERVAL_SEC
                val idx = kart.watchdogWriteIdx % kart.watchdogHistory.size
                val oldestDist = if (kart.watchdogSampleCount >= kart.watchdogHistory.size) {
                    kart.watchdogHistory[idx]
                } else {
                    kart.raceDistance
                }
                kart.watchdogHistory[idx] = kart.raceDistance
                kart.watchdogWriteIdx++
                kart.watchdogSampleCount++

                val stuckByProgress = kart.watchdogSampleCount > kart.watchdogHistory.size &&
                    (kart.raceDistance - oldestDist) < GameConstants.WATCHDOG_MIN_DISTANCE
                val stuckFarOffRoad = kart.farOffTrackTimerSec >= GameConstants.FAR_OFF_TRACK_RESPAWN_SEC
                val stuckBotWrongWay = !kart.isHuman && kart.wrongWayTimerSec >= GameConstants.BOT_WRONG_WAY_RESPAWN_SEC

                if (stuckByProgress || stuckFarOffRoad || stuckBotWrongWay) {
                    respawnKartBehindLastGood(kart, track)
                    onRespawnToast()
                    return
                }
            }
        }

        // 12. Visual Tilt, Roll & Road Banking (v7: everything eased with frame-rate independent
        // exponentials; the trick flip and landing squash are shaped curves, never instant snaps)
        fun ease(rate: Float) = 1f - kotlin.math.exp(-rate * dt)
        val targetYawOffset = when {
            kart.spinOutTimerSec > 0f -> kart.spinOutAngleRad
            kart.isDrifting -> -kart.driftDirection * (0.30f + 0.10f * driftInto.coerceAtLeast(0f))
            else -> -inp.steer * 0.10f
        }
        kart.visualYawOffsetRad += (targetYawOffset - kart.visualYawOffsetRad) *
            (if (kart.spinOutTimerSec > 0f) 1f else ease(10f))

        val bankRoll = ground.bankAngleRad
        val steerRoll = if (kart.isDrifting) kart.driftDirection * (0.10f + 0.06f * driftInto.coerceAtLeast(0f)) else inp.steer * 0.06f
        val targetRoll = steerRoll + bankRoll
        kart.visualRollRad += (targetRoll - kart.visualRollRad) * ease(8f)

        val airborne = kart.y > supportY + 0.05f
        val airPitch = when {
            !airborne -> 0f
            kart.vy > 1f -> -0.14f
            kart.vy < -2f -> 0.10f
            else -> 0f
        }
        // trick: one smooth nose-up-and-back arc over 0.42 s (sin bump), not a -0.38 rad snap
        val trickPitch = if (kart.trickVisualSec > 0f) {
            val p = 1f - kart.trickVisualSec / 0.42f
            -0.42f * sin(p * PI.toFloat())
        } else 0f
        // landing: brief nose-down squash that decays (suspension feel)
        val landPitch = 0.10f * kart.landingImpact * kart.landingImpact
        // longitudinal weight transfer: nose lifts under throttle/boost, dives under braking/impacts
        val longAccel = ((kart.forwardSpeed - kart.visSpeedPrev) / dt).coerceIn(-60f, 40f)
        kart.visSpeedPrev = kart.forwardSpeed
        kart.visAccelSmoothed += (longAccel - kart.visAccelSmoothed) * ease(6f)
        val weightPitch = if (airborne) 0f else (-kart.visAccelSmoothed * 0.0035f).coerceIn(-0.06f, 0.07f)
        val targetPitch = airPitch + trickPitch + landPitch + weightPitch
        kart.visualPitchRad += (targetPitch - kart.visualPitchRad) * ease(if (kart.trickVisualSec > 0f) 22f else 9f)

        // 13. Plane-Crossing Checkpoint Gates & Unwrapped Lap Completion (with C3 Sub-Tick Interpolation)
        for (g in track.checkpoints) {
            if (g.index == 0) continue
            val prevSide = (kart.prevX - g.center.x) * g.normalX + (kart.prevZ - g.center.z) * g.normalZ
            val currSide = (kart.x - g.center.x) * g.normalX + (kart.z - g.center.z) * g.normalZ
            if (prevSide < 0f && currSide >= 0f && abs(kart.y - g.center.y) < 4.5f) {
                val lat = abs((kart.x - g.center.x) * g.rightX + (kart.z - g.center.z) * g.rightZ)
                if (lat <= g.halfWidth && g.index > kart.highestCheckpointPassed) {
                    kart.highestCheckpointPassed = g.index
                }
            }
        }

        val gate0 = track.checkpoints[0]
        val prevSide0 = (kart.prevX - gate0.center.x) * gate0.normalX + (kart.prevZ - gate0.center.z) * gate0.normalZ
        val currSide0 = (kart.x - gate0.center.x) * gate0.normalX + (kart.z - gate0.center.z) * gate0.normalZ
        val lat0 = abs((kart.x - gate0.center.x) * gate0.rightX + (kart.z - gate0.center.z) * gate0.rightZ)
        val crossedFinishPlane = prevSide0 < 0f && currSide0 >= 0f && lat0 <= gate0.halfWidth && abs(kart.y - gate0.center.y) < 5.0f
        val wrappedFinishSpline = oldS > track.totalLength * 0.80f && newS < track.totalLength * 0.20f && deltaS > 0f

        val requiredLapDist = kart.lapsCompleted * track.totalLength + 0.85f * track.totalLength
        if ((crossedFinishPlane || wrappedFinishSpline) && kart.raceDistance >= requiredLapDist) {
            val subTickFrac = if (crossedFinishPlane) {
                val denom = currSide0 - prevSide0
                if (denom > 0.0001f) (-prevSide0 / denom).coerceIn(0f, 1f) else 1f
            } else {
                val distToLine = (track.totalLength - oldS).coerceAtLeast(0f)
                if (deltaS > 0.001f) (distToLine / deltaS).coerceIn(0f, 1f) else 1f
            }
            kart.lapsCompleted += 1
            val completedLap = kart.lapsCompleted
            kart.lap = kart.lapsCompleted + 1
            kart.highestCheckpointPassed = 0
            kart.raceDistance = kart.lapsCompleted * track.totalLength + newS
            onLapCompleted(completedLap, subTickFrac)
        }
    }

    private fun resolvePropCollisions(kart: KartRuntime, track: Track): Boolean {
        var hitAny = false
        val kartRadius = 1.0f

        // 1. Solid Placed Landmarks from JSON (vertical cylinder collisions)
        for (prop in track.placedProps) {
            if (!prop.solid || prop.isPylon) continue
            if (kart.y > prop.y + 25f || kart.y + 1.5f < prop.y) continue
            val radius = prop.radius + kartRadius
            val dx = kart.x - prop.x
            val dz = kart.z - prop.z
            val d = hypot(dx, dz)
            if (d < radius && d > 0.001f) {
                val nx = dx / d
                val nz = dz / d
                kart.x = prop.x + nx * (radius + 0.01f)
                kart.z = prop.z + nz * (radius + 0.01f)
                if (prop.name.contains("orange", ignoreCase = true)) {
                    val dot = kart.vx * nx + kart.vz * nz
                    if (dot < 0f) {
                        kart.vx -= 1.8f * dot * nx
                        kart.vz -= 1.8f * dot * nz
                        if (kart.wallBumpCooldownSec <= 0f) kart.forwardSpeed *= 0.84f
                    }
                } else {
                    wallContact(kart, nx, nz)
                }
                hitAny = true
            }
        }

        // 2. Any remaining solid TrackProps
        for (prop in track.props) {
            if (!prop.isSolidWall) continue
            if (kart.y > prop.y + prop.sy + 0.5f || kart.y + 1.5f < prop.y) continue

            if (prop.isRoundBumper) {
                val radius = maxOf(prop.sx, prop.sz) * 0.5f + kartRadius
                val dx = kart.x - prop.x
                val dz = kart.z - prop.z
                val d = hypot(dx, dz)
                if (d < radius && d > 0.001f) {
                    val nx = dx / d
                    val nz = dz / d
                    kart.x = prop.x + nx * (radius + 0.01f)
                    kart.z = prop.z + nz * (radius + 0.01f)
                    if (prop.restitution >= 0.6f) {
                        val dot = kart.vx * nx + kart.vz * nz
                        if (dot < 0f) {
                            kart.vx -= (1f + prop.restitution) * dot * nx
                            kart.vz -= (1f + prop.restitution) * dot * nz
                            if (kart.wallBumpCooldownSec <= 0f) kart.forwardSpeed *= 0.82f
                        }
                    } else {
                        wallContact(kart, nx, nz)
                    }
                    hitAny = true
                }
            } else {
                val dx = kart.x - prop.x
                val dz = kart.z - prop.z
                val c = cos(-prop.yawRad)
                val s = sin(-prop.yawRad)
                val localX = dx * c - dz * s
                val localZ = dx * s + dz * c

                val hx = prop.sx * 0.5f
                val hz = prop.sz * 0.5f
                val clampedX = localX.coerceIn(-hx, hx)
                val clampedZ = localZ.coerceIn(-hz, hz)

                val diffX = localX - clampedX
                val diffZ = localZ - clampedZ
                val distSq = diffX * diffX + diffZ * diffZ

                if (distSq < kartRadius * kartRadius) {
                    val pushLocalX: Float
                    val pushLocalZ: Float
                    if (distSq > 0.0001f) {
                        val dist = kotlin.math.sqrt(distSq)
                        val penetration = (kartRadius - dist) + 0.01f
                        pushLocalX = (diffX / dist) * penetration
                        pushLocalZ = (diffZ / dist) * penetration
                    } else {
                        val ox = hx - abs(localX)
                        val oz = hz - abs(localZ)
                        if (ox < oz) {
                            pushLocalX = if (localX >= 0f) ox + kartRadius else -(ox + kartRadius)
                            pushLocalZ = 0f
                        } else {
                            pushLocalX = 0f
                            pushLocalZ = if (localZ >= 0f) oz + kartRadius else -(oz + kartRadius)
                        }
                    }

                    val cw = cos(prop.yawRad)
                    val sw = sin(prop.yawRad)
                    val worldPushX = pushLocalX * cw - pushLocalZ * sw
                    val worldPushZ = pushLocalX * sw + pushLocalZ * cw

                    kart.x += worldPushX
                    kart.z += worldPushZ
                    val pl = hypot(worldPushX, worldPushZ)
                    if (pl > 1e-4f) wallContact(kart, worldPushX / pl, worldPushZ / pl)
                    hitAny = true
                }
            }
        }
        return hitAny
    }

    /**
     * v7: wall contact = slide, not stop. Removes the velocity going into the wall, scrubs speed by
     * how head-on the hit was (once per impact), and turns the nose along the wall so the kart
     * glides off instead of grinding and shuddering against it every tick.
     */
    private fun wallContact(kart: KartRuntime, nx: Float, nz: Float) {
        val vn = kart.vx * nx + kart.vz * nz
        if (vn < 0f) {
            kart.vx -= 1.15f * vn * nx
            kart.vz -= 1.15f * vn * nz
        }
        val fx = sin(kart.headingRad)
        val fz = cos(kart.headingRad)
        val into = -(fx * nx + fz * nz) // 1 = driving straight into it
        val headOn = into.coerceIn(0f, 1f)
        if (kart.wallBumpCooldownSec <= 0f) {
            kart.forwardSpeed *= 1f - 0.45f * headOn
        } else {
            kart.forwardSpeed *= 1f - 0.03f * headOn
        }
        if (headOn > 0.02f && headOn < 0.97f) {
            // tangent direction closest to the current heading
            var tx = -nz
            var tz = nx
            if (tx * fx + tz * fz < 0f) { tx = -tx; tz = -tz }
            val err = wrapAngle(kotlin.math.atan2(tx, tz) - kart.headingRad)
            kart.headingRad += err.coerceIn(-0.05f, 0.05f) * headOn
        }
    }

    fun wrapAngle(angle: Float): Float {
        var a = angle
        while (a > PI.toFloat()) a -= PI.toFloat() * 2f
        while (a < -PI.toFloat()) a += PI.toFloat() * 2f
        return a
    }
}
