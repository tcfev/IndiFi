package org.fordem.indifi.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
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
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fordem.indifi.R
import org.fordem.indifi.ui.dao.DeviceInfoDao
import org.fordem.indifi.ui.dao.PeerPublicKeyDao
import org.fordem.indifi.ui.encryption.KeyStoreManager
import org.fordem.indifi.ui.service.MulticastService
import org.fordem.indifi.ui.viewmodel.DeviceInfoViewModel
import org.fordem.indifi.ui.utils.Constants
import org.fordem.indifi.ui.utils.Constants.MULTICAST_PORT
import org.fordem.indifi.ui.utils.Constants.androidId
import org.fordem.indifi.ui.utils.Constants.connectivityManager
import org.fordem.indifi.ui.utils.Constants.currentBoundInterface
import org.fordem.indifi.ui.utils.Constants.ipLcGo
import org.fordem.indifi.ui.utils.Constants.isGoViaLegacy
import org.fordem.indifi.ui.utils.Constants.isRegisterReceiver
import org.fordem.indifi.ui.utils.Constants.legacyClientCallback
import org.fordem.indifi.ui.utils.Constants.networkCallback
import org.fordem.indifi.ui.utils.LegacyNetworkManager
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.utils.buildJsonForDeviceList
import org.fordem.indifi.ui.utils.buildLCHelloMessage
import org.fordem.indifi.ui.utils.getHotspotGatewayIP
import org.fordem.indifi.ui.utils.getOwnIp
import org.fordem.indifi.ui.utils.isHotspotEnabled
import org.fordem.indifi.ui.viewmodel.PeerPublicKeyViewModel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import javax.inject.Inject

@AndroidEntryPoint
class WifiScanActivity : AppCompatActivity() {
    private val deviceViewModel: DeviceInfoViewModel by viewModels()
    private val peerPublicKeyViewModel: PeerPublicKeyViewModel by viewModels()

    private lateinit var wifiManager: WifiManager

    //    private lateinit var wifiReceiver: BroadcastReceiver
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val wifiList = mutableListOf<String>()
    private val LOCATION_PERMISSION_CODE = 1001

    @Inject
    lateinit var peerPublicKeyDao: PeerPublicKeyDao

    @Inject
    lateinit var deviceInfoDao: DeviceInfoDao
    private val messageRouterHelper = MessageRouterHelper

    val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
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

    private fun getMulticastCapableIp(): InetAddress {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (iface in interfaces) {
            if ((iface.name == "p2p0" /*|| iface.name == "ap0"*/ /*|| iface.name == "wlan0"*/) && iface.isUp) {
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr
                    }
                }
            }
        }

        // Fallback: use dhcp IP (for GM clients)
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo
        val ip = String.format(
            "%d.%d.%d.%d",
            dhcpInfo.ipAddress and 0xff,
            dhcpInfo.ipAddress shr 8 and 0xff,
            dhcpInfo.ipAddress shr 16 and 0xff,
            dhcpInfo.ipAddress shr 24 and 0xff
        )
        return InetAddress.getByName(ip)
    }

    private fun getMulticastGMIp(): InetAddress {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (iface in interfaces) {
            if ((iface.name == "wlan0") && iface.isUp) {
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr
                    }
                }
            }
        }

        // Fallback: use dhcp IP (for GM clients)
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo
        val ip = String.format(
            "%d.%d.%d.%d",
            dhcpInfo.ipAddress and 0xff,
            dhcpInfo.ipAddress shr 8 and 0xff,
            dhcpInfo.ipAddress shr 16 and 0xff,
            dhcpInfo.ipAddress shr 24 and 0xff
        )
        return InetAddress.getByName(ip)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_scan)

        MessageRouterHelper.startMulticastService()
        MessageRouterHelper.bindMulticastService(this@WifiScanActivity)


//        isGoViaLegacy = true
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager



        listView = findViewById(R.id.lvWifiList)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, wifiList)
        listView.adapter = adapter

        checkPermissionsAndStartScan()

        registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))


//        if (ContextCompat.checkSelfPermission(
//                this,
//                Manifest.permission.ACCESS_FINE_LOCATION
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            ActivityCompat.requestPermissions(
//                this,
//                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
//                LOCATION_PERMISSION_CODE
//            )
//        } else {
//            ensureLocationEnabledAndScan()
//        }

//        messageRouterHelper.bindService(this)
//        messageRouterHelper.startIndifiService()


        listView.setOnItemClickListener { _, _, position, _ ->
            val ssidWithBssid = wifiList[position]
            val ssid = ssidWithBssid.substringBefore(" - ")
            showPasswordDialog(ssid)
        }


        val tv = findViewById<Button>(R.id.tv)
        tv.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
                val myDevice = deviceInfoDao.getDeviceById(androidId)

                if (myDevice != null) {
                    // Switch to background for network operations
                    withContext(Dispatchers.IO) {
                        try {
                            val group = InetAddress.getByName("230.0.0.1") // multicast group
                            val message = "LC_HELLO from ${Build.MODEL}"
                            val buffer = message.toByteArray()

                            val localIp = if (!myDevice.isRelayDevice) {
                                getMulticastCapableIp() // For LC-GO
                            } else {
                                getMulticastGMIp() // For LC-GM
                            }

                            val localSocketAddress = InetSocketAddress(localIp, 0)
                            DatagramSocket(localSocketAddress).use { socket ->
                                val packet = DatagramPacket(buffer, buffer.size, group, MULTICAST_PORT)
                                socket.send(packet)
                                Log.d("APP_LAYER", "Sent: $message via $localIp")
                            }
                        } catch (e: Exception) {
                            Log.e("APP_LAYER", "Error sending multicast", e)
                        }
                    }
                }
            }
        }


//        tv.setOnClickListener {
//            CoroutineScope(Dispatchers.Main).launch {
//                androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
//                val myDevice = deviceInfoDao.getDeviceById(androidId)
//
//                if (myDevice != null) {
//                    if (!myDevice.isRelayDevice) {
//                        // LC-GO side
//                        Thread {
//                            try {
//                                val group =
//                                    InetAddress.getByName("230.0.0.1") // mDNS group or any custom group
//                                val message = "LC_HELLO from ${Build.MODEL}"
//                                val buffer = message.toByteArray()
//
//                                val localIp = getMulticastCapableIp()
//                                val localSocketAddress = InetSocketAddress(localIp, 0)
//
//                                val socket = DatagramSocket(localSocketAddress)
//
//                                val packet =
//                                    DatagramPacket(buffer, buffer.size, group, MULTICAST_PORT)
//                                socket.send(packet)
//                                socket.close()
//                            } catch (e: Exception) {
//                                e.printStackTrace()
//                            }
//                        }.start()
//                    } else {
//                        // LC-GM side
//                        try {
//                            val group =
//                                InetAddress.getByName("230.0.0.1") // mDNS group or any custom group
//                            val message = "LC_HELLO from ${Build.MODEL}"
//                            val buffer = message.toByteArray()
//
//                            val localIp = getMulticastGMIp()
//                            val localSocketAddress = InetSocketAddress(localIp, 0)
//
//                            val socket = DatagramSocket(localSocketAddress)
//
//                            val packet = DatagramPacket(buffer, buffer.size, group, MULTICAST_PORT)
//                            socket.send(packet)
//                            socket.close()
//                        } catch (e: Exception) {
//                            e.printStackTrace()
//                        }
//                    }
//                }
//            }
//        }

//        tv.setOnClickListener {
//            CoroutineScope(Dispatchers.Main).launch {
//                androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
//                val myDevice = deviceInfoDao.getDeviceById(androidId)
//
//                if (myDevice != null) {
//                    val group = InetAddress.getByName("230.0.0.1") // Multicast group
//                    val socket = DatagramSocket()
//                    val type = "HELLO"
//                    val sourceRole = if (!myDevice.isRelayDevice) "GO" else "GM"
//                    val payload = "LC_HELLO from ${Build.MODEL}"
//                    val protocolMessage = "$type|$sourceRole|$payload"
//
//                    // Use application layer protocol format
//                    val buffer = protocolMessage.toByteArray()
//
//
////                    // Select IP binding based on role
////                    val localIp = if (!myDevice.isRelayDevice) {
////                        getMulticastCapableIp() // For LC-GO
////                    } else {
////                        getMulticastGMIp() // For LC-GM
////                    }
//
////                    val localSocketAddress = InetSocketAddress(localIp, 0)
//
//                    Thread {
//                        try {
////                            DatagramSocket(/*localSocketAddress*/).use { socket ->
//                            val packet = DatagramPacket(buffer, buffer.size, group, MULTICAST_PORT)
//                            socket.send(packet)
//                            socket.close()
//                            Log.d("APP_LAYER", "Sent: $protocolMessage")
////                            }
//                        } catch (e: Exception) {
//                            Log.e("APP_LAYER", "Error sending multicast", e)
//                        }
//                    }.start()
//                }
//            }
//        }


        legacyClientCallback = {
            val myAndroidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

            deviceViewModel.viewModelScope.launch(Dispatchers.IO) {
                try {
                    deviceViewModel.updateLcIpAndRelayByAndroidId(
                        androidId = myAndroidId,
                        newLcIp = ipLcGo,
                        isRelayDevice = true
                    )
                    Log.d(
                        "GO_SELF_UPDATE",
                        "Updated GO’s own LC IP and relay flag: $ipLcGo, Relay=true"
                    )
                } catch (e: Exception) {
                    Log.e("GO_SELF_UPDATE", "Failed to update GO’s own entry: ${e.message}")
                }
            }

            deviceViewModel.viewModelScope.launch(Dispatchers.IO) {
                try {
                    val allDevices = deviceViewModel.allDevices
                    allDevices.collect { deviceList ->
                        val dataToSend = buildJsonForDeviceList(deviceList)

                        // Send to Legacy Wifi Clients
                        try {
                            MessageRouterHelper.indifiService?.sendMessageToServerAsLc(
                                "",
                                dataToSend
                            )
                            Toast.makeText(applicationContext, "Info Broadcast", Toast.LENGTH_SHORT)
                                .show()
                        } catch (e: Exception) {
                            Log.e("Broadcast", "Failed to send to: ${e.message}")
                        }

                        // Send to WFD peers
                        try {
                            MessageRouterHelper.indifiService?.broadcastMessageToAllWfdPeers(
                                dataToSend
                            )
                        } catch (e: Exception) {
                            Log.e("BroadcastWFD", "Failed to send to WFD: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Broadcast", "Failed: ${e.message}")
                }
            }

//            deviceViewModel.viewModelScope.launch(Dispatchers.IO) {
//                try {
//                    val allKeysFlow = peerPublicKeyDao.getAllKeys() // Flow<List<PeerKeyInfo>>
//                    allKeysFlow.collect { keyList ->
//                        val dataToSend = buildJsonForPeerKeys(keyList)
//
//                        try {
//                            MessageRouterHelper.indifiService?.broadcastMessageToAllLegacyClients(
//                                dataToSend
//                            )
//                        } catch (e: Exception) {
//                            Log.e("BroadcastKeys", "Failed to send keys: ${e.message}")
//                        }
//                    }
//                } catch (e: Exception) {
//                    Log.e("Broadcast", "Failed: ${e.message}")
//                }
//            }
        }

//        legacyClientCallback = {
//            val sharedPrefs = getSharedPreferences("LegacyClientInfo", MODE_PRIVATE)
//            val myAndroidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
//            val lcIpGO = getHotspotGatewayIP(this@WifiScanActivity)
//            val isRelay = true // Or false, depending on whether GO is acting as a relay
//
//            lifecycleScope.launch(Dispatchers.IO) {
//                try {
//                    deviceViewModel.updateLcIpAndRelayByAndroidId(
//                        androidId = myAndroidId,
//                        newLcIp = ipLcGo,
//                        isRelayDevice = true
//                    )
//                    Log.d("GO_SELF_UPDATE", "Updated GO’s own LC IP and relay flag: $ipLcGo, Relay=true")
//                } catch (e: Exception) {
//                    Log.e("GO_SELF_UPDATE", "Failed to update GO’s own entry: ${e.message}")
//                }
//            }
//
//            // Launching a single coroutine for both device list and key list broadcasts
//            lifecycleScope.launch(Dispatchers.IO) {
//                try {
//                    // Collect all devices
//                    val deviceList = deviceViewModel.allDevices.first()
//
//                    // Filter devices with name containing "LC_GM"
////                        val gmDevices = deviceList.filter { it.name.contains("LC_GM", ignoreCase = true) }
//
//                    // Build JSON payload
//                    val deviceListJson = buildJsonForDeviceList(deviceList)
//
//                    // Send to each GM's IP
//                    deviceList.forEach { device ->
//                        try {
//                            MessageRouterHelper.indifiService?.broadcastMessageToAllLC(
//                                device.lcIp, // each LC_GM IP
//                                deviceListJson
//                            )
//                            Log.d("Broadcast", "Sent device list to: ${device.lcIp}")
//                        } catch (e: Exception) {
//                            Log.e(
//                                "Broadcast",
//                                "Failed to send device list to ${device.lcIp}: ${e.message}"
//                            )
//                        }
//                    }
//                } catch (e: Exception) {
//                    Log.e("Broadcast", "Failed collecting devices: ${e.message}")
//                }
//            }
//
//            lifecycleScope.launch(Dispatchers.IO) {
//                try {
//                    // Collect public keys flow
//                    val keyList = peerPublicKeyViewModel.allKeys.first()
//                    // Build JSON for peer keys
//                    val keyListJson = buildJsonForPeerKeys(keyList)
//
//                    // Get current LC_GM devices from DB again
////                        val gmDevices = deviceViewModel.allDevices.first()
////                            .filter { it.name.contains("LC_GM", ignoreCase = true) }
//
//                    // Send keys to each GM
//                    keyList.forEach { device ->
//                        try {
//                            MessageRouterHelper.indifiService?.broadcastMessageToAllLC(
//                                device.ip,
//                                keyListJson
//                            )
//                            Log.d("BroadcastKeys", "Sent keys to: ${device.ip}")
//                        } catch (e: Exception) {
//                            Log.e(
//                                "BroadcastKeys",
//                                "Failed to send keys to ${device.ip}: ${e.message}"
//                            )
//                        }
//                    }
//                } catch (e: Exception) {
//                    Log.e("BroadcastKeys", "Failed collecting keys: ${e.message}")
//                }
//            }
//        }

    }

    override fun onResume() {
        super.onResume()

        if (isHotspotEnabled(this)) {
            isGoViaLegacy = true
        }
    }

    private val PERMISSIONS_REQUEST_CODE = 1001

    private fun checkPermissionsAndStartScan() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            if (isLocationEnabled()) {
                startWifiScan() // <- your custom logic
            } else {
                Toast.makeText(this, "Please enable location services", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    override fun onStart() {
        super.onStart()
        val connectedDevices = getConnectedDevicesFromARP()
        if (isHotspotEnabled(this) /*&& connectedDevices.isNotEmpty()*/) {
            isGoViaLegacy = true

//            val ownIp = getOwnIp(this, true) ?: return
//            val ownName = Build.MODEL ?: "GO_Device"
//            val timestamp = System.currentTimeMillis()
//
//            CoroutineScope(Dispatchers.IO).launch {
//                val duplicate = deviceViewModel.isDuplicateDevice(ownName, ownIp, timestamp)
//                if (!duplicate) {
//                    val goDevice = DeviceInfo(
//                        name = ownName,
//                        ip = ownIp,
//                        isGroupOwner = true,
//                        timestamp = timestamp
//                    )
//                    deviceViewModel.insert(goDevice)
//                    Log.d("LEGACY_GO", "Saved own device info: $goDevice")
//                }
//            }


//            startUdpReceiverOnGO()
//            startService(Intent(this, IndifiService::class.java)) // Done in onStart

            // This ensures GO listens for incoming socket messages
//            Handler(Looper.getMainLooper()).postDelayed({
//                MessageRouterHelper.indifiService?.startChatServer(
//                    onMessageReceived = { message ->
//                        runOnUiThread {
//                            Toast.makeText(
//                                this,
//                                message,
//                                Toast.LENGTH_SHORT
//                            )
//                                .show()
//                        }
//                    }
//                )
//            }, 3000)
        }
    }

    private fun getConnectedDevicesFromARP(): List<String> {
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

//    private fun isHotspotEnabled(context: Context): Boolean {
//        return try {
//            val wifiManager =
//                context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
//            val method = wifiManager.javaClass.getDeclaredMethod("isWifiApEnabled")
//            method.isAccessible = true
//            method.invoke(wifiManager) as Boolean
//        } catch (e: Exception) {
//            false
//        }
//    }

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
        if (!isRegisterReceiver) {
            val intentFilter = IntentFilter()
            intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            registerReceiver(wifiReceiver, intentFilter)
            isRegisterReceiver = true
        }
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
            if (!TextUtils.isEmpty(scanResult.SSID)) {
                // Show all networks regardless of their security type
                wifiList.add("${scanResult.SSID} - ${scanResult.BSSID} - ${scanResult.capabilities}")
            }
        }

        adapter.notifyDataSetChanged()
    }

//    private fun showScanResults() {
//        val results = wifiManager.scanResults
//        wifiList.clear()
//
//        for (scanResult in results) {
//            if (!TextUtils.isEmpty(scanResult.SSID) &&
//                scanResult.capabilities.contains("[ESS]") &&
//                !scanResult.capabilities.contains("WPA") &&
//                !scanResult.capabilities.contains("WPA2") &&
//                !scanResult.capabilities.contains("SAE") &&
//                !scanResult.capabilities.contains("EAP")
//            ) {
//                wifiList.add("${scanResult.SSID} - ${scanResult.BSSID}")
//            }
//        }
//
//        adapter.notifyDataSetChanged()
//    }

    private var multicastService: MulticastService? = null
    private var isMulticastBound = false


    private val multicastConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MulticastService.LocalBinder
            multicastService = binder.getService()
            isMulticastBound = true

            // ✅ Now you can safely call the function
//            IndifiService?.startListeningForMulticastMessages()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isMulticastBound = false
            multicastService = null
        }
    }


    private fun showPasswordDialog(ssid: String) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle("Connect to $ssid")
            .setMessage("Enter password:")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val password = input.text.toString()
                connectToWifi(ssid, password)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun connectToWifi(
        ssid: String,/*, capabilities: String*/
        password: String
    ) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val specifierBuilder = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)

            if (password.isNotEmpty()) {
                specifierBuilder.setWpa2Passphrase(password) // Set password if required
            }

            val specifier = specifierBuilder.build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
//                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()


            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    val result = connectivityManager?.bindProcessToNetwork(network)

                    val linkProps = connectivityManager?.getLinkProperties(network)
                    val ifaceName = linkProps?.interfaceName

                    if (ifaceName?.startsWith("wlan") == true) {
//                        val result = connectivityManager?.bindProcessToNetwork(network)
                        currentBoundInterface = ifaceName
                        Log.d("NetworkBinder", " Bound to $ifaceName: $result")
                    } else {
                        Log.d("NetworkBinder", " Skipped network, not matching target: $ifaceName")
                    }

//                    LegacyNetworkManager.boundNetwork = network
//                    LegacyNetworkManager.networkCallback = this // inside the anonymous object

                    Toast.makeText(this@WifiScanActivity, "Connected to $ssid", Toast.LENGTH_SHORT)
                        .show()

                    deviceViewModel.viewModelScope.launch {
                        delay(3000) // Allow network stabilization

                        val lcIpGM = withContext(Dispatchers.IO) {
//                            reinstall and check if the IP is correct after having p2p connection
                            getOwnIp(this@WifiScanActivity)
                        }

                        if (lcIpGM.isNullOrBlank() || lcIpGM == "0.0.0.0") {
                            Toast.makeText(
                                this@WifiScanActivity,
                                "Failed to get valid IP",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }
//                        val gmBase64Key = KeyStoreManager.getOwnPublicKeyBase64()

//                        reinstall and check if the IP is correct after having p2p connection
                        val lcIpGO = getHotspotGatewayIP(this@WifiScanActivity)
                        if (lcIpGO.isNullOrBlank()) {
                            Toast.makeText(
                                this@WifiScanActivity,
                                "Failed to get GO IP",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }


//                        val serviceIntent = Intent(this@WifiScanActivity, IndifiService::class.java)
//                        startForegroundService(serviceIntent) // Start the service
//                        bindService(serviceIntent, multicastConnection, Context.BIND_AUTO_CREATE) // Bind to it

                        val timestamp = System.currentTimeMillis()
                        androidId =
                            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
//                        val goBase64Key = KeyStoreManager.getOwnPublicKeyBase64()

//                        val gmDevice = DeviceInfo(
//                            name = "LC_GM",
//                            wfdIp = "",
//                            lcIp = lcIpGM,
//                            androidId = androidId,
//                            isGroupOwner = false,
//                            timestamp = timestamp,
//                            base64Key = "",
//                            groupId = "",
//                            isRelayDevice = true
//                        )
//
//                        val goDevice = DeviceInfo(
//                            name = "LC_GO",
//                            wfdIp = "",
//                            lcIp = lcIpGO,
//                            androidId = "", // GO's androidId is unknown
//                            isGroupOwner = true,
//                            timestamp = timestamp - 1,
//                            base64Key = "",
//                            groupId = "",
//                            isRelayDevice = true
//                        )

//                        withContext(Dispatchers.IO) {
//                            deviceViewModel.insertOrIgnore(gmDevice)
//                            deviceViewModel.insertOrIgnore(goDevice)
//                        }

                        withContext(Dispatchers.IO) {
//                            test again by connecting all devices and see if it still creates problem
//                                    than start tracing why lcIpGm is same as lcIpGm

                            deviceViewModel.updateLcIpAndRelayByAndroidId(
                                androidId = androidId,
                                newLcIp = lcIpGM,
                                isRelayDevice = true
                            )

//                            // Update existing LC-GM entry
//                            deviceViewModel.updateLcIpByNameAndRole(
//                                newLcIp = lcIpGM,
//                                isGroupOwner = true,
//                                isRelayDevice = true
//                            )

//                            // Update existing LC-GO entry
//                            deviceViewModel.updateLcIpByNameAndRole(
//                                newLcIp = lcIpGO,
//                                isGroupOwner = true,
//                                isRelayDevice = true
//                            )
                        }


                        // Now collect from Flow and act only once
                        var sent = false
                        deviceViewModel.allDevices.collectLatest { deviceList ->
                            if (deviceList.size >= 2 && !sent) {
                                sent = true // prevent multiple emissions

                                val deviceArray = JSONArray().apply {
                                    deviceList.forEach { device ->
                                        put(JSONObject().apply {
                                            put("name", device.name)
                                            put("wfdIp", device.wfdIp)
                                            put("lcIp", device.lcIp)
                                            put("androidId", device.androidId)
                                            put("groupId", device.groupId)
                                            put("isGroupOwner", device.isGroupOwner)
                                            put("isRelayDevice", device.isRelayDevice)
                                            put("timestamp", device.timestamp)
                                            put("base64Key", device.base64Key)

//                                            this point is fixed, now start testing.
                                        })
                                    }
                                }

                                val helloJson = buildLCHelloMessage(
                                    lcIpGO = lcIpGO,
                                    type = "LC_HELLO",
                                    deviceArray = deviceArray
                                )

                                val keyExchangeJson = JSONObject().apply {
                                    put("type", "KEY_EXCHANGE")
                                    put("publicKey", KeyStoreManager.getOwnPublicKeyBase64())
                                }

                                Handler(Looper.getMainLooper()).postDelayed({
                                    Log.d("GM_HELLO", "Sending HELLO to: $lcIpGO")
//                                    ipLcGo = lcIpGO
                                    getSharedPreferences("LegacyClientInfo", MODE_PRIVATE)
                                        .edit().putString("gatewayIP", lcIpGO).apply()

                                    MessageRouterHelper.indifiService?.sendMessageToServerAsLc(
                                        hostAddress = lcIpGO,
                                        message = helloJson
                                    )

                                    MessageRouterHelper.indifiService?.sendMessageToServerAsLc(
                                        hostAddress = lcIpGO,
                                        message = keyExchangeJson.toString()
                                    )
                                }, 3000)

                                this.cancel() // stop collecting after first send
                            }
                        }
                    }
                }

                override fun onUnavailable() {
                    Toast.makeText(
                        this@WifiScanActivity,
                        "Connection to $ssid failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            connectivityManager?.requestNetwork(request, networkCallback!!)

        } else {
            deviceViewModel.viewModelScope.launch {
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                // Remove existing config if already present
                wifiManager.configuredNetworks.find { it.SSID == "\"$ssid\"" }?.let {
                    wifiManager.removeNetwork(it.networkId)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        wifiManager.staConcurrencyForMultiInternetMode
                    } else {
                        TODO("VERSION.SDK_INT < TIRAMISU")
                    }
                }
                delay(3000)

                // Android 9 and below
                val conf = WifiConfiguration().apply {
                    SSID = "\"$ssid\""
                    if (password.isNotEmpty()) {
                        preSharedKey = "\"$password\""
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
                    } else {
                        allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                    }
                }

//                val conf = WifiConfiguration().apply {
//                    SSID = "\"$ssid\""
//                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE) // Open network
//                }
                delay(3000)

                val netId = wifiManager.addNetwork(conf)
                if (netId != -1) {
                    wifiManager.disconnect()
                    wifiManager.enableNetwork(netId, true)
                    wifiManager.reconnect()
                    delay(3000)

                    Toast.makeText(
                        this@WifiScanActivity,
                        "Connecting to $ssid...",
                        Toast.LENGTH_SHORT
                    ).show()


//                Handler(mainLooper).postDelayed({
//                    sendHelloPacketToGO()
//                    MessageRouterHelper.sendHelloToGO(getHotspotGatewayIP()!!)

//                    deviceViewModel.viewModelScope.launch {
                    val deferredValue = async(Dispatchers.IO) { getOwnIp(this@WifiScanActivity) }
                    val lcIpGM = deferredValue.await()
                    delay(3000)

                    if (lcIpGM.isNullOrBlank() || lcIpGM == "0.0.0.0") {
                        Toast.makeText(
                            this@WifiScanActivity,
                            "Failed to get valid IP",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }


                    val ownName = /*Build.MODEL ?:*/ "LC_GM"
                    val timestamp = System.currentTimeMillis()

//                    val helloJson = """
//                        {
//                            \"type\": \"HELLO\",
//                            \"name\": \"$ownName\",
//                            \"ip\": \"$lcIpGM\",
//                            \"isGroupOwner\": false,
//                            \"timestamp\": $timestamp
//                        }
//                    """.trimIndent()

                    val lcIpGO = getHotspotGatewayIP(this@WifiScanActivity)
                    val goName = "LC_GO" // Or fetch from SSID / any other logic
                    val goTimestamp = timestamp - 1  // Just to keep some order
                    delay(3000)

                    if (lcIpGO.isNullOrBlank()) {
                        Toast.makeText(
                            this@WifiScanActivity,
                            "Failed to get GO IP",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

//                    androidId = Settings.Secure.ANDROID_ID

                    val deviceArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", ownName)
                            put("ip", lcIpGM)
                            put("androidId", androidId)
                            put("isGroupOwner", false)
                            put("timestamp", timestamp)
                        })

                        put(JSONObject().apply {
                            put("name", goName)
                            put("ip", lcIpGO)
                            put("androidId", "")
                            put("isGroupOwner", true)
                            put("timestamp", goTimestamp)
                        })
                    }

                    // 1. Send Hello Message to Go
                    val helloJson = buildLCHelloMessage(
                        lcIpGO = lcIpGO,
                        type = "LC_HELLO",
                        deviceArray = deviceArray
                    )

//                    val helloJson = JSONObject().apply {
//                        put("type", "HELLO")
//                        put("devices", deviceArray)
//                    }.toString()

//                little more delay here

                    // 2. Send KEY_EXCHANGE to GO
                    val keyExchangeJson =
                        JSONObject().apply {
                            put(
                                "type",
                                "KEY_EXCHANGE"
                            )
                            put(
                                "publicKey",
                                KeyStoreManager.getOwnPublicKeyBase64()
                            )
                        }

                    Handler(mainLooper).postDelayed({
                        val gatewayIp = getHotspotGatewayIP(this@WifiScanActivity)
                        if (gatewayIp.isNullOrBlank() || gatewayIp == "0.0.0.0") {
                            Log.e("GM_HELLO", "Gateway IP is invalid. Cannot send HELLO.")
                            return@postDelayed
                        }

//                        ipLcGo = gatewayIp
                        Log.d("GM_HELLO", "Sending HELLO to: $gatewayIp")
                        Log.d("GM_HELLO", "HELLO JSON: $helloJson")


                        val sharedPrefs =
                            getSharedPreferences("LegacyClientInfo", MODE_PRIVATE)
                        sharedPrefs.edit().putString("gatewayIP", gatewayIp).apply()

                        MessageRouterHelper.indifiService?.sendMessageToServerAsLc(
                            hostAddress = gatewayIp,
                            message = helloJson
                        )


                        MessageRouterHelper.indifiService?.sendMessageToServerAsLc(
                            hostAddress = gatewayIp,
                            message = keyExchangeJson.toString()
                        )
                    }, 3000)


//                    Handler(mainLooper).postDelayed({
//                        Log.d(
//                            "GM_HELLO",
//                            "Sending HELLO to: ${getHotspotGatewayIP(this@WifiScanActivity)}"
//                        )
//                        Log.d("GM_HELLO", "HELLO JSON: $helloJson")
//
//                        MessageRouterHelper.indifiService?.sendMessageToServerAsLc(
//                            hostAddress = getHotspotGatewayIP(this@WifiScanActivity)!!,
//                            message = /*Constants.DummyLCMessage*/ /*"New device connected"*/ helloJson
//                        )
//                    }, 500)
//                    }


                } else {
                    Toast.makeText(
                        this@WifiScanActivity,
                        "Failed to add open network $ssid",
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
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
//        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            ensureLocationEnabledAndScan()
//        } else {
//            Toast.makeText(this, "Permission denied. Cannot scan Wi-Fi.", Toast.LENGTH_SHORT).show()
//        }

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                if (isLocationEnabled()) {
                    startWifiScan()
                } else {
                    Toast.makeText(this, "Please enable location services", Toast.LENGTH_LONG)
                        .show()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            } else {
                Toast.makeText(this, "Missing permissions. Cannot scan.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRegisterReceiver) {
            unregisterReceiver(wifiReceiver)

            isGoViaLegacy = false
            isRegisterReceiver = false
        }

        if (isMulticastBound) {
            unbindService(multicastConnection)
            isMulticastBound = false
        }

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
