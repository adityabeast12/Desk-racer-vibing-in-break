package com.example.render

import com.example.game.RacePhase
import com.example.game.RenderKartSnapshot
import com.example.game.Track
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Camera {
    var posX: Float = 0f
    var posY: Float = 4.2f
    var posZ: Float = 52f

    var lookX: Float = 0f
    var lookY: Float = 1.0f
    var lookZ: Float = 40f

    var currentFovDeg: Float = 62f

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
        landingShakeSignal: Int
    ) {
        if (landingShakeSignal != lastLandingSignal) {
            lastLandingSignal = landingShakeSignal
            shakeTimerSec = 0.22f
        }

        // 1. Pre-race 4s camera fly-by along the track spline (Part C3)
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

            posX += (desiredX - posX) * 0.14f
            posY += (desiredY - posY) * 0.14f
            posZ += (desiredZ - posZ) * 0.14f
            lookX += (sLook.pos.x - lookX) * 0.18f
            lookY += (sLook.pos.y + 1.0f - lookY) * 0.18f
            lookZ += (sLook.pos.z - lookZ) * 0.18f
            currentFovDeg = 64f
            return
        }

        if (targetKart == null) return

        // 2. Showroom / Results / Finished local player orbit camera (Part B4)
        val isOrbitMode = phase == RacePhase.SHOWROOM_MENU ||
            phase == RacePhase.RESULTS ||
            phase == RacePhase.FINISHED

        if (isOrbitMode) {
            finishOrbitAngleRad = (finishOrbitAngleRad + dt * 0.75f) % (2f * PI.toFloat())
            val orbitAngle = if (phase == RacePhase.SHOWROOM_MENU) showroomAngleRad else finishOrbitAngleRad
            val orbitRadius = if (phase == RacePhase.SHOWROOM_MENU) 5.4f else 6.2f
            val desiredX = targetKart.x + sin(orbitAngle) * orbitRadius
            val desiredZ = targetKart.z + cos(orbitAngle) * orbitRadius
            val desiredY = targetKart.y + 2.2f
            posX += (desiredX - posX) * 0.15f
            posY += (desiredY - posY) * 0.15f
            posZ += (desiredZ - posZ) * 0.15f
            lookX = targetKart.x
            lookY = targetKart.y + 0.7f
            lookZ = targetKart.z
            currentFovDeg = 56f
            return
        }

        val fwdX = sin(targetKart.headingRad)
        val fwdZ = cos(targetKart.headingRad)

        val chaseDist = if (targetKart.isBoosting) 7.4f else 6.6f
        val chaseHeight = 3.15f

        val desiredCamX = targetKart.x - fwdX * chaseDist
        val desiredCamY = targetKart.y + chaseHeight
        val desiredCamZ = targetKart.z - fwdZ * chaseDist

        val springRate = (11f * dt).coerceIn(0.05f, 0.35f)
        posX += (desiredCamX - posX) * springRate
        posY += (desiredCamY - posY) * springRate
        posZ += (desiredCamZ - posZ) * springRate

        val desiredLookX = targetKart.x + fwdX * 3.8f
        val desiredLookY = targetKart.y + 0.85f
        val desiredLookZ = targetKart.z + fwdZ * 3.8f

        val lookRate = (16f * dt).coerceIn(0.08f, 0.45f)
        lookX += (desiredLookX - lookX) * lookRate
        lookY += (desiredLookY - lookY) * lookRate
        lookZ += (desiredLookZ - lookZ) * lookRate

        if (shakeTimerSec > 0f) {
            shakeTimerSec = (shakeTimerSec - dt).coerceAtLeast(0f)
            val mag = shakeTimerSec * 0.9f
            posY += (Random.nextFloat() - 0.5f) * mag
            lookY += (Random.nextFloat() - 0.5f) * mag
        }

        // FOV kick (+8 degrees on boost)
        val targetFov = if (targetKart.isBoosting) 70f else 62f + (abs(targetKart.speed) / 30f) * 2.5f
        currentFovDeg += (targetFov - currentFovDeg) * (8f * dt).coerceIn(0.05f, 0.3f)
    }
}
