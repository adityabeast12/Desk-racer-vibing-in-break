package com.example.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
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
    var vx: Float = 0f
    var vy: Float = 0f
    var vz: Float = 0f
    var headingRad: Float = 0f
    var forwardSpeed: Float = 0f

    // Visual Body Tilt & Roll
    var visualYawOffsetRad: Float = 0f
    var visualRollRad: Float = 0f
    var visualPitchRad: Float = 0f
    var spinOutAngleRad: Float = 0f

    // Hop, Drift & Boost State
    var hopTimerSec: Float = 0f
    var isDrifting: Boolean = false
    var driftDirection: Int = 0 // -1 left, +1 right (screen steer sign)
    var driftChargeUnits: Int = 0
    var boostTimerSec: Float = 0f
    var boostMultiplier: Float = GameConstants.BOOST_SPEED_MULT
    var trickArmedInAir: Boolean = false
    var landingShakeTrigger: Boolean = false

    // Status Timers
    var spinOutTimerSec: Float = 0f
    var invulnerableTimerSec: Float = 0f
    var isOffTrack: Boolean = false
    var wallStuckTimerSec: Float = 0f
    var isRespawning: Boolean = false
    var respawnTimerSec: Float = 0f
    var wrongWayTimerSec: Float = 0f
    var isWrongWay: Boolean = false

    // Rocket Start Tracking
    var rocketStartHoldSec: Float = 0f
    var rocketStartOverheated: Boolean = false

    // Item State
    var heldItem: ItemType = ItemType.NONE
    var pendingItem: ItemType = ItemType.NONE
    var rouletteTimerSec: Float = 0f
    var orbitingTacksCount: Int = 0
    var shieldEraserHeld: Boolean = false
    var magnetTimerSec: Float = 0f

    // Progress & Timing
    var lap: Int = 1
    var highestCheckpointPassed: Int = 0
    var splineDistance: Float = 0f
    var racePosition: Int = slotIndex + 1
    var currentLapStartMs: Long = 0L
    var bestLapMs: Long = 0L
    var finishTimeMs: Long = 0L
    var hasFinishedRace: Boolean = false

    val sparkTier: Int
        get() = when {
            !isDrifting -> 0
            driftChargeUnits >= GameConstants.SPARK_PURPLE_UNITS -> 3
            driftChargeUnits >= GameConstants.SPARK_ORANGE_UNITS -> 2
            driftChargeUnits >= GameConstants.SPARK_BLUE_UNITS -> 1
            else -> 0
        }

    val totalRaceProgress: Float
        get() = (lap - 1) * 10000f + highestCheckpointPassed * 900f + splineDistance
}

object KartPhysics {

    fun resetKartToGrid(kart: KartRuntime, slot: StartGridSlot) {
        kart.x = slot.x
        kart.y = slot.y
        kart.z = slot.z
        kart.vx = 0f
        kart.vy = 0f
        kart.vz = 0f
        kart.headingRad = slot.headingRad
        kart.forwardSpeed = 0f
        kart.visualYawOffsetRad = 0f
        kart.visualRollRad = 0f
        kart.visualPitchRad = 0f
        kart.spinOutAngleRad = 0f
        kart.hopTimerSec = 0f
        kart.isDrifting = false
        kart.driftDirection = 0
        kart.driftChargeUnits = 0
        kart.boostTimerSec = 0f
        kart.spinOutTimerSec = 0f
        kart.invulnerableTimerSec = 0f
        kart.isRespawning = false
        kart.respawnTimerSec = 0f
        kart.wrongWayTimerSec = 0f
        kart.isWrongWay = false
        kart.heldItem = ItemType.NONE
        kart.pendingItem = ItemType.NONE
        kart.rouletteTimerSec = 0f
        kart.orbitingTacksCount = 0
        kart.shieldEraserHeld = false
        kart.magnetTimerSec = 0f
        kart.lap = 1
        kart.highestCheckpointPassed = 0
        kart.splineDistance = 0f
        kart.racePosition = slot.slotIndex + 1
        kart.bestLapMs = 0L
        kart.finishTimeMs = 0L
        kart.hasFinishedRace = false
    }

    fun triggerSpinOut(kart: KartRuntime) {
        if (kart.invulnerableTimerSec > 0f || kart.isRespawning) return
        kart.spinOutTimerSec = GameConstants.SPIN_OUT_DURATION_SEC
        kart.invulnerableTimerSec = GameConstants.SPIN_OUT_DURATION_SEC + GameConstants.INVULNERABLE_AFTER_HIT_SEC
        kart.forwardSpeed *= GameConstants.SPIN_OUT_SPEED_FACTOR
        kart.isDrifting = false
        kart.driftChargeUnits = 0
        kart.boostTimerSec = 0f
    }

    fun applyBoost(kart: KartRuntime, durationSec: Float, mult: Float = GameConstants.BOOST_SPEED_MULT) {
        if (durationSec > kart.boostTimerSec) {
            kart.boostTimerSec = durationSec
        }
        kart.boostMultiplier = mult
        val boostedTop = GameConstants.TOP_SPEED * mult
        if (kart.forwardSpeed < boostedTop * 0.88f) {
            kart.forwardSpeed = boostedTop * 0.88f
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
        onWallBump: () -> Unit = {},
        onBoostTriggered: () -> Unit = {},
        onRespawnToast: () -> Unit = {},
        onLapCompleted: (completedLap: Int) -> Unit = {}
    ) {
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

        // 2. Lakitu Respawn State
        if (kart.isRespawning) {
            kart.respawnTimerSec -= dt
            if (kart.respawnTimerSec <= 0f) {
                val cp = track.checkpoints.getOrElse(kart.highestCheckpointPassed) { track.checkpoints.first() }
                kart.x = cp.center.x
                kart.y = cp.center.y + 0.6f
                kart.z = cp.center.z
                kart.vx = 0f
                kart.vy = 0f
                kart.vz = 0f
                kart.forwardSpeed = 0f
                kart.headingRad = cp.headingRad
                kart.isRespawning = false
                kart.invulnerableTimerSec = 1.2f
            }
            return
        }

        if (kart.invulnerableTimerSec > 0f) {
            kart.invulnerableTimerSec = (kart.invulnerableTimerSec - dt).coerceAtLeast(0f)
        }
        if (kart.boostTimerSec > 0f) {
            kart.boostTimerSec = (kart.boostTimerSec - dt).coerceAtLeast(0f)
        }
        if (kart.magnetTimerSec > 0f) {
            kart.magnetTimerSec = (kart.magnetTimerSec - dt).coerceAtLeast(0f)
        }

        // 3. Nearest Spline Sample & Off-Track Check
        val (nearestSample, signedLateral) = track.findNearestSample(kart.x, kart.z)
        kart.splineDistance = nearestSample.distance
        val halfRoad = track.roadWidth * 0.5f
        kart.isOffTrack = abs(signedLateral) > halfRoad && kart.y <= 0.35f

        // 4. Spin-Out Handling (720 degree rotation over 1.1s)
        if (kart.spinOutTimerSec > 0f) {
            kart.spinOutTimerSec -= dt
            val progress = 1f - (kart.spinOutTimerSec / GameConstants.SPIN_OUT_DURATION_SEC).coerceIn(0f, 1f)
            kart.spinOutAngleRad = progress * (PI.toFloat() * 4f)
            kart.forwardSpeed *= (1f - 2.2f * dt).coerceAtLeast(0f)
        } else {
            kart.spinOutAngleRad = 0f
        }

        // 5. Hop + Drift Initiation & Release Boost
        val canControl = kart.spinOutTimerSec <= 0f
        val grounded = kart.y <= 0.08f

        if (canControl && input.driftJustPressed) {
            if (grounded) {
                kart.hopTimerSec = GameConstants.HOP_DURATION_SEC
                kart.vy = GameConstants.HOP_VELOCITY_Y
                if (abs(input.steer) > 0.12f && kart.forwardSpeed > 8f) {
                    kart.isDrifting = true
                    kart.driftDirection = if (input.steer > 0f) 1 else -1
                    kart.driftChargeUnits = 0
                }
            } else if (kart.y > 0.6f && !kart.trickArmedInAir) {
                // Mid-air Ramp Trick!
                kart.trickArmedInAir = true
                kart.visualPitchRad = -0.38f
            }
        }

        if (kart.isDrifting) {
            if (!input.driftPressed || kart.forwardSpeed < 6f || !canControl) {
                // Release Drift -> Grant Spark Boost!
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
                // Accumulate Drift Charge (5 units/frame into drift, 2 units/frame counter-steer)
                val sameDir = (kart.driftDirection > 0 && input.steer > 0.1f) ||
                    (kart.driftDirection < 0 && input.steer < -0.1f)
                val increment = if (sameDir) GameConstants.DRIFT_CHARGE_INTO else GameConstants.DRIFT_CHARGE_COUNTER
                val maxAllowedUnits = if (smartSteering) {
                    GameConstants.SPARK_PURPLE_UNITS - 1 // Smart steering blocks purple tier
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
            var steerRate = GameConstants.BASE_STEER_RATE * speedScaleTurn
            if (kart.isDrifting) steerRate *= GameConstants.DRIFT_STEER_BONUS

            val effectiveSteer = if (kart.isDrifting) {
                // Blend base drift direction with player fine steering
                (kart.driftDirection * 0.55f + input.steer * 0.65f).coerceIn(-1.25f, 1.25f)
            } else {
                input.steer
            }

            // In our coordinate system (atan2(tx, tz)), positive steer (right) decreases headingRad or increases depending on handedness:
            // Forward vector is (sin(headingRad), cos(headingRad)), Right vector is (-cos(headingRad), sin(headingRad)).
            // Rotating toward Right (-cos, sin) means decreasing headingRad:
            kart.headingRad -= effectiveSteer * steerRate * dt

            // Smart Steering Assist pulls gently back inside track boundaries
            if (smartSteering && abs(signedLateral) > halfRoad * 0.82f) {
                val pullError = wrapAngle(nearestSample.headingRad - kart.headingRad)
                kart.headingRad += pullError.coerceIn(-0.6f, 0.6f) * GameConstants.SMART_STEERING_PULL * dt
                if (abs(signedLateral) > halfRoad * 0.98f) {
                    // Clamp position softly onto kerb edge
                    val clampLat = if (signedLateral > 0f) halfRoad * 0.98f else -halfRoad * 0.98f
                    kart.x = nearestSample.pos.x + nearestSample.rightX * clampLat
                    kart.z = nearestSample.pos.z + nearestSample.rightZ * clampLat
                }
            }
        }

        // 7. Forward Acceleration, Off-Track Drag, Boosts & Magnet Pull
        val gasActive = canControl && (input.gasPressed || (autoAccelerate && !input.brakePressed))
        var targetTopSpeed = GameConstants.TOP_SPEED * speedScale
        if (kart.boostTimerSec > 0f) {
            targetTopSpeed *= kart.boostMultiplier
        } else if (kart.isOffTrack) {
            targetTopSpeed *= GameConstants.OFF_TRACK_SPEED_MULT
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
                    val accel = if (kart.boostTimerSec > 0f) GameConstants.ACCEL * 2.4f else GameConstants.ACCEL
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
        }

        // 8. Velocity Integration with Lateral Grip Damping
        val fwdX = sin(kart.headingRad)
        val fwdZ = cos(kart.headingRad)
        val rightX = -cos(kart.headingRad)
        val rightZ = sin(kart.headingRad)

        val currentLateralVel = kart.vx * rightX + kart.vz * rightZ
        val gripDamping = if (kart.isDrifting) {
            GameConstants.DRIFT_LATERAL_DAMPING
        } else {
            GameConstants.NORMAL_LATERAL_DAMPING
        }
        val dampedLatVel = currentLateralVel * (1f - gripDamping * dt).coerceIn(0f, 1f)

        kart.vx = fwdX * kart.forwardSpeed + rightX * dampedLatVel
        kart.vz = fwdZ * kart.forwardSpeed + rightZ * dampedLatVel

        // Magnet pull toward nearest kart ahead
        if (kart.magnetTimerSec > 0f) {
            val aheadKart = allKarts.firstOrNull { it.slotIndex != kart.slotIndex && it.racePosition == kart.racePosition - 1 }
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

        // 9. Vertical Physics, Ramps, Sink Gap & Tricks
        var supportY = nearestSample.pos.y
        for (prop in track.props) {
            val hx = prop.sx * 0.5f
            val hz = prop.sz * 0.5f
            if (abs(kart.x - prop.x) <= hx && abs(kart.z - prop.z) <= hz) {
                if (prop.isBoostPad && kart.y <= prop.y + 0.5f) {
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

        // Kitchen Counter Sink Gap Fall Risk
        if (track.isInSinkHole(kart.x, kart.z) && kart.y <= 0.4f) {
            supportY = -12f
        }

        // Desk Edge Drop-Off
        if (abs(kart.x) > track.deskHalfExtentX || abs(kart.z) > track.deskHalfExtentZ) {
            supportY = -12f
        }

        val wasAirborne = kart.y > supportY + 0.35f
        kart.y += kart.vy * dt
        if (kart.y > supportY) {
            kart.vy -= GameConstants.GRAVITY * dt
            if (kart.y <= supportY && supportY >= 0f) {
                kart.y = supportY
                kart.vy = 0f
                if (wasAirborne) {
                    kart.landingShakeTrigger = true
                    if (kart.trickArmedInAir) {
                        kart.trickArmedInAir = false
                        applyBoost(kart, GameConstants.TRICK_BOOST_SEC)
                        onBoostTriggered()
                    }
                }
            }
        } else if (supportY >= 0f) {
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

        // 10. Solid Prop & Kart-to-Kart Collisions + Wall Unstuck Nudge
        var hitWallThisFrame = false
        for (prop in track.props) {
            if (!prop.isSolidWall) continue
            if (prop.isRoundBumper) {
                val radius = prop.sx * 0.5f + 1.1f
                val dx = kart.x - prop.x
                val dz = kart.z - prop.z
                val d = hypot(dx, dz)
                if (d < radius && d > 0.001f) {
                    val nx = dx / d
                    val nz = dz / d
                    kart.x = prop.x + nx * (radius + 0.08f)
                    kart.z = prop.z + nz * (radius + 0.08f)
                    kart.forwardSpeed *= 0.72f
                    hitWallThisFrame = true
                }
            } else {
                val hx = prop.sx * 0.5f + 0.95f
                val hz = prop.sz * 0.5f + 0.95f
                if (abs(kart.x - prop.x) < hx && abs(kart.z - prop.z) < hz && kart.y < prop.y + prop.sy) {
                    val ox = hx - abs(kart.x - prop.x)
                    val oz = hz - abs(kart.z - prop.z)
                    if (ox < oz) {
                        kart.x += if (kart.x > prop.x) ox + 0.08f else -(ox + 0.08f)
                    } else {
                        kart.z += if (kart.z > prop.z) oz + 0.08f else -(oz + 0.08f)
                    }
                    kart.forwardSpeed *= 0.68f
                    hitWallThisFrame = true
                }
            }
        }

        if (hitWallThisFrame) {
            onWallBump()
        }

        // Kart-to-Kart Bumping & Orbiting Tacks Contact
        for (other in allKarts) {
            if (other.slotIndex == kart.slotIndex || other.isRespawning) continue
            val dx = kart.x - other.x
            val dz = kart.z - other.z
            val d = hypot(dx, dz)
            if (d < 1.85f && d > 0.01f && abs(kart.y - other.y) < 1.5f) {
                val push = (1.85f - d) * 0.5f
                val nx = dx / d
                val nz = dz / d
                kart.x += nx * push
                kart.z += nz * push
                other.x -= nx * push
                other.z -= nz * push
                if (other.orbitingTacksCount > 0 && kart.invulnerableTimerSec <= 0f) {
                    other.orbitingTacksCount--
                    if (other.orbitingTacksCount <= 0) other.heldItem = ItemType.NONE
                    triggerSpinOut(kart)
                }
            }
        }

        // Never let a kart get stuck against a wall (< 1 u/s for 2 s while trying to move)
        if (gasActive && abs(kart.forwardSpeed) < GameConstants.WALL_STUCK_SPEED) {
            kart.wallStuckTimerSec += dt
            if (kart.wallStuckTimerSec >= GameConstants.WALL_STUCK_SEC) {
                kart.wallStuckTimerSec = 0f
                kart.x = nearestSample.pos.x
                kart.z = nearestSample.pos.z
                kart.headingRad = nearestSample.headingRad
                kart.forwardSpeed = 8f
            }
        } else {
            kart.wallStuckTimerSec = 0f
        }

        // 11. Visual Tilt, Roll & Wrong-Way Detection
        val targetYawOffset = when {
            kart.spinOutTimerSec > 0f -> kart.spinOutAngleRad
            kart.isDrifting -> -kart.driftDirection * 0.34f
            else -> -input.steer * 0.10f
        }
        kart.visualYawOffsetRad += (targetYawOffset - kart.visualYawOffsetRad) * (12f * dt).coerceAtMost(1f)

        val targetRoll = if (kart.isDrifting) kart.driftDirection * 0.14f else input.steer * 0.07f
        kart.visualRollRad += (targetRoll - kart.visualRollRad) * (10f * dt).coerceAtMost(1f)

        val targetPitch = if (kart.vy > 1f) -0.18f else if (kart.vy < -2f) 0.14f else 0f
        kart.visualPitchRad += (targetPitch - kart.visualPitchRad) * (10f * dt).coerceAtMost(1f)

        // Checkpoints & Lap Validation
        val nextCpIndex = (kart.highestCheckpointPassed + 1) % track.checkpoints.size
        val nextCp = track.checkpoints[nextCpIndex]
        val distToNextCp = hypot(kart.x - nextCp.center.x, kart.z - nextCp.center.z)
        if (distToNextCp < track.roadWidth * 1.35f) {
            if (nextCpIndex == 0 && kart.highestCheckpointPassed >= track.checkpoints.size - 2) {
                val completedLap = kart.lap
                kart.lap += 1
                kart.highestCheckpointPassed = 0
                onLapCompleted(completedLap)
            } else if (nextCpIndex > kart.highestCheckpointPassed) {
                kart.highestCheckpointPassed = nextCpIndex
            }
        }

        // Wrong-way check (heading opposes spline tangent for > 1 s)
        val dotTangent = fwdX * nearestSample.tangentX + fwdZ * nearestSample.tangentZ
        if (dotTangent < -0.35f && kart.forwardSpeed > 3f) {
            kart.wrongWayTimerSec += dt
            kart.isWrongWay = kart.wrongWayTimerSec > 1.0f
        } else {
            kart.wrongWayTimerSec = 0f
            kart.isWrongWay = false
        }
    }

    fun wrapAngle(angle: Float): Float {
        var a = angle
        while (a > PI.toFloat()) a -= PI.toFloat() * 2f
        while (a < -PI.toFloat()) a += PI.toFloat() * 2f
        return a
    }
}
