package com.example.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

data class LocalSubnetInfo(
    val interfaceName: String,
    val ipAddress: String,
    val broadcastAddress: InetAddress,
    val isHotspotOrWlanPreferred: Boolean
)

object NetUtils {

    /**
     * Enumerates active IPv4 interfaces, prioritizing hotspot/Wi-Fi interfaces
     * (`wlan`, `ap`, `swlan`, `softap`, `rndis`) over mobile data (`rmnet`, `ccmni`),
     * and computes each interface's real subnet broadcast address from InterfaceAddress.broadcast
     * or (ip | ~netmask).
     */
    fun detectActiveSubnets(): List<LocalSubnetInfo> {
        val results = ArrayList<LocalSubnetInfo>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val name = nif.name.lowercase()
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("dummy")) {
                    continue
                }
                val preferred = name.startsWith("ap") ||
                    name.startsWith("softap") ||
                    name.startsWith("swlan") ||
                    name.startsWith("wlan") ||
                    name.startsWith("rndis")

                for (ia in nif.interfaceAddresses) {
                    val addr = ia.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ipStr = addr.hostAddress ?: continue
                        val bcast = ia.broadcast ?: computeBroadcast(addr, ia.networkPrefixLength)
                        if (bcast != null) {
                            results.add(
                                LocalSubnetInfo(
                                    interfaceName = name,
                                    ipAddress = ipStr,
                                    broadcastAddress = bcast,
                                    isHotspotOrWlanPreferred = preferred
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return results.sortedByDescending { it.isHotspotOrWlanPreferred }
    }

    fun detectPrimaryLocalIp(): String {
        val subnets = detectActiveSubnets()
        // Prefer ap/softap/swlan first if hosting hotspot, then wlan
        val hotspotFirst = subnets.firstOrNull {
            it.interfaceName.startsWith("ap") ||
                it.interfaceName.startsWith("softap") ||
                it.interfaceName.startsWith("swlan") ||
                it.ipAddress.startsWith("192.168.43.") ||
                it.ipAddress.startsWith("192.168.137.")
        }
        return hotspotFirst?.ipAddress ?: subnets.firstOrNull()?.ipAddress ?: "192.168.43.1"
    }

    fun getAllBroadcastTargets(gatewayIp: String? = null): List<InetAddress> {
        val set = LinkedHashSet<InetAddress>()
        for (s in detectActiveSubnets()) {
            set.add(s.broadcastAddress)
        }
        runCatching { set.add(InetAddress.getByName("255.255.255.255")) }
        if (!gatewayIp.isNullOrBlank()) {
            runCatching { set.add(InetAddress.getByName(gatewayIp)) }
        }
        return set.toList()
    }

    /**
     * Reads the active Wi-Fi gateway IP via ConnectivityManager -> LinkProperties -> default route gateway.
     * When the host phone is running a mobile hotspot, the gateway IP IS the host phone's IP.
     */
    fun detectWifiGatewayIp(context: Context): String {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            val linkProps = if (activeNet != null) cm.getLinkProperties(activeNet) else null
            if (linkProps != null) {
                for (route in linkProps.routes) {
                    val gw = route.gateway
                    if (gw is Inet4Address && !gw.isLoopbackAddress) {
                        val ip = gw.hostAddress
                        if (!ip.isNullOrBlank() && ip != "0.0.0.0") {
                            return ip
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
        // Fallback: infer .1 from primary local IPv4 if on a /24 subnet
        val myIp = detectPrimaryLocalIp()
        val parts = myIp.split(".")
        if (parts.size == 4) {
            return "${parts[0]}.${parts[1]}.${parts[2]}.1"
        }
        return "192.168.43.1"
    }

    fun acquireMulticastLock(context: Context): WifiManager.MulticastLock? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wm?.createMulticastLock("DeskRacerLanMulticast")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun computeBroadcast(addr: Inet4Address, prefixLen: Short): InetAddress? {
        return try {
            val raw = addr.address
            val ipInt = ((raw[0].toInt() and 0xFF) shl 24) or
                ((raw[1].toInt() and 0xFF) shl 16) or
                ((raw[2].toInt() and 0xFF) shl 8) or
                (raw[3].toInt() and 0xFF)
            val shift = (32 - prefixLen.toInt().coerceIn(8, 30))
            val mask = -1 shl shift
            val bcastInt = ipInt or mask.inv()
            val out = byteArrayOf(
                ((bcastInt ushr 24) and 0xFF).toByte(),
                ((bcastInt ushr 16) and 0xFF).toByte(),
                ((bcastInt ushr 8) and 0xFF).toByte(),
                (bcastInt and 0xFF).toByte()
            )
            InetAddress.getByAddress(out)
        } catch (_: Exception) {
            null
        }
    }
}
