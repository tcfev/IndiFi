package org.fordem.indifi.ui.activity

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
import android.net.wifi.p2p.WifiP2pDeviceList
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
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.utils.PermissionUtils
import com.example.utils.PermissionUtils.hasAllPermissions
import com.example.utils.PermissionUtils.isLocationEnabled
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fordem.indifi.ui.utils.Constants
import org.fordem.indifi.databinding.ActivityWifiDirectScreen1Binding
import org.fordem.indifi.ui.model.DeviceInfo
import org.fordem.indifi.ui.dao.DeviceInfoDao
import org.fordem.indifi.ui.viewmodel.DeviceInfoViewModel
import org.fordem.indifi.ui.model.OwnDeviceInfo
import org.fordem.indifi.ui.dao.PeerPublicKeyDao
import org.fordem.indifi.ui.encryption.KeyStoreManager
import org.fordem.indifi.ui.utils.Constants.myMembersList
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.utils.buildJsonForDeviceList
import org.fordem.indifi.ui.utils.isWifiEnabled
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

@Suppress("DEPRECATION")
@SuppressLint("MissingPermission")
@AndroidEntryPoint
class WifiDirectScreen1Activity : BaseActivity() {
    private val deviceViewModel: DeviceInfoViewModel by viewModels()

    private var currentP2pInfo: WifiP2pInfo? = null
    private val binding: ActivityWifiDirectScreen1Binding by lazy {
        ActivityWifiDirectScreen1Binding.inflate(layoutInflater)
    }

    private val requiredPermissions = PermissionUtils.getRequiredDevicePermissions()

    private val permissionRequestCode = 100

    private lateinit var peerAdapter: ArrayAdapter<String>

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private lateinit var intentFilter: IntentFilter

    private val peers = mutableListOf<WifiP2pDevice>()

    private val TAG = "WifiDirectScreen1"
    private var isReceiverRegistered = false
    private var isChatActivityLaunched = false

    var gmName = ""
    var gmMac = ""

    @Inject
    lateinit var deviceInfoDao: DeviceInfoDao

    @Inject
    lateinit var peerPublicKeyDao: PeerPublicKeyDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupNavigationUI(binding.root, "Peer Scan")

        checkAndRequestPermissions()

        peerAdapter = ArrayAdapter(this, R.layout.simple_list_item_1, ArrayList())
        binding.lvPeers.adapter = peerAdapter

        wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)

        ///// TODO:  : Move this logic to NetworkManager
        setupIntentFilter()
        setupReceiver()

        binding.btnDisconnect.setOnClickListener {
            disconnectOrDisbandP2pGroup()
        }

        binding.btnDiscoverPeers.setOnClickListener {
            if(checkPreconditions()) {
                discoverPeers()
            }
        }

        binding.lvPeers.setOnItemClickListener { _, _, devicePositionIndex, _ ->
            connectToDevice(devicePositionIndex)
        }

        Constants.deviceConnectionCallback = { gmIP: String ->
            Log.d("WFD", "Device connected callback triggered with IP: $gmIP")
            val lastMember = myMembersList.last()
            val gmNumber = myMembersList.size + 1
            gmName = lastMember.deviceName
            gmMac = lastMember.deviceAddress
            val timestamp = System.currentTimeMillis()


            val device = DeviceInfo(
                name = gmName,
                ip = gmIP,
                isGroupOwner = false,
                timestamp = timestamp
            )

            deviceViewModel.viewModelScope.launch(Dispatchers.Main) {
                val exists =
                    deviceViewModel.isDuplicateDevice(device.name, device.ip, device.timestamp)
                if (!exists) {
                    Log.d("DB", "Inserting new device: Name=${device.name}, IP=${device.ip}, Timestamp=${device.timestamp}")
                    deviceViewModel.insert(device)

                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val allDevices = deviceViewModel.allDevices
                            allDevices.collect { deviceList ->
                                val dataToSend = buildJsonForDeviceList(deviceList)

                                try {
                                    MessageRouterHelper.messageRouterService?.broadcastMessageToAllGMs(
                                        dataToSend
                                    )

                                } catch (e: Exception) {
                                    Log.e("Broadcast", "Failed to send to: ${e.message}")
                                }
                            }
                        } catch (_: Exception) {
                        }
                    }

                } else {
                    // TODO:  : Check if the existing device is recent enough, Is this failing?
                    Log.d(
                        "DB",
                        "Device already exists with name ${device.name}, IP ${device.ip}, recent timestamp"
                    )
                }
            }
        }
    }

    private fun disconnectOrDisbandP2pGroup() {
        wifiP2pManager.requestGroupInfo(channel) { group ->
            if (group != null) {
                if (group.isGroupOwner) {
                    // Device is GO → prompt user before disbanding group
                    AlertDialog.Builder(this)
                        .setTitle("Disband Group?")
                        .setMessage("You are the Group Owner. Disbanding will disconnect all members. Proceed?")
                        .setPositiveButton("Yes") { _, _ ->
                            wifiP2pManager.removeGroup(
                                channel,
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
                        }
                        .setNegativeButton("No", null)
                        .show()
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

    @SuppressLint("MissingPermission")
    private fun checkPreconditions(): Boolean {
        return when {
            !hasAllPermissions(this, requiredPermissions) -> {
                Toast.makeText(this, "Please grant required permissions", Toast.LENGTH_SHORT).show()
                false
            }

            !isLocationEnabled(this) -> {
                Toast.makeText(this, "Please enable Location Services", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                false
            }

            !isWifiEnabled(this) -> {
                Toast.makeText(this, "Please Turn on Wifi", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                false
            }

            else -> {
                Toast.makeText(this, "All checks passed. Starting Wi-Fi Direct...", Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    private fun connectToDevice(position: Int) {
        val device = peers[position]
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC

            // TODO:  : Large basis of paper is based on this priority determination
//                groupOwnerIntent = 15 // 0–15 → 15 = strongly prefer to be GO
            groupOwnerIntent = 0 // Strongly prefer NOT to be GO — but this is the initiator
        }

        try {
            // TODO:  : Should we use wifiP2pManager.createGroup() instead?
            wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(
                        this@WifiDirectScreen1Activity,
                        "Connecting to ${device.deviceName}",
                        Toast.LENGTH_SHORT
                    ).show()
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

    private fun checkAndRequestPermissions() {
        if (!hasAllPermissions(this, requiredPermissions)) {
            ActivityCompat.requestPermissions(this, requiredPermissions, permissionRequestCode)
        }
    }

//    private fun hasAllPermissions(permissions: Array<String>): Boolean {
//        return requiredPermissions.all {
//            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
//        }
//    }

//    private fun isLocationEnabled(): Boolean {
//        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
//        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
//    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            if (hasAllPermissions(this, requiredPermissions)) {
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
                Log.d(TAG, "Broadcast received: ${intent?.action}")
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION -> {
                        Log.d(TAG, "P2P discovery state changed")
                        handleWifiP2pDiscoveryChanged(intent)
                    }

                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        Log.d(TAG, "P2P peers changed")
                        handleWifiP2pPeersChanged(intent)
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        Log.d(TAG, "P2P connection state changed")
                        handleWifiP2pConnectionChange(intent)
                    }
                }
            }
        }
    }

    fun handleWifiP2pDiscoveryChanged(intent: Intent) {
        val state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1)
        when (state) {
            WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED -> {
                Log.d(TAG, "P2P discovery started")
                Toast.makeText(this, "P2P Discovery Started", Toast.LENGTH_SHORT).show()
            }
            WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED -> {
                Log.d(TAG, "P2P discovery stopped")
                Toast.makeText(this, "P2P Discovery Stopped", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Log.d(TAG, "Unknown P2P discovery state: $state")
            }
        }
    }

    fun handleWifiP2pPeersChanged(intent: Intent) {
        Log.d(TAG, "Handling P2P peers changed")
        val peerListExtra = intent.getParcelableExtra<WifiP2pDeviceList>(WifiP2pManager.EXTRA_P2P_DEVICE_LIST)

        Log.d(TAG, "////// P2P PEERS DETAILS //////")
        Log.d(TAG, "Peers found: ${peers.size}")
        Log.d(TAG, "Peer Info: $peerListExtra")
        Log.d(TAG, "/////////////////////////////////")

        if (!hasAllPermissions(this, requiredPermissions)) return

        try {
            wifiP2pManager.requestPeers(channel) { peerList ->
                peers.clear()
                peers.addAll(peerList.deviceList)

                peerAdapter.clear()
                peers.forEach {
                    peerAdapter.add("${it.deviceName} (${it.deviceAddress})")
                }
                peerAdapter.notifyDataSetChanged()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing required permission for peer discovery", e)
        }
    }

    fun handleWifiP2pConnectionChange(intent: Intent) {
        Log.d(TAG, "Handling P2P connection change")
        val networkInfoExtra =
            intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
        val wifiP2pInfoExtra =
            intent.getParcelableExtra<WifiP2pInfo>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
        val wifiP2pGroupExtra = intent.getParcelableExtra<WifiP2pGroup>(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)

        Log.d(TAG, "////// P2P NETWORK DETAILS //////")
        Log.d(TAG, "Network Info: $networkInfoExtra")
        Log.d(TAG, "Wifi P2P Info: $wifiP2pInfoExtra")
        Log.d(TAG, "Wifi P2P Group: $wifiP2pGroupExtra")
        Log.d(TAG, "isGOViaWFD: ${wifiP2pGroupExtra?.isGroupOwner}")
        Log.d(TAG, "/////////////////////////////////")

        val networkInfo =
            intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
        if (networkInfo != null && networkInfo.isConnected) {
            wifiP2pManager.requestConnectionInfo(channel) { info ->
                currentP2pInfo = info
                // TODO:  
                // If the current device is the p2p group owner, WifiP2pInfo.groupOwnerAddress is anonymized unless
                // the caller holds the android.Manifest.permission#LOCAL_MAC_ADDRESS permission.
                if (info.groupFormed && info.groupOwnerAddress != null) {
                    try {
                        wifiP2pManager.requestGroupInfo(channel) { group ->
                            if (group != null) {
                                handleGroupInfo(group, info)
                            } else {
                                Log.e(TAG, "Group is null.")
                            }
                        }
                    } catch (_: Exception) {
                    }
                } else {
                    retryRequestConnectionInfoWithDelay(2000)
                }
            }
        } else {
            // TODO:  : Does this really mean disconnection? Check if this is firing on initial connection also
            Log.d(TAG, "P2P connection dropped")
            Toast.makeText(
                this@WifiDirectScreen1Activity,
                "Disconnected",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun handleGroupInfo(group: WifiP2pGroup, info: WifiP2pInfo) {
//        isGOViaWFD = group.isGroupOwner
        myMembersList = group.clientList

        if (group.isGroupOwner) {
            collectGroupInfo(group)
        } else {
            saveOwnInfoAsGM()
            val goIP = info.groupOwnerAddress.hostAddress
            Log.d(TAG, "I am Group Member. GO IP: $goIP")
            if (goIP != null) {
                buildHelloAndKeyExchange(goIP)
            }
        }
    }

    private fun retryRequestConnectionInfoWithDelay(delayMs: Long) {
        Handler(Looper.getMainLooper()).postDelayed({
            wifiP2pManager.requestConnectionInfo(channel) { retryInfo ->
                if (retryInfo.groupFormed && retryInfo.groupOwnerAddress != null) {
                    try {
                        wifiP2pManager.requestGroupInfo(channel) { group ->
                            if (group != null) {
                                handleGroupInfo(group, retryInfo)
                            } else {
                                Log.e(TAG, "Group is null.")
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }, delayMs)
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

    fun buildHelloMessage(name: String, ip: String): String {
        val json = JSONObject()
        json.put("type", "HELLO")
        json.put("name", name)
        json.put("ip", ip)
        json.put("isGroupOwner", false)
        json.put("timestamp", System.currentTimeMillis())
        return json.toString()
    }

//    private fun startSilentServer() {
//        MessageRouterHelper.messageRouterService?.startPrefSyncServer(
//            this@WifiDirectScreen1Activity,
//            currentP2pInfo!!
//        )
//    }

    private fun startServer() {
        MessageRouterHelper.messageRouterService?.startChatServer(
            onMessageReceived = {
            }
        )
    }

    private fun buildHelloAndKeyExchange(goIP: String) {
        Handler().postDelayed({
            MessageRouterHelper.messageRouterService?.sendMessageToServer(
                hostAddress = goIP,
                message = buildHelloMessage(Build.MODEL, getLocalIpAddress())
            )

            val keyExchangeJson = JSONObject().apply {
                put("type", "KEY_EXCHANGE")
                put("publicKey", KeyStoreManager.getOwnPublicKeyBase64())
            }

            MessageRouterHelper.messageRouterService?.sendMessageToServer(
                hostAddress = goIP,
                message = keyExchangeJson.toString()
            )
        }, 5000) // delay for connection to stabilize
        // at least 7 seconds required to connect to server
    }

    private fun collectGroupInfo(group: WifiP2pGroup) {
        val owner = group.owner
        val goName = group.networkName.substringAfterLast("-") //DIRECT-xT-Infinix SMART 6
        val goMac = owner.deviceAddress
        val goIP = currentP2pInfo!!.groupOwnerAddress.hostAddress
        val timestamp = System.currentTimeMillis()

        val isThisDeviceGO = currentP2pInfo?.isGroupOwner == true

        if (goIP != null) {
            deviceViewModel.findRecentDevice(goName, goIP, timestamp)
                .observe(this) { existingDevice ->
                    if (existingDevice == null) {
                        val device = DeviceInfo(
                            name = goName,
                            ip = goIP,
                            isGroupOwner = true,
                            timestamp = timestamp
                        )
                        Log.d("DB", "Inserting new GO device: Name=$goName, IP=$goIP, Timestamp=$timestamp")
                        deviceViewModel.insert(device)
                    } else {
                        Log.d("DB", "Device with name $goName and IP $goIP already added recently.")
                    }
                }
        }

        lifecycleScope.launch {
            deviceViewModel.ownDeviceInfo.collect { existing ->
                if (existing == null ||
                    existing.name != goName ||
                    existing.ip != goIP ||
                    !existing.isGroupOwner
                ) {
                    val goInfo = OwnDeviceInfo(
                        name = goName,
                        ip = goIP!!,
                        isGroupOwner = true
                    )
                    deviceViewModel.insertOwnDevice(goInfo)
                } else {
                    Log.d("OwnInfo", "Own GO info already up-to-date.")
                }
            }
        }
    }

    private fun saveOwnInfoAsGM() {
        lifecycleScope.launch {
            val myDeviceName = Build.MODEL
            val myDeviceIP = getLocalIpAddress()
            deviceViewModel.ownDeviceInfo.collect { existing ->
                if (existing == null || existing.name != myDeviceName || existing.ip != myDeviceIP || existing.isGroupOwner) {
                    val gmInfo = OwnDeviceInfo(name = myDeviceName, ip = myDeviceIP, isGroupOwner = false)
                    Log.d(TAG, "Saving own info as GM: $gmInfo")
                    deviceViewModel.insertOwnDevice(gmInfo)
                    Log.d("OwnInfo", "Saved own info as GM")
                } else {
                    Log.d("OwnInfo", "Own GM info already up-to-date.")
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
            wifiP2pManager.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Stopped peer discovery successfully")
                }
                override fun onFailure(reason: Int) {
                    Log.e(TAG, "Failed to stop peer discovery: $reason")
                }
            })

            wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(
                        this@WifiDirectScreen1Activity,
                        "Peer discovery started",
                        Toast.LENGTH_SHORT
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
}
