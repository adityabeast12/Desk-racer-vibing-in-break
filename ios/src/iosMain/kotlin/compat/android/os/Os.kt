@file:Suppress("unused")

package android.os

import platform.UIKit.UIDevice
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

object Build {
    object VERSION {
        /** Report a modern API level so the code takes its current (not legacy) paths; every API it touches is shimmed. */
        const val SDK_INT: Int = 33
        const val RELEASE: String = "iOS"
    }
    object VERSION_CODES {
        const val LOLLIPOP = 21
        const val M = 23
        const val N = 24
        const val O = 26
        const val O_MR1 = 27
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val S_V2 = 32
        const val TIRAMISU = 33
        const val UPSIDE_DOWN_CAKE = 34
    }
    val MODEL: String get() = UIDevice.currentDevice.model
    const val MANUFACTURER: String = "Apple"
    const val BRAND: String = "Apple"
    const val DEVICE: String = "iphone"
    const val PRODUCT: String = "ios"
    const val HARDWARE: String = "apple"
    val FINGERPRINT: String get() = "apple/ios/" + UIDevice.currentDevice.systemVersion
    val SUPPORTED_ABIS: Array<String> = arrayOf("arm64-v8a")
}

object SystemClock {
    fun uptimeMillis(): Long = drshim.JSystem.nanoTime() / 1_000_000L
    fun elapsedRealtime(): Long = drshim.JSystem.nanoTime() / 1_000_000L
    fun elapsedRealtimeNanos(): Long = drshim.JSystem.nanoTime()
    fun sleep(ms: Long) = drshim.JThread.sleep(ms)
}

class VibrationEffect private constructor(val durationMs: Long, val amplitude: Int) {
    companion object {
        const val DEFAULT_AMPLITUDE = -1
        fun createOneShot(milliseconds: Long, amplitude: Int): VibrationEffect = VibrationEffect(milliseconds, amplitude)
        fun createWaveform(timings: LongArray, repeat: Int): VibrationEffect = VibrationEffect(timings.sum(), DEFAULT_AMPLITUDE)
    }
}

/** Taptic Engine stand-in for the Android vibrator: short pulses map to light/medium/heavy impacts. */
open class Vibrator internal constructor() {
    fun hasVibrator(): Boolean = true
    fun hasAmplitudeControl(): Boolean = false
    fun vibrate(effect: VibrationEffect) = pulse(effect.durationMs)
    fun vibrate(ms: Long) = pulse(ms)
    fun cancel() {}

    private fun pulse(ms: Long) {
        dispatch_async(dispatch_get_main_queue()) {
            val style = when {
                ms >= 55L -> UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy
                ms >= 30L -> UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium
                else -> UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
            }
            val g = UIImpactFeedbackGenerator(style = style)
            g.impactOccurred()
        }
    }

    companion object {
        val default: Vibrator by lazy { Vibrator() }
    }
}

class VibratorManager {
    val defaultVibrator: Vibrator get() = Vibrator.default
    fun getDefaultVibrator(): Vibrator = Vibrator.default
}

object Environment {
    const val DIRECTORY_PICTURES = "Pictures"
}

class Bundle
