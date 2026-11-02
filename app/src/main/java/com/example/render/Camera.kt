package com.example.render

import android.opengl.Matrix
import com.example.game.GameConstants
import com.example.game.RacePhase
import com.example.game.RenderKartSnapshot
import com.example.game.Track
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

class Camera {
    var posX: Float = 0f
    var posY: Float = 4.2f
    var posZ: Float = 52f

    var lookX: Float = 0f
    var lookY: Float = 1.0f
    var lookZ: Float = 40f

    var currentFovDeg: Float = 62f
    var currentLensShiftX: Float = 0f

    private var shakeTimerSec: Float = 0f
    private var lastLandingSignal: Int = 0
    private var finishOrbitAngleRad: Float = 0f

    fun updateChase(
        dt: Float,
        targetKart: RenderKartSnapshot?,
        phase: RacePhase,
        track: Track,
        flybyProgress01: Float,
        showroomAngleRad: Float,
        landingShakeSignal: Int,
        isGarageStage: Boolean = false,
        garageZoom: Float = 1.0f,
        viewportWidth: Int = 1280,
        viewportHeight: Int = 720
    ) {
        if (landingShakeSignal != lastLandingSignal) {
            lastLandingSignal = landingShakeSignal
            shakeTimerSec = 0.22f
        }

        currentLensShiftX = if (isGarageStage && phase == RacePhase.SHOWROOM_MENU) {
            val w = viewportWidth.coerceAtLeast(1).toFloat()
            computeLensShiftX(stageCenterX = w * 0.30f, screenWidth = w)
        } else {
            0f
        }

        // 1. Pre-race 4s camera fly-by along the track spline
        if (phase == RacePhase.FLYBY_INTRO && track.samples.isNotEmpty()) {
            val n = track.samples.size
            val idxFloat = (flybyProgress01.coerceIn(0f, 1f) * 0.35f + 0.65f) * n
            val idx0 = idxFloat.toInt() % n
            val idxLook = (idx0 + 8) % n
            val sCam = track.samples[idx0]
            val sLook = track.samples[idxLook]

            val desiredX = sCam.pos.x + sCam.rightX * 3.5f
            val desiredY = sCam.pos.y + 5.2f + sin(flybyProgress01 * PI.toFloat()) * 2.5f
            val desiredZ = sCam.pos.z + sCam.rightZ * 3.5f

            val fp = smoothFactor(9.0f, dt)
            val fl = smoothFactor(11.9f, dt)
            posX += (desiredX - posX) * fp
            posY += (desiredY - posY) * fp
            posZ += (desiredZ - posZ) * fp
            lookX += (sLook.pos.x - lookX) * fl
            lookY += (sLook.pos.y + 1.0f - lookY) * fl
            lookZ += (sLook.pos.z - lookZ) * fl
            currentFovDeg = 64f
            return
        }

        if (targetKart == null) return
        if (phase != RacePhase.RACING && phase != RacePhase.COUNTDOWN) chaseHeadingInit = false

        // 2. Showroom / Garage / Results / Finished local player orbit camera
        val isOrbitMode = phase == RacePhase.SHOWROOM_MENU ||
            phase == RacePhase.RESULTS ||
            phase == RacePhase.FINISHED

        if (isOrbitMode) {
            finishOrbitAngleRad = (finishOrbitAngleRad + dt * 0.75f) % (2f * PI.toFloat())
            val orbitAngle = if (phase == RacePhase.SHOWROOM_MENU) showroomAngleRad else finishOrbitAngleRad
            val baseDist = if (isGarageStage) {
                computeGarageCameraDistance(fovYDeg = 46f) / garageZoom.coerceIn(0.8f, 1.4f)
            } else if (phase == RacePhase.SHOWROOM_MENU) {
                5.4f
            } else {
                6.2f
            }
            val desiredX = targetKart.x + sin(orbitAngle) * baseDist
            val desiredZ = targetKart.z + cos(orbitAngle) * baseDist
            val desiredY = targetKart.y + if (isGarageStage) 1.55f else 2.2f
            val lerpRate = if (isGarageStage) smoothFactor(25.8f, dt) else smoothFactor(9.75f, dt)
            posX += (desiredX - posX) * lerpRate
            posY += (desiredY - posY) * lerpRate
            posZ += (desiredZ - posZ) * lerpRate
            lookX = targetKart.x
            lookY = targetKart.y + if (isGarageStage) 0.58f else 0.7f
            lookZ = targetKart.z
            currentFovDeg = if (isGarageStage) 46f else 56f
            return
        }

        // v6: follow a smoothed heading so tiny steering corrections at top speed don't shake the camera
        if (!chaseHeadingInit) {
            chaseHeadingRad = targetKart.headingRad
            chaseHeadingInit = true
        } else {
            var dH = (targetKart.headingRad - chaseHeadingRad) % (2f * PI.toFloat())
            if (dH > PI.toFloat()) dH -= 2f * PI.toFloat()
            if (dH < -PI.toFloat()) dH += 2f * PI.toFloat()
            chaseHeadingRad += dH * smoothFactor(9f, dt)
        }
        val fwdX = sin(chaseHeadingRad)
        val fwdZ = cos(chaseHeadingRad)

        val chaseDist = if (targetKart.isBoosting) 7.4f else 6.6f
        val chaseHeight = 3.15f

        val desiredCamX = targetKart.x - fwdX * chaseDist
        val desiredCamY = targetKart.y + chaseHeight
        val desiredCamZ = targetKart.z - fwdZ * chaseDist

        val springRate = smoothFactor(11f, dt)
        posX += (desiredCamX - posX) * springRate
        posY += (desiredCamY - posY) * springRate
        posZ += (desiredCamZ - posZ) * springRate

        val desiredLookX = targetKart.x + fwdX * 3.8f
        val desiredLookY = targetKart.y + 0.85f
        val desiredLookZ = targetKart.z + fwdZ * 3.8f

        val lookRate = smoothFactor(16f, dt)
        lookX += (desiredLookX - lookX) * lookRate
        lookY += (desiredLookY - lookY) * lookRate
        lookZ += (desiredLookZ - lookZ) * lookRate

        if (shakeTimerSec > 0f) {
            shakeTimerSec = (shakeTimerSec - dt).coerceAtLeast(0f)
            val mag = shakeTimerSec * 0.9f
            posY += (Random.nextFloat() - 0.5f) * mag
            lookY += (Random.nextFloat() - 0.5f) * mag
        }

        // Speed FOV — 62° at rest -> 70° at top speed (+6° on boost), damped at 8 rad/s
        val speedNorm = (abs(targetKart.speed) / GameConstants.TOP_SPEED).coerceIn(0f, 1.15f)
        val boostKick = if (targetKart.isBoosting) 6.0f else 0.0f
        val targetFov = 62.0f + speedNorm * 8.0f + boostKick
        currentFovDeg += (targetFov - currentFovDeg) * smoothFactor(8.0f, dt)
    }

    private var chaseHeadingRad = 0f
    private var chaseHeadingInit = false

    /** Frame-rate independent exponential smoothing factor for rate [k] per second. */
    private fun smoothFactor(k: Float, dt: Float): Float = (1f - kotlin.math.exp(-k * dt)).coerceIn(0f, 1f)

    companion object {
        fun computeLensShiftX(stageCenterX: Float, screenWidth: Float): Float {
            val halfW = (screenWidth * 0.5f).coerceAtLeast(1f)
            val screenCenterX = screenWidth * 0.5f
            return (stageCenterX - screenCenterX) / halfW
        }

        fun computeGarageCameraDistance(
            kartVisualHeight: Float = 2.45f,
            targetFillFraction: Float = 0.70f,
            fovYDeg: Float = 46f
        ): Float {
            val halfFovRad = Math.toRadians((fovYDeg * 0.5).toDouble())
            val tanHalfFov = tan(halfFovRad).toFloat().coerceAtLeast(0.1f)
            return (kartVisualHeight / (2f * targetFillFraction * tanHalfFov)).coerceAtLeast(4.1f)
        }

        fun buildOffCenterPerspectiveMatrix(
            outProj: FloatArray,
            fovYDeg: Float,
            aspect: Float,
            near: Float,
            far: Float,
            lensShiftX: Float
        ) {
            Matrix.perspectiveM(outProj, 0, fovYDeg, aspect, near, far)
            outProj[8] = -lensShiftX
        }
    }
}
