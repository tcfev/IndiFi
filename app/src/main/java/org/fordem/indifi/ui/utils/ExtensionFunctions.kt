package org.fordem.indifi.ui.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fordem.indifi.ui.model.DeviceInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress


suspend fun getOwnIp(context: Context): String? {
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



