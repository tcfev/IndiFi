package org.fordem.indifi

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.AndroidViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class WifiDirectViewModel @Inject constructor(
    private val context: Application
) : AndroidViewModel(context) {

    private val _discoveredDevices = mutableListOf<String>()
    val discoveredDevices: List<String> = _discoveredDevices

    private val wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel = wifiP2pManager.initialize(context, Looper.getMainLooper(), null)

    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    wifiP2pManager.requestPeers(channel) { peerList ->
                        _discoveredDevices.clear()
                        _discoveredDevices.addAll(peerList.deviceList.map { it.deviceName })
                    }
                }
            }
        }
    }

    fun startDiscovery() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WFD", "Peer discovery started.")
            }

            override fun onFailure(reason: Int) {
                Log.e("WFD", "Discovery failed with reason $reason")
            }
        })
    }

    fun registerReceiver(activity: ComponentActivity) {
        activity.registerReceiver(receiver, intentFilter)
    }

    fun unregisterReceiver(activity: ComponentActivity) {
        activity.unregisterReceiver(receiver)
    }
}
