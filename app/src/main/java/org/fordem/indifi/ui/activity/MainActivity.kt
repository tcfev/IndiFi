package org.fordem.indifi.ui.activity

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.fordem.indifi.databinding.ActivityMainBinding
import org.fordem.indifi.ui.adapter.DeviceAdapter
import org.fordem.indifi.ui.db.DeviceInfo
import org.fordem.indifi.ui.db.DeviceInfoViewModel
import org.fordem.indifi.ui.db.OwnDeviceInfo
import org.fordem.indifi.ui.utils.Constants
import org.fordem.indifi.ui.utils.Constants.isGOViaWFD
import org.fordem.indifi.ui.utils.Constants.isGoViaLegacy
import org.fordem.indifi.ui.utils.Constants.legacyClientCallback
import org.fordem.indifi.ui.utils.Constants.openChatCallback
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.utils.getOwnIp
import org.fordem.indifi.ui.utils.isHotspotEnabled
import org.json.JSONArray
import org.json.JSONObject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val messageRouterHelper = MessageRouterHelper

    private val deviceInfoViewModel: DeviceInfoViewModel by viewModels()
    private lateinit var adapter: DeviceAdapter

    override fun onStart() {
        super.onStart()
//        messageRouterHelper.bindService(this)
//        val serviceIntent = Intent(this, MessageRouterService::class.java)
//        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
//        startService(serviceIntent) // Keeps the service running in background


    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        messageRouterHelper.bindService(this)
        messageRouterHelper.startMessageRouterService()

//        MessageRouterHelper.messageRouterService?.startChatServer(
//            deviceInfoViewModel,
//            onMessageReceived = {
//            }
//        )



        openChatCallback = { selectedDevice ->
            lifecycleScope.launch {
                if (isGOViaWFD && !isGoViaLegacy/*selectedDevice.name != "GM_Device"*/) {
                    val ownInfo = deviceInfoViewModel.getOwnInfoDirect()

                    if (ownInfo != null) {
                        isGOViaWFD = ownInfo.isGroupOwner
                    }
                    val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
                        putExtra("device_ip", selectedDevice.ip)
                        putExtra("device_name", selectedDevice.name)
                        if (ownInfo != null) {
                            putExtra("groupOwnerAddress", ownInfo.ip)
                        } // GO knows GM IP
//                // Check if current device is GO and pass extra info if true
//                if (selectedDevice.isGroupOwner) {
//                    putExtra("isGroupOwner", true)
//                    putExtra("groupOwnerAddress", selectedDevice.ip) // assuming IP is the host address
//                }

                        // Based on this device's role (not the selected device)
                        if (isGOViaWFD || selectedDevice.ip != ownInfo?.ip) {
                            putExtra("isGroupOwner", true)
                        } else {
                            putExtra("isGroupOwner", false)
                        }
                    }
                    startActivity(intent)
                } else {
                    val ownInfo = deviceInfoViewModel.getOwnInfoDirect()

                    if (ownInfo != null && ownInfo.isGroupOwner) {
                        isGoViaLegacy = ownInfo.isGroupOwner
                    }
                    val allDevices = deviceInfoViewModel.allDevices.firstOrNull() ?: emptyList()

                    // Find GO and selected device
                    val goDevice = allDevices.find { it.isGroupOwner }
                    val selectedInfo = allDevices.find { it.ip == selectedDevice.ip }

                    val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
                        putExtra("device_ip", selectedDevice.ip)
                        putExtra("device_name", selectedDevice.name)


                        // Check if selected device is GO
                        if (selectedInfo?.isGroupOwner == true) {
                            putExtra("isGroupOwner", false) // you're the GM
                            putExtra("groupOwnerAddress", selectedDevice.ip)
                        } else {
                            putExtra("isGroupOwner", true)  // you're the GO
                            putExtra("groupOwnerAddress", goDevice?.ip) // fallback
                        }


//                        if (ownInfo != null) {
//                            putExtra("groupOwnerAddress", ownInfo.ip)
//                        } // GO knows GM IP
//                // Check if current device is GO and pass extra info if true
//                if (selectedDevice.isGroupOwner) {
//                    putExtra("isGroupOwner", true)
//                    putExtra("groupOwnerAddress", selectedDevice.ip) // assuming IP is the host address
//                }

//                        // Based on this device's role (not the selected device)
//                        if (isGOViaWFD || selectedDevice.ip != ownInfo?.ip) {
//                            putExtra("isGroupOwner", true)
//                        } else {
//                            putExtra("isGroupOwner", false)
//                        }
                    }
                    startActivity(intent)
                }
            }
        }

        adapter = DeviceAdapter()
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    deviceInfoViewModel.allDevices.collect { list ->
                        adapter.submitList(list)
                    }
                } catch (_: Exception) {
                }
            }
        }

        binding.btnDiscoverNearby.setOnClickListener {
            startActivity(Intent(this, WifiDirectScreen1Activity::class.java))
        }

//        binding.btnLegacyWifi.setOnClickListener {
//            startActivity(Intent(this, LegacyWifiActivity::class.java))
//        }

        binding.btnListHotspots.setOnClickListener {
            startActivity(Intent(this, WifiScanActivity::class.java))
        }

//        startMessageRouterService()


    }

    override fun onResume() {
        super.onResume()
        if (isHotspotEnabled(this)) {
            isGoViaLegacy = true
        }

//        lifecycleScope.launch {
//            val deferredValue = async(Dispatchers.IO) { getOwnIp(this@MainActivity) }
//            val ownIpGM = deferredValue.await()
//            val ownName = Build.MODEL ?: "GM_Device"
//
//            deviceInfoViewModel.ownDeviceInfo.collect { existing ->
//                if (existing == null ||
//                    existing.name != ownName ||
//                    existing.ip != ownIpGM ||
//                    !existing.isGroupOwner
//                ) {
//                    val goInfo = OwnDeviceInfo(
//                        name = ownName,
//                        ip = ownIpGM!!,
//                        isGroupOwner = true
//                    )
//                    deviceInfoViewModel.insertOwnDevice(goInfo)
//                } else {
//                    Log.d("OwnInfo", "Own GO info already up-to-date.")
//                }
//            }
//        }

    }

    override fun onDestroy() {
        MessageRouterHelper.unbindService(this)
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        MessageRouterHelper.unbindService(this)
    }
}




