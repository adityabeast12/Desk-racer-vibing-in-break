package com.example.render

import com.example.game.GameConstants
import com.example.game.RenderKartSnapshot
import com.example.game.RenderProjectileSnapshot
import com.example.game.WorldRenderSnapshot
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI

/**
 * Smooth rendering between fixed 60 Hz simulation steps.
 *
 * The sim thread publishes every step with its simulation timestamp; the GL thread renders one
 * step in the past and blends the two most recent snapshots. This removes the speed-proportional
 * "vibration" caused by uneven step delivery vs. 60/90/120 Hz display refresh.
 */
object RenderInterp {
    private class SnapPair(
        val prev: WorldRenderSnapshot?,
        val prevNs: Long,
        val curr: WorldRenderSnapshot,
        val currNs: Long
    )

    private val ref = AtomicReference<SnapPair?>(null)
    private val stepNs: Long = (GameConstants.SIM_DT * 1_000_000_000.0).toLong()
    private const val TWO_PI = (2.0 * PI).toFloat()
    private const val TELEPORT_DIST_SQ = 36f

    fun publish(snap: WorldRenderSnapshot, simTimeNs: Long) {
        val old = ref.get()
        val prev = old?.curr
        val prevNs = old?.currNs ?: (simTimeNs - stepNs)
        ref.set(SnapPair(prev, prevNs, snap, simTimeNs))
    }

    fun reset() {
        ref.set(null)
    }

    /** Returns an interpolated copy of [latest] for display at [nowNs], or [latest] unchanged. */
    fun resolve(latest: WorldRenderSnapshot?, nowNs: Long): WorldRenderSnapshot? {
        if (latest == null) return null
        val p = ref.get() ?: return latest
        if (p.curr !== latest) return latest
        val prev = p.prev ?: return latest
        if (prev.trackId != latest.trackId) return latest
        val span = (p.currNs - p.prevNs).coerceAtLeast(1L)
        val renderNs = nowNs - stepNs
        val t = ((renderNs - p.prevNs).toDouble() / span.toDouble()).toFloat().coerceIn(0f, 1f)
        if (t >= 0.999f) return latest

        val prevKarts = HashMap<Int, RenderKartSnapshot>(prev.karts.size * 2)
        for (k in prev.karts) prevKarts[k.slotIndex] = k
        val karts = latest.karts.map { c ->
            val a = prevKarts[c.slotIndex] ?: return@map c
            val dx = c.x - a.x
            val dy = c.y - a.y
            val dz = c.z - a.z
            if (dx * dx + dy * dy + dz * dz > TELEPORT_DIST_SQ) return@map c
            c.copy(
                x = a.x + dx * t,
                y = a.y + dy * t,
                z = a.z + dz * t,
                headingRad = lerpAngle(a.headingRad, c.headingRad, t),
                visualYawOffsetRad = lerp(a.visualYawOffsetRad, c.visualYawOffsetRad, t),
                visualRollRad = lerp(a.visualRollRad, c.visualRollRad, t),
                visualPitchRad = lerp(a.visualPitchRad, c.visualPitchRad, t),
                wheelSpinRad = lerpAngle(a.wheelSpinRad, c.wheelSpinRad, t),
                frontWheelSteerRad = lerp(a.frontWheelSteerRad, c.frontWheelSteerRad, t),
                speed = lerp(a.speed, c.speed, t)
            )
        }

        val prevProj = HashMap<Int, RenderProjectileSnapshot>(prev.projectiles.size * 2 + 1)
        for (pr in prev.projectiles) prevProj[pr.id] = pr
        val projectiles = latest.projectiles.map { c ->
            val a = prevProj[c.id] ?: return@map c
            val dx = c.x - a.x
            val dz = c.z - a.z
            if (dx * dx + dz * dz > TELEPORT_DIST_SQ) return@map c
            c.copy(
                x = a.x + dx * t,
                y = lerp(a.y, c.y, t),
                z = a.z + dz * t,
                headingRad = lerpAngle(a.headingRad, c.headingRad, t)
            )
        }

        return latest.copy(
            karts = karts,
            projectiles = projectiles,
            showroomCameraAngleRad = lerpAngle(prev.showroomCameraAngleRad, latest.showroomCameraAngleRad, t),
            flybyProgress = if (prev.phase == latest.phase) lerp(prev.flybyProgress, latest.flybyProgress, t) else latest.flybyProgress,
            elapsedTimeSec = lerp(prev.elapsedTimeSec, latest.elapsedTimeSec, t)
        )
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun lerpAngle(a: Float, b: Float, t: Float): Float {
        var d = (b - a) % TWO_PI
        if (d > PI.toFloat()) d -= TWO_PI
        if (d < -PI.toFloat()) d += TWO_PI
        return a + d * t
    }
}
