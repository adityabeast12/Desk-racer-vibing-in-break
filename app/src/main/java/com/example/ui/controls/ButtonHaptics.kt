package com.example.ui.controls

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

internal fun triggerButtonHaptic40ms(view: View) {
    try {
        val vibrator = view.context.getSystemService(Vibrator::class.java)
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40L, VibrationEffect.DEFAULT_AMPLITUDE))
                return
            }
        }
    } catch (_: Exception) {
    }
    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
}
