package org.fordem.indifi.ui.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

fun getP2pNetwork(context: Context): Network? {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    for (network in connectivityManager.allNetworks) {
        val linkProps = connectivityManager.getLinkProperties(network)
        val iface = linkProps?.interfaceName
        if (iface != null && iface.startsWith("p2p")) {
            Log.d("NetworkSelect", "Detected P2P interface: $iface")
            return network
        }
    }
    Log.w("NetworkSelect", "No P2P interface found")
    return null
}

fun getWifiNetwork(context: Context): Network? {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    for (network in connectivityManager.allNetworks) {
        val linkProps = connectivityManager.getLinkProperties(network)
        val iface = linkProps?.interfaceName
        if (iface != null && iface.startsWith("wlan")) {
            Log.d("NetworkSelect", "Detected Wi-Fi interface: $iface")
            return network
        }
    }
    Log.w("NetworkSelect", "No Wi-Fi (wlan0) interface found")
    return null
}

//fun bindProcessToP2pNetwork(context: Context): Boolean {
//    val interfaces = NetworkInterface.getNetworkInterfaces()
//    val list = interfaces.toList()
//    //go through the available interfaces
//    for (interF in list) {
//        if (interF.name.equals("wlan0")) {
//            //look into the interface's ipaddresses (ipv4,ipv6)
//            interF.inetAddresses.toList()[0].hostAddress
//        }
//    }
//
////    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
////    val networks = cm.allNetworks
////    for (network in networks) {
////        val caps = cm.getNetworkCapabilities(network)
////        if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
////            val linkProperties = cm.getLinkProperties(network)
////            if (linkProperties?.interfaceName?.startsWith("p2p") == true) {
////                cm.bindProcessToNetwork(network)
////                Log.d("Bind", "Process bound to p2p")
////                return true
////            }
////        }
////    }
////    Log.w("Bind", "Failed to bind to p2p0")
//    return false
//}

fun bindProcessToP2pNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networks = cm.allNetworks
    for (network in networks) {
        val linkProperties = cm.getLinkProperties(network)
        val iface = linkProperties?.interfaceName

        if (iface != null && iface.startsWith("p2p0")) {
            val result = cm.bindProcessToNetwork(network)
            Log.d("Bind", "Process bound to $iface: $result")
            return result
        }
    }

    Log.w("Bind", "Failed to bind to any p2p interface")
    return false
}

fun bindProcessToWifiNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networks = cm.allNetworks
    for (network in networks) {
        val caps = cm.getNetworkCapabilities(network)
        if (caps != null &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        ) {
            val linkProperties = cm.getLinkProperties(network)
            if (linkProperties?.interfaceName == "wlan0") {
                cm.bindProcessToNetwork(network)
                Log.d("Bind", "Process bound to wlan0")
                return true
            }
        }
    }
    Log.w("Bind", "Failed to bind to wlan0")
    return false
}

fun getInterfaceIp(interfaceName: String): InetAddress? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (interFace in interfaces) {
            if (interFace.name == interfaceName) {
                val addresses = interFace.inetAddresses
                for (addr in addresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
