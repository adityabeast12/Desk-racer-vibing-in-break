package com.example.render

import kotlin.math.max
import kotlin.math.min

/**
 * v7.5 adaptive graphics. Five render budgets from "potato" to "max"; in AUTO the governor watches
 * real frame times during a race and steps the budget down fast when the device can't hold its
 * target, and back up slowly when there is clear headroom. Typical mobile practice (dynamic
 * resolution first — it's the cheapest knob for fill-rate-bound GPUs — then effects, then refresh).
 *
 * Pure Kotlin: no GL / Android calls, so it is unit-tested with synthetic frame times.
 */
data class RenderBudget(
    val level: Int,
    /** Fraction of the native surface resolution to render the 3D scene at (HUD stays native). */
    val renderScale: Float,
    /** Hard cap on the rendered short side in pixels (1440p phones don't need 1440p 3D). */
    val maxShortSidePx: Int,
    val planarShadows: Boolean,
    val fullParticles: Boolean,
    val skidMarks: Boolean,
    val normalMaps: Float,
    /** Cheaper lit shader path: no env reflections / clear-coat / point light. */
    val liteShading: Boolean,
    val propDrawDist: Float,
    val pylonDrawDist: Float,
    val lodDist: Float
) {
    companion object {
        val LEVELS = listOf(
            RenderBudget(0, 0.55f, 540, false, false, false, 0f, true, 150f, 110f, 30f),
            RenderBudget(1, 0.68f, 640, false, false, true, 0f, true, 210f, 150f, 45f),
            RenderBudget(2, 0.80f, 760, true, true, true, 0.6f, false, 280f, 200f, 65f),
            RenderBudget(3, 0.90f, 900, true, true, true, 0.85f, false, 340f, 240f, 80f),
            RenderBudget(4, 1.00f, 1080, true, true, true, 1f, false, 380f, 260f, 90f)
        )
        const val MAX_LEVEL = 4
        fun of(level: Int) = LEVELS[level.coerceIn(0, MAX_LEVEL)]
    }
}

enum class DeviceTier { LOW, MID, HIGH }

object DeviceProfiler {
    /**
     * Rough tier from RAM, CPU and GPU name. Errs low: a mid phone that starts at level 2 and climbs
     * to 4 in ten seconds is invisible; a low phone that starts at 4 stutters through its first race.
     */
    fun classify(totalRamGb: Float, cores: Int, maxCpuGhz: Float, gpuRenderer: String, mediaPerfClass: Int): DeviceTier {
        val gpu = gpuRenderer.lowercase()
        var score = 0
        score += when {
            totalRamGb >= 7.5f -> 2
            totalRamGb >= 5.5f -> 1
            totalRamGb < 3.5f -> -2
            else -> 0
        }
        score += when {
            maxCpuGhz >= 2.8f -> 2
            maxCpuGhz >= 2.3f -> 1
            maxCpuGhz in 0.1f..1.9f -> -1
            else -> 0
        }
        if (cores in 1..4) score -= 1
        score += gpuScore(gpu)
        if (mediaPerfClass >= 33) score += 2 else if (mediaPerfClass >= 31) score += 1
        return when {
            score >= 4 -> DeviceTier.HIGH
            score >= 1 -> DeviceTier.MID
            else -> DeviceTier.LOW
        }
    }

    internal fun gpuScore(gpu: String): Int {
        fun num(prefix: String): Int? = Regex("$prefix\\s*-?\\s*(\\d+)").find(gpu)?.groupValues?.get(1)?.toIntOrNull()
        num("adreno")?.let { n ->
            return when {
                n >= 730 -> 3
                n >= 640 -> 2
                n >= 618 -> 1
                n >= 600 -> 0
                else -> -2
            }
        }
        if (gpu.contains("immortalis")) return 3
        num("mali-g")?.let { n ->
            return when {
                n >= 710 -> 2   // G710/G715/G720
                n in 76..99 -> 1 // G76/G77/G78
                n in 610..699 -> 0 // G610/G615
                n in 68..72 -> 0
                else -> -2       // G52/G57/G31/G51
            }
        }
        if (gpu.contains("mali-t") || gpu.contains("mali-4")) return -3
        if (gpu.contains("powervr")) return -2
        if (gpu.contains("xclipse")) return 2
        if (gpu.contains("swiftshader") || gpu.contains("llvmpipe")) return -3
        return 0
    }

    fun startLevel(tier: DeviceTier): Int = when (tier) {
        DeviceTier.LOW -> 1
        DeviceTier.MID -> 3
        DeviceTier.HIGH -> 4
    }

    /** Highest budget AUTO may climb to. Low tier can still reach 3 if it proves it has headroom. */
    fun ceilingLevel(tier: DeviceTier): Int = when (tier) {
        DeviceTier.LOW -> 3
        else -> RenderBudget.MAX_LEVEL
    }
}

class PerfGovernor(startLevel: Int, private var ceiling: Int = RenderBudget.MAX_LEVEL) {

    var level: Int = startLevel.coerceIn(0, ceiling)
        private set

    /** Refresh cap the governor asks for (0 = no cap from the governor). 60 first, 30 as last resort. */
    var capHz: Float = 0f
        private set

    var lastWindowFps: Float = 0f
        private set

    private var winTime = 0f
    private var winFrames = 0
    private var winSlow = 0
    private var badWindows = 0
    private var goodWindows = 0
    private var holdOffSec = 0f
    private var failedUpgradeLevel = -1
    private var failedUpgradeCooldown = 0f
    private var lastUpgradeAgo = 999f
    private var triedHighRefresh = false
    private var failedUpgrades = 0

    /**
     * Feed one real frame time. [targetHz] is what the display/settings aim for. Returns true when
     * [level] or [capHz] changed and the caller should re-apply the budget.
     */
    fun onFrame(dtSec: Float, targetHz: Float): Boolean {
        if (dtSec <= 0f || dtSec > 0.5f) return false // pauses, loading hitches: ignore
        val effTarget = if (capHz > 0f) min(targetHz, capHz) else targetHz
        val budget = 1f / effTarget
        winTime += dtSec
        winFrames++
        if (dtSec > budget * 1.6f) winSlow++
        if (holdOffSec > 0f) holdOffSec -= dtSec
        if (failedUpgradeCooldown > 0f) failedUpgradeCooldown -= dtSec
        lastUpgradeAgo += dtSec
        if (winTime < WINDOW_SEC) return false

        val fps = winFrames / winTime
        val slowFrac = winSlow.toFloat() / max(1, winFrames)
        lastWindowFps = fps
        winTime = 0f; winFrames = 0; winSlow = 0
        if (holdOffSec > 0f) return false // let the last change settle before judging it

        val struggling = fps < effTarget * 0.88f || slowFrac > 0.12f
        val comfortable = fps > effTarget * 0.97f && slowFrac < 0.02f
        if (struggling) { badWindows++; goodWindows = 0 } else if (comfortable) { goodWindows++; badWindows = 0 } else { badWindows = 0; goodWindows = 0 }

        if (badWindows >= 2) {
            badWindows = 0
            // a just-made upgrade that immediately struggles is remembered so we don't oscillate
            if (lastUpgradeAgo < WINDOW_SEC * 4) {
                failedUpgradeLevel = level
                failedUpgrades = (failedUpgrades + 1).coerceAtMost(4)
                failedUpgradeCooldown = 30f * (1 shl failedUpgrades) // 60 s, 120 s, 240 s ...
            }
            return stepDown(effTarget, targetHz)
        }
        if (goodWindows >= 4) {
            goodWindows = 0
            return stepUp(targetHz)
        }
        return false
    }

    private fun stepDown(effTarget: Float, targetHz: Float): Boolean {
        // 1) a 90-144 Hz panel the GPU can't feed: ask for 60 before sacrificing image quality
        if (capHz == 0f && targetHz > 65f) {
            capHz = 60f; holdOffSec = SETTLE_SEC; return true
        }
        // 2) cheaper budget
        if (level > 0) {
            level--; holdOffSec = SETTLE_SEC; return true
        }
        // 3) even the potato budget can't hold 60: a steady 30 beats a ragged 40-55
        if (effTarget > 35f) {
            capHz = 30f; holdOffSec = SETTLE_SEC; return true
        }
        return false
    }

    private fun stepUp(targetHz: Float): Boolean {
        if (failedUpgradeCooldown > 0f) return false // the last attempt to go up didn't hold
        if (capHz in 1f..35f) { capHz = 60f; holdOffSec = SETTLE_SEC; lastUpgradeAgo = 0f; return true }
        val next = level + 1
        if (next <= ceiling) {
            level = next; holdOffSec = SETTLE_SEC; lastUpgradeAgo = 0f; return true
        }
        if (level >= ceiling && capHz == 60f && targetHz > 65f && !triedHighRefresh) {
            // already maxed at 60 with headroom: try the panel's high refresh once
            capHz = 0f; holdOffSec = SETTLE_SEC; lastUpgradeAgo = 0f; triedHighRefresh = true; return true
        }
        return false
    }

    /** Thermal event: drop a level immediately and keep AUTO from climbing back past it. */
    fun thermalStepDown() {
        if (level > 0) level--
        ceiling = min(ceiling, level)
        holdOffSec = SETTLE_SEC
    }

    companion object {
        const val WINDOW_SEC = 1.5f
        const val SETTLE_SEC = 2.0f
    }
}
