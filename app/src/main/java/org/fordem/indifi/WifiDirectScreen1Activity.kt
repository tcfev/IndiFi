package org.fordem.indifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.fordem.indifi.Constants.connectedGMIPs
import org.fordem.indifi.WifiDirectActivity.Companion
import org.fordem.indifi.databinding.ActivityWifiDirectScreen1Binding
import org.json.JSONObject

class WifiDirectScreen1Activity : AppCompatActivity() {
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
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CHANGE_WIFI_STATE
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        checkAndRequestPermissions()

        peerAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        binding.lvPeers.adapter = peerAdapter

        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)
        setupIntentFilter()
        setupReceiver()


        TcpHelper.startChatServer { _, _ ->
        }

        binding.btnDisconnect.setOnClickListener {
            wifiP2pManager.requestGroupInfo(channel) { group ->
                if (group != null) {
                    if (group.isGroupOwner) {
                        // Device is GO → prompt user before disbanding group
                        AlertDialog.Builder(this)
                            .setTitle("Disband Group?")
                            .setMessage("You are the Group Owner. Disbanding will disconnect all members. Proceed?")
                            .setPositiveButton("Yes") { _, _ ->
                                wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                                    override fun onSuccess() {
                                        getSharedPreferences("group_info", MODE_PRIVATE).edit().clear().apply()

                                        Log.d("WFD", "Group disbanded (GO)")
                                        Toast.makeText(this@WifiDirectScreen1Activity, "Group disbanded", Toast.LENGTH_SHORT).show()
                                    }

                                    override fun onFailure(reason: Int) {
                                        Log.e("WFD", "Failed to disband group: $reason")
                                        Toast.makeText(this@WifiDirectScreen1Activity, "Failed to disband group", Toast.LENGTH_SHORT).show()
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
                                Toast.makeText(this@WifiDirectScreen1Activity, "Disconnected from group", Toast.LENGTH_SHORT).show()
                            }

                            override fun onFailure(reason: Int) {
                                Log.e("WFD", "Failed to leave group: $reason")
                                Toast.makeText(this@WifiDirectScreen1Activity, "Failed to disconnect", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this, "Please Turn on Wifi", Toast.LENGTH_LONG)
                        .show()
                    val panelIntent = Intent(Settings.ACTION_WIFI_SETTINGS)
                    startActivity(panelIntent)
                }

                else -> {
                    Toast.makeText(
                        this,
                        "All checks passed. Starting Wi-Fi Direct...",
                        Toast.LENGTH_SHORT
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
//                wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
//                    override fun onSuccess() {
//                        Log.d(TAG, " Old group removed before connecting")

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
//                    }

//                    override fun onFailure(reason: Int) {
//                        Log.w(TAG, "Failed to remove old group (possibly none existed): $reason")
//
//                        // Proceed anyway
//                        wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
//                            override fun onSuccess() {
//                                Toast.makeText(
//                                    this@WifiDirectScreen1Activity,
//                                    "Connecting to ${device.deviceName}",
//                                    Toast.LENGTH_SHORT
//                                ).show()
//                            }
//
//                            override fun onFailure(reason: Int) {
//                                Toast.makeText(
//                                    this@WifiDirectScreen1Activity,
//                                    "Connection failed: $reason",
//                                    Toast.LENGTH_SHORT
//                                ).show()
//                                Log.e(TAG, "Connection failed: $reason")
//                            }
//                        })
//                    }
//                })
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing required permission for peer discovery", e)
            }
        }

        Constants.deviceConnectionCallback = {
            val sharedPref = getSharedPreferences("group_info", Context.MODE_PRIVATE)
            sharedPref.edit().apply {
                putString(
                    "GM_IP",
                    it
                )
                apply()
            }

            // Broadcast all prefs
            val allPrefs = sharedPref.all.mapValues { it.value.toString() }
            val jsonData = JSONObject(allPrefs).toString()

//            Handler(Looper.myLooper()!!).postDelayed({
            TcpHelper.broadcastToGMs(jsonData)
//            }, 20000)

//            TcpHelper.startSilentReceiver(this)
        }
    }

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
        val lm = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        return lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
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

//                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
//                        val networkInfo =
//                            intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
//
//                        if (networkInfo != null && networkInfo.isConnected /*&& !isChatActivityLaunched*/) {
//                            wifiP2pManager.requestConnectionInfo(channel) { info ->
//                                if (info.groupFormed && info.groupOwnerAddress != null) {
//                                    isChatActivityLaunched = true
//
//                                    if (!info.isGroupOwner) {
//                                        info.groupOwnerAddress.hostAddress?.let {
//                                            TcpHelper.sendMessageToServer(
//                                                it, "New device connected"
//                                            )
//                                        }
//
//                                        Handler(mainLooper).postDelayed({
//                                            TcpHelper.startSilentReceiver(applicationContext)
//                                        }, 10000)
//                                    }
//
////                                    launchChatActivity(info)
//                                } else {
//                                    Handler(Looper.getMainLooper()).postDelayed({
//                                        wifiP2pManager.requestConnectionInfo(channel) { retryInfo ->
//                                            if (retryInfo.groupFormed && retryInfo.groupOwnerAddress != null) {
////                                                launchChatActivity(retryInfo)
//                                            }
//                                        }
//                                    }, 2000) // Wait 2 seconds
//                                }
//                            }
//
//                        } else {
//                            Log.d(TAG, "P2P connection dropped")
//                            Toast.makeText(
//                                this@WifiDirectScreen1Activity,
//                                "Disconnected",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                        }
//                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo =
                            intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                        if (networkInfo != null && networkInfo.isConnected) {
                            wifiP2pManager.requestConnectionInfo(channel) { info ->
                                if (info.groupFormed && info.groupOwnerAddress != null) {
                                    try {
                                        wifiP2pManager.requestGroupInfo(channel) { group ->
                                            if (group != null) {
                                                val isGO = group.isGroupOwner
                                                val myDevice =
                                                    group.owner // The device who created the group

                                                if (isGO) {
//                                                Log.d(TAG, "I am the Group Owner.")
//                                                Constants.deviceConnectionCallback(info.groupOwnerAddress.toString())
                                                } else {
                                                    val goIP = info.groupOwnerAddress.hostAddress
                                                    Log.d(TAG, "I am Group Member. GO IP: $goIP")

                                                    goIP?.let {
                                                        TcpHelper.sendMessageToServer(
                                                            it,
                                                            "New device connected"
                                                        )
                                                    }

                                                    Handler(Looper.getMainLooper()).postDelayed({
                                                        TcpHelper.startSilentReceiver(
                                                            applicationContext
                                                        )
                                                    }, 20000)
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
                                                // Retry logic or launchChatActivity
                                            }
                                        }
                                    }, 2000)
                                }
                            }

                        } else {
                            Log.d(TAG, "P2P connection dropped")
                            Toast.makeText(
                                this@WifiDirectScreen1Activity,
                                "Disconnected",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                }
            }
        }
    }

    private fun launchChatActivity(info: WifiP2pInfo) {
        if (info.groupFormed && info.groupOwnerAddress != null) {

            // 🔍 Show role as Toast
            val role = if (info.isGroupOwner) "Group Owner (GO)" else "Group Member (GM)"
            Toast.makeText(this, "You are the $role", Toast.LENGTH_LONG).show()
            Log.d(TAG, "Connection established. Role: $role")

            val intent = Intent(this@WifiDirectScreen1Activity, ChatActivity::class.java).apply {
                putExtra("isGroupOwner", info.isGroupOwner)
                putExtra("groupOwnerAddress", info.groupOwnerAddress?.hostAddress)
            }
            startActivity(intent)
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

    private fun isWifiEnabled(context: Context): Boolean {
        val wifiManager =
            context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }
}
