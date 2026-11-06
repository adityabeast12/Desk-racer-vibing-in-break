package com.example.game

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.GameController.GCController
import platform.GameController.GCKeyCodeDownArrow
import platform.GameController.GCKeyCodeEscape
import platform.GameController.GCKeyCodeKeyA
import platform.GameController.GCKeyCodeKeyD
import platform.GameController.GCKeyCodeKeyE
import platform.GameController.GCKeyCodeKeyQ
import platform.GameController.GCKeyCodeKeyS
import platform.GameController.GCKeyCodeKeyW
import platform.GameController.GCKeyCodeLeftArrow
import platform.GameController.GCKeyCodeLeftShift
import platform.GameController.GCKeyCodeReturnOrEnter
import platform.GameController.GCKeyCodeRightArrow
import platform.GameController.GCKeyCodeSpacebar
import platform.GameController.GCKeyCodeUpArrow
import platform.GameController.GCKeyboard
import kotlin.math.abs
import kotlin.math.sign

/** Which device the player is driving with right now (the HUD adapts to it). */
enum class InputSource { TOUCH, KEYBOARD_MOUSE, GAMEPAD }

/**
 * iOS hardware input: MFi / Xbox / PlayStation controllers and Bluetooth keyboards through the
 * GameController framework, polled every frame. Same mapping and auto-switching as Android's.
 */
class HardwareInput(private val input: InputState) {
    private val _source = MutableStateFlow(InputSource.TOUCH)
    val source: StateFlow<InputSource> = _source.asStateFlow()
    val pauseRequests = MutableStateFlow(0)

    var raceCapture: Boolean = false
        set(v) {
            if (field && !v) releaseAll()
            field = v
        }

    var onWantPointerCapture: ((Boolean) -> Unit)? = null

    private var lastPause = false
    private var lastSig = ""

    fun onTouchScreen() {
        if (_source.value != InputSource.TOUCH) {
            _source.value = InputSource.TOUCH
            releaseAll()
        }
    }

    private class Frame {
        var steer = 0f
        var digital = false
        var gas = false
        var brake = false
        var drift = false
        var item = false
        var back = false
        var pause = false
        var any = false
    }

    private fun readPad(f: Frame): Boolean {
        val pad = GCController.controllers().firstOrNull { (it as? GCController)?.extendedGamepad != null } as? GCController ?: return false
        val g = pad.extendedGamepad ?: return false
        val stick = shapeStick(g.leftThumbstick.xAxis.value)
        val dpad = (if (g.dpad.right.pressed) 1f else 0f) - (if (g.dpad.left.pressed) 1f else 0f)
        if (dpad != 0f) { f.steer = dpad; f.digital = true } else f.steer = stick
        f.gas = g.buttonA.pressed || g.rightTrigger.value > 0.25f
        f.brake = g.buttonB.pressed || g.leftTrigger.value > 0.35f
        f.drift = g.rightShoulder.pressed
        f.item = g.leftShoulder.pressed || g.buttonX.pressed
        f.back = g.buttonY.pressed
        f.pause = g.buttonMenu.pressed
        f.any = f.gas || f.brake || f.drift || f.item || f.back || f.pause || abs(f.steer) > 0.2f
        return true
    }

    private fun readKeyboard(f: Frame): Boolean {
        val kb = GCKeyboard.coalescedKeyboard?.keyboardInput ?: return false
        fun k(code: Long) = kb.buttonForKeyCode(code)?.pressed == true
        val right = k(GCKeyCodeKeyD) || k(GCKeyCodeRightArrow)
        val left = k(GCKeyCodeKeyA) || k(GCKeyCodeLeftArrow)
        f.steer = (if (right) 1f else 0f) - (if (left) 1f else 0f)
        f.digital = true
        f.gas = k(GCKeyCodeKeyW) || k(GCKeyCodeUpArrow)
        f.brake = k(GCKeyCodeKeyS) || k(GCKeyCodeDownArrow)
        f.drift = k(GCKeyCodeSpacebar) || k(GCKeyCodeLeftShift)
        f.item = k(GCKeyCodeKeyE) || k(GCKeyCodeReturnOrEnter)
        f.back = k(GCKeyCodeKeyQ)
        f.pause = k(GCKeyCodeEscape)
        f.any = f.gas || f.brake || f.drift || f.item || f.back || f.pause || f.steer != 0f
        return true
    }

    /** Called every ~16 ms from the iOS root. */
    fun tick(dt: Float) {
        val pad = Frame()
        val kb = Frame()
        val hasPad = runCatching { readPad(pad) }.getOrDefault(false)
        val hasKb = runCatching { readKeyboard(kb) }.getOrDefault(false)
        if (hasPad && pad.any) use(InputSource.GAMEPAD) else if (hasKb && kb.any) use(InputSource.KEYBOARD_MOUSE)
        val f = when (_source.value) {
            InputSource.GAMEPAD -> if (hasPad) pad else null
            InputSource.KEYBOARD_MOUSE -> if (hasKb) kb else null
            InputSource.TOUCH -> null
        } ?: return
        if (f.pause && !lastPause && raceCapture) pauseRequests.value = pauseRequests.value + 1
        lastPause = f.pause
        if (!raceCapture) return
        val sig = "${f.steer}${f.gas}${f.brake}${f.drift}${f.item}${f.back}"
        if (sig == lastSig) return
        lastSig = sig
        input.updateSteer(f.steer, f.digital)
        input.gasPressed = f.gas
        input.brakePressed = f.brake
        input.updateDrift(f.drift)
        if (f.back && !input.itemHeldDown) input.triggerItem(fireBackward = true)
        input.updateItemHold(f.item)
    }

    private fun use(src: InputSource) {
        if (_source.value != src) _source.value = src
    }

    private fun releaseAll() {
        lastSig = ""
        input.updateSteer(0f)
        input.gasPressed = false
        input.brakePressed = false
        input.updateDrift(false)
        input.updateItemHold(false)
    }

    companion object {
        fun shapeStick(x: Float): Float {
            val dz = 0.14f
            val a = abs(x)
            if (a < dz) return 0f
            val n = ((a - dz) / (1f - dz)).coerceIn(0f, 1f)
            return sign(x) * (n * (0.55f + 0.45f * n))
        }
    }
}
