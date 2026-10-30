package com.example.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

object CpuDriver {

    fun computeCpuInput(
        kart: KartRuntime,
        track: Track,
        allKarts: List<KartRuntime>,
        difficulty: CpuDifficulty,
        leadingHumanProgress: Float
    ): Pair<TouchInputSnapshot, Float> {
        // Look-ahead along the Catmull-Rom spline with a per-slot racing line lateral offset
        val laneOffset = ((kart.slotIndex % 3) - 1) * 1.6f
        val lookAheadDist = kart.splineDistance + 14.5f
        val targetSample = track.sampleAtDistance(lookAheadDist)
        val farSample = track.sampleAtDistance(kart.splineDistance + 26f)

        val targetX = targetSample.pos.x + targetSample.rightX * laneOffset
        val targetZ = targetSample.pos.z + targetSample.rightZ * laneOffset

        val dx = targetX - kart.x
        val dz = targetZ - kart.z
        val desiredHeading = atan2(dx, dz)
        val angleDiff = KartPhysics.wrapAngle(desiredHeading - kart.headingRad)

        // Note: in KartPhysics, positive steer turns Right (decreases headingRad), so steer = -angleDiff
        val steer = (-angleDiff * 1.85f).coerceIn(-1f, 1f)

        // Check corner curvature ahead to decide whether to Hop + Drift
        val cornerCurve = abs(KartPhysics.wrapAngle(farSample.headingRad - targetSample.headingRad))
        val shouldDrift = cornerCurve > 0.38f && abs(steer) > 0.35f && kart.forwardSpeed > 16f
        val driftJustPressed = shouldDrift && !kart.isDrifting

        // Check if CPU should fire held item
        var fireItem = false
        if (kart.heldItem != ItemType.NONE && kart.rouletteTimerSec <= 0f) {
            when (kart.heldItem) {
                ItemType.BATTERY, ItemType.MAGNET, ItemType.PAPER_PLANE -> {
                    fireItem = Random.nextFloat() < 0.04f
                }
                ItemType.MARBLE, ItemType.TRIPLE_TACKS -> {
                    val targetAhead = allKarts.any { other ->
                        other.slotIndex != kart.slotIndex &&
                            hypot(other.x - kart.x, other.z - kart.z) < 28f
                    }
                    if (targetAhead && Random.nextFloat() < 0.05f) {
                        fireItem = true
                    }
                }
                ItemType.ERASER -> {
                    if (Random.nextFloat() < 0.03f) fireItem = true
                }
                ItemType.NONE -> Unit
            }
        }

        // Mild rubber-banding (x0.90 to x1.08 based on gap to leading human)
        val gapToHuman = leadingHumanProgress - kart.totalRaceProgress
        val rubberBand = when {
            gapToHuman > 140f -> 1.07f
            gapToHuman > 50f -> 1.03f
            gapToHuman < -140f -> 0.90f
            gapToHuman < -60f -> 0.94f
            else -> 1.0f
        }
        val speedScale = (difficulty.baseSpeedMult * rubberBand).coerceIn(0.85f, 1.08f)

        val snapshot = TouchInputSnapshot(
            steer = steer,
            gasPressed = true,
            brakePressed = false,
            driftPressed = shouldDrift,
            driftJustPressed = driftJustPressed,
            itemJustPressed = fireItem,
            itemFireBackward = false,
            itemHeldDown = false
        )
        return Pair(snapshot, speedScale)
    }
}
