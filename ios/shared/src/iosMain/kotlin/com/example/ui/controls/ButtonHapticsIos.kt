package com.example.ui.controls

import android.view.View
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

private val buttonTap by lazy { UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid) }

/** Pedal / item press: a crisp Taptic tick (Android: 40 ms vibration). Called on the main thread. */
internal fun triggerButtonHaptic40ms(view: View) {
    buttonTap.impactOccurred()
}
