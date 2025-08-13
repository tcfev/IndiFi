//package org.fordem.indifi.ui.service
//
//import android.annotation.SuppressLint
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.net.ConnectivityManager
//import android.net.NetworkCapabilities
//import android.net.wifi.WifiManager
//import android.net.wifi.WpsInfo
//import android.net.wifi.p2p.WifiP2pConfig
//import android.net.wifi.p2p.WifiP2pManager
//import android.os.Binder
//import android.os.Build
//import android.os.Handler
//import android.os.IBinder
//import android.os.Looper
//import android.provider.Settings
//import android.util.Base64
//import android.util.Log
//import android.widget.Toast
//import dagger.hilt.android.AndroidEntryPoint
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.flow.collectLatest
//import kotlinx.coroutines.flow.first
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import org.fordem.indifi.R
//import org.fordem.indifi.ui.dao.DeviceInfoDao
//import org.fordem.indifi.ui.dao.PeerPublicKeyDao
//import org.fordem.indifi.ui.encryption.AESGCMHelper
//import org.fordem.indifi.ui.encryption.KeyStoreManager
//import org.fordem.indifi.ui.model.DeviceInfo
//import org.fordem.indifi.ui.model.PeerPublicKeyEntity
//import org.fordem.indifi.ui.utils.Constants
//import org.fordem.indifi.ui.utils.Constants.PORT
//import org.fordem.indifi.ui.utils.Constants.SILENTPORT
//import org.fordem.indifi.ui.utils.Constants.connectedGMIPs
//import org.fordem.indifi.ui.utils.Constants.connectivityManager
//import org.fordem.indifi.ui.utils.Constants.currentBoundInterface
//import org.fordem.indifi.ui.utils.Constants.deviceConnectionCallback
//import org.fordem.indifi.ui.utils.Constants.ipLcGo
//import org.fordem.indifi.ui.utils.Constants.isGOViaWFD
//import org.fordem.indifi.ui.utils.Constants.legacyClientCallback
//import org.fordem.indifi.ui.utils.Constants.networkCallback
//import org.json.JSONArray
//import org.json.JSONObject
//import java.io.BufferedReader
//import java.io.BufferedWriter
//import java.io.IOException
//import java.io.InputStreamReader
//import java.io.OutputStreamWriter
//import java.net.DatagramPacket
//import java.net.DatagramSocket
//import java.net.Inet4Address
//import java.net.InetAddress
//import java.net.InetSocketAddress
//import java.net.MulticastSocket
//import java.net.NetworkInterface
//import java.net.ServerSocket
//import java.net.Socket
//import javax.crypto.SecretKey
//import javax.inject.Inject
//
//@AndroidEntryPoint
//class IndifiService : Service() {
//    private val clientSockets = mutableListOf<Socket>()
//    private var lastClientAddress: InetAddress? = null
//    private val gmAddresses = mutableSetOf<Socket>()  // All connected GMs
//    private var serverSocket: ServerSocket? = null
//
//    private val peerAESKeys = mutableMapOf<String, SecretKey>()
//    private var isServerRunning = false
//    private val binder = LocalBinder()
//
//    @Inject
//    lateinit var deviceInfoDao: DeviceInfoDao
//
//    @Inject
//    lateinit var peerPublicKeyDao: PeerPublicKeyDao
//
//    private var lastBroadcastSize = 0
//    private var lastBroadcastTime = 0L
//    private var multicastLock: WifiManager.MulticastLock? = null
//
//
//    inner class LocalBinder : Binder() {
//        fun getService(): IndifiService = this@IndifiService
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        multicastLock?.release()
//        multicastLock = null
//    }
//
//    override fun onCreate() {
//        super.onCreate()
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            createNotificationChannel()
//            val notification = Notification.Builder(this, "SYNC_CHANNEL")
//                .setContentTitle("Group sync running")
//                .setContentText("Listening to GO for updates")
//                .setSmallIcon(R.drawable.ic_launcher_background)
//                .build()
//
//            startForeground(1, notification)
//        }
//
//
//        startListeningForMulticastMessages()
//
//        // Start listening here
//        startChatServer(
//            onMessageReceived = { message ->
//                Log.d("SERVICE", "Message received at startup: $message")
//                // Handle message if needed
//
//            }
//        )
//
//        observeDeviceListChanges()
//    }
//
////    private fun startListeningForMulticastMessages() {
////        CoroutineScope(Dispatchers.IO).launch {
////            try {
////                val multicastSocket = MulticastSocket(5000)
////                val group = InetAddress.getByName("230.0.0.1") // or "224.0.0.251"
////                val networkInterface = NetworkInterface.getByName("wlan0") // LC connection
////
////                multicastSocket.joinGroup(InetSocketAddress(group, 5000), networkInterface)
////                Log.d("MULTICAST", "Joined multicast group on wlan0")
////
////                val buffer = ByteArray(2048)
////
////                while (true) {
////                    val packet = DatagramPacket(buffer, buffer.size)
////                    multicastSocket.receive(packet)
////
////                    val message = String(packet.data, 0, packet.length)
////                    Log.d("MULTICAST", "Received from ${packet.address.hostAddress}: $message")
////
////                    // Forward to WFD clients or handle it
////                    withContext(Dispatchers.Main) {
////                        // Optional: Parse and handle as you do in TCP
////                        handleMulticastMessage(message, packet.address)
////                    }
////                }
////            } catch (e: Exception) {
////                Log.e("MULTICAST", "Error: ${e.message}")
////            }
////        }
////    }
//
//    private fun startListeningForMulticastMessages() {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                // 🔒 Acquire multicast lock
//                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
//                multicastLock = wifiManager.createMulticastLock("myMulticastLock")
//                multicastLock?.setReferenceCounted(true)
//                multicastLock?.acquire()
//
//                val multicastSocket = MulticastSocket(SILENTPORT)
//                val group = InetAddress.getByName("230.0.0.1")
//
////                val iface = NetworkInterface.getNetworkInterfaces().toList().find {
////                    it.isUp && it.supportsMulticast() && !it.isLoopback && it.name.startsWith("wlan")
////                } ?: run {
////                    Log.e("MULTICAST", "No suitable multicast interface found")
////                    return@launch
////                }
//
//                multicastSocket.joinGroup(group/*InetSocketAddress(group, 5000), iface*/)
////                Log.d("MULTICAST", "Joined multicast group on ${iface.name}")
//
//                val buffer = ByteArray(4096)
//
//                while (true) {
//                    val packet = DatagramPacket(buffer, buffer.size)
//                    multicastSocket.receive(packet)
//
//                    val message = String(packet.data, 0, packet.length)
//                    withContext(Dispatchers.Main) {
//                        Toast.makeText(applicationContext, "Received: $message", Toast.LENGTH_LONG)
//                            .show()
//                    }
//                    withContext(Dispatchers.IO) {
//                        handleMulticastMessage(message, packet.address)
//                    }
//
//
//                    multicastSocket.close()
//                }
//
//
//            } catch (e: Exception) {
//                Log.e("MULTICAST", "Error: ${e.message}")
//            }
//        }
//    }
//
//    private suspend fun handleMulticastMessage(message: String, senderIp: InetAddress) {
//        try {
//            Log.d("MULTICAST", "Received from $senderIp: $message")
//
//            // Special handling for tag-prefixed data
//            when {
//                message.startsWith("DEVICE_LIST:") -> {
//                    parseDeviceList(message)
//                }
//
//                message.startsWith("PEER_KEYS_LIST:") -> {
//                    parseKeysList(message)
//                }
//
//                message.startsWith("{") && message.endsWith("}") -> {
//                    val obj = JSONObject(message)
//
//                    when (obj.optString("type")) {
//                        "LC_HELLO" -> receiveHelloMessageFromLc(obj)
////                        "WFD_HELLO" -> receiveHelloMessage(obj, senderIp)
//
//                        "KEY_EXCHANGE" -> {
//                            // You can trigger key exchange if needed
//                            Log.d("MULTICAST", "Received KEY_EXCHANGE from $senderIp")
//                        }
//
//                        else -> {
//                            // Check for AES-GCM encrypted message
//                            if (obj.has("ciphertext") && obj.has("iv")) {
//                                getEncryptedMessage(
//                                    senderIp.hostAddress!!,
//                                    obj
//                                ) // You may need to adjust this function
//                            } else if (obj.has("name") && obj.has("ip") && obj.has("timestamp")) {
//                                getDevicesInfo(obj)
//                            } else {
//                                Log.w("MULTICAST", "Unknown JSON content: $message")
//                            }
//                        }
//                    }
//                }
//
//                else -> {
//                    Log.w("MULTICAST", "Unrecognized message format: $message")
//                }
//            }
//
//            // ✅ Relay to all WFD peers (optional)
//            broadcastMessageToAllWfdPeersAsRelay(message, null)
//
//        } catch (e: Exception) {
//            Log.e("MULTICAST", "Invalid message or parse error: ${e.message}")
//        }
//
//
//        //        try {
////            val json = JSONObject(message)
////            when (json.optString("type")) {
////                "LC_HELLO" -> receiveHelloMessageFromLc(json)
////                "KEY_EXCHANGE" -> {
////                    // Handle key exchange if needed
////                }
////                "DEVICE_LIST:" -> {
////                    parseDeviceList(line!!)
////                }
////
////                else -> {
////                    Log.d("MULTICAST", "Unknown type or data: $message")
////                }
////            }
////
////            // Optionally forward to WFD GMs
////            broadcastMessageToAllWfdPeersAsRelay(message, null)
////
////        } catch (e: Exception) {
////            Log.e("MULTICAST", "Invalid message: $message, error: ${e.message}")
////        }
//    }
//
//    private suspend fun receiveHelloMessageFromLc(obj: JSONObject) {
//        ipLcGo = obj.getString("lcIpGO")
//        val devicesArray = obj.getJSONArray("devices")
//        for (i in 0 until devicesArray.length()) {
//            val device = devicesArray.getJSONObject(i)
//            val name =
//                device.getString("name")        // name tag is not matching to the hello message received
//            val wfdIp = device.getString("wfdIp")
//            val lcIp = device.getString("lcIp")
//            val androidId = device.getString("androidId")
//            val groupId = device.getString("groupId")
//            val isGroupOwner = device.getBoolean("isGroupOwner")
//            val isRelayDevice = device.getBoolean("isRelayDevice")
//            val timestamp = device.getLong("timestamp")
//            val base64Key = device.getString("base64Key")
//
//            val duplicate = deviceInfoDao.isDuplicateDevice(
//                name = name,
//                androidId = androidId
//            ) > 0
//            if (!duplicate) {
//                val newDevice = DeviceInfo(
//                    name = name,
//                    wfdIp = wfdIp,
//                    lcIp = lcIp,
//                    androidId = androidId,
//                    groupId = groupId,
//                    isGroupOwner = isGroupOwner,
//                    isRelayDevice = isRelayDevice,
//                    timestamp = timestamp,
//                    base64Key = base64Key
//                )
//                deviceInfoDao.insertDevice(newDevice)
//                Log.d("GO_RECEIVER", "Inserted device: $newDevice")
//            } else {
//                Log.d(
//                    "GO_RECEIVER",
//                    "Skipped duplicate: $name - $wfdIp"
//                )
//            }
//        }
//
//        Log.d("GM_RECEIVER", "Parsed HELLO message")
//
//        legacyClientCallback()
//    }
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val chan = NotificationChannel(
//                "SYNC_CHANNEL",
//                "Group Pref Sync",
//                NotificationManager.IMPORTANCE_LOW
//            )
//            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
//            manager.createNotificationChannel(chan)
//        }
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        return START_STICKY // ensures service restarts if killed
//    }
//
//    override fun onBind(intent: Intent?): IBinder = binder
//
//    @SuppressLint("NewApi")
//    fun startChatServer(
//        onMessageReceived: (String) -> Unit
//    ) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                serverSocket = ServerSocket(PORT)
//                Log.d("TCP", "Server started on port $PORT")
//
//                while (true) {
//                    val clientSocket = serverSocket!!.accept()
//                    getClientIP(clientSocket)
////                    connectionCallback(clientSocket)
//                    Log.d("TCP", "Client connected: ${clientSocket.inetAddress.hostAddress}")
//                    handleClient(clientSocket, onMessageReceived)
//                }
//            } catch (e: IOException) {
//                Log.e("TCP", "Server error: ${e.message}")
//            }
//        }
//    }
//
//    private fun connectionCallback(clientSocket: Socket) {
//        if (isGOViaWFD) {
//            deviceConnectionCallback(
//                clientSocket.inetAddress.hostAddress!!
//            )
//        }
//    }
//
//    private fun getClientIP(clientSocket: Socket) {
//        clientSockets.add(clientSocket)
//        lastClientAddress = clientSocket.inetAddress  // ← Save GM's IP
//        gmAddresses.add(clientSocket)
//        connectedGMIPs.add(clientSocket.inetAddress.hostAddress!!) // Save GM IP
//    }
//
//    private fun handleClient(
//        socket: Socket,
//        onMessageReceived: (String) -> Unit
//    ) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
//                var line: String?
//
//                while (reader.readLine().also { line = it } != null) {
//                    Log.d("TCP", "Received (raw): $line")
//
////                    // Attempt to decrypt if JSON has "ciphertext" and "iv"
////                    val lineStr = try {
////                        val obj = JSONObject(line)
////                        if (obj.has("ciphertext") && obj.has("iv")) {
////                            val senderIp = socket.inetAddress.hostAddress!!
////                            val aesKey = peerAESKeys[senderIp]
////                            if (aesKey != null) {
////                                val decrypted = AESGCMHelper.decrypt(
////                                    aesKey,
////                                    Base64.decode(obj.getString("ciphertext"), Base64.NO_WRAP),
////                                    Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
////                                )
////                                Log.d("DECRYPT", "Decrypted from $senderIp: $decrypted")
////                                decrypted
////                            } else {
////                                Log.w("DECRYPT", "No AES key for $senderIp")
////                                line
////                            }
////                        } else {
////                            line
////                        }
////                    } catch (e: Exception) {
////                        Log.e("DECRYPT", "Failed to parse or decrypt: ${e.message}")
////                        line
////                    }
//
//                    try {
//                        when {
//                            line!!.startsWith("DEVICE_LIST:") -> {
//                                parseDeviceList(line!!)
//                            }
//
//                            line!!.startsWith("PEER_KEYS_LIST:") -> {
//                                parseKeysList(line!!)
//                            }
//
//                            line!!.startsWith("{") && line!!.endsWith("}") -> {
//                                val obj = JSONObject(line.toString())
//
//                                when {
//                                    obj.optString("type") == "KEY_EXCHANGE" -> {
//                                        startKeyExchange(socket, obj)
//                                    }
//
//                                    obj.optString("type") == "WFD_HELLO" -> {
//                                        receiveHelloMessage(obj, socket)
////                                        legacyClientCallback()
//                                    }
//
//                                    obj.optString("type") == "LC_HELLO" -> {
//                                        receiveHelloMessage(obj, socket)
////                                        legacyClientCallback()
//                                    }
//
//                                    obj.has("name") && obj.has("ip") && obj.has("timestamp") -> {
//                                        getDevicesInfo(obj)
//                                    }
//
//                                    (obj.has("ciphertext") && obj.has("iv")) -> {
//                                        getEncryptedMessage(socket.inetAddress.hostAddress!!, obj)
//                                    }
//                                }
//                            }
//
////                            else -> {
////                                val obj = JSONObject(line)
////
////                                // Handle encrypted chat message
////                                if (obj.has("ciphertext") && obj.has("iv")) {
////                                    getEncryptedMessage(socket, obj)
////                                }
////                            }
//                        }
//                    } catch (e: Exception) {
//                        Log.e("MSG_PARSE", "Error parsing message: ${e.message}")
//                    }
//                    onMessageReceived(line!!)
//                }
//
//                socket.close()
////                clientSockets.remove(socket)
//
//            } catch (e: IOException) {
//                Log.e("TCP", "Client disconnected or error: ${e.message}")
//            } finally {
//                try {
//                    socket.close()
////                    clientSockets.remove(socket)
//                } catch (e: IOException) {
//                    Log.e("TCP", "Error closing socket: ${e.message}")
//                }
//            }
//        }
//    }
//
//    private suspend fun getEncryptedMessage(rawIp: String, obj: JSONObject) {
////        val rawIp = socket.inetAddress.hostAddress ?: return
//        val senderIp = if (rawIp == "::1") "127.0.0.1" else rawIp
//
//        var aesKey = peerAESKeys[senderIp]
//        if (aesKey == null) {
//            Log.w("DECRYPT", "AES key not in memory for $senderIp. Trying Room DB...")
//
//            // Try loading from Room DB
//            val entity = peerPublicKeyDao.getKeyByIp(senderIp)
//            if (entity != null) {
//                val peerPublicKey = KeyStoreManager.base64ToPublicKey(entity.base64Key)
//                aesKey = KeyStoreManager.deriveSharedAESKey(peerPublicKey)
//
//                // Cache it
//                peerAESKeys[senderIp] = aesKey
//                Constants.peerPublicKeys[senderIp] = peerPublicKey
//
//                Log.d("DECRYPT", "Derived AES key from DB for $senderIp")
//            } else {
//                Log.e("DECRYPT", "No public key found in DB for $senderIp")
//            }
//        }
//
//        if (aesKey != null) {
//            try {
//                val ciphertext = Base64.decode(obj.getString("ciphertext"), Base64.NO_WRAP)
//                val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
//
//                // the aeskey is null here, the problem with first encrypted message
//                val decrypted = AESGCMHelper.decrypt(aesKey, ciphertext, iv)
//
////                if (decrypted.contains("ciphertext") || decrypted.contains("iv")){
////                    decrypted = AESGCMHelper.decrypt(aesKey, ciphertext, iv)
////                    return
////                }
//
////                // Try parsing decrypted string as JSON
////                val maybeJson = try {
////                    JSONObject(decrypted)
////                } catch (e: Exception) {
////                    null
////                }
////
////                // If it's a wrapped encrypted JSON again, decrypt again
////                if (maybeJson != null && maybeJson.has("ciphertext") && maybeJson.has("iv")) {
////                    val innerCiphertext = Base64.decode(maybeJson.getString("ciphertext"), Base64.NO_WRAP)
////                    val innerIv = Base64.decode(maybeJson.getString("iv"), Base64.NO_WRAP)
////
////                    decrypted = AESGCMHelper.decrypt(aesKey, innerCiphertext, innerIv)
////                }
//
//                if (decrypted.trim() == "ping") {
//                    Log.d("KeepAlive", "Ping received from $senderIp")
//                    return
//                }
//
//
//                Log.d("CHAT", "Decrypted message from $senderIp: $decrypted")
//
//                Handler(Looper.getMainLooper()).post {
//                    Constants.chatCallback(senderIp, decrypted)
//                }
//
//            } catch (e: Exception) {
//                Log.e("DECRYPT", "Decryption failed from $senderIp: ${e.message}", e)
//            }
//        } else {
//            Log.w("DECRYPT", "Still no AES key available for $senderIp")
//        }
//    }
//
//    private suspend fun getDevicesInfo(obj: JSONObject) {
//        val name = obj.getString("name")
//        val wfdIp = obj.getString("wfdIp")
//        val lcIp = obj.getString("lcIp")
//        val androidId = obj.getString("androidId")
//        val groupId = obj.getString("groupId")
//        val isGO = obj.getBoolean("isGroupOwner")
//        val isRelayDevice = obj.getBoolean("isRelayDevice")
//        val timestamp = obj.getLong("timestamp")
//        val base64Key = obj.getString("base64Key")
//
//        val device = DeviceInfo(
//            name = name,
//            wfdIp = wfdIp,
//            lcIp = lcIp,
//            androidId = androidId,
//            groupId = groupId,
//            isGroupOwner = isGO,
//            isRelayDevice = isRelayDevice,
//            timestamp = timestamp,
//            base64Key = base64Key
//        )
//        val exists = deviceInfoDao.isDuplicateDevice(name = name, androidId = androidId) > 0
//        if (!exists) deviceInfoDao.insertDevice(device)
//
//        Log.d(
//            "GM_RECEIVER",
//            "Parsed fallback device broadcast: $device"
//        )
//    }
//
//    private suspend fun receiveHelloMessage(obj: JSONObject, socket: Socket) {
//        if (obj.optString("type") == "WFD_HELLO") {
//            val name =
//                obj.getString("name")        // name tag is not matching to the hello message received
//            val wfdIp = obj.getString("wfdIp")
//            val lcIp = obj.getString("lcIp")
//            val androidId = obj.getString("androidId")
//            var groupId = obj.getString("groupId")
//            if (groupId.isNullOrBlank()) {
//                val prefs = getSharedPreferences("group_prefs", Context.MODE_PRIVATE)
//                groupId = prefs.getString("groupId", null).toString()
//            }
//
//            val isGroupOwner = obj.getBoolean("isGroupOwner")
//            val isRelayDevice = obj.getBoolean("isRelayDevice")
//            val timestamp = obj.getLong("timestamp")
//            val base64Key = obj.getString("base64Key")
//
//            val duplicate = deviceInfoDao.isDuplicateDevice(
//                name = name,
//                androidId = androidId
//            ) > 0
//            if (!duplicate) {
//                val newDevice = DeviceInfo(
//                    name = name,
//                    wfdIp = wfdIp,
//                    lcIp = lcIp,
//                    androidId = androidId,
//                    groupId = groupId,
//                    isGroupOwner = isGroupOwner,
//                    isRelayDevice = isRelayDevice,
//                    timestamp = timestamp,
//                    base64Key = base64Key
//                )
//                deviceInfoDao.insertDevice(newDevice)
//                Log.d("GO_RECEIVER", "Inserted device: $newDevice")
//            } else {
//                Log.d(
//                    "GO_RECEIVER",
//                    "Skipped duplicate: $name - $wfdIp"
//                )
//            }
//            Log.d("GM_RECEIVER", "Parsed HELLO message")
//
//            withContext(Dispatchers.IO) {
//                // After processing HELLO, send GO public key
//                try {
//                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
//                    val response = JSONObject().apply {
//                        put("type", "KEY_EXCHANGE")
//                        put(
//                            "publicKey",
//                            KeyStoreManager.getOwnPublicKeyBase64()
//                        )
//                    }
//                    writer.write(response.toString() + "\n")
//                    writer.flush()
//                    Log.d(
//                        "KEY_EXCHANGE",
//                        "Auto-sent GO public key to GM after HELLO"
//                    )
//                } catch (e: Exception) {
//                    Log.e(
//                        "KEY_EXCHANGE",
//                        "Failed to send public key after HELLO: ${e.message}"
//                    )
//                }
//            }
//
//        } /*else if (obj.optString("type") == "LC_HELLO") {
//            ipLcGo = obj.getString("lcIpGO")
//            val devicesArray = obj.getJSONArray("devices")
//            for (i in 0 until devicesArray.length()) {
//                val device = devicesArray.getJSONObject(i)
//                val name =
//                    device.getString("name")        // name tag is not matching to the hello message received
//                val wfdIp = device.getString("wfdIp")
//                val lcIp = device.getString("lcIp")
//                val androidId = device.getString("androidId")
//                val groupId = device.getString("groupId")
//                val isGroupOwner = device.getBoolean("isGroupOwner")
//                val isRelayDevice = device.getBoolean("isRelayDevice")
//                val timestamp = device.getLong("timestamp")
//                val base64Key = device.getString("base64Key")
//
////                val myAndroidIdSavedInDb =
////                    Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
//
////                if (androidId == myAndroidIdSavedInDb && isGroupOwner) {
////                    // This is the GO’s own record — update LC IP and Relay Flag
////                    deviceInfoDao.updateLcIpByAndroidId(
////                        androidId = androidId,
////                        newLcIp = lcIp,
////                        isRelayDevice = isRelayDevice
////                    )
////                    Log.d(
////                        "GO_RECEIVER",
////                        "Updated GO’s own LC IP and Relay Status → $lcIp | $isRelayDevice"
////                    )
////                }
//
//                val duplicate = deviceInfoDao.isDuplicateDevice(
//                    name = name,
//                    androidId = androidId
//                ) > 0
//                if (!duplicate) {
//                    val newDevice = DeviceInfo(
//                        name = name,
//                        wfdIp = wfdIp,
//                        lcIp = lcIp,
//                        androidId = androidId,
//                        groupId = groupId,
//                        isGroupOwner = isGroupOwner,
//                        isRelayDevice = isRelayDevice,
//                        timestamp = timestamp,
//                        base64Key = base64Key
//                    )
//                    deviceInfoDao.insertDevice(newDevice)
//                    Log.d("GO_RECEIVER", "Inserted device: $newDevice")
//                } else {
//                    Log.d(
//                        "GO_RECEIVER",
//                        "Skipped duplicate: $name - $wfdIp"
//                    )
//                }
//            }
//
//            Log.d("GM_RECEIVER", "Parsed HELLO message")
//            // After processing HELLO, send GO public key
////            withContext(Dispatchers.IO) {
////                try {
////                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
////                    val response = JSONObject().apply {
////                        put("type", "KEY_EXCHANGE")
////                        put(
////                            "publicKey",
////                            KeyStoreManager.getOwnPublicKeyBase64()
////                        )
////                    }
////                    writer.write(response.toString() + "\n")
////                    writer.flush()
////                    Log.d(
////                        "KEY_EXCHANGE",
////                        "Auto-sent GO public key to GM after HELLO"
////                    )
////                } catch (e: Exception) {
////                    Log.e(
////                        "KEY_EXCHANGE",
////                        "Failed to send public key after HELLO: ${e.message}"
////                    )
////                }
////            }
//
//            legacyClientCallback()
//        }*/
//    }
//
//    private fun startKeyExchange(socket: Socket, obj: JSONObject) {
//        try {
//            val rawIp = socket.inetAddress.hostAddress ?: "unknown"
//            val senderIp = if (rawIp == "::1") "127.0.0.1" else rawIp
//            val base64PublicKey = obj.getString("publicKey")
//
//            Log.d("KEY_EXCHANGE", "Received public key from $senderIp: $base64PublicKey")
//
//            // 1. Convert Base64 to PublicKey
//            val peerPublicKey = KeyStoreManager.base64ToPublicKey(base64PublicKey)
//
//            // 2. Derive AES key
//            val aesKey = KeyStoreManager.deriveSharedAESKey(peerPublicKey)
//
//            // 3. Store in memory
//            Constants.peerAESKeys[senderIp] = aesKey
//            Constants.peerPublicKeys[senderIp] = peerPublicKey
//
//            Log.d("KEY_EXCHANGE", "Shared AES key stored for $senderIp")
//
//            // 4. Save peer public key to Room (if not already exists)
//            CoroutineScope(Dispatchers.IO).launch {
//                try {
//                    val existing = peerPublicKeyDao.getKeyByIp(senderIp)
//                    if (existing == null) {
//                        peerPublicKeyDao.insertKey(
//                            PeerPublicKeyEntity(ip = senderIp, base64Key = base64PublicKey)
//                        )
//                        Log.d("E2EE", "Saved peer public key to Room for $senderIp")
//
//                        // 5. Send our own public key back
//                        val senderSocket = Socket(senderIp, PORT)
//                        val writer =
//                            BufferedWriter(OutputStreamWriter(senderSocket.getOutputStream()))
//                        val response = JSONObject().apply {
//                            put("type", "KEY_EXCHANGE")
//                            put("publicKey", KeyStoreManager.getOwnPublicKeyBase64())
//                        }
//                        writer.write(response.toString() + "\n")
//                        writer.flush()
//                        senderSocket.close()
//
//                        Log.d("KEY_EXCHANGE", "Sent GO public key to $senderIp")
//                    } else {
//                        Log.d("E2EE", "Peer public key already exists in Room for $senderIp")
//
//                        return@launch
//                    }
//                } catch (e: Exception) {
//                    Log.e("E2EE", "Failed to save public key to Room: ${e.message}")
//                }
//            }
//            socket.close()
//
//
//
//
//            connectionCallback(socket)
//        } catch (e: Exception) {
//            Log.e("KEY_EXCHANGE", "Failed to handle key exchange: ${e.message}", e)
//        }
//    }
//
//    private suspend fun parseKeysList(lineStr: String) {
//        val jsonArrayString = lineStr.removePrefix("PEER_KEYS_LIST:")
//        val jsonArray = JSONArray(jsonArrayString)
//        val parsedPeerKeys = mutableListOf<PeerPublicKeyEntity>()
//
//        for (i in 0 until jsonArray.length()) {
//            val obj = jsonArray.getJSONObject(i)
//            val device = PeerPublicKeyEntity(
//                ip = obj.getString("ip"),
//                base64Key = obj.getString("base64Key")
//            )
//            parsedPeerKeys.add(device)
//        }
//
//        val existingKeys =
//            peerPublicKeyDao.getAllKeys().first() // Use suspend function to fetch current keys
//        val existingIps = existingKeys.map { it.ip }
//        parsedPeerKeys.forEach { key ->
//            if (key.ip !in existingIps) {
//                peerPublicKeyDao.insertKey(key)
//            }
//        }
//
//        Log.d("GM_RECEIVER", "Parsed PEER_KEYS_LIST broadcast: $parsedPeerKeys")
//    }
//
////    private suspend fun parseDeviceList(lineStr: String) {
////        val jsonArrayString = lineStr.removePrefix("DEVICE_LIST:")
////        val jsonArray = JSONArray(jsonArrayString)
////        val parsedDevices = mutableListOf<DeviceInfo>()
////
////        for (i in 0 until jsonArray.length()) {
////            val obj = jsonArray.getJSONObject(i)
////            val device = DeviceInfo(
////                deviceId = obj.optInt("deviceId"),
////                name = obj.getString("name"),
////                wfdIp = obj.getString("wfdIp"),
////                lcIp = obj.getString("lcIp"),
////                androidId = obj.getString("androidId"),
////                groupId = obj.getString("groupId"),
////                isGroupOwner = obj.getBoolean("isGroupOwner"),
////                isRelayDevice = obj.getBoolean("isRelayDevice"),
////                timestamp = obj.getLong("timestamp"),
////                base64Key = obj.getString("base64Key")
////            )
////            parsedDevices.add(device)
////        }
////
////        parsedDevices.forEach { device ->
////            val exists = deviceInfoDao.isDuplicateDevice(
////                name = device.name,
////                androidId = device.androidId
////            ) > 0
////            if (!exists) deviceInfoDao.insertDevice(device)
////        }
////
////        Log.d("GM_RECEIVER", "Parsed DEVICE_LIST broadcast: $parsedDevices")
////
////
////        // Relay same DEVICE_LIST to WFD + LC peers
////        try {
////            // Make sure to send exactly as you received
////            val rawJson = "DEVICE_LIST:$jsonArrayString"
////
////            if (shouldRelayDeviceList(rawJson)) {
////                broadcastMessageToAllWfdPeers(rawJson)
//////                broadcastMessageToAllLegacyClients(rawJson)
////
////                Log.d("GM_RELAY", "Relayed DEVICE_LIST to all peers.")
////
////            } else {
////                Log.d("GM_RELAY", "Skipped relaying duplicate DEVICE_LIST.")
////            }
////        } catch (e: Exception) {
////            Log.e("GM_RELAY", "Failed to relay DEVICE_LIST: ${e.message}")
////        }
////    }
//
//    private suspend fun parseDeviceList(lineStr: String) {
//        val jsonArrayString = lineStr.removePrefix("DEVICE_LIST:")
//        val jsonArray = JSONArray(jsonArrayString)
//        val parsedDevices = mutableListOf<DeviceInfo>()
//
//        for (i in 0 until jsonArray.length()) {
//            val obj = jsonArray.getJSONObject(i)
//            val device = DeviceInfo(
//                deviceId = obj.optInt("deviceId"),
//                name = obj.getString("name"),
//                wfdIp = obj.getString("wfdIp"),
//                lcIp = obj.getString("lcIp"),
//                androidId = obj.getString("androidId"),
//                groupId = obj.getString("groupId"),
//                isGroupOwner = obj.getBoolean("isGroupOwner"),
//                isRelayDevice = obj.getBoolean("isRelayDevice"),
//                timestamp = obj.getLong("timestamp"),
//                base64Key = obj.getString("base64Key")
//            )
//            parsedDevices.add(device)
//        }
//
//        parsedDevices.forEach { device ->
//            val exists = deviceInfoDao.isDuplicateDevice(
//                name = device.name,
//                androidId = device.androidId
//            ) > 0
//            if (!exists) deviceInfoDao.insertDevice(device)
//        }
//
//        Log.d("GM_RECEIVER", "Parsed DEVICE_LIST broadcast: $parsedDevices")
//
////        // Perform relay logic only if this device is a RELAY
////        if (!isRelayDevice()) {
////            Log.d("GM_RELAY", "Not a relay device. Skipping handoff.")
////            return
////        }
//
//
////        WFD-GO can not be LC-GM, need to change strategy, Now, WFD-GM will work as LC-GM
////        Reason Being WFD-GO wlan0 is required for both WFD and LC but can be used only for one connection at a time
////        While WFD-GM uses p2p0 for WFD and can use wlan0 for LC simultanouslly, Now Start Shifting LC-GM responsiblity to WFD-GM
//
//        // Extract groupId of this device (relay) from parsed list
//        val thisDeviceAndroidId =
//            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
//        val groupId =
//            parsedDevices.find { it.androidId.trim() == thisDeviceAndroidId }?.groupId?.trim()
//        val isRelay = parsedDevices.any { it.isRelayDevice }
//
//        if (groupId == null && !isRelay) {
//            Log.e("GM_RELAY", "GroupId not found OR this is not a relay device in DEVICE_LIST.")
//            return
//        }
//
//        if (isRelay) {
//            // Coroutine for switching networks and relaying
//            CoroutineScope(Dispatchers.IO).launch {
//                try {
//                    // 1. Disconnect from LC-GO (hotspot)
////                    disconnectFromLegacyNetwork()
//
//                    // 2. Wait for disconnection to complete
////                    delay(10000)
//
////                if (isCurrentlyConnectedToHotspot()) {
//                    // 3. Reconnect to WFD group
////                    if (groupId != null) {
////                        reconnectToWfdGroup(groupId) { reconnected ->
////                            if (reconnected) {
//                    Log.d("GM_RELAY", "Reconnected to WFD group.")
//
//                    try {
//                        val rawJson = "DEVICE_LIST:$jsonArrayString"
//
////                                    if (shouldRelayDeviceList(rawJson)) {
//                        broadcastMessageToAllWfdPeersAsRelay(rawJson, groupId)
//                        Log.d("GM_RELAY", "Relayed DEVICE_LIST to WFD peers.")
////                                    } else {
////                                        Log.d("GM_RELAY", "Skipped duplicate DEVICE_LIST.")
////                                    }
//                    } catch (e: Exception) {
//                        Log.e(
//                            "GM_RELAY",
//                            "Failed to relay DEVICE_LIST after WFD rejoin: ${e.message}"
//                        )
//                    }
////                            } else {
////                                Log.e("GM_RELAY", "Failed to reconnect to WFD group.")
////                            }
////                        }
////                    } else {
////                        Log.e("GM_RELAY", "GroupId is null")
////                    }
////                } else {
////                    Log.e("GM_RELAY", "Device is not disconnected from Hotspot")
////                }
//
//
//                } catch (e: Exception) {
//                    Log.e("GM_RELAY", "Error in relay handoff: ${e.message}")
//                }
//            }
//        } else {
//            broadcastMessageToAllWfdPeers(jsonArrayString)
//        }
//    }
//
////    private suspend fun parseDeviceList(lineStr: String) {
////        val jsonArrayString = lineStr.removePrefix("DEVICE_LIST:")
////        val jsonArray = JSONArray(jsonArrayString)
////        val parsedDevices = mutableListOf<DeviceInfo>()
////
////        for (i in 0 until jsonArray.length()) {
////            val obj = jsonArray.getJSONObject(i)
////            val device = DeviceInfo(
////                deviceId = obj.optInt("deviceId"),
////                name = obj.getString("name"),
////                wfdIp = obj.getString("wfdIp"),
////                lcIp = obj.getString("lcIp"),
////                androidId = obj.getString("androidId"),
////                groupId = obj.getString("groupId"),
////                isGroupOwner = obj.getBoolean("isGroupOwner"),
////                isRelayDevice = obj.getBoolean("isRelayDevice"),
////                timestamp = obj.getLong("timestamp"),
////                base64Key = obj.getString("base64Key")
////            )
////            parsedDevices.add(device)
////        }
////
////        // Insert new devices only
////        parsedDevices.forEach { device ->
////            val exists = deviceInfoDao.isDuplicateDevice(
////                name = device.name,
////                androidId = device.androidId
////            ) > 0
////            if (!exists) deviceInfoDao.insertDevice(device)
////        }
////
////        Log.d("GM_RECEIVER", "Parsed DEVICE_LIST broadcast: $parsedDevices")
////
////        val thisDeviceAndroidId =
////            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).trim()
////        Log.d("GM_RECEIVER", "Local androidId = $thisDeviceAndroidId")
////
////        val isRelay =
////            parsedDevices.any { it.androidId.trim() == thisDeviceAndroidId && it.isRelayDevice }
////
////        if (!isRelay) {
////            Log.d("GM_RELAY", "This is not a relay device. Skipping broadcast.")
////            return
////        }
////
////        try {
////            val rawJson = "DEVICE_LIST:$jsonArrayString"
////            if (shouldRelayDeviceList(rawJson)) {
////                broadcastMessageToAllWfdPeers(rawJson)
////                Log.d("GM_RELAY", "Relayed DEVICE_LIST to WFD peers.")
////            } else {
////                Log.d("GM_RELAY", "Skipped duplicate DEVICE_LIST.")
////            }
////        } catch (e: Exception) {
////            Log.e("GM_RELAY", "Failed to relay DEVICE_LIST: ${e.message}")
////        }
////    }
//
//
//    fun isCurrentlyConnectedToHotspot(): Boolean {
//        val connectivityManager =
//            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//        val network = connectivityManager.activeNetwork ?: return false
//        val capabilities = connectivityManager.getNetworkCapabilities(network)
//        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
//    }
//
//    private fun disconnectFromLegacyNetwork() {
//        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
//        wifiManager.disconnect()
//        Log.d("NETWORK_SWITCH", "Disconnected from legacy hotspot.")
//    }
//
//    @SuppressLint("MissingPermission")
//    fun reconnectToWfdGroup(groupId: String, callback: (Boolean) -> Unit) {
//        val context = applicationContext
//        val wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
//        val channel = wifiP2pManager.initialize(context, Looper.getMainLooper(), null)
//
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                delay(10000) // Delay before reconnecting, adjust as needed
//
//                // Fetch known GO device for this group
//                val knownGoDevice = deviceInfoDao.getLatestWfdGoDeviceByGroupId(groupId)
//                if (knownGoDevice == null) {
//                    Log.e("WFD_RECONNECT", "No known WFD GO device found in DB.")
//                    withContext(Dispatchers.Main) { callback(false) }
//                    return@launch
//                }
//
//                val config = WifiP2pConfig().apply {
//                    deviceAddress =
//                        "5a:32:68:25:65:33" // Replace with knownGoDevice.wfdMac if available
//                    wps.setup = WpsInfo.PBC
//                    groupOwnerIntent = 0
//                }
//
//                withContext(Dispatchers.Main) {
//                    wifiP2pManager.connect(channel, config, object : WifiP2pManager.ActionListener {
//                        override fun onSuccess() {
//                            Log.d("WFD_RECONNECT", "Connection initiated to WFD GO.")
//
//                            // Wait for actual connection to form (polling style)
//                            CoroutineScope(Dispatchers.IO).launch {
//                                delay(4000) // Give system time to establish group
//                                withContext(Dispatchers.Main) {
//                                    wifiP2pManager.requestConnectionInfo(channel) { info ->
//                                        if (info.groupFormed && !info.isGroupOwner) {
////                                            val goName = info.groupOwnerAddress?.hostName
//                                            val goIp = info.groupOwnerAddress?.hostAddress ?: "N/A"
//                                            Log.d(
//                                                "WFD_RECONNECT",
//                                                "Connected to WFD group. GO IP: $goIp"
//                                            )
//
//                                            CoroutineScope(Dispatchers.IO).launch {
//                                                try {
//                                                    deviceInfoDao.updateWfdIpByNameAndGroupId(
//                                                        groupId,
//                                                        goIp
//                                                    )
//
//                                                    callback(true)
//                                                } catch (e: Exception) {
//                                                    Log.e(
//                                                        "WFD_RECONNECT",
//                                                        "Failed to update GO WFD IP: ${e.message}"
//                                                    )
//                                                }
//                                            }
//                                        } else {
//                                            Log.e(
//                                                "WFD_RECONNECT",
//                                                "WFD group not formed or this device is GO."
//                                            )
//                                            callback(false)
//                                        }
//                                    }
//                                }
//                            }
//                        }
//
//                        override fun onFailure(reason: Int) {
//                            Log.e("WFD_RECONNECT", "WFD connect() failed. Reason: $reason")
//                            callback(false)
//                        }
//                    })
//                }
//
//            } catch (e: Exception) {
//                Log.e("WFD_RECONNECT", "Error in reconnectToWfdGroup: ${e.message}")
//                withContext(Dispatchers.Main) { callback(false) }
//            }
//        }
//    }
//
//    private fun connectToKnownWfdGo(
//        manager: WifiP2pManager,
//        channel: WifiP2pManager.Channel
//    ): Boolean {
//        // TODO: Match saved GO from previous group and initiate connection
//        // manager.connect(...) using WifiP2pConfig
//        return true // simulate success for now
//    }
//
//
//    private var lastDeviceListHash: Int? = null
//
////    private fun shouldRelayDeviceList(rawJson: String): Boolean {
////        val currentHash = rawJson.hashCode()
////        return if (lastDeviceListHash != currentHash) {
////            lastDeviceListHash = currentHash
////            true
////        } else {
////            false
////        }
////    }
//
//
////    private suspend fun isDuplicateDevice(
////        name: String,
//////        ip: String,
////        androidId: String/*,
////        timestamp: Long*/
////    ): Boolean {
////        val byName = deviceInfoDao.findByName(name = name)
//////        val byIp = deviceInfoDao.findByIp(ip = ip)
////        val byAndroidId = deviceInfoDao.findByAndroidId(androidId = androidId)
//////        val recent = deviceInfoDao.findRecent(currentTime = timestamp)
////
////        return byName.any { d ->
//////            byIp.any { it.deviceId == d.deviceId } &&
////                    byAndroidId.any { it.androidId == d.androidId } /*&&
////                    recent.any { it.deviceId == d.deviceId }*/
////        }
////    }
//
////    private fun observeDeviceListChanges() {
////        CoroutineScope(Dispatchers.IO).launch {
////            deviceInfoDao.getAllDevices().collectLatest { currentList ->
////                if (currentList.size > lastBroadcastSize) {
////                    lastBroadcastSize = currentList.size
////                    broadcastUpdatedDeviceList(currentList)
////                }
////            }
////        }
////    }
//
//    private val knownDeviceKeys = mutableSetOf<String>()
//
//    private fun observeDeviceListChanges() {
//        CoroutineScope(Dispatchers.IO).launch {
//            deviceInfoDao.getAllDevices().collectLatest { currentList ->
//
//                val newDeviceKeys = currentList.map { device ->
//                    "${device.name}_${device.wfdIp}_${device.androidId}"
//                }.toSet()
//
//                val isNewEntryAdded = newDeviceKeys.any { it !in knownDeviceKeys }
//
//                if (isNewEntryAdded) {
//                    knownDeviceKeys.clear()
//                    knownDeviceKeys.addAll(newDeviceKeys)
//                    broadcastUpdatedDeviceList(currentList)
//                }
//            }
//        }
//    }
//
//    private fun broadcastUpdatedDeviceList(devices: List<DeviceInfo>) {
//        val now = System.currentTimeMillis()
//        if (now - lastBroadcastTime < 5000) {
//            Log.d("AUTO_REBROADCAST", "Debounced rebroadcast")
//            return
//        }
//        lastBroadcastTime = now
//
//        val jsonArray = JSONArray()
//        devices.forEach { device ->
//            val obj = JSONObject()
//            obj.put("deviceId", device.deviceId)
//            obj.put("name", device.name)
//            obj.put("wfdIP", device.wfdIp)
//            obj.put("lcIp", device.lcIp)
//            obj.put("androidId", device.androidId)
//            obj.put("isGroupOwner", device.isGroupOwner)
//            obj.put("timestamp", device.timestamp)
//            jsonArray.put(obj)
//        }
//
//        val message = "DEVICE_LIST:$jsonArray"
//        Log.d("AUTO_REBROADCAST", "Rebroadcasting updated device list")
//
//        devices.forEach { device ->
//            try {
//                val socket = Socket()
//                socket.connect(InetSocketAddress(device.wfdIp, PORT), 2000)
//                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
//                writer.write(message)
//                writer.flush()
//                writer.close()
//                socket.close()
//            } catch (e: Exception) {
//                Log.e(
//                    "REBROADCAST_ERROR",
//                    "Failed to send to ${device.wfdIp}: ${e.localizedMessage}"
//                )
//            }
//        }
//    }
//
//    //    private fun broadcastMessageToAllWfdPeersAsRelay(dataToSend: String, groupId: String?) {
////        CoroutineScope(Dispatchers.IO).launch {
////            val allDevices = deviceInfoDao.getAllDevicesOnce() // Suspended function
////            val localIp = (getInterfaceIp("p2p0"))
////
////            val localSocketAddress = InetSocketAddress("192.168.56.194", 0)
////
//////            if (localIp != null) {
//////                if (localIp.hostAddress == "0.0.0.0") {
////                    allDevices.forEach { device ->
////                        if (!device.isGroupOwner && device.groupId == groupId) {
////                            try {
////                                val socket = Socket()
////                                socket.bind(localSocketAddress)
////
////                                socket.connect(InetSocketAddress(device.wfdIp, PORT), 5000)
////                                val writer =
////                                    BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
////                                writer.write(dataToSend)
////                                writer.newLine()
////                                writer.flush()
////
////                                socket.close()
////                                Handler(Looper.getMainLooper()).post {
////                                    Log.d("BROADCAST", "Sent to ${device.wfdIp}")
////                                }
////                            } catch (e: Exception) {
////                                Handler(Looper.getMainLooper()).post {
////                                    Log.e(
////                                        "BROADCAST",
////                                        "Failed to send to ${device.wfdIp}: ${e.message}"
////                                    )
////                                }
////                                withContext(Dispatchers.Main) {
////                                    Toast.makeText(
////                                        applicationContext,
////                                        "Exception: ${e.message}",
////                                        Toast.LENGTH_SHORT
////                                    ).show()
////                                }
////
////                            }
////                        }
////                    }
//////                } else {
//////                    withContext(Dispatchers.Main) {
//////                        Toast.makeText(applicationContext, "localIp is 0.0.0.0", Toast.LENGTH_SHORT)
//////                            .show()
//////                    }
//////                }
//////            } else {
//////                withContext(Dispatchers.Main) {
//////                    Toast.makeText(applicationContext, "localIp is null", Toast.LENGTH_SHORT).show()
//////                }
//////            }
////        }
////    }
//
////    private fun broadcastMessageToAllWfdPeersAsRelay(dataToSend: String, groupId: String?) {
//////        NetworkBiner.unbind()
////
////        connectivityManager.bindProcessToNetwork(null)
////        if (networkCallback != null) {
////            try {
////                connectivityManager.unregisterNetworkCallback(networkCallback!!)
////            } catch (_: Exception) {
////            }
////        }
////        Log.d("NetworkBinder", "🔓 Unbound from $currentBoundInterface")
////        networkCallback = null
////        currentBoundInterface = null
////
////        CoroutineScope(Dispatchers.IO).launch {
//////            val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
//////            var p2pNetwork: Network? = null
//////            // Try to bind to p2p0 network before sending
//////            for (network in cm.allNetworks) {
//////                val caps = cm.getNetworkCapabilities(network)
//////                val linkProps = cm.getLinkProperties(network)
//////
//////                if (caps != null &&
//////                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
//////                    linkProps?.interfaceName?.startsWith("p2p") == true
//////                ) {
//////                    p2pNetwork = network
//////                    cm.bindProcessToNetwork(network)
//////                    Log.d("BROADCAST", "Successfully bound to p2p0")
//////                    break
//////                }
//////            }
//////            // Optional: wait briefly to ensure IP is assigned
//////            delay(10000)
//////            val localIp = getInterfaceIp("p2p0")
////            val allDevices = deviceInfoDao.getAllDevicesOnce()
////
//////            if (p2pNetwork == null || localIp == null || localIp.hostAddress == "0.0.0.0") {
//////                withContext(Dispatchers.Main) {
//////                    Toast.makeText(applicationContext, "P2P not bound or IP invalid", Toast.LENGTH_SHORT).show()
//////                }
//////                return@launch
//////            }
//////            val localSocketAddress = InetSocketAddress(localIp, 0)
////
////            for (device in allDevices) {
////                if (!device.isGroupOwner && device.groupId == groupId) {
////                    try {
////                        val socket = Socket()
//////                        socket.bind(localSocketAddress)
////                        socket.connect(InetSocketAddress(device.wfdIp, PORT), 5000)
////
////                        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
////                        writer.write(dataToSend)
////                        writer.newLine()
////                        writer.flush()
////                        socket.close()
////
////                        withContext(Dispatchers.Main) {
////                            Log.d("BROADCAST", "Sent to ${device.wfdIp}")
////                        }
////                    } catch (e: Exception) {
////                        withContext(Dispatchers.Main) {
////                            Log.e("BROADCAST", "Failed to send to ${device.wfdIp}: ${e.message}")
////                            Toast.makeText(
////                                applicationContext,
////                                "Send failed: ${e.message}",
////                                Toast.LENGTH_SHORT
////                            ).show()
////                        }
////                    }
////                }
////            }
////
////            // Optional: unbind after work
//////            cm.bindProcessToNetwork(null)
////        }
////    }
//
//    private fun broadcastMessageToAllWfdPeersAsRelay(dataToSend: String, groupId: String?) {
//        // Unbind any globally bound network (safe cleanup)
//        connectivityManager.bindProcessToNetwork(null)
//        if (networkCallback != null) {
//            try {
//                connectivityManager.unregisterNetworkCallback(networkCallback!!)
//            } catch (_: Exception) {
//            }
//        }
//        Log.d("NetworkBinder", " Unbound from $currentBoundInterface")
//        networkCallback = null
//        currentBoundInterface = null
//
//        CoroutineScope(Dispatchers.IO).launch {
//            val p2pInterface = NetworkInterface.getByName("p2p0")
//            val localP2pIp = p2pInterface?.inetAddresses?.toList()
//                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
//
//            if (p2pInterface == null || localP2pIp == null) {
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(
//                        applicationContext,
//                        " p2p0 interface or IP not found",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//                return@launch
//            }
//
//            val localSocketAddr = InetSocketAddress(localP2pIp, 0)
//            val allDevices = deviceInfoDao.getAllDevicesOnce()
//
//            for (device in allDevices) {
//                if (!device.isGroupOwner && device.groupId == groupId) {
//                    try {
//                        val socket = Socket()
//                        socket.bind(localSocketAddr)
//                        socket.connect(InetSocketAddress(device.wfdIp, PORT), 5000)
//
//                        socket.getOutputStream().bufferedWriter().use {
//                            it.write(dataToSend)
//                            it.newLine()
//                            it.flush()
//                        }
//                        socket.close()
//
//                        withContext(Dispatchers.Main) {
//                            Log.d("BROADCAST", " Sent to ${device.wfdIp}")
//                        }
//                    } catch (e: Exception) {
//                        withContext(Dispatchers.Main) {
//                            Log.e("BROADCAST", " Failed to ${device.wfdIp}: ${e.message}")
//                            Toast.makeText(
//                                applicationContext,
//                                "Send failed to ${device.wfdIp}: ${e.message}",
//                                Toast.LENGTH_SHORT
//                            ).show()
//                        }
//                    }
//                }
//            }
//        }
//    }
//
////    fun broadcastMessageToAllWfdPeers(dataToSend: String) {
////        CoroutineScope(Dispatchers.IO).launch {
////            val allDevices = deviceInfoDao.getAllDevicesOnce() // Suspended function
////
////            allDevices.forEach { device ->
////                if (!device.isGroupOwner) {
////                    try {
////                        val socket = Socket()
////                        socket.connect(InetSocketAddress(device.wfdIp, PORT), 5000)
////                        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
////                        writer.write(dataToSend)
////                        writer.newLine()
////                        writer.flush()
////
////                        socket.close()
////                        Handler(Looper.getMainLooper()).post {
////                            Log.d("BROADCAST", "Sent to ${device.wfdIp}")
////                        }
////                    } catch (e: Exception) {
////                        Handler(Looper.getMainLooper()).post {
////                            Log.e(
////                                "BROADCAST",
////                                "Failed to send to ${device.wfdIp}: ${e.message}"
////                            )
////                        }
////                    }
////                }
////            }
////        }
////    }
//
//    fun broadcastMessageToAllWfdPeers(dataToSend: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            val allDevices = deviceInfoDao.getAllDevicesOnce()
//
//            val p2pInterface = NetworkInterface.getByName("p2p0")
//            val localAddr = p2pInterface?.inetAddresses?.toList()
//                ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
//
//            if (p2pInterface == null || localAddr == null) {
//                Log.e("BROADCAST", "p2p0 interface or IP not available")
//                return@launch
//            }
//
//            allDevices.forEach { device ->
//                if (!device.isGroupOwner && device.wfdIp.isNotBlank()) {
//                    try {
//                        val socket = Socket()
//                        socket.bind(InetSocketAddress(localAddr, 0)) // bind to p2p0 IP
//                        socket.connect(InetSocketAddress(device.wfdIp, PORT), 5000)
//
//                        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
//                        writer.write(dataToSend)
//                        writer.newLine()
//                        writer.flush()
//                        socket.close()
//
//                        Handler(Looper.getMainLooper()).post {
//                            Log.d("BROADCAST", " Sent to ${device.wfdIp}")
//                        }
//                    } catch (e: Exception) {
//                        Handler(Looper.getMainLooper()).post {
//                            Log.e("BROADCAST", " Failed to send to ${device.wfdIp}: ${e.message}")
//                        }
//                    }
//                }
//            }
//        }
//    }
//
//    fun broadcastMessageToAllLegacyClients(dataToSend: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            val allDevices = deviceInfoDao.getAllDevicesOnce() // Suspended function
//
//            allDevices.forEach { device ->
////                if (!device.isGroupOwner) {
//
//
//                try {
//                    val socket = Socket()
//                    socket.connect(InetSocketAddress(device.wfdIp, PORT), 5000)
//                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
//                    writer.write(dataToSend)
//                    writer.newLine()
//                    writer.flush()
//
//                    socket.close()
//                    Handler(Looper.getMainLooper()).post {
//                        Log.d("BROADCAST", "Sent to ${device.wfdIp}")
//                    }
//
////                    val socket = Socket()
////                    socket.connect(InetSocketAddress(device.lcIp, PORT), 5000)
////                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
////                    writer.write(dataToSend)
////                    writer.newLine()
////                    writer.flush()
////
////                    socket.close()
////                    Handler(Looper.getMainLooper()).post {
////                        Log.d("BROADCAST", "Sent to ${device.lcIp}")
////                    }
//                } catch (e: Exception) {
//                    Handler(Looper.getMainLooper()).post {
//                        Log.e("BROADCAST", "Failed to send to ${device.lcIp}: ${e.message}")
//                    }
//                }
////                }
//            }
//        }
//    }
//
////    fun sendMessageToServerAsWfd(hostAddress: String, message: String) {
////        CoroutineScope(Dispatchers.IO).launch {
////            try {
////                val socket = Socket()
////                socket.connect(InetSocketAddress(hostAddress, PORT), 5000)
////                socket.getOutputStream().bufferedWriter().use {
////                    it.write(message)
////                    it.newLine()
////                    it.flush()
////                }
////
////                socket.close()
////
////                Log.d("TCP", "Multicast message sent to group $hostAddress")
////
////
//////                val socket = Socket()
//////                socket.connect(InetSocketAddress(hostAddress, PORT), 10000)
////////                ipLcGo = hostAddress
//////                socket.getOutputStream().bufferedWriter().use {
//////                    it.write(message)
//////                    it.newLine()
//////                    it.flush()
//////                }
//////                socket.close()
////            } catch (e: IOException) {
////                Log.e("TCP", "Send failed: ${e.message}")
////            }
////        }
////    }
//
//    fun sendMessageToServerAsWfd(hostAddress: String, message: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val p2pInterface = NetworkInterface.getByName("p2p0")
//                val localAddr = p2pInterface?.inetAddresses?.toList()
//                    ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
//
//                if (p2pInterface == null || localAddr == null) {
//                    Log.e("TCP", " p2p0 interface or IP not available")
//                    return@launch
//                }
//
//                val socket = Socket()
//                socket.bind(InetSocketAddress(localAddr, 0)) // bind to p2p0 interface
//
//                socket.connect(InetSocketAddress(hostAddress, PORT), 5000)
//
//                socket.getOutputStream().bufferedWriter().use {
//                    it.write(message)
//                    it.newLine()
//                    it.flush()
//                }
//
//                socket.close()
//                Log.d("TCP", " Unicast message sent to $hostAddress via p2p0")
//
//            } catch (e: IOException) {
//                Log.e("TCP", " Send failed: ${e.message}")
//            }
//        }
//    }
//
//    fun sendMessageToServerAsLc(hostAddress: String, message: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//             try {
//                val multicastGroup = InetAddress.getByName("230.0.0.1") // Safer custom group IP
//
//                val socket = DatagramSocket()
//
//                val message1 = "LC_HELLO from ${Build.MODEL}"
//                val data = message1.toByteArray()
//                val packet = DatagramPacket(data, data.size, multicastGroup, SILENTPORT)
//
//                socket.send(packet)
//                socket.close()
//
//                Log.d(
//                    "Multicast",
//                    " Message sent to $multicastGroup:$PORT"
//                )
//
//            } catch (e: Exception) {
//                Log.e("Multicast", " Failed to send multicast: ${e.message}")
//            }
//
//        }
//    }
//
////    fun connectToPeerAndSendMessage(goIp: String, message: String) {
////        CoroutineScope(Dispatchers.IO).launch {
////            try {
////                val socket = Socket(goIp, PORT)
////                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
////                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
////
////                val existingKey = peerAESKeys[goIp]
////                if (existingKey == null) {
////                    // Send our public key as KEY_EXCHANGE
////                    val request = JSONObject().apply {
////                        put("type", "KEY_EXCHANGE")
////                        put("publicKey", KeyStoreManager.getOwnPublicKeyBase64())
////                    }
////                    writer.write(request.toString() + "\n")
////                    writer.flush()
////                    Log.d("E2EE", "Sent our public key to $goIp")
////
////                    // Read GO's public key response
////                    val reply = reader.readLine() ?: return@launch
////                    val replyObj = JSONObject(reply)
////
////                    if (replyObj.optString("type") == "KEY_EXCHANGE") {
////                        val base64GoPubKey = replyObj.getString("publicKey")
////                        val goPubKey = KeyStoreManager.base64ToPublicKey(base64GoPubKey)
////
////                        val aesKey = KeyStoreManager.deriveSharedAESKey(goPubKey)
////                        peerAESKeys[goIp] = aesKey
////                        KeyStoreManager.addPeerPublicKey(goIp, goPubKey)
////
////                        // SAVE to Room
////                        try {
////                            val existing = peerPublicKeyDao.getKeyByIp(goIp)
////                            if (existing == null) {
////                                peerPublicKeyDao.insertKey(
////                                    PeerPublicKeyEntity(
////                                        ip = goIp,
////                                        base64Key = Base64.encodeToString(
////                                            goPubKey.encoded,
////                                            Base64.NO_WRAP
////                                        )
////                                    )
////                                )
////                            } else {
////                                Log.d("E2EE", "Saved peer public key to Room for $goIp")
////                            }
////                        } catch (e: Exception) {
////                            Log.e("E2EE", "Failed to save public key to Room: ${e.message}")
////                        }
////                        Log.d("KEY_EXCHANGE", "Stored GO public key and AES key for $goIp")
////                    }
////                }
////
////                // Encrypt the actual message
////                val aesKey = peerAESKeys[goIp]
////                if (aesKey != null) {
////                    val (ciphertext, iv) = AESGCMHelper.encrypt(aesKey, message)
////                    val wrappedJson = JSONObject().apply {
////                        put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
////                        put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
////                    }
////                    writer.write(wrappedJson.toString() + "\n")
////                    writer.flush()
////                    Log.d("E2EE", "Encrypted message sent to $goIp")
////                }
////
////                socket.close()
////            } catch (e: Exception) {
////                Log.e("E2EE", "Failed to connect/send to $goIp: ${e.message}")
////            }
////        }
////    }
//
//    fun connectToPeerAndSendMessage(targetIP: String, message: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val socket = Socket(targetIP, PORT)
//                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
////                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
//
////                // Step 1: Get public key from Room DB
////                val entity = peerPublicKeyDao.getKeyByIp(goIp)
////                if (entity == null) {
////                    Log.e("E2EE", "No public key found in DB for $goIp")
////                    socket.close()
////                    return@launch
////                }
////
////                val goPubKey = KeyStoreManager.base64ToPublicKey(entity.base64Key)
////
////                // Step 2: Derive shared AES key
////                val aesKey = KeyStoreManager.deriveSharedAESKey(goPubKey)
////                peerAESKeys[goIp] = aesKey // Cache in memory
////
////                // Step 3: Encrypt the message
////                val (ciphertext, iv) = AESGCMHelper.encrypt(aesKey, message)
////                val wrappedJson = JSONObject().apply {
////                    put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
////                    put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
////                }
////
////                // Step 4: Send the encrypted message
////                writer.write(message + "\n")
////                writer.flush()
//
//                // If not a keep-alive ping, send actual message
//                if (message != "ping") {
//                    writer.write(message + "\n")
//                    writer.flush()
//                    Log.d("E2EE", "Message sent to $targetIP")
//                }
//
//                Log.d("E2EE", "Encrypted message sent to $targetIP")
//                // Start keep-alive (pinging every 30s) after initial successful message
//                if (message != "ping") {
//                    startKeepAlive(targetIP)
//                }
//
//                socket.close()
//            } catch (e: Exception) {
//                Log.e("E2EE", "Failed to connect/send to $targetIP: ${e.message}")
//            }
//        }
//    }
//
//    private fun startKeepAlive(targetIP: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            while (true) {
//                delay(30_000) // Send ping every 30 seconds
//                try {
//                    connectToPeerAndSendMessage(targetIP, "ping")
//                    Log.d("KeepAlive", "Ping sent to $targetIP")
//                } catch (e: Exception) {
//                    Log.e("KeepAlive", "Ping failed: ${e.message}")
//                }
//            }
//        }
//    }
//}
//
