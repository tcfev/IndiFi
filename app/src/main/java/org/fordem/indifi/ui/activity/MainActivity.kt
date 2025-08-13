package org.fordem.indifi.ui.activity

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.widget.Toast
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
import org.fordem.indifi.ui.utils.Constants.connectivityManager
import org.fordem.indifi.ui.viewmodel.DeviceInfoViewModel
import org.fordem.indifi.ui.utils.Constants.isGOViaWFD
import org.fordem.indifi.ui.utils.Constants.isGoViaLegacy
import org.fordem.indifi.ui.utils.Constants.openChatCallback
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.utils.isHotspotEnabled

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private val messageRouterHelper = MessageRouterHelper
    private val deviceInfoViewModel: DeviceInfoViewModel by viewModels()
    private lateinit var adapter: DeviceAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

//        GM1 is having LC connection to GO2 but receives multicast messages only and unable
//        to send multicast messages, WFD groups internally working fine, break the code from
//        both LC-GM and LC-GO sides and try to figure out the problem.

//        messageRouterHelper.startIndifiService()
//        messageRouterHelper.bindService(this)

//        openChatCallback = { selectedDevice ->
//            lifecycleScope.launch {
//                if (isGOViaWFD && !isGoViaLegacy/*selectedDevice.name != "GM_Device"*/) {
//                    val ownInfo = deviceInfoViewModel.getOwnInfoDirect()
//
//                    if (ownInfo != null) {
//                        isGOViaWFD = ownInfo.isGroupOwner
//                    }
//                    val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
//                        putExtra("device_ip", selectedDevice.ip)
//                        putExtra("device_name", selectedDevice.name)
//                        if (ownInfo != null) {
//                            putExtra("groupOwnerAddress", ownInfo.ip)
//                        } // GO knows GM IP
//
//                        // Based on this device's role (not the selected device)
//                        if (isGOViaWFD || selectedDevice.ip != ownInfo?.ip) {
//                            putExtra("isGroupOwner", true)
//                        } else {
//                            putExtra("isGroupOwner", false)
//                        }
//                    }
//                    startActivity(intent)
//                } else {
//                    val ownIp = getLocalIpAddress(this@MainActivity)
//                    val selectedIp = selectedDevice.ip
//
//                    val isSelf = ownIp == selectedIp
//                    val isGO = deviceInfoViewModel.getOwnInfoDirect()?.isGroupOwner == true
//
//                    val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
//                        putExtra("device_ip", /*selectedDevice.ip*/ownIp)
//                        putExtra("device_name", selectedDevice.name)
//
//                        if (isSelf) {
//                            Toast.makeText(this@MainActivity, "You cannot chat with yourself", Toast.LENGTH_SHORT).show()
//                            return@apply
//                        }
//
//                        if (isGO) {
//                            putExtra("isGroupOwner", true)
//                            putExtra("groupOwnerAddress", ownIp)
//                        } else {
//                            putExtra("isGroupOwner", false)
//                            putExtra("groupOwnerAddress", selectedIp)
//                        }
//                    }
//                    startActivity(intent)
//
//                }
//            }
//        }

        openChatCallback = { selectedDevice ->
            lifecycleScope.launch {
                val ownInfo = deviceInfoViewModel.getOwnInfoDirect()
                val ownIp = ownInfo?.ip ?: getLocalIpAddress(this@MainActivity)
                val selectedIp = selectedDevice.wfdIp

                if (ownIp == selectedIp) {
                    Toast.makeText(this@MainActivity, "You cannot chat with yourself", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val intent = Intent(this@MainActivity, ChatActivity::class.java).apply {
                    putExtra("peerIp", selectedIp)               // Actual target IP
                    putExtra("peerName", selectedDevice.name)    // Target device name
                    putExtra("ownIp", ownIp)                     // Own IP (optional for reference)
                }
                startActivity(intent)
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
        binding.btnListHotspots.setOnClickListener {
            startActivity(Intent(this, WifiScanActivity::class.java))
        }
    }

    private fun getLocalIpAddress(context: Context): String? {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val linkProperties = connectivityManager!!.getLinkProperties(connectivityManager!!.activeNetwork) ?: return null

        return linkProperties.linkAddresses
            .map { it.address.hostAddress }
            .find { it?.startsWith("192.") == true || it?.startsWith("172.") == true || it?.startsWith("10.") == true }
    }


    override fun onResume() {
        super.onResume()
        if (isHotspotEnabled(this)) {
            isGoViaLegacy = true
        }
    }

//    override fun onDestroy() {
//        MessageRouterHelper.unbindService(this)
//        super.onDestroy()
//    }
//
//    override fun onStop() {
//        super.onStop()
//        MessageRouterHelper.unbindService(this)
//    }
}




