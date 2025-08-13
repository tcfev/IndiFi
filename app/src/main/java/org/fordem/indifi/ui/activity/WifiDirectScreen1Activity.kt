package org.fordem.indifi.ui.activity

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fordem.indifi.ui.utils.Constants
import org.fordem.indifi.databinding.ActivityWifiDirectScreen1Binding
import org.fordem.indifi.ui.model.DeviceInfo
import org.fordem.indifi.ui.dao.DeviceInfoDao
import org.fordem.indifi.ui.viewmodel.DeviceInfoViewModel
import org.fordem.indifi.ui.model.OwnDeviceInfo
import org.fordem.indifi.ui.dao.PeerPublicKeyDao
import org.fordem.indifi.ui.encryption.KeyStoreManager
import org.fordem.indifi.ui.utils.Constants.isGOViaWFD
import org.fordem.indifi.ui.utils.Constants.myMembersList
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.utils.NetworkBinder
import org.fordem.indifi.ui.utils.buildJsonForDeviceList
import org.fordem.indifi.ui.utils.buildJsonForPeerKeys
import org.fordem.indifi.ui.utils.buildWfdHelloMessage
import org.fordem.indifi.ui.viewmodel.PeerPublicKeyViewModel
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.UUID
import javax.inject.Inject

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
@AndroidEntryPoint
class WifiDirectScreen1Activity : AppCompatActivity() {
    private val deviceViewModel: DeviceInfoViewModel by viewModels()
    private val peerPublicKeyViewModel: PeerPublicKeyViewModel by viewModels()

    private var currentP2pInfo: WifiP2pInfo? = null
    private val binding: ActivityWifiDirectScreen1Binding by lazy {
        ActivityWifiDirectScreen1Binding.inflate(layoutInflater)
    }

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.CHANGE_WIFI_STATE
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CHANGE_WIFI_STATE
        )
    }

    private val permissionRequestCode = 100

    private lateinit var peerAdapter: ArrayAdapter<String>

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private lateinit var intentFilter: IntentFilter

    private val peers = mutableListOf<WifiP2pDevice>()

    private val TAG = "WFD"
    private var isReceiverRegistered = false
    private var isChatActivityLaunched = false

    var gmName = ""
    var gmMac = ""

    @Inject
    lateinit var deviceInfoDao: DeviceInfoDao

    @Inject
    lateinit var peerPublicKeyDao: PeerPublicKeyDao
    private val messageRouterHelper = MessageRouterHelper


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        checkAndRequestPermissions()

        peerAdapter = ArrayAdapter(this, R.layout.simple_list_item_1, ArrayList())
        binding.lvPeers.adapter = peerAdapter

//        NetworkBinder.unbind()
//        connectivityManager.bindProcessToNetwork(null)
//        if (networkCallback != null) {
//            try {
//                connectivityManager.unregisterNetworkCallback(networkCallback!!)
//            } catch (_: Exception) { }
//        }
//        Log.d("NetworkBinder", " Unbound from $currentBoundInterface")
//        networkCallback = null
//        currentBoundInterface = null


        wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)
        setupIntentFilter()
        setupReceiver()

        binding.btnDisconnect.setOnClickListener {
            wifiP2pManager.requestGroupInfo(channel) { group ->
                if (group != null) {
                    if (group.isGroupOwner) {
                        // Device is GO → prompt user before disbanding group
                        AlertDialog.Builder(this).setTitle("Disband Group?")
                            .setMessage("You are the Group Owner. Disbanding will disconnect all members. Proceed?")
                            .setPositiveButton("Yes") { _, _ ->
                                wifiP2pManager.removeGroup(channel,
                                    object : WifiP2pManager.ActionListener {
                                        override fun onSuccess() {
                                            getSharedPreferences("group_info", MODE_PRIVATE).edit()
                                                .clear().apply()

                                            Log.d("WFD", "Group disbanded (GO)")
                                            Toast.makeText(
                                                this@WifiDirectScreen1Activity,
                                                "Group disbanded",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                        override fun onFailure(reason: Int) {
                                            Log.e("WFD", "Failed to disband group: $reason")
                                            Toast.makeText(
                                                this@WifiDirectScreen1Activity,
                                                "Failed to disband group",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    })
                            }.setNegativeButton("No", null).show()
                    } else {
                        // Device is GM → leave the group
                        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() {
                                Log.d("WFD", "Left group (GM)")
                                Toast.makeText(
                                    this@WifiDirectScreen1Activity,
                                    "Disconnected from group",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            override fun onFailure(reason: Int) {
                                Log.e("WFD", "Failed to leave group: $reason")
                                Toast.makeText(
                                    this@WifiDirectScreen1Activity,
                                    "Failed to disconnect",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                    }
                } else {
                    Log.e("WFD", "Group is null")
                    Toast.makeText(this, "No group info available", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnDiscoverPeers.setOnClickListener {
            when {
                !hasAllPermissions() -> {
                    Toast.makeText(this, "Please grant required permissions", Toast.LENGTH_SHORT)
                        .show()
                }

                !isLocationEnabled() -> {
                    Toast.makeText(this, "Please enable Location Services", Toast.LENGTH_LONG)
                        .show()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }

                !isWifiEnabled(this) -> {
                    Toast.makeText(this, "Please Turn on Wifi", Toast.LENGTH_LONG).show()
                    val panelIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    startActivity(panelIntent)
                }

                else -> {
                    Toast.makeText(
                        this, "All checks passed. Starting Wi-Fi Direct...", Toast.LENGTH_SHORT
                    ).show()

                    discoverPeers()
                }
            }
        }

        binding.lvPeers.setOnItemClickListener { _, _, position, _ ->
            val device = peers[position]
            val config = WifiP2pConfig().apply {
                deviceAddress = device.deviceAddress
                wps.setup = WpsInfo.PBC
//                groupOwnerIntent = 15 // 0–15 → 15 = strongly prefer to be GO
                groupOwnerIntent = 0 // Strongly prefer NOT to be GO — but this is the initiator
            }

            try {
                wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        val identifier = device.deviceName
                            ?: device.deviceAddress // Fallback to MAC if name is null
                        saveConnectedDeviceMac(
                            this@WifiDirectScreen1Activity,
                            identifier,
                            device.deviceAddress
                        )

                        Toast.makeText(
                            this@WifiDirectScreen1Activity,
                            "Connecting to ${device.deviceName}",
                            Toast.LENGTH_SHORT
                        ).show()


//                        messageRouterHelper.startIndifiService()
//                        messageRouterHelper.bindService(this@WifiDirectScreen1Activity)
//
//                        messageRouterHelper.startMulticastService()
//                        messageRouterHelper.bindMulticastService(this@WifiDirectScreen1Activity)

                    }

                    override fun onFailure(reason: Int) {
                        Toast.makeText(
                            this@WifiDirectScreen1Activity,
                            "Connection failed: $reason",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.e(TAG, "Connection failed: $reason")
                    }
                })
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing required permission for peer discovery", e)
            }
        }

        Constants.deviceConnectionCallback = { gmIP: String ->
            val lastMember = myMembersList.last()
            val gmNumber = myMembersList.size + 1
            gmName = lastMember.deviceName
            gmMac = lastMember.deviceAddress
            val timestamp = System.currentTimeMillis()

//            val device = DeviceInfo(
//                name = gmName,
//                ip = gmIP,
//                androidId = "",
//                isGroupOwner = false,
//                timestamp = timestamp
//            )

//            deviceViewModel.viewModelScope.launch(Dispatchers.Main) {
//                val exists = deviceViewModel.isDuplicateDevice(device.name, device.ip, device.androidId)
//                if (!exists) {
//                    deviceViewModel.insert(device)
//                } else {
//                    Log.d(
//                        "DB",
//                        "Device already exists with name ${device.name}, IP ${device.ip}, recent timestamp"
//                    )
//                }

            deviceViewModel.viewModelScope.launch(Dispatchers.IO) {
                try {
                    val allDevices = deviceViewModel.allDevices
                    allDevices.collect { deviceList ->
                        val dataToSend = buildJsonForDeviceList(deviceList)

                        try {
                            MessageRouterHelper.indifiService?.broadcastMessageToAllWfdPeers(
                                dataToSend
                            )

                        } catch (e: Exception) {
                            Log.e("Broadcast", "Failed to send to: ${e.message}")
                        }
                    }


//                    val allKeysFlow = peerPublicKeyDao.getAllKeys() // Flow<List<PeerKeyInfo>>
//                    allKeysFlow.collect { keyList ->
//                        val dataToSend = buildJsonForPeerKeys(keyList)
//
//                        try {
//                            MessageRouterHelper.indifiService?.broadcastMessageToAllGMs(
//                                dataToSend
//                            )
//                        } catch (e: Exception) {
//                            Log.e("BroadcastKeys", "Failed to send keys: ${e.message}")
//                        }
//                    }
                } catch (e: Exception) {
                    Log.e("Broadcast", "Failed to send Devices list to: ${e.message}")
                }
                delay(3000)
            }


            peerPublicKeyViewModel.viewModelScope.launch(Dispatchers.IO) {
                try {
                    val allKeysFlow = peerPublicKeyViewModel.allKeys // Flow<List<PeerKeyInfo>>
                    allKeysFlow.collect { keyList ->
                        val dataToSend = buildJsonForPeerKeys(keyList)

                        try {
                            MessageRouterHelper.indifiService?.broadcastMessageToAllWfdPeers(
                                dataToSend
                            )
                        } catch (e: Exception) {
                            Log.e("BroadcastKeys", "Failed to send keys: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Broadcast", "Failed to send peer keys list to: ${e.message}")
                }
            }

//            }
        }
    }

    fun saveConnectedDeviceMac(context: Context, identifier: String, macAddress: String) {
        val prefs = context.getSharedPreferences("connected_devices", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("device_mac_map", "{}")
        val jsonObject = jsonString?.let { JSONObject(it) }

        jsonObject?.put(identifier, macAddress)

        prefs.edit().putString("device_mac_map", jsonObject.toString()).apply()
    }

//    private fun buildJsonForDeviceList(deviceList: List<DeviceInfo>): String {
//        val jsonArray = org.json.JSONArray()
//
//        deviceList.forEach { device ->
//            val json = JSONObject()
//            json.put("deviceId", device.deviceId)
//            json.put("name", device.name)
//            json.put("ip", device.ip)
//            json.put("androidId", device.androidId)
//            json.put("isGroupOwner", device.isGroupOwner)
//            json.put("timestamp", device.timestamp)
//            jsonArray.put(json)
//        }
//
//        return "DEVICE_LIST:$jsonArray"
//    }

    private fun checkAndRequestPermissions() {
        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(this, requiredPermissions, permissionRequestCode)
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isLocationEnabled(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (hasAllPermissions()) {
                Toast.makeText(this, "Permissions granted. Ready to proceed.", Toast.LENGTH_SHORT)
                    .show()
            } else {
                Toast.makeText(
                    this,
                    "Some permissions are missing. Wi-Fi Direct may not work.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupIntentFilter() {
        intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
    }

    private fun setupReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        if (!hasAllPermissions()) return

                        try {
                            wifiP2pManager.requestPeers(channel) { peerList ->
                                peers.clear()
                                peers.addAll(peerList.deviceList)

                                peerAdapter.clear()
                                peers.forEach {
                                    peerAdapter.add("${it.deviceName} (${it.deviceAddress})")
                                }
                                peerAdapter.notifyDataSetChanged()

                                Log.d(TAG, "Peers found: ${peers.size}")
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "Missing required permission for peer discovery", e)
                        }
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo =
                            intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                        if (networkInfo != null && networkInfo.isConnected) {
                            wifiP2pManager.requestConnectionInfo(channel) { info ->
                                currentP2pInfo = info
                                if (info.groupFormed && info.groupOwnerAddress != null) {
                                    try {
                                        wifiP2pManager.requestGroupInfo(channel) { group ->
                                            if (group != null) {
//                                                CoroutineScope(Dispatchers.IO).launch {
//                                                    delay(3000) // let soft-AP stabilize
//
//                                                    Log.d(
//                                                        "LC-GO",
//                                                        "Soft-AP is up on wlan0. Starting IndifiService"
//                                                    )
//                                                    withContext(Dispatchers.Main) {
//                                                        Toast.makeText(
//                                                            this@WifiDirectScreen1Activity,
//                                                            "LC-GO Soft-AP is up on wlan0. Starting IndifiService",
//                                                            Toast.LENGTH_SHORT
//                                                        ).show()
//                                                    }
//
//                                                    messageRouterHelper.bindService(this@WifiDirectScreen1Activity)
//                                                    messageRouterHelper.startIndifiService()
//                                                }


                                                isGOViaWFD = group.isGroupOwner
                                                val ssid = group.networkName
                                                val password = group.passphrase
//                                                val goIp = group.owner.ipAddress


                                                Log.d(
                                                    "GO",
                                                    "Group Formed, ssid: $ssid, pass: $password"
                                                )

                                                if (isGOViaWFD) {

                                                    myMembersList =
                                                        group.clientList // The list of devices attached to the group

                                                    collectGroupInfo(group)

                                                    messageRouterHelper.startIndifiService()
                                                    messageRouterHelper.bindService(this@WifiDirectScreen1Activity)

                                                } else {
                                                    messageRouterHelper.startIndifiService()
                                                    messageRouterHelper.bindService(this@WifiDirectScreen1Activity)

                                                    val androidId = Settings.Secure.getString(
                                                        contentResolver,
                                                        Settings.Secure.ANDROID_ID
                                                    )

                                                    // Save own info as GM
                                                    lifecycleScope.launch { // this code block will not run without lifecycleScope
                                                        val myDeviceName =
                                                            Build.MODEL // your device name
                                                        val myDeviceIP =
                                                            getLocalIpAddress() // a helper you should already have

                                                        deviceViewModel.ownDeviceInfo.collect { existing ->
                                                            if (existing == null || existing.name != myDeviceName || existing.androidId != androidId || existing.isGroupOwner) {
                                                                val gmInfo = OwnDeviceInfo(
                                                                    name = myDeviceName,
                                                                    ip = myDeviceIP,
                                                                    androidId = androidId,
                                                                    isGroupOwner = false // GM flag
                                                                )
                                                                deviceViewModel.insertOwnDevice(
                                                                    gmInfo
                                                                )
                                                                Log.d(
                                                                    "OwnInfo",
                                                                    "Saved own info as GM"
                                                                )
                                                            } else {
                                                                Log.d(
                                                                    "OwnInfo",
                                                                    "Own GM info already up-to-date."
                                                                )
                                                            }
                                                        }
                                                    }

                                                    val goIP = info.groupOwnerAddress.hostAddress
                                                    Log.d(
                                                        TAG,
                                                        "I am Group Member. GO IP: $goIP"
                                                    )

                                                    if (goIP != null) {

//                                                        start working from here, try to combine base64key with the deviceInfo table
//                                                        so that it become easy to save and retrive the ip and its key.

                                                        val base64Key =
                                                            KeyStoreManager.getOwnPublicKeyBase64()

                                                        // 2. Send KEY_EXCHANGE to GO
                                                        val keyExchangeJson =
                                                            JSONObject().apply {
                                                                put(
                                                                    "type",
                                                                    "KEY_EXCHANGE"
                                                                )
                                                                put(
                                                                    "publicKey",
                                                                    base64Key
                                                                )
                                                            }

                                                        val helloJson = buildWfdHelloMessage(
                                                            androidId = androidId,
                                                            name = Build.MODEL,
                                                            wfdIp = getLocalIpAddress(),
                                                            ownPublicKeyBase64 = base64Key
                                                        )
                                                        Handler().postDelayed(
                                                            {
                                                                MessageRouterHelper.indifiService?.sendMessageToServerAsWfd(
                                                                    hostAddress = goIP,
                                                                    message = helloJson
                                                                )

                                                                MessageRouterHelper.indifiService?.sendMessageToServerAsWfd(
                                                                    hostAddress = goIP,
                                                                    message = keyExchangeJson.toString()
                                                                )
                                                            }, 5000
                                                        ) // at least 7 seconds required to connect to server
                                                    }
                                                }
                                            } else {
                                                Log.e(TAG, "Group is null.")
                                            }
                                        }
                                    } catch (_: Exception) {
                                    }
                                } else {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        wifiP2pManager.requestConnectionInfo(channel) { retryInfo ->
                                            if (retryInfo.groupFormed && retryInfo.groupOwnerAddress != null) {
                                                try {
                                                    wifiP2pManager.requestGroupInfo(channel) { group ->
                                                        if (group != null) {
                                                            isGOViaWFD = group.isGroupOwner
                                                            myMembersList =
                                                                group.clientList // The list of devices attached to the group

                                                            if (isGOViaWFD) {
                                                                collectGroupInfo(group)

                                                                messageRouterHelper.startIndifiService()
                                                                messageRouterHelper.bindService(this@WifiDirectScreen1Activity)

                                                            } else {

                                                                messageRouterHelper.startIndifiService()
                                                                messageRouterHelper.bindService(this@WifiDirectScreen1Activity)

                                                                val androidId =
                                                                    Settings.Secure.ANDROID_ID

                                                                // Save own info as GM
                                                                lifecycleScope.launch {// this code block will not run without lifecycleScope
                                                                    val myDeviceName =
                                                                        Build.MODEL // your device name
                                                                    val myDeviceIP =
                                                                        getLocalIpAddress() // a helper you should already have

                                                                    deviceViewModel.ownDeviceInfo.collect { existing ->
                                                                        if (existing == null || existing.name != myDeviceName || existing.androidId != androidId || existing.isGroupOwner) {
                                                                            val gmInfo =
                                                                                OwnDeviceInfo(
                                                                                    name = myDeviceName,
                                                                                    ip = myDeviceIP,
                                                                                    androidId = androidId,
                                                                                    isGroupOwner = false // GM flag
                                                                                )
                                                                            deviceViewModel.insertOwnDevice(
                                                                                gmInfo
                                                                            )
                                                                            Log.d(
                                                                                "OwnInfo",
                                                                                "Saved own info as GM"
                                                                            )
                                                                        } else {
                                                                            Log.d(
                                                                                "OwnInfo",
                                                                                "Own GM info already up-to-date."
                                                                            )
                                                                        }
                                                                    }
                                                                }

                                                                val goIP =
                                                                    info.groupOwnerAddress.hostAddress
                                                                Log.d(
                                                                    TAG,
                                                                    "I am Group Member. GO IP: $goIP"
                                                                )
                                                                if (goIP != null) {
                                                                    val base64Key =
                                                                        KeyStoreManager.getOwnPublicKeyBase64()

                                                                    val keyExchangeJson =
                                                                        JSONObject().apply {
                                                                            put(
                                                                                "type",
                                                                                "KEY_EXCHANGE"
                                                                            )
                                                                            put(
                                                                                "publicKey",
                                                                                base64Key
                                                                            )
                                                                        }

                                                                    val helloJson =
                                                                        buildWfdHelloMessage(
                                                                            androidId = androidId,
                                                                            name = Build.MODEL, // or any custom GM name
                                                                            wfdIp = getLocalIpAddress(),
                                                                            ownPublicKeyBase64 = base64Key // a helper that returns IP of this GM
                                                                        )
                                                                    Handler().postDelayed(
                                                                        {
                                                                            MessageRouterHelper.indifiService?.sendMessageToServerAsWfd(
                                                                                hostAddress = goIP,
                                                                                message = helloJson
                                                                            )

                                                                            MessageRouterHelper.indifiService?.sendMessageToServerAsWfd(
                                                                                hostAddress = goIP,
                                                                                message = keyExchangeJson.toString()
                                                                            )
                                                                        }, 5000
                                                                    ) // at least 7 seconds required to connect to server

                                                                }
                                                            }
                                                        } else {
                                                            Log.e(TAG, "Group is null.")
                                                        }
                                                    }
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                    }, 2000)
                                }
                            }
                        } else {
                            Log.d(TAG, "P2P connection dropped")
                            Toast.makeText(
                                this@WifiDirectScreen1Activity, "Disconnected", Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                }
            }
        }
    }

    fun getLocalIpAddress(): String {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (intf in interfaces) {
            val addrs = intf.inetAddresses
            for (addr in addrs) {
                if (!addr.isLoopbackAddress && addr is Inet4Address) {
                    return addr.hostAddress!!
                }
            }
        }
        return "0.0.0.0"
    }


//    private fun startSilentServer() {
//        MessageRouterHelper.indifiService?.startPrefSyncServer(
//            this@WifiDirectScreen1Activity,
//            currentP2pInfo!!
//        )
//    }

    private fun startServer() {
        MessageRouterHelper.indifiService?.startChatServer(onMessageReceived = {})
    }

    private fun collectGroupInfo(group: WifiP2pGroup) {
        deviceViewModel.viewModelScope.launch(Dispatchers.IO) {
            val goName = group.networkName.substringAfterLast("-") //DIRECT-xT-Infinix SMART 6
            val goIP = currentP2pInfo!!.groupOwnerAddress.hostAddress
            val macAddress = group.owner.deviceAddress
            val timestamp = System.currentTimeMillis()
            val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val base64Key = KeyStoreManager.getOwnPublicKeyBase64()
            val groupId = UUID.randomUUID().toString()
            val sharedPreferences = getSharedPreferences("group_prefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().putString("groupId", groupId).apply()

            if (goIP != null) {
                val duplicate = deviceViewModel.isDuplicateDevice(
                    name = goName, androidId = androidId
                )

                if (!duplicate) {
                    val newDevice = DeviceInfo(
                        name = goName,
                        wfdIp = goIP,
                        lcIp = "",
                        androidId = androidId,
                        isGroupOwner = true,
                        timestamp = timestamp,
                        base64Key = base64Key,
                        groupId = groupId,
                        isRelayDevice = false
                    )
                    deviceViewModel.insert(newDevice)
                    Log.d("GO_RECEIVER", "Inserted device: $newDevice")
                }
            }

            deviceViewModel.ownDeviceInfo.collect { existing ->
                if (existing == null || existing.name != goName || existing.androidId != androidId) {
                    val goInfo = OwnDeviceInfo(
                        name = goName, ip = goIP!!, androidId = androidId, isGroupOwner = true
                    )
                    deviceViewModel.insertOwnDevice(goInfo)
                } else {
                    Log.d("OwnInfo", "Own GO info already up-to-date.")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isReceiverRegistered) {
            registerReceiver(receiver, intentFilter)
            isReceiverRegistered = true
        }
    }

    override fun onPause() {
        super.onPause()
        if (isReceiverRegistered) {
            unregisterReceiver(receiver)
            isReceiverRegistered = false
        }
    }

    override fun onStop() {
        super.onStop()
        isChatActivityLaunched = false
        if (isReceiverRegistered) {
            unregisterReceiver(receiver)
            isReceiverRegistered = false
        }
    }

    private fun discoverPeers() {
        try {
            wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(
                        this@WifiDirectScreen1Activity, "Peer discovery started", Toast.LENGTH_SHORT
                    ).show()
                    Log.d(TAG, "Peer discovery started successfully")
                }

                override fun onFailure(reason: Int) {
                    val message = when (reason) {
                        WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct not supported"
                        WifiP2pManager.BUSY -> "System busy, try again"
                        WifiP2pManager.ERROR -> "Internal error"
                        else -> "Unknown error"
                    }
                    Toast.makeText(
                        this@WifiDirectScreen1Activity,
                        "Discovery failed: $message",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e(TAG, "Discovery failed: $message")
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing required permission for peer discovery", e)
        }
    }

    fun getWlan0Ip(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (iface in interfaces) {
                if (iface.name.equals("wlan0", ignoreCase = true)) {
                    val addresses = iface.inetAddresses
                    for (address in addresses) {
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            return address.hostAddress
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }
}
