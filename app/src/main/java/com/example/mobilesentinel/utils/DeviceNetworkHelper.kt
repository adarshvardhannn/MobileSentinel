package com.example.mobilesentinel.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class NetworkInfo(
    val ipAddress: String,
    val interfaceName: String,
    val type: String // "WiFi", "Mobile", "Ethernet", "VPN", "Loopback", "Other"
)

@Singleton
class DeviceNetworkHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Returns all available IP addresses on the device grouped by interface.
     * Prioritizes WiFi, then Mobile, then others.
     */
    fun getAllNetworkAddresses(): List<NetworkInfo> {
        val addresses = mutableListOf<NetworkInfo>()

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp) continue

                val type = getInterfaceType(intf.name)
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        addresses.add(
                            NetworkInfo(
                                ipAddress = addr.hostAddress ?: "",
                                interfaceName = intf.displayName,
                                type = type
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback: try WifiManager
            val wifiIp = getWifiIpAddress()
            if (wifiIp != null) {
                addresses.add(NetworkInfo(wifiIp, "wlan0", "WiFi"))
            }
        }

        // Always add loopback as fallback
        addresses.add(NetworkInfo("127.0.0.1", "lo", "Loopback"))

        // Sort: WiFi first, then Mobile, then Ethernet, then Others, Loopback last
        return addresses.sortedBy { info ->
            when (info.type) {
                "WiFi" -> 0
                "Mobile" -> 1
                "Ethernet" -> 2
                "VPN" -> 3
                "Other" -> 4
                "Loopback" -> 5
                else -> 6
            }
        }
    }

    /**
     * Returns the primary (best) IP address of the device.
     * Prefers WiFi > Mobile > any other non-loopback address > 127.0.0.1
     */
    fun getPrimaryIpAddress(): String {
        val addresses = getAllNetworkAddresses()
        return addresses.firstOrNull { it.type != "Loopback" }?.ipAddress
            ?: "127.0.0.1"
    }

    /**
     * Returns WiFi IP address using WifiManager (legacy but reliable).
     */
    fun getWifiIpAddress(): String? {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return null
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            if (ipInt == 0) return null

            return String.format(
                Locale.US,
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (_: Exception) {
            return null
        }
    }

    /**
     * Checks if the device is currently connected to any network.
     */
    fun isConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Returns the current connection type as a human-readable string.
     */
    fun getConnectionType(): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return "Unknown"
        val network = connectivityManager.activeNetwork ?: return "Not Connected"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
    }

    private fun getInterfaceType(interfaceName: String): String {
        return when {
            interfaceName.startsWith("wlan") || interfaceName.startsWith("wifi") -> "WiFi"
            interfaceName.startsWith("rmnet") || interfaceName.startsWith("ccmni") ||
                    interfaceName.startsWith("pdp") -> "Mobile"
            interfaceName.startsWith("eth") -> "Ethernet"
            interfaceName.startsWith("tun") || interfaceName.startsWith("pptp") -> "VPN"
            interfaceName == "lo" -> "Loopback"
            else -> "Other"
        }
    }
}

