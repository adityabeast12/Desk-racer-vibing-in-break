@file:Suppress("unused")

package android.app

class ActivityManager {
    class ConfigurationInfo(val reqGlEsVersion: Int)
    class MemoryInfo {
        var totalMem: Long = drshim.Platform.totalMemoryBytes
        var availMem: Long = totalMem / 2
        var lowMemory: Boolean = false
    }
    /** EAGL on every supported iPhone offers OpenGL ES 3.0. */
    val deviceConfigurationInfo: ConfigurationInfo = ConfigurationInfo(0x30000)
    val isLowRamDevice: Boolean get() = drshim.Platform.totalMemoryBytes < 2_500_000_000L
    fun getMemoryInfo(info: MemoryInfo) {}
}
