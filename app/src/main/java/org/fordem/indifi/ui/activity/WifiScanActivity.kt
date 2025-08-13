package org.fordem.indifi.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fordem.indifi.R
import org.fordem.indifi.databinding.ActivityWifiScanBinding
import org.fordem.indifi.ui.viewmodel.DeviceInfoViewModel
import org.fordem.indifi.ui.utils.Constants
import org.fordem.indifi.ui.utils.Constants.legacyClientCallback
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.utils.buildJsonForDeviceList
import org.fordem.indifi.ui.utils.getHotspotGatewayIP
import org.fordem.indifi.ui.utils.getOwnIpAsGateway
import org.fordem.indifi.ui.utils.isGoViaLegacy
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import org.fordem.indifi.ui.model.DeviceInfo


@AndroidEntryPoint
class WifiScanActivity : BaseActivity() {
    private lateinit var binding: ActivityWifiScanBinding

    private val deviceViewModel: DeviceInfoViewModel by viewModels()
    private lateinit var wifiManager: WifiManager
    private lateinit var wifiReceiver: BroadcastReceiver
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val wifiList = mutableListOf<String>()
    private val LOCATION_PERMISSION_CODE = 1001
    private val TAG = "WifiScanActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Proper binding and setContentView here
        binding = ActivityWifiScanBinding.inflate(layoutInflater)
        setupNavigationUI(binding.root, "Hotspots")

        // TODO:  : This does not seem correct, why setting this true here? This was NOT commented out.
//        isGoViaLegacy = true


        // TODO:  : REFACTORING, THIS WAS UNCOMMENTED
        setupWifiNetworkListUi()

        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager

        setupWifiBroadcastReceiver()
        checkPermissions()
    }

    private fun setupWifiNetworkListUi() {
        listView = findViewById(R.id.lvWifiList)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, wifiList)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val ssidWithBssid = wifiList[position]
            val ssid = ssidWithBssid.substringBefore(" - ")
            showConnectDialog(ssid)
        }

        legacyClientCallback = {
            deviceViewModel.viewModelScope.launch {
                try {
                    deviceViewModel.allDevices.collect { deviceList ->
                        val dataToSend = buildJsonForDeviceList(deviceList)
                        try {
                            MessageRouterHelper.messageRouterService?.broadcastMessageToAllGMs(dataToSend)
                            runOnUiThread {
                                Toast.makeText(applicationContext, "Info Broadcast", Toast.LENGTH_SHORT).show()

                                // Update the wifiList UI here, mapping DeviceInfo to String
                                wifiList.clear()
                                wifiList.addAll(deviceList.map { device ->
                                    // Example representation: use device name and ip
                                    "${device.name} - ${device.ip}"
                                })
                                adapter.notifyDataSetChanged()
                            }
                        } catch (e: Exception) {
                            Log.e("Broadcast", "Failed to send to: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Broadcast", "Failed: ${e.message}")
                }
            }
        }
    }

    private fun setupWifiBroadcastReceiver() {
        wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                if (success) {
                    showScanResults()
                } else {
                    // TODO:  : This fails on older Android / Device, why?
                    Toast.makeText(
                        this@WifiScanActivity,
                        "Scan failed or restricted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // TODO:  : Refer to research paper Method 1, Method 2 for "get IP addresses of GM's".
        // TODO: Method 2 should be performed, especially considering "getConnectedDevicesFromARP" will fail because of permission issues.
//        val connectedDevices = getConnectedDevicesFromARP()
        if (isGoViaLegacy(this) /*&& connectedDevices.isNotEmpty()*/) {
            Log.d(TAG, "Hotspot is enabled via IP, starting legacy GO setup. This device will act as AP/AP (GO2 in documentation).")
//            isGoViaLegacy = true

            CoroutineScope(Dispatchers.IO).launch {
                // TODO:  : THIS WAS COMMENTED OUT
                val ownIp = getOwnIpAsGateway(this@WifiScanActivity) ?: return@launch
                val ownName = Build.MODEL ?: "GO_Device"
                val timestamp = System.currentTimeMillis()
                if (!deviceViewModel.isDuplicateDevice(ownName, ownIp, timestamp)) {
                        val goDevice = DeviceInfo(name = ownName, ip = ownIp, isGroupOwner = true, timestamp = timestamp)
                        deviceViewModel.insert(goDevice)
                        Log.d("LEGACY_GO", "Saved own device info: $goDevice")
                }
            }


//            startUdpReceiverOnGO()
//            startService(Intent(this, MessageRouterService::class.java)) // Done in onStart

            // This ensures GO listens for incoming socket messages
//            Handler(Looper.getMainLooper()).postDelayed({
//                MessageRouterHelper.messageRouterService?.startChatServer(
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
            // TODO:  : END COMMENTED OUT
        }
    }

    private fun getConnectedDevicesFromARP(): List<String> {
        val connectedIps = mutableListOf<String>()
        try {
            // TODO:  , Need permissions, failed: java.io.FileNotFoundException: /proc/net/arp: open failed: EACCES (Permission denied)
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
            Log.d(TAG, "Error reading ARP table: ${e.message}")
        }
        return connectedIps
    }

    private fun startWifiScan() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiReceiver, intentFilter)

        // TODO:  : This is always returning false
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

    private fun showConnectDialog(ssid: String) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle("Connect to $ssid")
//            .setMessage("Enter password:")
//            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
//                val password = input.text.toString()

                // TODO:  : Removed "connectToWifi(ssid) and implemented improved connectoWifi2(ssid)
                connectToWifi2(ssid)
//                connectToWifi(ssid/*, password*/)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = Uri.parse("package:" + this.packageName)
        this.startActivity(intent)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun connectToWifi2(ssid: String/*, capabilities: String*/) {
        // Permissions Check
        if (!Settings.System.canWrite(this)) {
            requestWriteSettingsPermission()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d("WifiScanActivity", "Using Q and above connection method")
            connectToWifiQAndAbove(ssid)
        } else {
            Log.d("WifiScanActivity", "Using legacy connection method")
            connectToWifiLegacy(ssid)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun connectToWifiQAndAbove(ssid: String) {
        Log.d("WifiScanActivity", "Connecting to $ssid using WifiNetworkSpecifier")
        val specifier = WifiNetworkSpecifier.Builder().setSsid(ssid).build() // No passphrase for open networks
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).setNetworkSpecifier(specifier).build()
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d("WifiScanActivity", "onAvailable: $network")
                connectivityManager.bindProcessToNetwork(network)

                // Insert both own device and GO device info in IO coroutine
                CoroutineScope(Dispatchers.IO).launch {
                    val ownIp = getOwnIpAsGateway(applicationContext)
                    if (!ownIp.isNullOrBlank() && ownIp != "0.0.0.0") {
                        val ownName = Build.MODEL ?: "GM_Device"
                        val timestamp = System.currentTimeMillis()
                        val duplicate = deviceViewModel.isDuplicateDevice(ownName, ownIp, timestamp)
                        if (!duplicate) {
                            val ownDevice = DeviceInfo(
                                name = ownName,
                                ip = ownIp,
                                isGroupOwner = false,
                                timestamp = timestamp
                            )
                            deviceViewModel.insert(ownDevice)
                            Log.d("WifiScanActivity", "Inserted own device info: $ownDevice")
                        }
                    } else {
                        Log.w("WifiScanActivity", "Could not get valid own IP, skipping own device insert")
                    }

                    val goIp = getHotspotGatewayIP(applicationContext)
                    if (!goIp.isNullOrBlank() && goIp != "0.0.0.0") {
                        val goName = "GO_Device"
                        val timestampGo = System.currentTimeMillis() - 1  // Slightly earlier timestamp
                        val duplicateGo = deviceViewModel.isDuplicateDevice(goName, goIp, timestampGo)
                        if (!duplicateGo) {
                            val goDevice = DeviceInfo(
                                name = goName,
                                ip = goIp,
                                isGroupOwner = true,
                                timestamp = timestampGo
                            )
                            deviceViewModel.insert(goDevice)
                            Log.d("WifiScanActivity", "Inserted GO device info: $goDevice")
                        }
                    } else {
                        Log.w("WifiScanActivity", "Could not get valid GO IP, skipping GO device insert")
                    }
                }

                // Show toast on UI thread
                runOnUiThread {
                    Toast.makeText(
                        this@WifiScanActivity,
                        "Connected to $ssid",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onUnavailable() {
                super.onUnavailable()
                Log.d("WifiScanActivity", "onUnavailable for $ssid")
                Toast.makeText(
                    this@WifiScanActivity,
                    "Connection to $ssid failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        connectivityManager.requestNetwork(request, networkCallback)
    }

    @SuppressLint("MissingPermission")
    private fun connectToWifiLegacy(ssid: String) {
        deviceViewModel.viewModelScope.launch {
            removeExistingNetworkConfig(ssid)

            val netId = addOpenNetworkConfig(ssid)
            if (netId != -1) {
                connectToNetwork(netId, ssid)
                handlePostConnection(ssid)

                // Insert both own device and GO device info after connection
                val ownIp = getOwnIpAsGateway(applicationContext)
                if (!ownIp.isNullOrBlank() && ownIp != "0.0.0.0") {
                    val ownName = Build.MODEL ?: "GM_Device"
                    val timestamp = System.currentTimeMillis()
                    if (!deviceViewModel.isDuplicateDevice(ownName, ownIp, timestamp)) {
                        val ownDevice = DeviceInfo(name = ownName, ip = ownIp, isGroupOwner = false, timestamp = timestamp)
                        deviceViewModel.insert(ownDevice)
                        Log.d("WifiScanActivity", "Inserted own device info: $ownDevice")
                    }
                } else {
                    Log.w("WifiScanActivity", "Could not get valid own IP, skipping own device insert")
                }

                val goIp = getHotspotGatewayIP(applicationContext)
                if (!goIp.isNullOrBlank() && goIp != "0.0.0.0") {
                    val goName = "GO_Device"
                    val timestampGo = System.currentTimeMillis() - 1
                    val duplicateGo = deviceViewModel.isDuplicateDevice(goName, goIp, timestampGo)
                    if (!duplicateGo) {
                        val goDevice = DeviceInfo(
                            name = goName,
                            ip = goIp,
                            isGroupOwner = true,
                            timestamp = timestampGo
                        )
                        deviceViewModel.insert(goDevice)
                        Log.d("WifiScanActivity", "Inserted GO device info: $goDevice")
                    }
                } else {
                    Log.w("WifiScanActivity", "Could not get valid GO IP, skipping GO device insert")
                }
            } else {
                Toast.makeText(
                    this@WifiScanActivity,
                    "Failed to add open network $ssid",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun removeExistingNetworkConfig(ssid: String) {
        wifiManager.configuredNetworks.find { it.SSID == "\"$ssid\"" }?.let {
            wifiManager.removeNetwork(it.networkId)
        }
    }

    private fun addOpenNetworkConfig(ssid: String): Int {
        val conf = WifiConfiguration().apply {
            SSID = "\"$ssid\""
            allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE) // Open network
        }
        return wifiManager.addNetwork(conf)
    }

    private fun connectToNetwork(netId: Int, ssid: String) {
        wifiManager.disconnect()
        wifiManager.enableNetwork(netId, true)
        wifiManager.reconnect()

        Toast.makeText(
            this@WifiScanActivity,
            "Connecting to $ssid...",
            Toast.LENGTH_SHORT
        ).show()
    }

    private suspend fun handlePostConnection(ssid: String) {
        val ownIpGM = withContext(Dispatchers.IO) { getOwnIpAsGateway(this@WifiScanActivity) }

        if (ownIpGM.isNullOrBlank() || ownIpGM == "0.0.0.0") {
            Toast.makeText(this@WifiScanActivity, "Failed to get valid IP", Toast.LENGTH_SHORT).show()
            return
        }

        val ownName = Build.MODEL ?: "GM_Device"
        val timestamp = System.currentTimeMillis()

        val goIp = getHotspotGatewayIP(this@WifiScanActivity)
        val goName = "GO_Device" // Or fetch from SSID / any other logic
        val goTimestamp = timestamp - 1  // Just to keep some order

        if (goIp.isNullOrBlank()) {
            Toast.makeText(this@WifiScanActivity, "Failed to get GO IP", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceArray = JSONArray().apply {
            put(JSONObject().apply {
                put("name", ownName)
                put("ip", ownIpGM)
                put("isGroupOwner", false)
                put("timestamp", timestamp.toLong())
            })

            put(JSONObject().apply {
                put("name", goName)
                put("ip", goIp)
                put("isGroupOwner", true)
                put("timestamp", goTimestamp.toLong())
            })
        }

        val helloJson = JSONObject().apply {
            put("type", "HELLO")
            put("devices", deviceArray)
        }.toString()

        Handler(mainLooper).postDelayed({
            val gatewayIp = getHotspotGatewayIP(this@WifiScanActivity)
            if (gatewayIp.isNullOrBlank() || gatewayIp == "0.0.0.0") {
                Log.e("GM_HELLO", "Gateway IP is invalid. Cannot send HELLO.")
                return@postDelayed
            }

            Log.d("GM_HELLO", "Sending HELLO to: $gatewayIp")
            Log.d("GM_HELLO", "HELLO JSON: $helloJson")

            MessageRouterHelper.messageRouterService?.sendMessageToServer(hostAddress = gatewayIp, message = helloJson)
        }, 500)
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

    private fun checkPermissions() {
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

    @SuppressLint("MissingPermission")
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
            deviceViewModel.viewModelScope.launch {
                // Remove existing config if already present
                wifiManager.configuredNetworks.find { it.SSID == "\"$ssid\"" }?.let {
                    wifiManager.removeNetwork(it.networkId)
                }

                // Android 9 and below
                val conf = WifiConfiguration().apply {
                    SSID = "\"$ssid\""
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE) // Open network
                }

                val netId = wifiManager.addNetwork(conf)
                if (netId != -1) {
                    wifiManager.disconnect()
                    wifiManager.enableNetwork(netId, true)
                    wifiManager.reconnect()

                    Toast.makeText(
                        this@WifiScanActivity,
                        "Connecting to $ssid...",
                        Toast.LENGTH_SHORT
                    ).show()


//                    val deferredValueGO =
//                        async(Dispatchers.IO) { getOwnIp(applicationContext, true) }
//                    val ownIpGO = deferredValueGO.await()
////                    delay(5000)
//                    if (ownIpGO.isNullOrBlank() || ownIpGO == "0.0.0.0") {
////                    Toast.makeText(
////                        applicationContext,
////                        "Failed to get valid IP",
////                        Toast.LENGTH_SHORT
////                    ).show()
//
//                        Handler(Looper.getMainLooper()).post {
//                            Log.d("LEGACY_GO", "Failed to get vvalid IP")
//                        }
//                        return@launch
//                    }
////                val ownIp = getOwnIp(context, true) ?: return@launch
//                    val ownNameGO = /*Build.MODEL ?: */"GO_Device"
//                    val timestampGO = System.currentTimeMillis()
//                    val duplicate =
//                        deviceViewModel.isDuplicateDevice(ownNameGO, ownIpGO, timestampGO)
//                    if (!duplicate) {
//                        val goDevice = DeviceInfo(
//                            name = ownNameGO,
//                            ip = ownIpGO,
//                            isGroupOwner = true,
//                            timestamp = timestampGO
//                        )
//                        deviceViewModel.insert(goDevice)
//                        Handler(Looper.getMainLooper()).post {
//                            Log.d("LEGACY_GO", "Saved own device info: $goDevice")
//                        }
//                    }


//                Handler(mainLooper).postDelayed({
//                    sendHelloPacketToGO()
//                    MessageRouterHelper.sendHelloToGO(getHotspotGatewayIP()!!)

//                    deviceViewModel.viewModelScope.launch {
                    val deferredValue =
                        async(Dispatchers.IO) { getOwnIpAsGateway(this@WifiScanActivity) }
                    val ownIpGM = deferredValue.await()
//                    delay(5000)

                    if (ownIpGM.isNullOrBlank() || ownIpGM == "0.0.0.0") {
                        Toast.makeText(
                            this@WifiScanActivity,
                            "Failed to get valid IP",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    val ownName = Build.MODEL ?: "GM_Device"
                    val timestamp = System.currentTimeMillis()

//                    val helloJson = """
//                        {
//                            \"type\": \"HELLO\",
//                            \"name\": \"$ownName\",
//                            \"ip\": \"$ownIpGM\",
//                            \"isGroupOwner\": false,
//                            \"timestamp\": $timestamp
//                        }
//                    """.trimIndent()

                    val goIp = getHotspotGatewayIP(this@WifiScanActivity)
                    val goName = "GO_Device" // Or fetch from SSID / any other logic
                    val goTimestamp = timestamp - 1  // Just to keep some order

                    if (goIp.isNullOrBlank()) {
                        Toast.makeText(
                            this@WifiScanActivity,
                            "Failed to get GO IP",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    val deviceArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("name", ownName)
                            put("ip", ownIpGM)
                            put("isGroupOwner", false)
                            put("timestamp", timestamp)
                        })

                        put(JSONObject().apply {
                            put("name", goName)
                            put("ip", goIp)
                            put("isGroupOwner", true)
                            put("timestamp", goTimestamp)
                        })
                    }

                    val helloJson = JSONObject().apply {
                        put("type", "HELLO")
                        put("devices", deviceArray)
                    }.toString()

//                little more delay here

                    Handler(mainLooper).postDelayed({
                        val gatewayIp = getHotspotGatewayIP(this@WifiScanActivity)
                        if (gatewayIp.isNullOrBlank() || gatewayIp == "0.0.0.0") {
                            Log.e("GM_HELLO", "Gateway IP is invalid. Cannot send HELLO.")
                            return@postDelayed
                        }

//                        ipLcGo = gatewayIp
                        Log.d("GM_HELLO", "Sending HELLO to: $gatewayIp")
                        Log.d("GM_HELLO", "HELLO JSON: $helloJson")

                        MessageRouterHelper.messageRouterService?.sendMessageToServer(
                            hostAddress = gatewayIp,
                            message = helloJson
                        )
                    }, 500)


//                    Handler(mainLooper).postDelayed({
//                        Log.d(
//                            "GM_HELLO",
//                            "Sending HELLO to: ${getHotspotGatewayIP(this@WifiScanActivity)}"
//                        )
//                        Log.d("GM_HELLO", "HELLO JSON: $helloJson")
//
//                        MessageRouterHelper.messageRouterService?.sendMessageToServer(
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiReceiver)

//        isGoViaLegacy = false
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
