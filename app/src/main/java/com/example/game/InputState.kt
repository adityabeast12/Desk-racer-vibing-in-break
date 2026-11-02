package com.example.game

import java.util.concurrent.atomic.AtomicBoolean

data class TouchInputSnapshot(
    val steer: Float = 0f,          // -1f (left) .. +1f (right)
    val gasPressed: Boolean = false,
    val brakePressed: Boolean = false,
    val driftPressed: Boolean = false,
    val driftJustPressed: Boolean = false,
    val itemJustPressed: Boolean = false,
    val itemFireBackward: Boolean = false,
    val itemHeldDown: Boolean = false
)

/**
 * Part G3: Lock-free thread-safe input bridge written directly by touch controls / sensors on the UI thread
 * and read at 60 Hz by the background simulation thread without triggering Compose recomposition.
 * Includes an atomic press-edge queue so a quick <16ms tap between two 60 Hz ticks is never lost,
 * plus a 120 ms drift input buffer for landing hop/drift initiation.
 */
class InputState {
    @Volatile
    var steer: Float = 0f

    @Volatile
    var gasPressed: Boolean = false

    @Volatile
    var brakePressed: Boolean = false

    @Volatile
    var driftPressed: Boolean = false

    @Volatile
    var itemHeldDown: Boolean = false

    val itemHeldForShield: Boolean
        get() = itemHeldDown

    @Volatile
    var itemFireBackward: Boolean = false

    @Volatile
    private var driftBufferUntilMs: Long = 0L

    private val driftEdge = AtomicBoolean(false)
    private val itemEdge = AtomicBoolean(false)

    fun updateSteer(value: Float) {
        steer = value.coerceIn(-1f, 1f)
    }

    fun updateDrift(pressed: Boolean) {
        if (pressed && !driftPressed) {
            driftEdge.set(true)
            driftBufferUntilMs = System.currentTimeMillis() + 120L
        }
        driftPressed = pressed
    }

    fun triggerItem(fireBackward: Boolean = false) {
        itemFireBackward = fireBackward
        itemEdge.set(true)
    }

    fun updateItemHold(held: Boolean, fireBackward: Boolean = false) {
        if (held && !itemHeldDown) {
            itemFireBackward = fireBackward
            itemEdge.set(true)
        }
        itemHeldDown = held
    }

    fun consumeFrameInput(): TouchInputSnapshot {
        val nowMs = System.currentTimeMillis()
        val rawDriftEdge = driftEdge.getAndSet(false)
        val bufferedDriftEdge = rawDriftEdge || (driftPressed && nowMs <= driftBufferUntilMs)
        val iEdge = itemEdge.getAndSet(false)
        return TouchInputSnapshot(
            steer = steer,
            gasPressed = gasPressed,
            brakePressed = brakePressed,
            driftPressed = driftPressed,
            driftJustPressed = bufferedDriftEdge,
            itemJustPressed = iEdge,
            itemFireBackward = itemFireBackward,
            itemHeldDown = itemHeldDown
        )
    }

    fun reset() {
        steer = 0f
        gasPressed = false
        brakePressed = false
        driftPressed = false
        itemHeldDown = false
        itemFireBackward = false
        driftBufferUntilMs = 0L
        driftEdge.set(false)
        itemEdge.set(false)
    }
}
