package org.fordem.indifi.ui.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fordem.indifi.ui.model.DeviceInfo
import org.fordem.indifi.ui.model.PeerPublicKeyEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

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
            Log.e("getOwnIp", "Error getting own IP", e)
            null
        }
    }
}

suspend fun getOwnIpAsP2p(context: Context): String? {
    return withContext(Dispatchers.IO) {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (intf.name.equals("wlan0", ignoreCase = true)) {
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            Log.d("getOwnIp", "Found IP on wlan0: ${addr.hostAddress}")
                            return@withContext addr.hostAddress
                        }
                    }
                }
            }
            Log.e("getOwnIp", "No valid IP found on wlan0")
            null
        } catch (e: Exception) {
            Log.e("getOwnIp", "Error getting own IP", e)
            null
        }
    }
}

fun getHotspotGatewayIP(context: Context): String? {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

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

//                Constants.ipLcGo = ip
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
            put("wfdIp", it.wfdIp)
            put("lcIp", it.lcIp)
            put("androidId", it.androidId)
            put("groupId", it.groupId)
            put("isGroupOwner", it.isGroupOwner)
            put("isRelayDevice", it.isRelayDevice)
            put("timestamp", it.timestamp)
            put("base64Key", it.base64Key)
        })
    }

    return "DEVICE_LIST:$jsonArray"
}

fun buildJsonForPeerKeys(peerList: List<PeerPublicKeyEntity>): String {
    val jsonArray = JSONArray()
    peerList.forEach {
        jsonArray.put(JSONObject().apply {
            put("ip", it.ip)
            put("base64Key", it.base64Key)
        })
    }

//    for (peer in peerList) {
//        val obj = JSONObject()
//        obj.put("ip", peer.ip)
//        obj.put("base64Key", peer.base64Key)
//        jsonArray.put(obj)
//    }

//    val wrapper = JSONObject()
//    wrapper.put("type", "PEER_KEYS_LIST")
//    wrapper.put("keys", jsonArray)
//    return wrapper.toString()

    return "PEER_KEYS_LIST:$jsonArray"
}


fun buildWfdHelloMessage(
    androidId: String,
    name: String,
    wfdIp: String,
    ownPublicKeyBase64: String
): String {
    val json = JSONObject()
    json.put("type", "WFD_HELLO")
    json.put("name", name)
    json.put("wfdIp", wfdIp)
    json.put("lcIp", "")
    json.put("androidId", androidId)
    json.put("groupId", "")
    json.put("isGroupOwner", false)
    json.put("isRelayDevice", false)
    json.put("timestamp", System.currentTimeMillis())
    json.put("base64Key", ownPublicKeyBase64)
    return json.toString()
}


fun buildLCHelloMessage(lcIpGO: String, type: String, deviceArray: JSONArray): String {
    val json = JSONObject()
//    json.put("type", "LC_HELLO")
//    json.put("name", name)
//    json.put("ip", ip)
//    json.put("androidId", androidId)
//    json.put("isGroupOwner", false)
//    json.put("timestamp", System.currentTimeMillis())
//    return json.toString()

    json.apply {
        put("type", type)
        put("lcIpGO", lcIpGO)
        put("devices", deviceArray)
    }.toString()

    return json.toString()
}

fun getDeviceMacByIdentifier(context: Context, identifier: String): String? {
    val prefs = context.getSharedPreferences("connected_devices", Context.MODE_PRIVATE)
    val jsonString = prefs.getString("device_mac_map", "{}")
    val jsonObject = jsonString?.let { JSONObject(it) }

    return if (jsonObject!!.has(identifier)) jsonObject.getString(identifier) else null
}





