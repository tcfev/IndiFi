package org.fordem.indifi

import android.Manifest
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.*
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class WifiScanActivity : AppCompatActivity() {

    private lateinit var wifiManager: WifiManager
    private lateinit var wifiReceiver: BroadcastReceiver
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val wifiList = mutableListOf<String>()
    private val LOCATION_PERMISSION_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_scan)

        listView = findViewById(R.id.lvWifiList)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, wifiList)
        listView.adapter = adapter

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val success = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) ?: false
                if (success) {
                    showScanResults()
                } else {
                    Toast.makeText(this@WifiScanActivity, "Scan failed or restricted", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_CODE)
        } else {
            ensureLocationEnabledAndScan()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val ssidWithBssid = wifiList[position]
            val ssid = ssidWithBssid.substringBefore(" - ")
            showPasswordDialog(ssid)
        }
    }

    override fun onStart() {
        super.onStart()

        val intent = Intent(this, UdpListenerService::class.java)
        startService(intent)

    }

    private fun ensureLocationEnabledAndScan() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (isGpsEnabled || isNetworkEnabled) {
            startWifiScan()
        } else {
            Toast.makeText(this, "Please enable Location (GPS) manually", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }
    }

    private fun startWifiScan() {
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiReceiver, intentFilter)

        val success = wifiManager.startScan()
        if (!success) {
            Toast.makeText(this, "Wi-Fi scan start failed!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showScanResults() {
        val results = wifiManager.scanResults
        wifiList.clear()

        for (scanResult in results) {
            if (!TextUtils.isEmpty(scanResult.SSID)) {
                wifiList.add("${scanResult.SSID} - ${scanResult.BSSID}")
            }
        }

        adapter.notifyDataSetChanged()
    }

    private fun showPasswordDialog(ssid: String) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        AlertDialog.Builder(this)
            .setTitle("Connect to $ssid")
            .setMessage("Enter password:")
            .setView(input)
            .setPositiveButton("Connect") { _, _ ->
                val password = input.text.toString()
                connectToWifi(ssid, password)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun connectToWifi(ssid: String, password: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
//                    connectivityManager.bindProcessToNetwork(network)
//                    Toast.makeText(this@WifiScanActivity, "Connected to $ssid", Toast.LENGTH_SHORT).show()


                    connectivityManager.bindProcessToNetwork(network)
                    runOnUiThread {
                        Toast.makeText(this@WifiScanActivity, "Connected to $ssid", Toast.LENGTH_SHORT).show()
                        sendHelloPacketToGO()
                        startActivity(Intent(this@WifiScanActivity, ChatActivity::class.java))
                        finish()
                    }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    Toast.makeText(this@WifiScanActivity, "Connection to $ssid failed", Toast.LENGTH_SHORT).show()
                }
            }

            connectivityManager.requestNetwork(request, networkCallback)

        } else {
            // Android 9 and below
            val conf = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                preSharedKey = "\"$password\""
            }

            val netId = wifiManager.addNetwork(conf)
            if (netId != -1) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()
                Toast.makeText(this, "Connecting to $ssid...", Toast.LENGTH_SHORT).show()


                // Wait and then go to chat
                Handler(mainLooper).postDelayed({
                    sendHelloPacketToGO()

                    val intent = Intent(this, ChatActivity::class.java)
                    startActivity(intent)
                    finish()
                }, 4000) // ~4 seconds delay for connection
            } else {
                Toast.makeText(this, "Failed to add network $ssid", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendHelloPacketToGO() {
        Thread {
            try {
                val socket = DatagramSocket()
                val message = "HELLO"
                val buffer = message.toByteArray()
                val address = InetAddress.getByName("192.168.43.1")
                val packet = DatagramPacket(buffer, buffer.size, address, 9876)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            ensureLocationEnabledAndScan()
        } else {
            Toast.makeText(this, "Permission denied. Cannot scan Wi-Fi.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiReceiver)
    }

    // GO Side - Call this method in GO's main activity to listen for incoming GM HELLO
    fun startListeningForGMHello(context: Context) {
        Thread {
            try {
                val socket = DatagramSocket(9876)
                val buffer = ByteArray(1024)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    if (message.trim() == "HELLO") {
                        runOnUiThread {
                            Toast.makeText(context, "GM connected!", Toast.LENGTH_SHORT).show()
                            context.startActivity(Intent(context, ChatActivity::class.java))
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }
}
