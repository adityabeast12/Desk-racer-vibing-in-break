package com.example.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class TrackValidationReport(
    val trackId: Int,
    val trackName: String,
    val totalLength: Float,
    val gateCount: Int,
    val startGridCount: Int,
    val itemBoxCount: Int,
    val coinCount: Int,
    val issues: List<String>
) {
    val isValid: Boolean
        get() = issues.isEmpty()
}

object TrackValidator {

    fun validateAll(): List<TrackValidationReport> {
        return Tracks.all.map { validate(it) }
    }

    fun validate(track: Track): TrackValidationReport {
        val issues = ArrayList<String>()
        val halfRoad = track.roadWidth * 0.5f

        // 1. Validate 16 checkpoint gates evenly spaced
        if (track.checkpoints.size != GameConstants.NUM_CHECKPOINT_GATES) {
            issues.add("Expected ${GameConstants.NUM_CHECKPOINT_GATES} gates, got ${track.checkpoints.size}")
        } else {
            val expectedStep = track.totalLength / GameConstants.NUM_CHECKPOINT_GATES
            for (i in track.checkpoints.indices) {
                val expectedDist = i * expectedStep
                val err = abs(track.checkpoints[i].distanceAlongSpline - expectedDist)
                if (err > expectedStep * 0.25f) {
                    issues.add("Gate $i distance ${track.checkpoints[i].distanceAlongSpline} deviates from $expectedDist")
                }
            }
        }

        // 2. Validate 8 start grid slots sit on the road behind the finish line
        if (track.startGrid.size != GameConstants.MAX_RACERS) {
            issues.add("Expected ${GameConstants.MAX_RACERS} start grid slots, got ${track.startGrid.size}")
        }
        for (slot in track.startGrid) {
            val res = track.findNearestSampleWindowed(slot.x, slot.y, slot.z, -1)
            if (abs(res.lateralOffset) > halfRoad) {
                issues.add("Start grid slot ${slot.slotIndex} is off-road (lateral=${res.lateralOffset})")
            }
        }

        // 3. Validate solid prop clearance (Part B2: >= roadWidth/2 + 1.5u unless isRoadObstacle with >= 5u gap)
        val requiredClearance = halfRoad + 1.5f
        for ((pIdx, prop) in track.props.withIndex()) {
            if (!prop.isSolidWall || prop.isRamp || prop.isBoostPad) continue
            for (s in track.samples) {
                if (abs(s.pos.y - prop.y) > 4.0f) continue
                val dx = prop.x - s.pos.x
                val dz = prop.z - s.pos.z
                val c = cos(-prop.yawRad)
                val sn = sin(-prop.yawRad)
                val localX = dx * c - dz * sn
                val localZ = dx * sn + dz * c
                val hx = if (prop.isRoundBumper) maxOf(prop.sx, prop.sz) * 0.5f else prop.sx * 0.5f
                val hz = if (prop.isRoundBumper) maxOf(prop.sx, prop.sz) * 0.5f else prop.sz * 0.5f
                val clampedX = localX.coerceIn(-hx, hx)
                val clampedZ = localZ.coerceIn(-hz, hz)
                val dist = hypot(localX - clampedX, localZ - clampedZ)
                if (!prop.isRoadObstacle && dist < requiredClearance - 0.1f) {
                    issues.add(
                        "Prop #$pIdx (${prop.kind}) intrudes road corridor at sample ${s.index} (dist=${"%.2f".format(dist)} < ${"%.2f".format(requiredClearance)})"
                    )
                    break
                }
            }
        }

        // 4. Validate item boxes & coins lie on the road corridor
        for (box in track.itemBoxes) {
            val res = track.findNearestSampleWindowed(box.x, box.y - 1.0f, box.z, -1)
            if (abs(res.lateralOffset) > halfRoad + 0.5f) {
                issues.add("ItemBox ${box.id} is off-road (lateral=${res.lateralOffset})")
            }
        }
        for (coin in track.coins) {
            val res = track.findNearestSampleWindowed(coin.x, coin.y - 0.55f, coin.z, -1)
            if (abs(res.lateralOffset) > halfRoad + 0.5f) {
                issues.add("Coin ${coin.id} is off-road (lateral=${res.lateralOffset})")
            }
        }

        return TrackValidationReport(
            trackId = track.id,
            trackName = track.name,
            totalLength = track.totalLength,
            gateCount = track.checkpoints.size,
            startGridCount = track.startGrid.size,
            itemBoxCount = track.itemBoxes.size,
            coinCount = track.coins.size,
            issues = issues
        )
    }
}
