package org.fordem.indifi

import android.content.Context
import android.content.Intent
import android.net.*
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.text.format.Formatter
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.*

class LegacyWifiActivity : AppCompatActivity() {
    private lateinit var etSsid: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar
    private var udpSender: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val broadcastPort = 9876
    private val broadcastIntervalMs = 3000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legacy_wifi)

        etSsid = findViewById(R.id.etSsid)
        etPassword = findViewById(R.id.etPassword)
        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)

        findViewById<Button>(R.id.btnBroadcast).setOnClickListener {
            val ssid = etSsid.text.toString()
            val password = etPassword.text.toString()
            if (ssid.isNotBlank() && password.isNotBlank()) {
                startBroadcast(ssid, password)
                tvStatus.text = "Broadcasting hotspot info..."
            } else {
                Toast.makeText(this, "Please enter SSID and Password", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnScanAndConnect).setOnClickListener {
            acquireMulticastLock()
            listenForBroadcast()
        }
    }

    private fun startBroadcast(ssid: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                udpSender = DatagramSocket()
                udpSender?.reuseAddress = true
                udpSender?.broadcast = true

                val json = JSONObject().apply {
                    put("ssid", ssid)
                    put("password", password)
                    put("deviceName", Build.MODEL)
                }.toString()

                val buffer = json.toByteArray()
                val packet = DatagramPacket(
                    buffer,
                    buffer.size,
                    InetAddress.getByName("192.168.43.255"), // 👈 more reliable broadcast IP
                    broadcastPort
                )

                while (true) {
                    udpSender?.send(packet)
                    delay(broadcastIntervalMs)
                }
            } catch (e: Exception) {
                Log.e("UDP_BROADCAST", "Broadcast Error: ${e.message}")
            }
        }
    }

    private fun listenForBroadcast() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        Log.d("GM IP", ip)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = DatagramSocket(broadcastPort, InetAddress.getByName("0.0.0.0"))
                socket.reuseAddress = true
                socket.broadcast = true
//                socket.bind(InetSocketAddress(broadcastPort))

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                socket.receive(packet) // Will block until something is received

                val msg = String(packet.data, 0, packet.length)
                Log.d("UDP_RECEIVE", "Received packet: $msg")

                val json = JSONObject(msg)
                val ssid = json.getString("ssid")
                val password = json.getString("password")

                runOnUiThread {
                    tvStatus.text = "Connecting to $ssid"
                    progressBar.visibility = View.VISIBLE
                }

                connectToHotspot(ssid, password)

                socket.close()
                releaseMulticastLock()

            } catch (e: Exception) {
                Log.e("UDP_RECEIVE", "Error receiving: ${e.message}")
            }
        }
    }

    private fun connectToHotspot(ssid: String, password: String) {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(password)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    connectivityManager.bindProcessToNetwork(network)
                    val gatewayIP = getGatewayIp()

                    runOnUiThread {
                        tvStatus.text = "Connected! Launching chat..."
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@LegacyWifiActivity, "Connected to host: $gatewayIP", Toast.LENGTH_SHORT).show()
                    }

                    startChat(false, gatewayIP)
                }

                override fun onUnavailable() {
                    runOnUiThread {
                        tvStatus.text = "Connection failed"
                        progressBar.visibility = View.GONE
                    }
                }
            })
        } else {
            val wifiConfig = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                preSharedKey = "\"$password\""
                allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK)
            }

            val netId = wifiManager.addNetwork(wifiConfig)
            if (netId != -1) {
                wifiManager.disconnect()
                wifiManager.enableNetwork(netId, true)
                wifiManager.reconnect()

                Handler(mainLooper).postDelayed({
                    val gatewayIP = getGatewayIp()
                    tvStatus.text = "Connected to hotspot! Host IP: $gatewayIP"
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Connected to host: $gatewayIP", Toast.LENGTH_SHORT).show()
                    startChat(false, gatewayIP)
                }, 3000)
            } else {
                Toast.makeText(this, "Failed to connect to network", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getGatewayIp(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val dhcpInfo = wifiManager.dhcpInfo
        return Formatter.formatIpAddress(dhcpInfo.gateway)
    }

    private fun startChat(isGroupOwner: Boolean, hostAddress: String) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("isGroupOwner", isGroupOwner)
        intent.putExtra("groupOwnerAddress", hostAddress)
        startActivity(intent)
    }

    private fun acquireMulticastLock() {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("multicastLock").apply {
            setReferenceCounted(true)
            acquire()
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) it.release()
        }
    }
}
