package org.fordem.indifi.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.*
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.fordem.indifi.R
import org.fordem.indifi.ui.utils.Constants
import org.fordem.indifi.ui.utils.Constants.isGoViaLegacy
import org.fordem.indifi.ui.utils.Constants.lastDeviceInfo
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.utils.MessageRouterHelper.isServiceBound
import org.fordem.indifi.ui.utils.MessageRouterHelper.serviceConnection
import org.fordem.indifi.ui.utils.MessageRouterService
import org.fordem.indifi.ui.utils.UdpListenerService
import org.fordem.indifi.ui.utils.getHotspotGatewayIP
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WifiScanActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var wifiReceiver: BroadcastReceiver
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val wifiList = mutableListOf<String>()
    private val LOCATION_PERMISSION_CODE = 1001

    //    private var messageRouterService: MessageRouterService? = null
//    private var isServiceBound = false
    private var currentP2pInfo: WifiP2pInfo? = null
//    private val serviceConnection = object : ServiceConnection {
//        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
//            val localBinder = binder as MessageRouterService.LocalBinder
//            messageRouterService = localBinder.getService()
//            isServiceBound = true
//
//            // You can now call service methods like:
//            // messageRouterService?.startTcpServer(info, isGO)
//
//            messageRouterService?.startChatServer(
//                context = this@WifiScanActivity,
//                onMessageReceived = {
//                }
//            )
//            messageRouterService?.startSilentReceiver(this@WifiScanActivity)
//            messageRouterService?.startPrefSyncServer(this@WifiScanActivity, currentP2pInfo!!)
//
//        }
//
//        override fun onServiceDisconnected(name: ComponentName?) {
//            messageRouterService = null
//            isServiceBound = false
//        }
//    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_scan)

        listView = findViewById(R.id.lvWifiList)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, wifiList)
        listView.adapter = adapter

        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val success =
                    intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                if (success) {
                    showScanResults()
                } else {
                    Toast.makeText(
                        this@WifiScanActivity,
                        "Scan failed or restricted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_CODE
            )
        } else {
            ensureLocationEnabledAndScan()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val ssidWithBssid = wifiList[position]
            val ssid = ssidWithBssid.substringBefore(" - ")
            showPasswordDialog(ssid)
        }
    }

    override fun onStart() {
        super.onStart()

//        val intent = Intent(this, UdpListenerService::class.java)
//        startService(intent)

//        val connectedDevices = getConnectedDevicesFromARP()
        if (isHotspotEnabled(this) /*&& connectedDevices.isNotEmpty()*/) {
            isGoViaLegacy = true

//            startUdpReceiverOnGO()
            startService(Intent(this, MessageRouterService::class.java)) // Done in onStart

            // This ensures GO listens for incoming socket messages
            Handler(Looper.getMainLooper()).postDelayed({
                MessageRouterHelper.messageRouterService?.startChatServer(
                    context = this@WifiScanActivity,
                    onMessageReceived = { message ->
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                message,
                                Toast.LENGTH_SHORT
                            )
                                .show()
                        }
                    }
                )
            }, 3000)
        }
    }

    fun getConnectedDevicesFromARP(): List<String> {
        val connectedIps = mutableListOf<String>()
        try {
            val arpFile = File("/proc/net/arp")
            if (!arpFile.exists()) return connectedIps

            arpFile.forEachLine { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 4 && parts[0] != "IP") {
                    val ip = parts[0]
                    val mac = parts[3]
                    if (mac.matches("..:..:..:..:..:..".toRegex())) {
                        connectedIps.add(ip)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return connectedIps
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


    private fun ensureLocationEnabledAndScan() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (isGpsEnabled || isNetworkEnabled) {
            startWifiScan()
        } else {
            Toast.makeText(this, "Please enable Location (GPS) manually", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun startWifiScan() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiReceiver, intentFilter)

        val success = wifiManager.startScan()
        if (!success) {
            Toast.makeText(this, "Wi-Fi scan start failed!", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun showScanResults() {
        val results = wifiManager.scanResults
        wifiList.clear()

        for (scanResult in results) {
            if (!TextUtils.isEmpty(scanResult.SSID) &&
                scanResult.capabilities.contains("[ESS]") &&
                !scanResult.capabilities.contains("WPA") &&
                !scanResult.capabilities.contains("WPA2") &&
                !scanResult.capabilities.contains("SAE") &&
                !scanResult.capabilities.contains("EAP")
            ) {
                wifiList.add("${scanResult.SSID} - ${scanResult.BSSID}")
            }
        }

        adapter.notifyDataSetChanged()
    }

    private fun showPasswordDialog(ssid: String) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle("Connect to $ssid")
//            .setMessage("Enter password:")
//            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
//                val password = input.text.toString()
                connectToWifi(ssid/*, password*/)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @Suppress("DEPRECATION")
    private fun connectToWifi(ssid: String/*, capabilities: String*/) {
//        if (!capabilities.contains("OPEN", ignoreCase = true) && !capabilities.contains("ESS")) {
//            Toast.makeText(this, "This network requires a password. Skipping.", Toast.LENGTH_SHORT).show()
//            return
//        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .build() // No passphrase for open networks

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager =
                getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    connectivityManager.bindProcessToNetwork(network)

                    runOnUiThread {
                        Toast.makeText(
                            this@WifiScanActivity,
                            "Connected to $ssid",
                            Toast.LENGTH_SHORT
                        ).show()
//                        MessageRouterHelper.sendHelloToGO(getHotspotGatewayIP()!!)

//                        if (isGoViaLegacy) {
//                            MessageRouterHelper.messageRouterService?.startChatServer(
//                                context = this@WifiScanActivity,
//                                onMessageReceived = { message ->
////                                    runOnUiThread {
////                                        Toast.makeText(this@WifiScanActivity, "Received in GM: $message", Toast.LENGTH_SHORT).show()
////                                        // Handle incoming message here
////                                    }
//                                }
//                            )
//                        } else {
//                            Handler(mainLooper).postDelayed({
//                                MessageRouterHelper.messageRouterService?.sendMessageToServer(
//                                    hostAddress = getHotspotGatewayIP(this@WifiScanActivity)!!,
//                                    message = Constants.DummyLCMessage
//                                )
//
//                            }, 5000)
//                        }

//                        startActivity(Intent(this@WifiScanActivity, ChatActivity::class.java))
//                        finish()
                    }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    Toast.makeText(
                        this@WifiScanActivity,
                        "Connection to $ssid failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            connectivityManager.requestNetwork(request, networkCallback)

        } else {
            // Android 9 and below
            val conf = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE) // Open network
            }

            val wifiManager =
                applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val netId = wifiManager.addNetwork(conf)
            if (netId != -1) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()

                Toast.makeText(this, "Connecting to $ssid...", Toast.LENGTH_SHORT).show()

//                Handler(mainLooper).postDelayed({
//                    sendHelloPacketToGO()
//                    MessageRouterHelper.sendHelloToGO(getHotspotGatewayIP()!!)
                    Handler(mainLooper).postDelayed({

                        MessageRouterHelper.messageRouterService?.sendMessageToServer(
                            hostAddress = getHotspotGatewayIP(this)!!,
                            message = /*Constants.DummyLCMessage*/ "Hello Mr. Arman"
                        )

                    }, 5000)
//                    MessageRouterService.sendMessageToServer
//                    startActivity(Intent(this, ChatActivity::class.java))
//                    finish()
//                }, 4000)
            } else {
                Toast.makeText(this, "Failed to add open network $ssid", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendHelloPacketToGO() {
        Thread {
            try {
                val gatewayIP = getHotspotGatewayIP(this)
                if (gatewayIP.isNullOrEmpty()) {
                    Log.e("UDP", "Could not determine gateway IP")
                    return@Thread
                }

                val socket = DatagramSocket()
                val message = /*"HELLO Thanks for connecting through LEGACY WIFI"*/
                    Constants.DummyLCMessage
                val buffer = message.toByteArray()

//                val buffer = Constants.DummyLCMessage.toByteArray()
                val address = InetAddress.getByName(gatewayIP)
                val packet = DatagramPacket(buffer, buffer.size, address, 9876)
                socket.send(packet)
                socket.close()


            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ensureLocationEnabledAndScan()
        } else {
            Toast.makeText(this, "Permission denied. Cannot scan Wi-Fi.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiReceiver)

//        if (isServiceBound) {
//            unbindService(serviceConnection)
//            isServiceBound = false
//        }
    }

    // GO Side - Call this method in GO's main activity to listen for incoming GM HELLO
//    fun startListeningForGMHello(context: Context) {
//        Thread {
//            try {
//                val socket = DatagramSocket(9876)
//                val buffer = ByteArray(1024)
//                while (true) {
//                    val packet = DatagramPacket(buffer, buffer.size)
//                    socket.receive(packet)
//                    val message = String(packet.data, 0, packet.length)
//                    if (message.trim() == "HELLO Thanks for connecting through Legacy Wifi") {
//                        runOnUiThread {
//                            Toast.makeText(context, "GM connected!", Toast.LENGTH_SHORT).show()
////                            context.startActivity(Intent(context, ChatActivity::class.java))
//                        }
//                        break
//                    }
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }.start()
//    }

//    private fun startUdpReceiverOnGO() {
//        Thread {
//            try {
//                val socket = DatagramSocket(9876)
//                val buffer = ByteArray(1024)
//                val packet = DatagramPacket(buffer, buffer.size)
//
//                while (true) {
//                    socket.receive(packet)
//
//                    val receivedMessage = String(packet.data, 0, packet.length)
//                    val senderIp = packet.address.hostAddress
//
//                    Log.d("UDP_RECEIVER", "Received from $senderIp: $receivedMessage")
//
//                    Handler(Looper.getMainLooper()).post {
//                        Toast.makeText(
//                            this,
//                            /*"From $senderIp: $receivedMessage"*/ receivedMessage,
//                            Toast.LENGTH_LONG
//                        ).show()
//                    }
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }.start()
//    }

}
