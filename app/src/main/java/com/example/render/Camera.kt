package com.example.render

import com.example.game.RenderKartSnapshot
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

    fun updateChase(
        dt: Float,
        targetKart: RenderKartSnapshot?,
        isShowroom: Boolean,
        showroomAngleRad: Float,
        landingShakeSignal: Int
    ) {
        if (landingShakeSignal != lastLandingSignal) {
            lastLandingSignal = landingShakeSignal
            shakeTimerSec = 0.22f
        }

        if (targetKart == null) return

        if (isShowroom) {
            val orbitRadius = 5.4f
            val desiredX = targetKart.x + sin(showroomAngleRad) * orbitRadius
            val desiredZ = targetKart.z + cos(showroomAngleRad) * orbitRadius
            val desiredY = targetKart.y + 2.1f
            posX += (desiredX - posX) * 0.15f
            posY += (desiredY - posY) * 0.15f
            posZ += (desiredZ - posZ) * 0.15f
            lookX = targetKart.x
            lookY = targetKart.y + 0.7f
            lookZ = targetKart.z
            currentFovDeg = 54f
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
