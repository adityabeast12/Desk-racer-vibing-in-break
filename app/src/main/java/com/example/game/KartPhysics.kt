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
    var bodyType: KartBodyType = KartBodyType.CLASSIC
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

    // Status Timers & Special Zones
    var spinOutTimerSec: Float = 0f
    var invulnerableTimerSec: Float = 0f
    var ghostTimerSec: Float = 0f // 1.5s ghost mode after respawn (50% alpha, no kart collisions)
    var isOffTrack: Boolean = false
    var isOnSlipperyZone: Boolean = false
    var paintTintHex: Long = 0L
    var paintTintTimerSec: Float = 0f
    var isRespawning: Boolean = false
    var respawnTimerSec: Float = 0f
    var wrongWayTimerSec: Float = 0f
    var isWrongWay: Boolean = false
    var farOffTrackTimerSec: Float = 0f

    // Progress Watchdog (Part B5: 6 samples at 0.5s intervals = 3.0s window)
    val watchdogHistory = FloatArray(6)
    var watchdogWriteIdx: Int = 0
    var watchdogSampleCount: Int = 0
    var watchdogSampleTimerSec: Float = 0f
    var lastGoodSampleIndex: Int = 0
    var watchdogRespawnCount: Int = 0

    // Rocket Start Tracking (Part D2)
    var rocketStartHoldSec: Float = 0f
    var rocketStartOverheated: Boolean = false
    var rocketStartStallTimerSec: Float = 0f

    // Item, Coin & Balloon State
    var heldItem: ItemType = ItemType.NONE
    var pendingItem: ItemType = ItemType.NONE
    var rouletteTimerSec: Float = 0f
    var orbitingTacksCount: Int = 0
    var shieldEraserHeld: Boolean = false
    var magnetTimerSec: Float = 0f
    var coinsHeld: Int = 0
    var balloonsRemaining: Int = 3

    // Unwrapped Progress & Lap Timing (Part B3 & B4)
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
            // Start slightly negative corresponding to grid distance behind start line
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
        kart.forwardSpeed *= GameConstants.SPIN_OUT_SPEED_FACTOR
        kart.isDrifting = false
        kart.driftChargeUnits = 0
        kart.boostTimerSec = 0f
        // Part D6: getting hit drops 2 coins (and pops 1 balloon in Battle mode)
        kart.coinsHeld = (kart.coinsHeld - 2).coerceAtLeast(0)
        if (kart.balloonsRemaining > 0) {
            kart.balloonsRemaining--
        }
        // Reset watchdog window so spin-out doesn't count as stuck
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
     * Places the kart on the centreline 10u BEHIND the last good position, facing the spline tangent,
     * with 1.5s of ghost mode (no kart collisions, 50% alpha) — Part B5.
     */
    fun respawnKartBehindLastGood(kart: KartRuntime, track: Track) {
        val goodSample = track.samples.getOrElse(kart.lastGoodSampleIndex) { track.samples.first() }
        val targetS = track.wrapDistance(goodSample.distance - GameConstants.WATCHDOG_RESPAWN_BACK_OFFSET)
        val respawnSample = track.sampleAtDistance(targetS)

        kart.x = respawnSample.pos.x
        kart.y = respawnSample.pos.y + 0.15f
        kart.z = respawnSample.pos.z
        kart.prevX = kart.x
        kart.prevY = kart.y
        kart.prevZ = kart.z
        kart.vx = 0f
        kart.vy = 0f
        kart.vz = 0f
        kart.forwardSpeed = 6.0f
        kart.headingRad = respawnSample.headingRad
        kart.isDrifting = false
        kart.driftChargeUnits = 0
        kart.isRespawning = false
        kart.respawnTimerSec = 0f
        kart.ghostTimerSec = GameConstants.GHOST_AFTER_RESPAWN_SEC
        kart.invulnerableTimerSec = GameConstants.GHOST_AFTER_RESPAWN_SEC
        kart.wrongWayTimerSec = 0f
        kart.isWrongWay = false
        kart.farOffTrackTimerSec = 0f
        kart.watchdogSampleCount = 0
        kart.watchdogSampleTimerSec = 0f

        // Re-seat trackS without corrupting unwrapped raceDistance
        val deltaBack = track.wrapSignedDelta(respawnSample.distance - kart.trackS)
        kart.trackS = respawnSample.distance
        kart.splineDistance = respawnSample.distance
        kart.lastSampleIndex = respawnSample.index
        kart.lastGoodSampleIndex = respawnSample.index
        if (deltaBack < 0f && abs(deltaBack) < 40f) {
            val minLapFloor = kart.lapsCompleted * track.totalLength
            kart.raceDistance = (kart.raceDistance + deltaBack).coerceAtLeast(minLapFloor)
        }
        kart.watchdogRespawnCount++
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
        onWallBump: () -> Unit = {},
        onBoostTriggered: () -> Unit = {},
        onLanded: () -> Unit = {},
        onRespawnToast: () -> Unit = {},
        onLapCompleted: (completedLap: Int) -> Unit = {}
    ) {
        kart.prevX = kart.x
        kart.prevY = kart.y
        kart.prevZ = kart.z

        // 1. Item Roulette Animation Countdown
        if (kart.rouletteTimerSec > 0f) {
            kart.rouletteTimerSec -= dt
            if (input.itemJustPressed && kart.rouletteTimerSec < GameConstants.ITEM_ROULETTE_SEC - 0.25f) {
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

        // 3. Windowed Height-Aware Nearest Spline Sample (Part B3)
        val nearestRes = track.findNearestSampleWindowed(
            worldX = kart.x,
            worldY = kart.y,
            worldZ = kart.z,
            prevSampleIndex = kart.lastSampleIndex,
            windowRadius = 40
        )
        val nearestSample = nearestRes.sample
        val signedLateral = nearestRes.lateralOffset
        val oldS = kart.trackS
        val newS = nearestSample.distance
        kart.lastSampleIndex = nearestSample.index
        kart.trackS = newS
        kart.splineDistance = newS

        val deltaS = track.wrapSignedDelta(newS - oldS)
        if (abs(deltaS) <= 25f) {
            kart.raceDistance += deltaS
        }

        val halfRoad = track.roadWidth * 0.5f
        val verticalDiffFromRoad = abs(kart.y - nearestSample.pos.y)
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
        val grounded = kart.y <= nearestSample.pos.y + 0.14f

        if (canControl && input.driftJustPressed) {
            if (grounded) {
                kart.hopTimerSec = GameConstants.HOP_DURATION_SEC
                kart.vy = GameConstants.HOP_VELOCITY_Y
                if (abs(input.steer) > 0.12f && kart.forwardSpeed > 8f) {
                    kart.isDrifting = true
                    kart.driftDirection = if (input.steer > 0f) 1 else -1
                    kart.driftChargeUnits = 0
                }
            } else if (kart.y > nearestSample.pos.y + 0.6f && !kart.trickArmedInAir) {
                kart.trickArmedInAir = true
                kart.visualPitchRad = -0.38f
            }
        }

        if (kart.isDrifting) {
            if (!input.driftPressed || kart.forwardSpeed < 6f || !canControl) {
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
                val sameDir = (kart.driftDirection > 0 && input.steer > 0.1f) ||
                    (kart.driftDirection < 0 && input.steer < -0.1f)
                val baseInc = if (sameDir) GameConstants.DRIFT_CHARGE_INTO else GameConstants.DRIFT_CHARGE_COUNTER
                val increment = if (kart.bodyType == KartBodyType.TURBO_COUPE) baseInc + 1 else baseInc
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
                (kart.driftDirection * 0.55f + input.steer * 0.65f).coerceIn(-1.25f, 1.25f)
            } else {
                input.steer
            }

            kart.headingRad -= effectiveSteer * steerRate * dt
            kart.frontWheelSteerRad = (-effectiveSteer * 0.42f).coerceIn(-0.45f, 0.45f)

            // Smart Steering Assist pulls gently back inside track boundaries
            if (smartSteering && abs(signedLateral) > halfRoad * 0.82f) {
                val pullError = wrapAngle(nearestSample.headingRad - kart.headingRad)
                kart.headingRad += pullError.coerceIn(-0.6f, 0.6f) * GameConstants.SMART_STEERING_PULL * dt
                if (abs(signedLateral) > halfRoad * 0.96f) {
                    val clampLat = if (signedLateral > 0f) halfRoad * 0.96f else -halfRoad * 0.96f
                    val excessLat = signedLateral - clampLat
                    kart.x -= nearestSample.rightX * excessLat
                    kart.z -= nearestSample.rightZ * excessLat
                }
            }
        }

        // 7. Forward Acceleration, Coins Bonus (+0.6%/coin), Off-Track Drag, Boosts
        val gasActive = canControl && (input.gasPressed || (autoAccelerate && !input.brakePressed))
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

        if (canControl) {
            if (input.brakePressed) {
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

        // Spin wheels proportional to distance travelled
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

        // Magnet pull toward nearest kart ahead
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
        var supportY = nearestSample.pos.y

        // Elevated bridge guard rails (Study Desk ruler bridge, Gamer Desk laptop bridge, Bookshelf Spiral, Toy Box bridge)
        if (nearestSample.pos.y > 1.5f) {
            val bridgeWallLimit = halfRoad + 0.4f
            val curDx = kart.x - nearestSample.pos.x
            val curDz = kart.z - nearestSample.pos.z
            val curLat = curDx * nearestSample.rightX + curDz * nearestSample.rightZ
            if (abs(curLat) > bridgeWallLimit) {
                val clampSide = if (curLat > 0f) bridgeWallLimit else -bridgeWallLimit
                val excessLat = curLat - clampSide
                kart.x -= nearestSample.rightX * excessLat
                kart.z -= nearestSample.rightZ * excessLat
                kart.forwardSpeed *= 0.96f
                // Redirect heading slightly toward bridge tangent so bots/players slide cleanly along the rail
                val wallHeadingErr = wrapAngle(nearestSample.headingRad - kart.headingRad)
                kart.headingRad += wallHeadingErr.coerceIn(-0.4f, 0.4f) * 4.5f * dt
            }
        }

        for (prop in track.props) {
            if (!prop.isRamp && !prop.isBoostPad) continue
            val dx = kart.x - prop.x
            val dz = kart.z - prop.z
            val c = cos(-prop.yawRad)
            val s = sin(-prop.yawRad)
            val localX = dx * c - dz * s
            val localZ = dx * s + dz * c
            if (abs(localX) <= prop.sx * 0.5f && abs(localZ) <= prop.sz * 0.5f && abs(kart.y - prop.y) < 3.0f) {
                if (prop.isBoostPad) {
                    applyBoost(kart, GameConstants.STICKY_NOTE_BOOST_SEC, GameConstants.STICKY_NOTE_BOOST_MULT)
                    onBoostTriggered()
                }
                if (prop.isRamp) {
                    supportY = maxOf(supportY, prop.y + prop.sy * 0.65f)
                    if (kart.forwardSpeed > 12f && kart.vy <= 1.5f) {
                        kart.vy = GameConstants.RAMP_LAUNCH_VY
                    }
                }
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

        val wasAirborne = kart.y > supportY + 0.35f
        kart.y += kart.vy * dt
        if (kart.y > supportY) {
            kart.vy -= GameConstants.GRAVITY * dt
            if (kart.y <= supportY && supportY >= -1f) {
                kart.y = supportY
                kart.vy = 0f
                if (wasAirborne) {
                    kart.landingShakeTrigger = true
                    onLanded()
                    if (kart.trickArmedInAir) {
                        kart.trickArmedInAir = false
                        applyBoost(kart, GameConstants.TRICK_BOOST_SEC)
                        onBoostTriggered()
                    }
                }
            }
        } else if (supportY >= -1f) {
            kart.y = supportY
            kart.vy = 0f
            kart.trickArmedInAir = false
        }

        if (kart.y < GameConstants.FALL_OFF_Y) {
            kart.isRespawning = true
            kart.respawnTimerSec = GameConstants.LAKITU_RESPAWN_SEC
            onRespawnToast()
            return
        }

        // 10. Collisions (Part B1 & B6):
        // Step A: Mass-weighted Kart-vs-Kart impulse collision (0.25u separation bias, 30% velocity exchange)
        if (kart.ghostTimerSec <= 0f) {
            for (other in allKarts) {
                if (other.slotIndex == kart.slotIndex || other.isRespawning || other.ghostTimerSec > 0f) continue
                val dx = kart.x - other.x
                val dz = kart.z - other.z
                val d = hypot(dx, dz)
                val minSep = 1.90f
                if (d < minSep && d > 0.001f && abs(kart.y - other.y) < 1.6f) {
                    val nx = dx / d
                    val nz = dz / d
                    val m1 = kart.bodyType.mass
                    val m2 = other.bodyType.mass
                    val invM1 = 1f / m1
                    val invM2 = 1f / m2
                    val invMassSum = invM1 + invM2

                    // 0.25 u separation bias distributed by inverse mass
                    val overlap = (minSep - d) + 0.25f
                    kart.x += nx * overlap * (invM1 / invMassSum)
                    kart.z += nz * overlap * (invM1 / invMassSum)
                    other.x -= nx * overlap * (invM2 / invMassSum)
                    other.z -= nz * overlap * (invM2 / invMassSum)

                    // Exchange 30% of relative normal velocity
                    val rvx = kart.vx - other.vx
                    val rvz = kart.vz - other.vz
                    val relNormalVel = rvx * nx + rvz * nz
                    if (relNormalVel < 0f) {
                        val impulse = -(1.30f * relNormalVel) / invMassSum
                        kart.vx += nx * (impulse * invM1) * 0.30f
                        kart.vz += nz * (impulse * invM1) * 0.30f
                        other.vx -= nx * (impulse * invM2) * 0.30f
                        other.vz -= nz * (impulse * invM2) * 0.30f
                        // Update forwardSpeed projection
                        kart.forwardSpeed = kart.vx * fwdX + kart.vz * fwdZ
                    }

                    if (other.orbitingTacksCount > 0 && kart.invulnerableTimerSec <= 0f) {
                        other.orbitingTacksCount--
                        if (other.orbitingTacksCount <= 0) other.heldItem = ItemType.NONE
                        triggerSpinOut(kart)
                    }
                }
            }
        }

        // Step B: Oriented Bounding Box (OBB) & Round Bumper Prop Collisions (Part B1)
        val hitProp = resolvePropCollisions(kart, track)
        if (hitProp) {
            onWallBump()
        }

        // Step C: Dynamic Hazards (Rolling Peas on Kitchen Counter, Scissors Gate on Art Desk, Spinning Top on Toy Box)
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

        // 11. Progress Watchdog (Part B5)
        // Track far-off-track timer (> roadWidth/2 + 18u for > 2.5s)
        if (abs(signedLateral) > halfRoad + GameConstants.FAR_OFF_TRACK_MARGIN) {
            kart.farOffTrackTimerSec += dt
        } else {
            kart.farOffTrackTimerSec = 0f
        }

        // Wrong-way check (heading opposes spline tangent)
        val dotTangent = fwdX * nearestSample.tangentX + fwdZ * nearestSample.tangentZ
        if (dotTangent < -0.30f && kart.forwardSpeed > 2.0f) {
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

        // 12. Visual Tilt, Roll & Road Banking
        val targetYawOffset = when {
            kart.spinOutTimerSec > 0f -> kart.spinOutAngleRad
            kart.isDrifting -> -kart.driftDirection * 0.34f
            else -> -input.steer * 0.10f
        }
        kart.visualYawOffsetRad += (targetYawOffset - kart.visualYawOffsetRad) * (12f * dt).coerceAtMost(1f)

        val bankRoll = nearestSample.bankAngleRad
        val steerRoll = if (kart.isDrifting) kart.driftDirection * 0.14f else input.steer * 0.07f
        val targetRoll = steerRoll + bankRoll
        kart.visualRollRad += (targetRoll - kart.visualRollRad) * (10f * dt).coerceAtMost(1f)

        val targetPitch = if (kart.vy > 1f) -0.18f else if (kart.vy < -2f) 0.14f else 0f
        kart.visualPitchRad += (targetPitch - kart.visualPitchRad) * (10f * dt).coerceAtMost(1f)

        // 13. Plane-Crossing Checkpoint Gates & Unwrapped Lap Completion (Part B3)
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

        // Finish Gate (Gate 0) crossing + unwrapped raceDistance verification
        val gate0 = track.checkpoints[0]
        val prevSide0 = (kart.prevX - gate0.center.x) * gate0.normalX + (kart.prevZ - gate0.center.z) * gate0.normalZ
        val currSide0 = (kart.x - gate0.center.x) * gate0.normalX + (kart.z - gate0.center.z) * gate0.normalZ
        val lat0 = abs((kart.x - gate0.center.x) * gate0.rightX + (kart.z - gate0.center.z) * gate0.rightZ)
        val crossedFinishPlane = prevSide0 < 0f && currSide0 >= 0f && lat0 <= gate0.halfWidth && abs(kart.y - gate0.center.y) < 5.0f
        val wrappedFinishSpline = oldS > track.totalLength * 0.80f && newS < track.totalLength * 0.20f && deltaS > 0f

        val requiredLapDist = kart.lapsCompleted * track.totalLength + 0.85f * track.totalLength
        if ((crossedFinishPlane || wrappedFinishSpline) && kart.raceDistance >= requiredLapDist) {
            kart.lapsCompleted += 1
            val completedLap = kart.lapsCompleted
            kart.lap = kart.lapsCompleted + 1
            kart.highestCheckpointPassed = 0
            // Snap raceDistance cleanly to exact lap boundary + newS so multi-lap drift never accumulates
            kart.raceDistance = kart.lapsCompleted * track.totalLength + newS
            onLapCompleted(completedLap)
        }
    }

    /**
     * Resolves solid prop collisions using Oriented Bounding Boxes (OBB) respecting prop.yawRad (Part B1)
     * and round cylinder bumpers with restitution (Part E3).
     */
    private fun resolvePropCollisions(kart: KartRuntime, track: Track): Boolean {
        var hitAny = false
        val kartRadius = 1.0f

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
                    kart.x = prop.x + nx * (radius + 0.08f)
                    kart.z = prop.z + nz * (radius + 0.08f)
                    if (prop.restitution >= 0.6f) {
                        // Bouncy orange bumper on Kitchen Counter (restitution 0.8)
                        val dot = kart.vx * nx + kart.vz * nz
                        if (dot < 0f) {
                            kart.vx -= (1f + prop.restitution) * dot * nx
                            kart.vz -= (1f + prop.restitution) * dot * nz
                        }
                        kart.forwardSpeed *= 0.82f
                    } else {
                        kart.forwardSpeed *= 0.72f
                    }
                    hitAny = true
                }
            } else {
                // Oriented Box (OBB): transform kart position into prop's local frame by rotating by -yawRad
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
                        val penetration = (kartRadius - dist) + 0.08f
                        pushLocalX = (diffX / dist) * penetration
                        pushLocalZ = (diffZ / dist) * penetration
                    } else {
                        // Kart center is inside OBB: push out along nearest face normal
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

                    // Rotate push back from prop local space (+yawRad) to world space
                    val cw = cos(prop.yawRad)
                    val sw = sin(prop.yawRad)
                    val worldPushX = pushLocalX * cw - pushLocalZ * sw
                    val worldPushZ = pushLocalX * sw + pushLocalZ * cw

                    kart.x += worldPushX
                    kart.z += worldPushZ
                    kart.forwardSpeed *= 0.72f
                    hitAny = true
                }
            }
        }
        return hitAny
    }

    fun wrapAngle(angle: Float): Float {
        var a = angle
        while (a > PI.toFloat()) a -= PI.toFloat() * 2f
        while (a < -PI.toFloat()) a += PI.toFloat() * 2f
        return a
    }
}
