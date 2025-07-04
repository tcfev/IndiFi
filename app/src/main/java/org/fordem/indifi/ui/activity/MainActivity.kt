package org.fordem.indifi.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import org.fordem.indifi.databinding.ActivityMainBinding
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.utils.MessageRouterHelper.startMessageRouterService
import org.fordem.indifi.ui.utils.MessageRouterService

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

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

    override fun onStart() {
        super.onStart()

        MessageRouterHelper.bindService(this)
    }

//    override fun onDestroy() {
//        MessageRouterHelper.unbindService(this)
//        super.onDestroy()
//    }

    override fun onStop() {
        super.onStop()
        MessageRouterHelper.unbindService(this)
    }
}




