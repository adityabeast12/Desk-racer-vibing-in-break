@file:Suppress("unused")

package android.view

import android.content.Context
import platform.UIKit.UISelectionFeedbackGenerator
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

object HapticFeedbackConstants {
    const val LONG_PRESS = 0
    const val VIRTUAL_KEY = 1
    const val KEYBOARD_TAP = 3
    const val CLOCK_TICK = 4
    const val CONTEXT_CLICK = 6
    const val CONFIRM = 16
    const val REJECT = 17
}

open class View(val context: Context) {
    open val width: Int get() = (drshim.Platform.configuration.screenWidthDp * drshim.Platform.screenScale).toInt()
    open val height: Int get() = (drshim.Platform.configuration.screenHeightDp * drshim.Platform.screenScale).toInt()
    var systemGestureExclusionRects: List<android.graphics.Rect> = emptyList()
    var keepScreenOn: Boolean = false
    var isHapticFeedbackEnabled: Boolean = true

    fun post(action: drshim.JRunnable): Boolean {
        dispatch_async(dispatch_get_main_queue()) { action.run() }
        return true
    }

    fun performHapticFeedback(feedbackConstant: Int): Boolean {
        dispatch_async(dispatch_get_main_queue()) { UISelectionFeedbackGenerator().selectionChanged() }
        return true
    }
}
