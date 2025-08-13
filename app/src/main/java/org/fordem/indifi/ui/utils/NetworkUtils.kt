package org.fordem.indifi.ui.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import androidx.appcompat.app.AppCompatActivity.WIFI_SERVICE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface


suspend fun getOwnIpAsGateway(context: Context): String? {
    return withContext(Dispatchers.IO) {
        try {
            // GO: Return hotspot gateway IP
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val gateway = wifiManager.connectionInfo.ipAddress

            if (gateway != 0) {
                InetAddress.getByAddress(
                    byteArrayOf(
                        (gateway and 0xFF).toByte(),
                        ((gateway shr 8) and 0xFF).toByte(),
                        ((gateway shr 16) and 0xFF).toByte(),
                        ((gateway shr 24) and 0xFF).toByte()
                    )
                ).hostAddress
            } else {
                Log.e("getOwnIp", "GO: Gateway is 0")
                null
            }
        } catch (e: Exception) {
            Log.e("getOwnIp", "Error getting IP", e)
            null
        }
    }
}

fun getHotspotGatewayIP(context: Context): String? {
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    var retries = 0
    while (retries < 5) {
        val dhcpInfo = wifiManager.dhcpInfo
        val gatewayInt = dhcpInfo?.gateway ?: 0

        if (gatewayInt != 0) {
            return try {
                val ip = InetAddress.getByAddress(
                    byteArrayOf(
                        (gatewayInt and 0xFF).toByte(),
                        ((gatewayInt shr 8) and 0xFF).toByte(),
                        ((gatewayInt shr 16) and 0xFF).toByte(),
                        ((gatewayInt shr 24) and 0xFF).toByte()
                    )
                ).hostAddress

                Constants.ipLcGo = ip
                return ip
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        Log.w("HELLO", "Gateway IP is 0, retrying... ($retries)")
        Thread.sleep(1000)
        retries++
    }

    Log.e("HELLO", "Failed to get a valid Gateway IP after retries.")
    return null
}

private fun ipToInt(ip: String): Int {
    val parts = ip.split(".")
    return (parts[3].toInt() shl 24) or (parts[2].toInt() shl 16) or (parts[1].toInt() shl 8) or parts[0].toInt()
}

fun getLocalIpAddress(context: Context): String? {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val linkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return null

    return linkProperties.linkAddresses
        .map { it.address.hostAddress }
        .find { it?.startsWith("192.") == true || it?.startsWith("172.") == true || it?.startsWith("10.") == true }
}

fun isGoViaLegacy(context: Context): Boolean {
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ipAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
    val dhcpInfo = wifiManager.dhcpInfo

    val hotspotEnabled = (ipAddress == "192.168.43.1" || dhcpInfo.gateway == ipToInt("192.168.43.1"))
    Log.d("HotspotCheck", "IP Address: $ipAddress, Gateway: ${Formatter.formatIpAddress(dhcpInfo.gateway)}, Hotspot Enabled: $hotspotEnabled")
    return hotspotEnabled
//    return (ipAddress == "192.168.43.1" || dhcpInfo.gateway == ipToInt("192.168.43.1"))
}

// TODO  : Check two versions of getLocalIpAddress, is one failing?
fun getLocalIpAddress(): String {
    val interfaces = NetworkInterface.getNetworkInterfaces()
    for (intf in interfaces) {
        val addrs = intf.inetAddresses
        for (addr in addrs) {
            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                Log.d("IP", "Local IP address: ${addr.hostAddress}")
                return addr.hostAddress!!
            }
        }
    }
    Log.d("IP", "Failed to get local IP address")
    return "0.0.0.0"
}

fun isWifiEnabled(context: Context): Boolean {
    val wifiManager =
        context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
    return wifiManager.isWifiEnabled
}


private fun isHotspotEnabled(context: Context): Boolean {
    return try {
        val wifiManager =
            context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
        method.isAccessible = true
        method.invoke(wifiManager) as Boolean
    } catch (e: Exception) {
        false
    }
}




