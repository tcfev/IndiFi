package com.example.network

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.utils.PermissionUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fordem.indifi.ui.activity.WifiDirectScreen1Activity
import org.fordem.indifi.ui.encryption.KeyStoreManager
import org.fordem.indifi.ui.model.DeviceInfo
import org.fordem.indifi.ui.model.OwnDeviceInfo
import org.fordem.indifi.ui.utils.Constants.isGOViaWFD
import org.fordem.indifi.ui.utils.Constants.myMembersList
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.utils.getLocalIpAddress
import org.fordem.indifi.ui.utils.isWifiEnabled
import org.fordem.indifi.ui.viewmodel.DeviceInfoViewModel
import org.json.JSONObject

object NetworkManager {

    private const val TAG = "NetworkManager"

    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var receiverRegistered = false

    private val peers = mutableListOf<WifiP2pDevice>()
    private var peerAdapter: ArrayAdapter<String>? = null
    private var currentP2pInfo: WifiP2pInfo? = null

    private var flowCollectionJob: Job? = null

    private var lifecycleObserver: DefaultLifecycleObserver? = null

    private lateinit var intentFilter: IntentFilter

    fun initializeManager(context: Context): Pair<WifiP2pManager, WifiP2pManager.Channel> {
        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        val ch = manager.initialize(appContext, appContext.mainLooper, null)
        wifiP2pManager = manager
        channel = ch
        return manager to ch
    }

    @SuppressLint("MissingPermission")
    fun discoverPeers(
        activity: Activity,
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel
    ) {
        if (!isWifiEnabled(activity)) {
            Toast.makeText(activity, "Please Turn on Wifi", Toast.LENGTH_LONG).show()
            activity.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            return
        }

        Toast.makeText(activity, "All checks passed. Starting Wi-Fi Direct...", Toast.LENGTH_SHORT)
            .show()
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Discovery Started")
            }

            override fun onFailure(reason: Int) {
                Log.e(TAG, "Discovery Failed: $reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun disconnect(activity: Activity, manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
        manager.requestGroupInfo(channel) { group ->
            if (group != null) {
                if (group.isGroupOwner) {
                    AlertDialog.Builder(activity)
                        .setTitle("Disband Group?")
                        .setMessage("You are the Group Owner. Disbanding will disconnect all members. Proceed?")
                        .setPositiveButton("Yes") { _, _ ->
                            removeGroup(
                                activity,
                                manager,
                                channel,
                                "Group disbanded",
                                "Failed to disband group"
                            )
                        }
                        .setNegativeButton("No", null)
                        .show()
                } else {
                    removeGroup(
                        activity,
                        manager,
                        channel,
                        "Disconnected from group",
                        "Failed to disconnect"
                    )
                }
            } else {
                Toast.makeText(activity, "No group info available", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeGroup(
        context: Context,
        manager: WifiP2pManager,
        channel: WifiP2pManager.Channel,
        successMsg: String,
        failMsg: String
    ) {
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                context.getSharedPreferences("group_info", Context.MODE_PRIVATE).edit().clear()
                    .apply()
                Toast.makeText(context, successMsg, Toast.LENGTH_SHORT).show()
            }

            override fun onFailure(reason: Int) {
                Toast.makeText(context, failMsg, Toast.LENGTH_SHORT).show()
            }
        })
    }

    fun registerReceiver(
        activity: Activity,
        deviceInfoViewModel: DeviceInfoViewModel,
        lifecycleOwner: LifecycleOwner
    ) {
        if (receiverRegistered) return
        val appContext = activity.applicationContext

        receiver = object : BroadcastReceiver() {
            @SuppressLint("MissingPermission")
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "Broadcast received: ${intent?.action}")
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION ->
                        handlePeersChanged(activity)

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION ->
                        handleConnectionChanged(activity, deviceInfoViewModel, lifecycleOwner)
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        appContext.registerReceiver(receiver, filter)
        receiverRegistered = true

        lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                unregisterReceiver(appContext)
                owner.lifecycle.removeObserver(this)
                lifecycleObserver = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver!!)
    }

    fun unregisterReceiver(context: Context) {
        if (receiverRegistered && receiver != null) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver not registered or already unregistered", e)
            }
            receiver = null
            receiverRegistered = false
        }
        flowCollectionJob?.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun handlePeersChanged(activity: Activity) {
        Log.d(TAG, "Available P2P peer list has changed: found, lost, or updated")
        val manager = wifiP2pManager ?: return
        val ch = channel ?: return

//        if (!hasAllPermissions(activity)) return

        try {
            manager.requestPeers(ch) { peerList ->
                peers.clear()
                peers.addAll(peerList.deviceList)
                peerAdapter?.apply {
                    clear()
                    peers.forEach { add("${it.deviceName} (${it.deviceAddress})") }
                    notifyDataSetChanged()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing required permission for peer discovery.", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleConnectionChanged(
        activity: Activity,
        deviceInfoViewModel: DeviceInfoViewModel,
        lifecycleOwner: LifecycleOwner
    ) {
//        Log.d(TAG, "Connection state changed")
//        val manager = wifiP2pManager ?: return
//        val ch = channel ?: return
//        val networkInfo = activity.intent?.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
//
//        if (networkInfo?.isConnected == true) {
//            manager.requestConnectionInfo(ch) { info ->
//                currentP2pInfo = info
//                if (info.groupFormed && info.groupOwnerAddress != null) {
//                    manager.requestGroupInfo(ch) { group ->
//                        if (group != null) {
//                            isGOViaWFD = group.isGroupOwner
//                            myMembersList = group.clientList
//                            if (isGOViaWFD) {
//                                collectGroupInfo(deviceInfoViewModel, group, lifecycleOwner)
//                            } else {
//                                saveOwnInfoAsGM(deviceInfoViewModel, lifecycleOwner)
//                                info.groupOwnerAddress.hostAddress?.let { sendHelloAndKeyExchange(it) }
//                            }
//                        }
//                    }
//                }
//            }
//        } else {
//            Toast.makeText(activity, "P2P connection dropped", Toast.LENGTH_SHORT).show()
//        }
    }

    private fun saveOwnInfoAsGM(
        deviceInfoViewModel: DeviceInfoViewModel,
        lifecycleOwner: LifecycleOwner
    ) {
        flowCollectionJob?.cancel()
        flowCollectionJob = lifecycleOwner.lifecycleScope.launch {
            val myDeviceName = Build.MODEL
            val myDeviceIP = getLocalIpAddress()
            deviceInfoViewModel.ownDeviceInfo.collect { existing ->
                if (existing == null || existing.name != myDeviceName || existing.ip != myDeviceIP || existing.isGroupOwner) {

                    val gmInfo = OwnDeviceInfo(
                        name = myDeviceName,
                        ip = myDeviceIP,
                        isGroupOwner = false
                    )
                    deviceInfoViewModel.insertOwnDevice(gmInfo)
                }
            }
        }
    }

    private fun collectGroupInfo(
        deviceInfoViewModel: DeviceInfoViewModel,
        group: WifiP2pGroup,
        lifecycleOwner: LifecycleOwner
    ) {
        val goName = group.networkName.substringAfterLast("-")
        val goIP = currentP2pInfo?.groupOwnerAddress?.hostAddress ?: return
        val timestamp = System.currentTimeMillis()

        deviceInfoViewModel.findRecentDevice(goName, goIP, timestamp)
            .observe(lifecycleOwner) { existing ->
                if (existing == null) {
                    val device = DeviceInfo(
                        name = goName,
                        ip = goIP,
                        isGroupOwner = true,
                        timestamp = timestamp
                    )
                    deviceInfoViewModel.insert(device)
                }
            }
        saveOwnInfoAsGO(deviceInfoViewModel, goName, goIP, lifecycleOwner)
    }

    private fun saveOwnInfoAsGO(
        deviceInfoViewModel: DeviceInfoViewModel,
        name: String,
        ip: String,
        lifecycleOwner: LifecycleOwner
    ) {
        flowCollectionJob?.cancel()
        flowCollectionJob = lifecycleOwner.lifecycleScope.launch {
            deviceInfoViewModel.ownDeviceInfo.collect { existing ->
                if (existing == null || existing.name != name || existing.ip != ip || !existing.isGroupOwner) {
                    val gmInfo = OwnDeviceInfo(
                        name = name,
                        ip = ip,
                        isGroupOwner = false
                    )
                    deviceInfoViewModel.insertOwnDevice(gmInfo)
                }
            }
        }
    }

    fun startServer() {
        val server = MessageRouterHelper.messageRouterService?.serverSocket
        if (server == null || server.isClosed) {
            Log.d("TCP", "Starting server...")
            MessageRouterHelper.messageRouterService?.startChatServer(
                onMessageReceived = { message ->
                    // You may log or show the message
                    Log.d("TCP", "Received: $message")
                }
            )
        } else {
            Log.d("TCP", "Server already running")
        }

//    private fun sendHelloAndKeyExchange(goIP: String) {
//        val helloMessage = CommunicationHandler.buildHelloMessage(Build.MODEL, getLocalIpAddress())
//        Handler(Looper.getMainLooper()).postDelayed({
//            MessageRouterHelper.messageRouterService?.sendMessageToServer(goIP, helloMessage)
//            val keyExchangeJson = JSONObject().apply {
//                put("type", "KEY_EXCHANGE")
//                put("publicKey", KeyStoreManager.getOwnPublicKeyBase64())
//            }
//            MessageRouterHelper.messageRouterService?.sendMessageToServer(goIP, keyExchangeJson.toString())
//        }, 5000)
//    }

        fun setPeerAdapter(adapter: ArrayAdapter<String>) {
            peerAdapter = adapter
        }

        fun getPeers(): List<WifiP2pDevice> = peers
    }
}