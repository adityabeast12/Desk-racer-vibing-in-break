package com.example.game

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
}

enum class PropKind {
    BOOK,
    MUG_ROUNDABOUT,
    PENCIL_RAIL,
    RULER_RAMP,
    STICKY_BOOST_PAD,
    KEYBOARD_BUMP,
    LAPTOP_BRIDGE,
    ORANGE_BUMPER,
    KETTLE_PILLAR,
    CUTTING_BOARD
}

data class TrackProp(
    val kind: PropKind,
    val x: Float,
    val y: Float,
    val z: Float,
    val sx: Float,
    val sy: Float,
    val sz: Float,
    val yawRad: Float = 0f,
    val pitchRad: Float = 0f,
    val colorHex: Long = 0xFF2563EBL,
    val secondaryHex: Long = 0xFFF8FAFCL,
    val isSolidWall: Boolean = true,
    val isRamp: Boolean = false,
    val isBoostPad: Boolean = false,
    val isRoundBumper: Boolean = false
)

data class SplineSample(
    val distance: Float,
    val pos: Vec3,
    val tangentX: Float,
    val tangentZ: Float,
    val rightX: Float,
    val rightZ: Float,
    val headingRad: Float
)

data class CheckpointGate(
    val index: Int,
    val distanceAlongSpline: Float,
    val center: Vec3,
    val headingRad: Float
)

data class ItemBoxPlacement(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float
)

data class StartGridSlot(
    val slotIndex: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val headingRad: Float
)

class Track(
    val id: Int,
    val name: String,
    val subtitle: String,
    val difficultyLabel: String,
    val roadWidth: Float,
    val deskSurfaceHex: Long,
    val roadSurfaceHex: Long,
    val kerbPrimaryHex: Long,
    val kerbSecondaryHex: Long,
    val skyTopHex: Long,
    val skyBottomHex: Long,
    val fogHex: Long,
    val deskHalfExtentX: Float = 95f,
    val deskHalfExtentZ: Float = 85f,
    val hasSinkGapZone: Boolean = false,
    val sinkGapMinX: Float = 0f,
    val sinkGapMaxX: Float = 0f,
    val sinkGapMinZ: Float = 0f,
    val sinkGapMaxZ: Float = 0f,
    controlPoints: List<Vec3>,
    val props: List<TrackProp>
) {
    val samples: List<SplineSample>
    val totalLength: Float
    val checkpoints: List<CheckpointGate>
    val startGrid: List<StartGridSlot>
    val itemBoxes: List<ItemBoxPlacement>

    init {
        val rawPoints = buildCatmullRomLoop(controlPoints, subdivisionsPerSegment = 18)
        val builtSamples = ArrayList<SplineSample>(rawPoints.size)
        var accumDist = 0f

        for (i in rawPoints.indices) {
            val prev = rawPoints[(i - 1 + rawPoints.size) % rawPoints.size]
            val curr = rawPoints[i]
            val next = rawPoints[(i + 1) % rawPoints.size]
            if (i > 0) {
                val dx = curr.x - prev.x
                val dy = curr.y - prev.y
                val dz = curr.z - prev.z
                accumDist += hypot(hypot(dx, dy), dz)
            }
            val txRaw = next.x - prev.x
            val tzRaw = next.z - prev.z
            val tLen = hypot(txRaw, tzRaw).coerceAtLeast(0.001f)
            val tx = txRaw / tLen
            val tz = tzRaw / tLen
            // Right vector in XZ plane: (tz, -tx) or (-tz, tx)
            val rx = -tz
            val rz = tx
            val heading = atan2(tx, tz)
            builtSamples.add(
                SplineSample(
                    distance = accumDist,
                    pos = curr,
                    tangentX = tx,
                    tangentZ = tz,
                    rightX = rx,
                    rightZ = rz,
                    headingRad = heading
                )
            )
        }
        val first = rawPoints.first()
        val last = rawPoints.last()
        totalLength = accumDist + hypot(first.x - last.x, first.z - last.z)
        samples = builtSamples

        // 10 Checkpoints spaced evenly every 10% around the closed spline
        val gates = ArrayList<CheckpointGate>(10)
        for (cpIdx in 0 until 10) {
            val targetDist = (cpIdx / 10f) * totalLength
            val sample = sampleAtDistance(targetDist)
            gates.add(
                CheckpointGate(
                    index = cpIdx,
                    distanceAlongSpline = targetDist,
                    center = sample.pos,
                    headingRad = sample.headingRad
                )
            )
        }
        checkpoints = gates

        // 2x3 Start Grid just behind the start/finish line (distance = totalLength - offset)
        val grid = ArrayList<StartGridSlot>(6)
        for (slot in 0 until 6) {
            val row = slot / 2
            val col = slot % 2
            val backDist = totalLength - 4.5f - row * 3.8f
            val lateralOffset = if (col == 0) -2.1f else 2.1f
            val s = sampleAtDistance(backDist)
            grid.add(
                StartGridSlot(
                    slotIndex = slot,
                    x = s.pos.x + s.rightX * lateralOffset,
                    y = s.pos.y,
                    z = s.pos.z + s.rightZ * lateralOffset,
                    headingRad = s.headingRad
                )
            )
        }
        startGrid = grid

        // 3 rows of 4 floating rainbow Item Boxes at 25%, 55%, and 82% of lap
        val boxes = ArrayList<ItemBoxPlacement>(12)
        val fractions = floatArrayOf(0.24f, 0.54f, 0.82f)
        var boxId = 0
        for (f in fractions) {
            val s = sampleAtDistance(f * totalLength)
            val offsets = floatArrayOf(-3.3f, -1.1f, 1.1f, 3.3f)
            for (lat in offsets) {
                boxes.add(
                    ItemBoxPlacement(
                        id = boxId++,
                        x = s.pos.x + s.rightX * lat,
                        y = s.pos.y + 1.15f,
                        z = s.pos.z + s.rightZ * lat
                    )
                )
            }
        }
        itemBoxes = boxes
    }

    fun sampleAtDistance(dist: Float): SplineSample {
        var wrapped = dist % totalLength
        if (wrapped < 0f) wrapped += totalLength
        var low = 0
        var high = samples.lastIndex
        while (low < high) {
            val mid = (low + high) ushr 1
            if (samples[mid].distance < wrapped) {
                low = mid + 1
            } else {
                high = mid
            }
        }
        return samples[low.coerceIn(0, samples.lastIndex)]
    }

    /**
     * Finds the nearest spline sample to (worldX, worldZ) and returns both the sample and
     * the lateral signed distance from the centreline.
     */
    fun findNearestSample(worldX: Float, worldZ: Float): Pair<SplineSample, Float> {
        var bestIdx = 0
        var bestDistSq = Float.MAX_VALUE
        for (i in samples.indices) {
            val p = samples[i].pos
            val dx = worldX - p.x
            val dz = worldZ - p.z
            val d2 = dx * dx + dz * dz
            if (d2 < bestDistSq) {
                bestDistSq = d2
                bestIdx = i
            }
        }
        val best = samples[bestIdx]
        val dx = worldX - best.pos.x
        val dz = worldZ - best.pos.z
        val lateral = dx * best.rightX + dz * best.rightZ
        return Pair(best, lateral)
    }

    fun isInSinkHole(x: Float, z: Float): Boolean {
        if (!hasSinkGapZone) return false
        return x in sinkGapMinX..sinkGapMaxX && z in sinkGapMinZ..sinkGapMaxZ
    }

    companion object {
        private fun buildCatmullRomLoop(points: List<Vec3>, subdivisionsPerSegment: Int): List<Vec3> {
            val result = ArrayList<Vec3>(points.size * subdivisionsPerSegment)
            val n = points.size
            for (i in 0 until n) {
                val p0 = points[(i - 1 + n) % n]
                val p1 = points[i]
                val p2 = points[(i + 1) % n]
                val p3 = points[(i + 2) % n]
                for (step in 0 until subdivisionsPerSegment) {
                    val t = step.toFloat() / subdivisionsPerSegment
                    val t2 = t * t
                    val t3 = t2 * t
                    val x = 0.5f * (
                        (2f * p1.x) +
                            (-p0.x + p2.x) * t +
                            (2f * p0.x - 5f * p1.x + 4f * p2.x - p3.x) * t2 +
                            (-p0.x + 3f * p1.x - 3f * p2.x + p3.x) * t3
                        )
                    val y = 0.5f * (
                        (2f * p1.y) +
                            (-p0.y + p2.y) * t +
                            (2f * p0.y - 5f * p1.y + 4f * p2.y - p3.y) * t2 +
                            (-p0.y + 3f * p1.y - 3f * p2.y + p3.y) * t3
                        )
                    val z = 0.5f * (
                        (2f * p1.z) +
                            (-p0.z + p2.z) * t +
                            (2f * p0.z - 5f * p1.z + 4f * p2.z - p3.z) * t2 +
                            (-p0.z + 3f * p1.z - 3f * p2.z + p3.z) * t3
                        )
                    result.add(Vec3(x, y.coerceAtLeast(0f), z))
                }
            }
            return result
        }
    }
}
