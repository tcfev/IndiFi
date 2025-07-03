package org.fordem.indifi.ui.utils

import android.content.Context
import android.net.DhcpInfo
import android.net.wifi.WifiManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity.WIFI_SERVICE
import java.net.InetAddress


fun getHotspotGatewayIP(context: Context): String? {
    try {
        val wifiManager =
            context.getSystemService(WIFI_SERVICE) as WifiManager
        var dhcpInfo: DhcpInfo
        var gatewayInt: Int

        // Wait for a valid IP lease (max 5 seconds)
        var retries = 0
        do {
            dhcpInfo = wifiManager.dhcpInfo
            gatewayInt = dhcpInfo.gateway
            if (gatewayInt != 0) break
            Thread.sleep(5000)
            retries++
        } while (retries < 10)

        if (gatewayInt == 0) {
            Log.e("HELLO", "No valid IP lease found.")
        }
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
    }
    return null
}