package com.example.game

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sign

/** Which device the player is driving with right now (the HUD adapts to it). */
enum class InputSource { TOUCH, KEYBOARD_MOUSE, GAMEPAD }

/**
 * v7.5 hardware input: keyboard, mouse and game controllers, with automatic switching. The most
 * recently used device becomes the active [source] (touch the screen and the touch HUD comes back;
 * press a key or a pad button and it gets out of the way).
 *
 * Driving controls (only while [raceCapture] is on — menus keep normal focus navigation):
 *
 *  Keyboard  W / Up = gas · S / Down = brake · A D / Left Right = steer · Space / Shift = drift
 *            E / Enter / J = item (hold = shield) · Q / K = throw item backwards · Esc / P = pause
 *  Mouse     right button = drift · left button = item · move (captured) = steering wheel
 *  Gamepad   left stick / d-pad = steer · RT or A = gas · LT or B = brake · RB / R2 = drift
 *            LB / X = item (hold = shield) · Y = throw backwards · Start = pause
 *
 * Outside a race the pad's A/B are translated to confirm/back so Compose focus navigation works.
 */
class HardwareInput(private val input: InputState) {

    private val _source = MutableStateFlow(InputSource.TOUCH)
    val source: StateFlow<InputSource> = _source.asStateFlow()

    /** Bumped when the player presses Start / Esc during a race. */
    val pauseRequests = MutableStateFlow(0)

    /** True while a race is running and not paused: game keys drive the kart. */
    @Volatile
    var raceCapture: Boolean = false
        set(v) {
            if (field && !v) releaseAll()
            field = v
        }

    /** Optional hook so the activity can capture / release the mouse pointer for wheel steering. */
    var onWantPointerCapture: ((Boolean) -> Unit)? = null

    // keyboard
    private var kLeft = false
    private var kRight = false
    private var kGas = false
    private var kBrake = false
    private var kDrift = false
    private var kItem = false
    // gamepad
    private var padStickX = 0f
    private var padHatX = 0f
    private var padGasAxis = 0f
    private var padBrakeAxis = 0f
    private var padGasBtn = false
    private var padBrakeBtn = false
    private var padDrift = false
    private var padDriftAxis = false
    private var padItem = false
    private var padDpadLeft = false
    private var padDpadRight = false
    // mouse
    private var mouseDrift = false
    private var mouseItem = false
    private var mouseWheel = 0f
    private var mouseCaptured = false

    fun onTouchScreen() {
        if (_source.value != InputSource.TOUCH) {
            _source.value = InputSource.TOUCH
            releaseAll()
            setPointerCapture(false)
        }
    }

    private fun use(src: InputSource) {
        if (_source.value != src) _source.value = src
    }

    // ------------------------------------------------------------------ keys

    /** Returns true when the event was consumed by the game. */
    fun handleKey(event: KeyEvent): Boolean {
        val isPad = isGamepad(event.source) || KeyEvent.isGamepadButton(event.keyCode)
        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP
        if (!down && !up) return false
        // Only real driving keys switch the HUD. Volume / Back / media keys (and the on-screen
        // keyboard) used to flip a phone into "keyboard mode": touch HUD hidden, tilt steering
        // ignored, until the screen was touched again.
        if (!isDrivingKey(event.keyCode, isPad) || event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) return false
        if (down) use(if (isPad) InputSource.GAMEPAD else InputSource.KEYBOARD_MOUSE)

        if (!raceCapture) return false
        val code = event.keyCode
        if (isPad) {
            when (code) {
                KeyEvent.KEYCODE_BUTTON_A -> padGasBtn = down
                KeyEvent.KEYCODE_BUTTON_B -> padBrakeBtn = down
                KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2 -> padDrift = down
                KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_X -> padItem = down
                KeyEvent.KEYCODE_BUTTON_Y -> { if (down && event.repeatCount == 0) input.triggerItem(fireBackward = true); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> padDpadLeft = down
                KeyEvent.KEYCODE_DPAD_RIGHT -> padDpadRight = down
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> return true
                KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU -> { if (down && event.repeatCount == 0) pauseRequests.value++; return true }
                else -> return false
            }
            publish()
            return true
        }
        when (code) {
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> kLeft = down
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> kRight = down
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP -> kGas = down
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN -> kBrake = down
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> kDrift = down
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_J -> kItem = down
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_K -> { if (down && event.repeatCount == 0) input.triggerItem(fireBackward = true); return true }
            KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_P -> { if (down && event.repeatCount == 0) pauseRequests.value++; return true }
            else -> return false
        }
        publish()
        return true
    }

    // ---------------------------------------------------------------- motion

    fun handleGenericMotion(event: MotionEvent): Boolean {
        if (isGamepad(event.source) && event.action == MotionEvent.ACTION_MOVE) {
            val x = axis(event, MotionEvent.AXIS_X)
            val hat = axis(event, MotionEvent.AXIS_HAT_X)
            val rt = maxOf(axis(event, MotionEvent.AXIS_RTRIGGER), axis(event, MotionEvent.AXIS_GAS))
            val lt = maxOf(axis(event, MotionEvent.AXIS_LTRIGGER), axis(event, MotionEvent.AXIS_BRAKE))
            if (abs(x) > 0.25f || abs(hat) > 0.5f || rt > 0.2f || lt > 0.2f) use(InputSource.GAMEPAD)
            if (!raceCapture) return false
            padStickX = shapeStick(x)
            padHatX = if (abs(hat) > 0.5f) sign(hat) else 0f
            padGasAxis = rt
            padBrakeAxis = lt
            publish()
            return true
        }
        if (isMouse(event)) {
            use(InputSource.KEYBOARD_MOUSE)
            if (!raceCapture) return false
            if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS || event.actionMasked == MotionEvent.ACTION_BUTTON_RELEASE) {
                mouseItem = event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)
                mouseDrift = event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)
                if (!mouseCaptured) setPointerCapture(true)
                publish()
                return true
            }
        }
        return false
    }

    /** Mouse clicks during a race (they arrive on the touch path): LMB item, RMB drift. */
    fun handleMouseButtons(event: MotionEvent): Boolean {
        use(InputSource.KEYBOARD_MOUSE)
        if (!raceCapture) return false
        mouseItem = (event.buttonState and MotionEvent.BUTTON_PRIMARY) != 0
        mouseDrift = (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0
        if (!mouseCaptured) setPointerCapture(true)
        publish()
        return true
    }

    /** Relative mouse motion while the pointer is captured: a virtual steering wheel. */
    fun handleCapturedPointer(event: MotionEvent): Boolean {
        if (!raceCapture) return false
        use(InputSource.KEYBOARD_MOUSE)
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            mouseWheel = (mouseWheel + event.x * MOUSE_STEER_PER_PX).coerceIn(-1f, 1f)
        }
        mouseItem = event.isButtonPressed(MotionEvent.BUTTON_PRIMARY)
        mouseDrift = event.isButtonPressed(MotionEvent.BUTTON_SECONDARY)
        publish()
        return true
    }

    /** Called every frame while racing: lets the mouse wheel drift back to centre. */
    fun tick(dt: Float) {
        if (!raceCapture || !mouseCaptured || mouseWheel == 0f) return
        val back = MOUSE_RECENTRE_PER_SEC * dt
        mouseWheel = if (abs(mouseWheel) <= back) 0f else mouseWheel - sign(mouseWheel) * back
        publish()
    }

    // ------------------------------------------------------------- mapping

    private fun publish() {
        if (_source.value == InputSource.TOUCH) return
        val digitalSteer = (if (kRight || padDpadRight) 1f else 0f) - (if (kLeft || padDpadLeft) 1f else 0f) + padHatX
        val analog = if (abs(padStickX) > 0f) padStickX else mouseWheel
        val steer: Float
        val digital: Boolean
        if (abs(analog) > 0.001f && digitalSteer == 0f) { steer = analog; digital = false }
        else { steer = digitalSteer.coerceIn(-1f, 1f); digital = true }
        input.updateSteer(steer, digital)
        input.gasPressed = kGas || padGasBtn || padGasAxis > 0.25f
        input.brakePressed = kBrake || padBrakeBtn || padBrakeAxis > 0.35f
        input.updateDrift(kDrift || padDrift || padDriftAxis || mouseDrift)
        input.updateItemHold(kItem || padItem || mouseItem)
    }

    private fun releaseAll() {
        kLeft = false; kRight = false; kGas = false; kBrake = false; kDrift = false; kItem = false
        padStickX = 0f; padHatX = 0f; padGasAxis = 0f; padBrakeAxis = 0f
        padGasBtn = false; padBrakeBtn = false; padDrift = false; padDriftAxis = false; padItem = false
        padDpadLeft = false; padDpadRight = false
        mouseDrift = false; mouseItem = false; mouseWheel = 0f
        input.updateSteer(0f)
        input.gasPressed = false
        input.brakePressed = false
        input.updateDrift(false)
        input.updateItemHold(false)
        if (mouseCaptured) setPointerCapture(false)
    }

    private fun setPointerCapture(on: Boolean) {
        mouseCaptured = on
        onWantPointerCapture?.invoke(on)
    }

    /** Menus: gamepad A/B -> confirm/back so focus navigation works everywhere. */
    fun translateForMenus(event: KeyEvent): KeyEvent? {
        if (raceCapture) return null
        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_ESCAPE -> KeyEvent.KEYCODE_BACK
            else -> return null
        }
        return KeyEvent(event.downTime, event.eventTime, event.action, mapped, event.repeatCount, event.metaState,
            event.deviceId, event.scanCode, event.flags, InputDevice.SOURCE_DPAD)
    }

    companion object {
        const val MOUSE_STEER_PER_PX = 0.0045f
        const val MOUSE_RECENTRE_PER_SEC = 0.9f

        private val PAD_KEYS = setOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER
        )
        private val KEYBOARD_KEYS = setOf(
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_S,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_P
        )

        /** Keys the game maps. Everything else (volume, back, media, letters while typing) is ignored. */
        fun isDrivingKey(code: Int, isPad: Boolean): Boolean = if (isPad) code in PAD_KEYS else code in KEYBOARD_KEYS

        fun isGamepad(source: Int): Boolean =
            (source and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (source and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK

        fun isMouse(event: MotionEvent): Boolean =
            (event.source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
                event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE

        /** Deadzone + slight curve so small stick wobble doesn't weave the kart. */
        fun shapeStick(x: Float): Float {
            val dz = 0.14f
            val a = abs(x)
            if (a < dz) return 0f
            val n = ((a - dz) / (1f - dz)).coerceIn(0f, 1f)
            return sign(x) * (n * (0.55f + 0.45f * n))
        }

        private fun axis(e: MotionEvent, a: Int): Float = e.getAxisValue(a)
    }
}
