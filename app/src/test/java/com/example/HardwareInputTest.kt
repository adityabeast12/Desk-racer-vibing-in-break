package com.example

import android.view.InputDevice
import android.view.KeyEvent
import com.example.game.HardwareInput
import com.example.game.InputSource
import com.example.game.InputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HardwareInputTest {
    private fun key(action: Int, code: Int, source: Int = InputDevice.SOURCE_KEYBOARD) =
        KeyEvent(0L, 0L, action, code, 0, 0, 1, 0, 0, source)

    @Test
    fun `keyboard drives the kart only during a race and switches the HUD`() {
        val input = InputState()
        val hw = HardwareInput(input)
        assertFalse(hw.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_D)))
        assertEquals(InputSource.KEYBOARD_MOUSE, hw.source.value)
        hw.raceCapture = true
        assertTrue(hw.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_D)))
        assertTrue(hw.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_W)))
        assertTrue(hw.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)))
        val snap = input.consumeFrameInput()
        assertEquals(1f, snap.steer)
        assertTrue(snap.steerIsDigital)
        assertTrue(snap.gasPressed)
        assertTrue(snap.driftJustPressed)
        hw.handleKey(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_D))
        assertEquals(0f, input.consumeFrameInput().steer)
        hw.onTouchScreen()
        assertEquals(InputSource.TOUCH, hw.source.value)
        assertFalse(input.gasPressed) // released on switch back to touch
    }

    @Test
    fun `gamepad buttons and menu translation`() {
        val input = InputState()
        val hw = HardwareInput(input)
        val a = key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, InputDevice.SOURCE_GAMEPAD)
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, hw.translateForMenus(a)?.keyCode)
        hw.raceCapture = true
        assertTrue(hw.handleKey(a))
        assertEquals(InputSource.GAMEPAD, hw.source.value)
        assertTrue(input.gasPressed)
        hw.handleKey(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_START, InputDevice.SOURCE_GAMEPAD))
        assertEquals(1, hw.pauseRequests.value)
        hw.raceCapture = false
        assertFalse(input.gasPressed)
    }

    @Test
    fun `volume back and soft keyboard keys never hide the touch HUD`() {
        val input = InputState()
        val hw = HardwareInput(input)
        hw.raceCapture = true
        for (code in listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_CAMERA)) {
            assertFalse(hw.handleKey(key(KeyEvent.ACTION_DOWN, code)))
            assertEquals(InputSource.TOUCH, hw.source.value)
        }
        val soft = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_W, 0, 0,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD)
        assertFalse(hw.handleKey(soft))
        assertEquals(InputSource.TOUCH, hw.source.value)
    }

    @Test
    fun `stick deadzone and curve`() {
        assertEquals(0f, HardwareInput.shapeStick(0.1f))
        assertEquals(1f, HardwareInput.shapeStick(1f), 1e-4f)
        assertTrue(HardwareInput.shapeStick(0.5f) in 0.15f..0.45f)
    }
}
