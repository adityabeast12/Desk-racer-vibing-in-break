package com.example.game

import kotlin.math.PI
import kotlin.math.abs
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
    CUTTING_BOARD,
    CRAYON_RAIL,
    PAINT_PALETTE,
    GLUE_BOLLARD,
    SHAMPOO_BOTTLE,
    RUBBER_DUCK,
    TOY_BLOCK,
    JACK_IN_BOX,
    WRENCH_ARCH,
    PENNANT_FLAG,
    STEAM_CLOUD
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
    val isRoundBumper: Boolean = false,
    val restitution: Float = 0.25f,
    val isRoadObstacle: Boolean = false
)

data class PaintPuddleZone(
    val x: Float,
    val z: Float,
    val radius: Float,
    val tintColorHex: Long
)

data class SlipperyZone(
    val x: Float,
    val z: Float,
    val radius: Float
)

data class DynamicHazardSpec(
    val id: Int,
    val kind: DynamicHazardKind,
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val axisX: Float,
    val axisZ: Float,
    val amplitude: Float,
    val periodSec: Float,
    val radius: Float,
    val phaseOffset: Float = 0f,
    val colorHex: Long = 0xFF22C55EL
)

enum class DynamicHazardKind {
    ROLLING_PEA,
    SCISSORS_GATE,
    SPINNING_TOP
}

data class SplineSample(
    val index: Int,
    val distance: Float,
    val normalizedT: Float,
    val pos: Vec3,
    val tangentX: Float,
    val tangentZ: Float,
    val rightX: Float,
    val rightZ: Float,
    val headingRad: Float,
    val curvature: Float = 0f,
    val bankAngleRad: Float = 0f
)

data class CheckpointGate(
    val index: Int,
    val sampleIndex: Int,
    val distanceAlongSpline: Float,
    val center: Vec3,
    val normalX: Float,
    val normalZ: Float,
    val rightX: Float,
    val rightZ: Float,
    val halfWidth: Float,
    val headingRad: Float
)

data class ItemBoxPlacement(
    val id: Int,
    val x: Float,
    val y: Float,
    val z: Float
)

data class TrackCoinPlacement(
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

data class NearestSampleResult(
    val sample: SplineSample,
    val lateralOffset: Float
)

class Track(
    val id: Int,
    val cupId: Int,
    val name: String,
    val subtitle: String,
    val difficultyStars: Int,
    val difficultyLabel: String,
    val roadWidth: Float,
    val deskSurfaceHex: Long,
    val roadSurfaceHex: Long,
    val kerbPrimaryHex: Long,
    val kerbSecondaryHex: Long,
    val skyTopHex: Long,
    val skyBottomHex: Long,
    val fogHex: Long,
    val musicAssetPath: String = "audio/music/race_study_desk_loop.ogg",
    val goldTimeMs: Long = 95_000L,
    val silverTimeMs: Long = 108_000L,
    val bronzeTimeMs: Long = 125_000L,
    val deskHalfExtentX: Float = 155f,
    val deskHalfExtentZ: Float = 145f,
    val hasSinkGapZone: Boolean = false,
    val sinkGapMinX: Float = 0f,
    val sinkGapMaxX: Float = 0f,
    val sinkGapMinZ: Float = 0f,
    val sinkGapMaxZ: Float = 0f,
    val hasWaterCenterPool: Boolean = false,
    val waterPoolRadiusX: Float = 90f,
    val waterPoolRadiusZ: Float = 48f,
    val hasRgbKerbs: Boolean = false,
    val bankedSegmentRange: ClosedFloatingPointRange<Float>? = null,
    val controlPoints: List<Vec3>,
    rawProps: List<TrackProp> = emptyList(),
    val paintPuddles: List<PaintPuddleZone> = emptyList(),
    val slipperyZones: List<SlipperyZone> = emptyList(),
    val dynamicHazards: List<DynamicHazardSpec> = emptyList()
) {
    val samples: List<SplineSample>
    val totalLength: Float
    val checkpoints: List<CheckpointGate>
    val startGrid: List<StartGridSlot>
    val itemBoxes: List<ItemBoxPlacement>
    val coins: List<TrackCoinPlacement>
    val props: List<TrackProp>

    init {
        val rawPoints = buildCatmullRomLoop(controlPoints, subdivisionsPerSegment = 18)
        val tempSamples = ArrayList<SplineSample>(rawPoints.size)
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
            val rx = -tz
            val rz = tx
            val heading = atan2(tx, tz)

            // Signed curvature estimate from previous and next segments
            val tx1 = curr.x - prev.x
            val tz1 = curr.z - prev.z
            val tx2 = next.x - curr.x
            val tz2 = next.z - curr.z
            val h1 = atan2(tx1, tz1)
            val h2 = atan2(tx2, tz2)
            var dHeading = h2 - h1
            while (dHeading > PI.toFloat()) dHeading -= (2.0 * PI).toFloat()
            while (dHeading < -PI.toFloat()) dHeading += (2.0 * PI).toFloat()
            val segLen = hypot(tx2, tz2).coerceAtLeast(0.2f)
            val curv = (dHeading / segLen).coerceIn(-0.4f, 0.4f)

            tempSamples.add(
                SplineSample(
                    index = i,
                    distance = accumDist,
                    normalizedT = 0f,
                    pos = curr,
                    tangentX = tx,
                    tangentZ = tz,
                    rightX = rx,
                    rightZ = rz,
                    headingRad = heading,
                    curvature = curv,
                    bankAngleRad = 0f
                )
            )
        }

        val lastPt = rawPoints.last()
        val firstPt = rawPoints.first()
        val closeLeg = hypot(
            hypot(firstPt.x - lastPt.x, firstPt.y - lastPt.y),
            firstPt.z - lastPt.z
        )
        totalLength = (accumDist + closeLeg).coerceAtLeast(1f)

        val finalizedSamples = ArrayList<SplineSample>(tempSamples.size)
        for (s in tempSamples) {
            val normT = (s.distance / totalLength).coerceIn(0f, 1f)
            val bank = if (bankedSegmentRange != null && normT in bankedSegmentRange) {
                (s.curvature * 14f).coerceIn(-0.21f, 0.21f) // ~12 degrees bank into turn
            } else {
                0f
            }
            finalizedSamples.add(s.copy(normalizedT = normT, bankAngleRad = bank))
        }
        samples = finalizedSamples

        // 16 evenly spaced vertical plane CheckpointGates across the road (Part B3)
        val gateCount = GameConstants.NUM_CHECKPOINT_GATES
        val builtGates = ArrayList<CheckpointGate>(gateCount)
        val gateHalfWidth = roadWidth * 0.5f + 8.0f
        for (g in 0 until gateCount) {
            val dist = (g.toFloat() / gateCount.toFloat()) * totalLength
            val s = sampleAtDistance(dist)
            builtGates.add(
                CheckpointGate(
                    index = g,
                    sampleIndex = s.index,
                    distanceAlongSpline = dist,
                    center = s.pos,
                    normalX = s.tangentX,
                    normalZ = s.tangentZ,
                    rightX = s.rightX,
                    rightZ = s.rightZ,
                    halfWidth = gateHalfWidth,
                    headingRad = s.headingRad
                )
            )
        }
        checkpoints = builtGates

        // 2x4 Staggered 8-Kart Start Grid behind finish line (Part D1: 4.2u lateral, 6u row, 3u stagger)
        val builtGrid = ArrayList<StartGridSlot>(GameConstants.MAX_RACERS)
        for (slot in 0 until GameConstants.MAX_RACERS) {
            val row = slot / 2      // 0..3
            val col = slot % 2      // 0 (left), 1 (right)
            val backDist = 6.0f + row * 6.0f + col * 3.0f
            val s = sampleAtDistance(totalLength - backDist)
            val lateral = if (col == 0) -2.1f else 2.1f // 4.2 u lateral spacing between columns
            builtGrid.add(
                StartGridSlot(
                    slotIndex = slot,
                    x = s.pos.x + s.rightX * lateral,
                    y = s.pos.y + 0.05f,
                    z = s.pos.z + s.rightZ * lateral,
                    headingRad = s.headingRad
                )
            )
        }
        startGrid = builtGrid

        // 3 rows of 4 Item Boxes at 22%, 50%, and 78% of the lap (Part E1)
        val builtBoxes = ArrayList<ItemBoxPlacement>(12)
        val rowFractions = floatArrayOf(0.22f, 0.50f, 0.78f)
        val laneFactors = floatArrayOf(-0.55f, -0.18f, 0.18f, 0.55f)
        var boxId = 0
        for (rf in rowFractions) {
            val s = sampleAtDistance(rf * totalLength)
            val usableHalf = (roadWidth * 0.5f) - 1.2f
            for (lf in laneFactors) {
                val lat = lf * usableHalf
                builtBoxes.add(
                    ItemBoxPlacement(
                        id = boxId++,
                        x = s.pos.x + s.rightX * lat,
                        y = s.pos.y + 1.0f,
                        z = s.pos.z + s.rightZ * lat
                    )
                )
            }
        }
        itemBoxes = builtBoxes

        // 10 Coins in racing-line arcs at ~35% and ~65% of the lap (Part D6)
        val builtCoins = ArrayList<TrackCoinPlacement>(GameConstants.COINS_PER_TRACK)
        var coinId = 0
        val arcStarts = floatArrayOf(0.33f, 0.63f)
        for (arcIdx in arcStarts.indices) {
            val baseFrac = arcStarts[arcIdx]
            for (c in 0 until 5) {
                val frac = (baseFrac + c * 0.012f) % 1.0f
                val s = sampleAtDistance(frac * totalLength)
                val curveSign = if (s.curvature >= 0f) 1f else -1f
                val arcLat = sin((c / 4f) * PI.toFloat()) * (roadWidth * 0.24f) * curveSign
                builtCoins.add(
                    TrackCoinPlacement(
                        id = coinId++,
                        x = s.pos.x + s.rightX * arcLat,
                        y = s.pos.y + 0.55f,
                        z = s.pos.z + s.rightZ * arcLat
                    )
                )
            }
        }
        coins = builtCoins

        // Sanitize solid props so none violate Part B2 clearance (roadWidth/2 + 1.5u)
        props = sanitizePropsClearance(rawProps)
    }

    private fun sanitizePropsClearance(inputProps: List<TrackProp>): List<TrackProp> {
        val minCenterlineClearance = roadWidth * 0.5f + 1.55f
        return inputProps.map { p ->
            if (!p.isSolidWall || p.isRamp || p.isBoostPad || p.isRoadObstacle) {
                p
            } else {
                var adjustedX = p.x
                var adjustedZ = p.z
                // Check against spline samples that overlap the prop's vertical span
                for (s in samples) {
                    if (s.pos.y + 2.0f < p.y || s.pos.y > p.y + p.sy + 2.0f) continue
                    val dx = adjustedX - s.pos.x
                    val dz = adjustedZ - s.pos.z
                    // Transform (dx, dz) into prop's local oriented frame (-yawRad)
                    val c = cos(-p.yawRad)
                    val sn = sin(-p.yawRad)
                    val localX = dx * c - dz * sn
                    val localZ = dx * sn + dz * c
                    val halfX = if (p.isRoundBumper) maxOf(p.sx, p.sz) * 0.5f else p.sx * 0.5f
                    val halfZ = if (p.isRoundBumper) maxOf(p.sx, p.sz) * 0.5f else p.sz * 0.5f
                    val clampedX = localX.coerceIn(-halfX, halfX)
                    val clampedZ = localZ.coerceIn(-halfZ, halfZ)
                    val diffX = localX - clampedX
                    val diffZ = localZ - clampedZ
                    val distToBox = hypot(diffX, diffZ)
                    if (distToBox < minCenterlineClearance) {
                        val pushNeeded = (minCenterlineClearance - distToBox) + 0.4f
                        val lat = dx * s.rightX + dz * s.rightZ
                        val sideSign = if (lat >= 0f) 1f else -1f
                        adjustedX += s.rightX * sideSign * pushNeeded
                        adjustedZ += s.rightZ * sideSign * pushNeeded
                    }
                }
                p.copy(x = adjustedX, z = adjustedZ)
            }
        }
    }

    fun wrapDistance(dist: Float): Float {
        var d = dist % totalLength
        if (d < 0f) d += totalLength
        return d
    }

    fun wrapSignedDelta(delta: Float): Float {
        var d = delta
        val half = totalLength * 0.5f
        while (d > half) d -= totalLength
        while (d < -half) d += totalLength
        return d
    }

    fun sampleAtDistance(dist: Float): SplineSample {
        val wrapped = wrapDistance(dist)
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
     * Windowed search around previous sample index (±windowRadius samples), preferring samples
     * whose deck elevation matches worldY. Falls back to full search only
     * when prevSampleIndex < 0 (e.g. initial placement) or horizontal distance exceeds 30u.
     * Prevents snapping to the wrong part of the track on bridges/crossovers (Part B3).
     */
    fun findNearestSampleWindowed(
        worldX: Float,
        worldY: Float,
        worldZ: Float,
        prevSampleIndex: Int,
        windowRadius: Int = 40
    ): NearestSampleResult {
        val n = samples.size
        var bestIdx = -1
        var bestScore = Float.MAX_VALUE
        var bestHorizSq = Float.MAX_VALUE

        if (prevSampleIndex in 0 until n) {
            for (offset in -windowRadius..windowRadius) {
                val idx = (prevSampleIndex + offset + n) % n
                val s = samples[idx]
                val dx = worldX - s.pos.x
                val dz = worldZ - s.pos.z
                val horizSq = dx * dx + dz * dz
                val dy = abs(worldY - s.pos.y)
                val heightPenalty = if (dy < 8.0f) dy * 1.5f else 80f + dy * 12f
                val score = horizSq + heightPenalty
                if (score < bestScore) {
                    bestScore = score
                    bestHorizSq = horizSq
                    bestIdx = idx
                }
            }
        }

        // Fallback to full search if no previous index or kart is horizontally far from windowed candidate
        if (bestIdx < 0 || bestHorizSq > 900f) {
            bestScore = Float.MAX_VALUE
            for (i in 0 until n) {
                val s = samples[i]
                val dx = worldX - s.pos.x
                val dz = worldZ - s.pos.z
                val dy = abs(worldY - s.pos.y)
                val heightPenalty = if (dy < 4.5f) dy * 2f else 400f + dy * 40f
                val score = dx * dx + dz * dz + heightPenalty
                if (score < bestScore) {
                    bestScore = score
                    bestIdx = i
                }
            }
        }

        val best = samples[bestIdx.coerceIn(0, n - 1)]
        val dx = worldX - best.pos.x
        val dz = worldZ - best.pos.z
        val lateral = dx * best.rightX + dz * best.rightZ
        return NearestSampleResult(best, lateral)
    }

    fun findNearestSample(worldX: Float, worldZ: Float): Pair<SplineSample, Float> {
        val res = findNearestSampleWindowed(worldX, 0f, worldZ, -1)
        return Pair(res.sample, res.lateralOffset)
    }

    fun queryNearestPoint(worldX: Float, worldZ: Float): NearestSampleResult {
        return findNearestSampleWindowed(worldX, 0f, worldZ, -1)
    }

    fun isInSinkHole(x: Float, z: Float): Boolean {
        if (!hasSinkGapZone) return false
        return x in sinkGapMinX..sinkGapMaxX && z in sinkGapMinZ..sinkGapMaxZ
    }

    fun isInWaterPool(x: Float, z: Float): Boolean {
        if (!hasWaterCenterPool) return false
        val nx = x / waterPoolRadiusX
        val nz = z / waterPoolRadiusZ
        return (nx * nx + nz * nz) < 0.72f
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
                    result.add(Vec3(x, y, z))
                }
            }
            return result
        }
    }
}
