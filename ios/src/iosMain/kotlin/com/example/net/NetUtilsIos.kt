package com.example.net

import android.content.Context
import android.net.wifi.WifiManager
import java.net.InetAddress
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.AF_INET
import platform.posix.IFF_LOOPBACK
import platform.posix.IFF_UP
import platform.posix.sockaddr_in

data class LocalSubnetInfo(
    val interfaceName: String,
    val ipAddress: String,
    val broadcastAddress: InetAddress,
    val isHotspotOrWlanPreferred: Boolean
)

/**
 * iOS NetUtils: same API as Android's, built on getifaddrs.
 *
 * iOS only lets apps with Apple's multicast entitlement send/receive broadcasts, so client discovery
 * also probes every host on the local /24 by unicast. An Android host answers a probe with a unicast
 * beacon, so the iPhone finds the room on the same Wi-Fi or on the Android phone's hotspot.
 */
object NetUtils {
    private var cache: List<LocalSubnetInfo> = emptyList()
    private var cacheAtMs = 0L
    private class Iface(val name: String, val ip: UInt, val mask: UInt)

    private fun ipBytes(s: UInt) = byteArrayOf(s.toByte(), (s shr 8).toByte(), (s shr 16).toByte(), (s shr 24).toByte())

    private fun interfaces(): List<Iface> {
        val out = ArrayList<Iface>()
        memScoped {
            val ifap = alloc<CPointerVar<ifaddrs>>()
            if (getifaddrs(ifap.ptr) != 0) return@memScoped
            try {
                var cur = ifap.value
                while (cur != null) {
                    val ifa = cur.pointed
                    val flags = ifa.ifa_flags
                    val addr = ifa.ifa_addr
                    if ((flags and IFF_UP.toUInt()) != 0u && (flags and IFF_LOOPBACK.toUInt()) == 0u && addr != null &&
                        addr.pointed.sa_family.toInt() == AF_INET
                    ) {
                        val ip = addr.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr
                        val mask = ifa.ifa_netmask?.reinterpret<sockaddr_in>()?.pointed?.sin_addr?.s_addr ?: 0x00FFFFFFu
                        val name = ifa.ifa_name?.toKString() ?: ""
                        val first = (ip and 0xFFu).toInt()
                        if (first != 169 && first != 127) out.add(Iface(name, ip, mask))
                    }
                    cur = ifa.ifa_next
                }
            } finally {
                freeifaddrs(ifap.value)
            }
        }
        return out
    }

    fun detectActiveSubnets(): List<LocalSubnetInfo> {
        val now = drshim.JSystem.currentTimeMillis()
        if (now - cacheAtMs < 2000L && cache.isNotEmpty()) return cache
        val list = interfaces()
            // en0 = Wi-Fi, bridge100 = this iPhone's own Personal Hotspot; skip cellular (pdp_ip*) and VPN tunnels
            .filter { !it.name.startsWith("pdp_ip") && !it.name.startsWith("utun") && !it.name.startsWith("ipsec") }
            .map { f ->
                val bcast = f.ip or f.mask.inv()
                LocalSubnetInfo(
                    interfaceName = f.name,
                    ipAddress = InetAddress.getByAddress(ipBytes(f.ip)).hostAddress,
                    broadcastAddress = InetAddress.getByAddress(ipBytes(bcast)),
                    isHotspotOrWlanPreferred = f.name.startsWith("en") || f.name.startsWith("bridge")
                )
            }
            .sortedByDescending { it.isHotspotOrWlanPreferred }
        cache = list
        cacheAtMs = now
        return list
    }

    fun detectPrimaryLocalIp(): String {
        val s = detectActiveSubnets()
        return (s.firstOrNull { it.interfaceName.startsWith("bridge") } ?: s.firstOrNull { it.interfaceName == "en0" } ?: s.firstOrNull())
            ?.ipAddress ?: "172.20.10.1"
    }

    fun getAllBroadcastTargets(gatewayIp: String? = null): List<InetAddress> {
        val set = LinkedHashSet<InetAddress>()
        if (!gatewayIp.isNullOrBlank()) runCatching { set.add(InetAddress.getByName(gatewayIp)) }
        val subnets = detectActiveSubnets()
        for (s in subnets) set.add(s.broadcastAddress)
        runCatching { set.add(InetAddress.getByName("255.255.255.255")) }
        if (gatewayIp != null) {
            // client discovery: unicast sweep of our /24 (works without the multicast entitlement)
            for (s in subnets.filter { it.isHotspotOrWlanPreferred }.take(1)) {
                val p = s.ipAddress.split('.')
                if (p.size != 4) continue
                for (h in 1..254) {
                    val ip = "${p[0]}.${p[1]}.${p[2]}.$h"
                    if (ip != s.ipAddress) runCatching { set.add(InetAddress.getByName(ip)) }
                }
            }
        }
        return set.toList()
    }

    /** iOS hides the default route; a phone hotspot / home router is almost always .1 of our subnet. */
    fun detectWifiGatewayIp(context: Context): String {
        val s = detectActiveSubnets().firstOrNull { it.interfaceName == "en0" } ?: detectActiveSubnets().firstOrNull()
        val parts = s?.ipAddress?.split('.')
        if (parts != null && parts.size == 4) return "${parts[0]}.${parts[1]}.${parts[2]}.1"
        return "192.168.43.1"
    }

    fun acquireMulticastLock(context: Context): WifiManager.MulticastLock? = null
}
