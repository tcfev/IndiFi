package org.fordem.indifi.ui.utils

import android.content.Context
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.text.format.Formatter
import android.util.Log
import androidx.appcompat.app.AppCompatActivity.WIFI_SERVICE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.fordem.indifi.ui.db.DeviceInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress


//fun getHotspotGatewayIP(context: Context): String? {
//    try {
//        val wifiManager = context.getSystemService(WIFI_SERVICE) as WifiManager
//        var dhcpInfo: DhcpInfo
//        var gatewayInt: Int
//
//        // Wait for a valid IP lease (max 5 seconds)
//        var retries = 0
//        do {
//            dhcpInfo = wifiManager.dhcpInfo
//            gatewayInt = dhcpInfo.gateway
//            if (gatewayInt != 0) break
////            Thread.sleep(5000)
//            retries++
//        } while (retries < 10)
//
//        if (gatewayInt == 0) {
//            Log.e("HELLO", "No valid IP lease found.")
//        }
//        val ip = InetAddress.getByAddress(
//            byteArrayOf(
//                (gatewayInt and 0xFF).toByte(),
//                ((gatewayInt shr 8) and 0xFF).toByte(),
//                ((gatewayInt shr 16) and 0xFF).toByte(),
//                ((gatewayInt shr 24) and 0xFF).toByte()
//            )
//        ).hostAddress
//        Constants.ipLcGo = ip
//        return ip
//    } catch (e: Exception) {
//        e.printStackTrace()
//    }
//    return null
//}

//fun getOwnIp(context: Context, isGroupOwner: Boolean): String? {
//    return try {
//        if (isGroupOwner) {
//            // For GO: Fetch gateway IP (hotspot owner)
//            val wifiManager =
//                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
//            val dhcpInfo = wifiManager.dhcpInfo
//            val gateway = dhcpInfo?.gateway ?: 0
//            if (gateway != 0) {
//                InetAddress.getByAddress(
//                    byteArrayOf(
//                        (gateway and 0xFF).toByte(),
//                        ((gateway shr 8) and 0xFF).toByte(),
//                        ((gateway shr 16) and 0xFF).toByte(),
//                        ((gateway shr 24) and 0xFF).toByte()
//                    )
//                ).hostAddress
//            } else {
//                Log.e("getOwnIp", "GO: Gateway is 0")
//                null
//            }
//        } else {
//            // For GM: Get device-assigned IP
////            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
////                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
////                val network = cm.activeNetwork
////                val linkProps = cm.getLinkProperties(network)
////                val ip = linkProps?.linkAddresses?.firstOrNull { !it.address.isLoopbackAddress && it.address.hostAddress?.contains(":") == false }
////                ip?.address?.hostAddress
////            } else {
//            val wifiManager =
//                context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
//            val ipInt = wifiManager.connectionInfo.ipAddress
//            if (ipInt != 0) {
//                InetAddress.getByAddress(
//                    byteArrayOf(
//                        (ipInt and 0xFF).toByte(),
//                        ((ipInt shr 8) and 0xFF).toByte(),
//                        ((ipInt shr 16) and 0xFF).toByte(),
//                        ((ipInt shr 24) and 0xFF).toByte()
//                    )
//                ).hostAddress
//            } else null
////            }
//        }
//    } catch (e: Exception) {
//        Log.e("getOwnIp", "Error getting IP", e)
//        null
//    }
//}

suspend fun getOwnIp(context: Context): String? {
    return withContext(Dispatchers.IO) {
        try {
//            if (isGroupOwner) {
//                repeat(10) { attempt ->
//                    val wifiManager =
//                        context.getSystemService(Context.WIFI_SERVICE) as WifiManager
//                    val ipInt = wifiManager.connectionInfo.ipAddress
//                    if (ipInt != 0) {
//                        return@withContext InetAddress.getByAddress(
//                            byteArrayOf(
//                                (ipInt and 0xFF).toByte(),
//                                ((ipInt shr 8) and 0xFF).toByte(),
//                                ((ipInt shr 16) and 0xFF).toByte(),
//                                ((ipInt shr 24) and 0xFF).toByte()
//                            )
//                        ).hostAddress
//                    }
//                    Log.w("getOwnIp", "Attempt $attempt: IP still 0.0.0.0")
//                    delay(3000)
//                }
//                null


            // GO: Return hotspot gateway IP
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
//                val dhcpInfo = wifiManager.dhcpInfo
//                val gateway = dhcpInfo?.gateway ?: 0
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
//            } else {
//                // GM: Wait for a valid assigned IP
//                repeat(10) { attempt ->
//                    val wifiManager =
//                        context.getSystemService(Context.WIFI_SERVICE) as WifiManager
//                    val ipInt = wifiManager.connectionInfo.ipAddress
//                    if (ipInt != 0) {
//                        return@withContext InetAddress.getByAddress(
//                            byteArrayOf(
//                                (ipInt and 0xFF).toByte(),
//                                ((ipInt shr 8) and 0xFF).toByte(),
//                                ((ipInt shr 16) and 0xFF).toByte(),
//                                ((ipInt shr 24) and 0xFF).toByte()
//                            )
//                        ).hostAddress
//                    }
//                    Log.w("getOwnIp", "Attempt $attempt: IP still 0.0.0.0")
//                    delay(3000)
//                }
//                Log.e("getOwnIp", "GM: Failed to get IP after retries")
//                null
//            }
        } catch (e: Exception) {
            Log.e("getOwnIp", "Error getting IP", e)
            null
        }
    }
}


//fun getOwnIp(context: Context): String? {
//    try {
//        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
//
//        var ip: String?
//        var attempts = 0
//
//        while (attempts < 10) {
//            val ipInt = wifiManager.connectionInfo.ipAddress
//            ip = InetAddress.getByAddress(
//                byteArrayOf(
//                    (ipInt and 0xFF).toByte(),
//                    ((ipInt shr 8) and 0xFF).toByte(),
//                    ((ipInt shr 16) and 0xFF).toByte(),
//                    ((ipInt shr 24) and 0xFF).toByte()
//                )
//            ).hostAddress
//
//            if (!ip.isNullOrBlank() && ip != "0.0.0.0") {
//                return ip
//            }
//
//            Thread.sleep(1000)
//            attempts++
//        }
//
//        Log.e("OwnIP", "Failed to get valid IP after $attempts attempts.")
//    } catch (e: Exception) {
//        Log.e("OwnIP", "Failed to get own IP", e)
//    }
//
//    return null
//}

//fun getHotspotGatewayIP(context: Context): String? {
//    return try {
//        val wifiManager =
//            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
//        val dhcpInfo = wifiManager.dhcpInfo
//
//        if (dhcpInfo == null || dhcpInfo.gateway == 0) {
//            Log.e("HELLO", "Gateway IP is 0 or DHCP info unavailable")
//            return null
//        }
//
//        val gatewayInt = dhcpInfo.gateway
//        val ip = InetAddress.getByAddress(
//            byteArrayOf(
//                (gatewayInt and 0xFF).toByte(),
//                ((gatewayInt shr 8) and 0xFF).toByte(),
//                ((gatewayInt shr 16) and 0xFF).toByte(),
//                ((gatewayInt shr 24) and 0xFF).toByte()
//            )
//        ).hostAddress
//
//        Constants.ipLcGo = ip
//        ip
//    } catch (e: Exception) {
//        e.printStackTrace()
//        null
//    }
//}

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
//        delay(1000)
        retries++
    }

    Log.e("HELLO", "Failed to get a valid Gateway IP after retries.")
    return null
}

//fun isHotspotEnabled(context: Context): Boolean {
//    return try {
//        val wifiManager =
//            context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
//        val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
//        method.isAccessible = true
//        method.invoke(wifiManager) as Boolean
//    } catch (e: Exception) {
//        false
//    }
//}

fun isHotspotEnabled(context: Context): Boolean {
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ipAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
    val dhcpInfo = wifiManager.dhcpInfo
    return (ipAddress == "192.168.43.1" || dhcpInfo.gateway == ipToInt("192.168.43.1"))
}

private fun ipToInt(ip: String): Int {
    val parts = ip.split(".")
    return (parts[3].toInt() shl 24) or (parts[2].toInt() shl 16) or (parts[1].toInt() shl 8) or parts[0].toInt()
}

fun buildJsonForDeviceList(deviceList: List<DeviceInfo>): String {
    val jsonArray = JSONArray()
    deviceList.forEach {
        jsonArray.put(JSONObject().apply {
            put("deviceId", it.deviceId)
            put("name", it.name)
            put("ip", it.ip)
            put("isGroupOwner", it.isGroupOwner)
            put("timestamp", it.timestamp)
        })
    }

    return "DEVICE_LIST:$jsonArray"
}



