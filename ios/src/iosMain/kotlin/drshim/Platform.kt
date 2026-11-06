package drshim

import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSError
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIScreen

object Platform {
    /** Updated by the Compose root from the window size (dp). */
    val configuration = android.content.res.Configuration().apply {
        screenWidthDp = 844
        screenHeightDp = 390
    }
    val screenScale: Float get() = UIScreen.mainScreen.scale.toFloat()
    val totalMemoryBytes: Long get() = NSProcessInfo.processInfo.physicalMemory.toLong()

    fun openSettingsFor(action: String?) {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

object AudioSession {
    private var active = false
    fun ensureActive() {
        if (active) return
        active = true
        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            val s = AVAudioSession.sharedInstance()
            s.setCategory(AVAudioSessionCategoryAmbient, error = err.ptr)
            s.setActive(true, error = err.ptr)
        }
    }
}

