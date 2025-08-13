package org.fordem.indifi.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.NetworkInfo
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.fordem.indifi.R
import org.fordem.indifi.databinding.ActivityWifiDirectBinding

@SuppressLint("MissingPermission")
class WifiDirectActivity : AppCompatActivity() {
    private val binding: ActivityWifiDirectBinding by lazy {
        ActivityWifiDirectBinding.inflate(layoutInflater)
    }

//    private lateinit var peerListView: ListView
    private lateinit var peerAdapter: ArrayAdapter<String>

    private lateinit var wifiP2pManager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private lateinit var intentFilter: IntentFilter

    private val peers = mutableListOf<WifiP2pDevice>()

    companion object {
        private const val TAG = "WFD"
        private const val PERMISSION_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

//        peerListView = findViewById(R.id.lvPeers)
        peerAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList())
        binding.lvPeers.adapter = peerAdapter

        wifiP2pManager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager.initialize(this, mainLooper, null)

        setupIntentFilter()
        setupReceiver()

        if (hasLocationPermission()) {
            discoverPeers()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_REQUEST_CODE
            )
        }

        binding.lvPeers.setOnItemClickListener { _, _, position, _ ->
            val device = peers[position]
            val config = WifiP2pConfig().apply {
                deviceAddress = device.deviceAddress
                wps.setup = WpsInfo.PBC
                groupOwnerIntent = 15 // 0–15 → 15 = strongly prefer to be GO
            }

            wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(
                        this@WifiDirectActivity,
                        "Connecting to ${device.deviceName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(
                        this@WifiDirectActivity,
                        "Connection failed: $reason",
                        Toast.LENGTH_SHORT
                    ).show()
                    Log.e(TAG, "Connection failed: $reason")
                }
            })
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
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
                        if (!hasLocationPermission()) return

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
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                        if (networkInfo != null && networkInfo.isConnected) {
                            wifiP2pManager.requestConnectionInfo(channel) { info ->
                                if (info.groupFormed && info.groupOwnerAddress != null) {
                                } else {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        wifiP2pManager.requestConnectionInfo(channel) { retryInfo ->
                                            if (retryInfo.groupFormed && retryInfo.groupOwnerAddress != null) {
//                                                launchChatActivity(retryInfo)
                                            }
                                        }
                                    }, 2000) // Wait 2 seconds
                                }
                            }

                        } else {
                            Log.d(TAG, "P2P connection dropped")
                            Toast.makeText(
                                this@WifiDirectActivity,
                                "Disconnected",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

//    private fun launchChatActivity(info: WifiP2pInfo) {
//        if (info.groupFormed && info.groupOwnerAddress != null) {
//
//            val intent = Intent(this@WifiDirectActivity, ChatActivity::class.java).apply {
//                putExtra("isGroupOwner", info.isGroupOwner)
//                putExtra("groupOwnerAddress", info.groupOwnerAddress?.hostAddress)
//            }
//            startActivity(intent)
//        }
//    }

    private fun discoverPeers() {
        wifiP2pManager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(
                    this@WifiDirectActivity,
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
                    this@WifiDirectActivity,
                    "Discovery failed: $message",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e(TAG, "Discovery failed: $message")
            }
        })
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && hasLocationPermission()) {
            discoverPeers()
        } else {
            Toast.makeText(this, "Permission required to discover peers", Toast.LENGTH_LONG).show()
        }
    }
}
