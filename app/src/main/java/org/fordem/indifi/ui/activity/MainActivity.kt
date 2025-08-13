package org.fordem.indifi.ui.activity

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.network.NetworkManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.fordem.indifi.databinding.ActivityMainBinding
import org.fordem.indifi.ui.adapter.DeviceAdapter
import org.fordem.indifi.ui.viewmodel.DeviceInfoViewModel
import org.fordem.indifi.ui.utils.Constants.isGOViaWFD
import org.fordem.indifi.ui.utils.Constants.openChatCallback
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.utils.isGoViaLegacy

@AndroidEntryPoint
class MainActivity : BaseActivity() {
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val messageRouterHelper = MessageRouterHelper

    private val deviceInfoViewModel: DeviceInfoViewModel by viewModels()
    private lateinit var adapter: DeviceAdapter


    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Setup Nav
        setupNavigationUI(binding.root, "Home")

        messageRouterHelper.bindService(this)
        messageRouterHelper.startMessageRouterService()

        openChatCallback = { selectedDevice ->
            lifecycleScope.launch {
                if (isGOViaWFD && !isGoViaLegacy(this@MainActivity)/*selectedDevice.name != "GM_Device"*/) {
                    val ownInfo = deviceInfoViewModel.getOwnInfoDirect()

                    if (ownInfo != null) {
                        isGOViaWFD = ownInfo.isGroupOwner
                    }
                    if (isGOViaWFD) {
                        NetworkManager.startServer()
                    }

                    val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
                        putExtra("device_ip", selectedDevice.ip)
                        putExtra("device_name", selectedDevice.name)
                        if (ownInfo != null) {
                            putExtra("groupOwnerAddress", ownInfo.ip)
                        } // GO knows GM IP

                        // Based on this device's role (not the selected device)
                        if (isGOViaWFD || selectedDevice.ip != ownInfo?.ip) {
                            putExtra("isGroupOwner", true)
                        } else {
                            putExtra("isGroupOwner", false)
                        }
                    }
                    startActivity(intent)
                } else {
                    // TODO:  : Check two versions of getLocalIpAddress, is one failing?
                    val ownIp = getLocalIpAddress(this@MainActivity)
                    val selectedIp = selectedDevice.ip

                    val isSelf = ownIp == selectedIp
                    val isGO = deviceInfoViewModel.getOwnInfoDirect()?.isGroupOwner == true

                    val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
                        putExtra("device_ip", /*selectedDevice.ip*/ownIp)
                        putExtra("device_name", selectedDevice.name)

                        if (isSelf) {
                            Toast.makeText(this@MainActivity, "You cannot chat with yourself", Toast.LENGTH_SHORT).show()
                            return@apply
                        }

                        if (isGO) {
                            putExtra("isGroupOwner", true)
                            putExtra("groupOwnerAddress", ownIp)
                        } else {
                            putExtra("isGroupOwner", false)
                            putExtra("groupOwnerAddress", selectedIp)
                        }
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
                        Log.d("MainActivity", "Received device list update: ${list.size} devices")
                        list.forEach { device ->
                            Log.d("MainActivity", "Device: ${device.name} - ${device.ip}")
                        }
                        adapter.submitList(list)
                    }
                } catch (_: Exception) {
                    // TODO:  : Empty Exception
                }
            }
        }

        binding.btnDiscoverNearby.setOnClickListener {
            startActivity(Intent(this, WifiDirectScreen1Activity::class.java))
        }
        binding.btnListHotspots.setOnClickListener {
            startActivity(Intent(this, WifiScanActivity::class.java))
        }
        binding.btnClearDevices.setOnClickListener {
                deviceInfoViewModel.deleteDevices()
                Toast.makeText(this@MainActivity, "Cleared all devices", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getLocalIpAddress(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkProperties = cm.getLinkProperties(cm.activeNetwork) ?: return null

        return linkProperties.linkAddresses
            .map { it.address.hostAddress }
            .find { it?.startsWith("192.") == true || it?.startsWith("172.") == true || it?.startsWith("10.") == true }
    }


    override fun onResume() {
        super.onResume()
//        if (isGoViaLegacy(this)) {
//            isGoViaLegacy = true
//        }
    }

    override fun onDestroy() {
        MessageRouterHelper.unbindService(this)
//        val intent = Intent(this, P2pHeartbeatService::class.java)
//        stopService(intent)
        super.onDestroy()
    }

    override fun onStop() {
        super.onStop()
        MessageRouterHelper.unbindService(this)
    }
}




