package com.example.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

object CpuDriver {

    private val LANE_FACTORS = floatArrayOf(-0.60f, -0.30f, 0.0f, 0.30f, 0.60f)
    private const val LANE_SWITCH_MARGIN = 8f

    fun computeCpuInput(
        kart: KartRuntime,
        track: Track,
        allKarts: List<KartRuntime>,
        projectiles: List<WorldProjectile> = emptyList(),
        itemBoxes: List<ActiveItemBox> = emptyList(),
        coins: List<ActiveCoin> = emptyList(),
        difficulty: CpuDifficulty,
        ccClass: CcClass = CcClass.CC_150,
        leadingHumanProgress: Float,
        raceElapsedSec: Float = 0f
    ): Pair<TouchInputSnapshot, Float> {
        val effectiveDiff = if (difficulty == CpuDifficulty.MIXED) {
            when (kart.slotIndex % 3) {
                0 -> CpuDifficulty.CHAMPION
                1 -> CpuDifficulty.PRO
                else -> CpuDifficulty.ROOKIE
            }
        } else {
            difficulty
        }

        val halfRoad = (track.roadWidth * 0.5f) - 1.1f
        val lookAheadDist = 14.0f + (kart.forwardSpeed * 0.18f).coerceIn(0f, 7f)
        val midSample = track.sampleAtDistance(kart.trackS + lookAheadDist * 0.55f)
        val targetSample = track.sampleAtDistance(kart.trackS + lookAheadDist)
        val farSample = track.sampleAtDistance(kart.trackS + lookAheadDist + 14.0f)

        val cornerSigned = KartPhysics.wrapAngle(farSample.headingRad - midSample.headingRad)
        val cornerAbs = abs(cornerSigned)

        // Default preferred lane: apex inside of turn for Pro/Champion, slot-staggered on straights
        val slotBias = ((kart.slotIndex % 5) - 2) * 0.14f
        val apexBias = if (effectiveDiff != CpuDifficulty.ROOKIE && cornerAbs > 0.22f) {
            // Note: negative dHeading means turning right (+rightX), positive means turning left (-rightX)
            if (cornerSigned < 0f) 0.38f else -0.38f
        } else {
            slotBias
        }

        // Evaluate 5 candidate lanes across the road corridor for obstacles, traps, karts, coins & boosts
        var bestLaneFactor = apexBias
        var bestScore = -1e6f
        var currentLaneScore = -1e6f

        for (i in LANE_FACTORS.indices) {
            val lf = LANE_FACTORS[i]
            val lat = lf * halfRoad
            val candX = targetSample.pos.x + targetSample.rightX * lat
            val candZ = targetSample.pos.z + targetSample.rightZ * lat
            val midX = midSample.pos.x + midSample.rightX * lat
            val midZ = midSample.pos.z + midSample.rightZ * lat

            var score = -abs(lf - apexBias) * 4.0f

            // Avoid solid props & boost toward boost pads
            for (prop in track.props) {
                if (abs(prop.y - targetSample.pos.y) > 3.5f) continue
                val dTarget = hypot(candX - prop.x, candZ - prop.z)
                val dMid = hypot(midX - prop.x, midZ - prop.z)
                val minD = minOf(dTarget, dMid)
                if (prop.isSolidWall) {
                    val safeR = maxOf(prop.sx, prop.sz) * 0.5f + 2.4f
                    if (minD < safeR) {
                        score -= (safeR - minD) * 40f
                    }
                } else if (prop.isBoostPad && minD < 4.5f) {
                    score += 9.0f
                }
            }

            // Avoid dynamic hazards (rolling peas, scissors gate, spinning top)
            for (h in track.dynamicHazards) {
                val phase = ((raceElapsedSec / h.periodSec) + h.phaseOffset) * (2f * PI.toFloat())
                val offset = sin(phase) * h.amplitude
                val hx = h.centerX + h.axisX * offset
                val hz = h.centerZ + h.axisZ * offset
                val dH = minOf(hypot(candX - hx, candZ - hz), hypot(midX - hx, midZ - hz))
                if (dH < h.radius + 3.2f) {
                    score -= 50f
                }
            }

            // Avoid dropped Eraser traps & projectiles
            for (p in projectiles) {
                val dP = minOf(hypot(candX - p.x, candZ - p.z), hypot(midX - p.x, midZ - p.z))
                if (dP < 3.5f) {
                    score -= 35f
                }
            }

            // Avoid rear-ending slower karts directly ahead
            for (other in allKarts) {
                if (other.slotIndex == kart.slotIndex || other.isRespawning) continue
                val dOther = minOf(hypot(candX - other.x, candZ - other.z), hypot(midX - other.x, midZ - other.z))
                if (dOther < 2.8f) {
                    score -= 14f
                }
            }

            // Seek item boxes when empty-handed
            if (kart.heldItem == ItemType.NONE && kart.rouletteTimerSec <= 0f) {
                for (b in itemBoxes) {
                    if (!b.active) continue
                    if (hypot(candX - b.x, candZ - b.z) < 3.2f) {
                        score += 6.5f
                        break
                    }
                }
            }

            // Seek coins when below 10
            if (kart.coinsHeld < GameConstants.MAX_COINS_HELD && effectiveDiff != CpuDifficulty.ROOKIE) {
                for (c in coins) {
                    if (!c.active) continue
                    if (hypot(candX - c.x, candZ - c.z) < 3.0f) {
                        score += 4.5f
                        break
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestLaneFactor = lf
            }
            if (abs(lf - kart.cpuLaneTarget) < 0.01f) currentLaneScore = score
        }

        // v7.2: lane hysteresis — only change lane when the new one is clearly better, then glide
        // across. Re-picking the best of 5 lanes every tick made bots zig-zag and weave around you.
        if (kart.cpuLaneInit && currentLaneScore > bestScore - LANE_SWITCH_MARGIN) {
            bestLaneFactor = kart.cpuLaneTarget
        }
        kart.cpuLaneTarget = bestLaneFactor
        if (!kart.cpuLaneInit) { kart.cpuLaneSmoothed = bestLaneFactor; kart.cpuLaneInit = true }
        kart.cpuLaneSmoothed += (bestLaneFactor - kart.cpuLaneSmoothed) * 0.06f

        val chosenLat = kart.cpuLaneSmoothed * halfRoad
        val targetX = targetSample.pos.x + targetSample.rightX * chosenLat
        val targetZ = targetSample.pos.z + targetSample.rightZ * chosenLat

        val dx = targetX - kart.x
        val dz = targetZ - kart.z
        val desiredHeading = atan2(dx, dz)
        val angleDiff = KartPhysics.wrapAngle(desiredHeading - kart.headingRad)

        // Note: positive steer turns Right (decreases headingRad), so steer = -angleDiff
        val steerGain = when (effectiveDiff) {
            CpuDifficulty.CHAMPION -> 2.15f
            CpuDifficulty.PRO -> 1.95f
            else -> 1.75f
        }
        val steer = (-angleDiff * steerGain).coerceIn(-1f, 1f)

        // Corner drift initiation & release logic
        // v7.2: bots drift like players — one hop at a real corner entry, hold it, release once.
        // They used to release at a spark tier and immediately hop again in the same corner, so the
        // field was constantly hopping. Also no hop while side-by-side with another kart.
        val crowded = allKarts.any { o ->
            o.slotIndex != kart.slotIndex && !o.isRespawning && hypot(o.x - kart.x, o.z - kart.z) < 4.2f
        }
        val cooledDown = raceElapsedSec - kart.cpuLastDriftEndSec > 1.6f || raceElapsedSec < kart.cpuLastDriftEndSec
        val canStart = effectiveDiff != CpuDifficulty.ROOKIE &&
            cornerAbs > 0.42f && abs(steer) > 0.38f && kart.forwardSpeed > 16f && cooledDown && !crowded
        val canHold = cornerAbs > 0.20f && kart.forwardSpeed > 10f
        val wantsDrift = if (kart.isDrifting) canHold else canStart
        val shouldReleaseForBoost = kart.isDrifting && (
            !canHold ||
                (effectiveDiff == CpuDifficulty.CHAMPION && kart.driftChargeUnits >= GameConstants.SPARK_PURPLE_UNITS) ||
                (effectiveDiff == CpuDifficulty.PRO && kart.driftChargeUnits >= GameConstants.SPARK_ORANGE_UNITS)
            )
        if (shouldReleaseForBoost) kart.cpuLastDriftEndSec = raceElapsedSec
        val holdDrift = wantsDrift && !shouldReleaseForBoost
        val driftJustPressed = holdDrift && !kart.isDrifting

        // Item firing logic
        var fireItem = false
        var fireBackward = false
        if (kart.heldItem != ItemType.NONE && kart.rouletteTimerSec <= 0f) {
            // v7: aim items instead of firing at random — forward cone / rear threat / straights
            val fx = sin(kart.headingRad)
            val fz = cos(kart.headingRad)
            var aheadInCone = false
            var closeBehind = false
            for (other in allKarts) {
                if (other.slotIndex == kart.slotIndex || other.isRespawning) continue
                val ox = other.x - kart.x
                val oz = other.z - kart.z
                val d = hypot(ox, oz)
                if (d < 0.5f || abs(other.y - kart.y) > 3f) continue
                val dot = (ox * fx + oz * fz) / d
                if (dot > 0.86f && d < 34f) aheadInCone = true
                if (dot < -0.80f && d < 14f) closeBehind = true
            }
            val onStraight = cornerAbs < 0.15f
            val aim = when (effectiveDiff) {
                CpuDifficulty.ROOKIE -> 0.02f
                CpuDifficulty.PRO -> 0.06f
                else -> 0.10f
            }
            when (kart.heldItem) {
                ItemType.BATTERY -> fireItem = onStraight && Random.nextFloat() < aim
                ItemType.MAGNET, ItemType.PAPER_PLANE -> fireItem = kart.racePosition > 1 && Random.nextFloat() < aim
                ItemType.MARBLE -> {
                    if (aheadInCone && Random.nextFloat() < aim * 1.5f) fireItem = true
                    else if (closeBehind && Random.nextFloat() < aim) { fireItem = true; fireBackward = true }
                }
                ItemType.TRIPLE_TACKS -> fireItem = aheadInCone && Random.nextFloat() < aim
                ItemType.ERASER -> {
                    // hold it as a shield; drop it when someone is right behind
                    if (closeBehind && Random.nextFloat() < aim) { fireItem = true; fireBackward = true }
                    else if (Random.nextFloat() < 0.004f) { fireItem = true; fireBackward = true }
                }
                ItemType.NONE -> Unit
            }
        }

        // Rubber-banding relative to leading human (only when a human is actively racing)
        val gapToHuman = if (leadingHumanProgress > 0f) {
            leadingHumanProgress - kart.totalRaceProgress
        } else {
            0f
        }
        // v7.2: continuous rubber band (the old 4-step table toggled target speed at each threshold,
        // so bots surged and braked — visible as pitching/bobbing around the player)
        val rubberBand = if (gapToHuman >= 0f) {
            1f + 0.07f * ((gapToHuman - 20f) / 140f).coerceIn(0f, 1f)
        } else {
            1f - 0.09f * ((-gapToHuman - 30f) / 130f).coerceIn(0f, 1f)
        }
        val speedScale = (effectiveDiff.baseSpeedMult * ccClass.speedScale * rubberBand).coerceIn(0.70f, 1.10f)

        val snapshot = TouchInputSnapshot(
            steer = steer,
            gasPressed = true,
            brakePressed = false,
            driftPressed = holdDrift,
            driftJustPressed = driftJustPressed,
            itemJustPressed = fireItem,
            itemFireBackward = fireBackward,
            itemHeldDown = kart.heldItem == ItemType.ERASER && !fireItem && kart.rouletteTimerSec <= 0f // trail it as a shield
        )
        return Pair(snapshot, speedScale)
    }
}
