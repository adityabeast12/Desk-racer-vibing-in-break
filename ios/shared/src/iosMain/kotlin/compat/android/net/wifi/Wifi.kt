@file:Suppress("unused")

package android.net.wifi

class WifiManager {
    /** iOS needs no multicast lock for unicast discovery; kept so LanDiscovery compiles unchanged. */
    class MulticastLock {
        var isHeld: Boolean = false
            private set
        fun setReferenceCounted(refCounted: Boolean) {}
        fun acquire() { isHeld = true }
        fun release() { isHeld = false }
    }
    fun createMulticastLock(tag: String): MulticastLock = MulticastLock()
}
