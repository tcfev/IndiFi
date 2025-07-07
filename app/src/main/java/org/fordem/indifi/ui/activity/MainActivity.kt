package org.fordem.indifi.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.fordem.indifi.databinding.ActivityMainBinding
import org.fordem.indifi.ui.adapter.DeviceAdapter
import org.fordem.indifi.ui.db.DeviceInfoViewModel
import org.fordem.indifi.ui.utils.Constants
import org.fordem.indifi.ui.utils.Constants.isGOViaWFD
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.utils.MessageRouterHelper.serviceConnection
import org.fordem.indifi.ui.utils.MessageRouterService

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

        Constants.openChatCallback = { selectedDevice ->
            lifecycleScope.launch {
                val ownInfo = deviceInfoViewModel.getOwnInfoDirect()

                start form integrating chat class with getting ip from this screen

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
            }
        }

        adapter = DeviceAdapter()
        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                deviceInfoViewModel.allDevices.collect { list ->
                    adapter.submitList(list)
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

    override fun onDestroy() {
        MessageRouterHelper.unbindService(this)
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        MessageRouterHelper.unbindService(this)
    }
}




