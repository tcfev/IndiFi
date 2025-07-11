package org.fordem.indifi.ui.utils

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.fordem.indifi.R
import org.fordem.indifi.ui.db.AppDatabase
import org.fordem.indifi.ui.model.DeviceInfo
import org.fordem.indifi.ui.db.DeviceInfoDao
import org.fordem.indifi.ui.db.PeerPublicKeyDao
import org.fordem.indifi.ui.encryption.AESGCMHelper
import org.fordem.indifi.ui.encryption.KeyStoreManager
import org.fordem.indifi.ui.model.PeerPublicKeyEntity
import org.fordem.indifi.ui.utils.Constants.PORT
import org.fordem.indifi.ui.utils.Constants.PREF_SYNC_PORT
import org.fordem.indifi.ui.utils.Constants.connectedGMIPs
import org.fordem.indifi.ui.utils.Constants.deviceConnectionCallback
import org.fordem.indifi.ui.utils.Constants.ipLcGo
import org.fordem.indifi.ui.utils.Constants.isGOViaWFD
import org.fordem.indifi.ui.utils.Constants.legacyClientCallback
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.SecretKey
import javax.inject.Inject

@AndroidEntryPoint
class MessageRouterService : Service() {
    private val clientSockets = mutableListOf<Socket>()
    private var lastClientAddress: InetAddress? = null
    private val gmAddresses = mutableSetOf<Socket>()  // All connected GMs
    private var serverSocket: ServerSocket? = null

    val peerAESKeys = mutableMapOf<String, SecretKey>()
    private var isServerRunning = false
    private val binder = LocalBinder()

    @Inject
    lateinit var deviceInfoDao: DeviceInfoDao

    @Inject
    lateinit var peerPublicKeyDao: PeerPublicKeyDao

    inner class LocalBinder : Binder() {
        fun getService(): MessageRouterService = this@MessageRouterService
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            val notification = Notification.Builder(this, "SYNC_CHANNEL")
                .setContentTitle("Group sync running")
                .setContentText("Listening to GO for updates")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .build()

            startForeground(101, notification)
        }


        // Start listening here
        startChatServer(
            onMessageReceived = { message ->
                Log.d("SERVICE", "Message received at startup: $message")
                // Handle message if needed

            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                "SYNC_CHANNEL",
                "Group Pref Sync",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY // ensures service restarts if killed
    }

    override fun onBind(intent: Intent?): IBinder = binder

    @SuppressLint("NewApi")
    fun startChatServer(
        onMessageReceived: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(PORT)
                Log.d("TCP", "Server started on port $PORT")

                while (true) {
                    val clientSocket = serverSocket!!.accept()
                    clientSockets.add(clientSocket)
                    lastClientAddress = clientSocket.inetAddress  // ← Save GM's IP
                    gmAddresses.add(clientSocket)
                    connectedGMIPs.add(clientSocket.inetAddress.hostAddress!!) // Save GM IP

                    if (isGOViaWFD) {
                        deviceConnectionCallback(
                            clientSocket.inetAddress.hostAddress!!
                        )
                    }
                    Log.d("TCP", "Client connected: ${clientSocket.inetAddress.hostAddress}")
                    handleClient(clientSocket, onMessageReceived)
                }
            } catch (e: IOException) {
                Log.e("TCP", "Server error: ${e.message}")
            }
        }
    }

//    fun startPrefSyncServer(context: Context, info: WifiP2pInfo) {
////        CoroutineScope(Dispatchers.IO).launch {
////            try {
////                val serverSocket = ServerSocket(8888)
////                while (true) {
////                    val socket = serverSocket.accept()
////                    val gmIP = socket.inetAddress.hostAddress ?: continue
////                    connectedGMIPs.add(gmIP)
////                    Log.d("GO_SERVER", "GM connected from $gmIP")
////
////                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
////                    val message = reader.readLine()
////                    Log.d("GO_SERVER", "Received: $message")
////
////                    // Optionally respond or update prefs here
////
////                    socket.close()
////                }
////            } catch (e: Exception) {
////                Log.e("GO_SERVER", "Error in TCP server", e)
////            }
////        }
//
//
//        if (info.groupOwnerAddress == null) {
//            Log.e("TCP_SYNC", "Group owner address is null. Aborting sync setup.")
//            return
//        }
//
//        val goIp = info.groupOwnerAddress.hostAddress ?: return
//
//        if (info.isGroupOwner) {
//            // GO Device: Start server
//            if (isServerRunning) return
//
//            isServerRunning = true
//
//            CoroutineScope(Dispatchers.IO).launch {
//                try {
//                    serverSocket = ServerSocket(/*8888*/PREF_SYNC_PORT)
//                    Log.d("TCP_SYNC", "GO Server started on port 8888")
//
//                    while (true) {
//                        val socket = serverSocket!!.accept()
//                        val remoteIP = socket.inetAddress.hostAddress
//                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
//                        val message = reader.readLine()
//
//                        Log.d("TCP_SYNC", "Received from $remoteIP: $message")
//
//                        if (remoteIP != null) connectedGMIPs.add(remoteIP)
//                        socket.close()
//                    }
//                } catch (e: Exception) {
//                    Log.e("TCP_SYNC", "GO Server error", e)
//                }
//            }
//
//        } else {
//            // GM Device: Send HELLO message to GO
//            CoroutineScope(Dispatchers.IO).launch {
//                try {
//                    Log.d("TCP_SYNC", "GM sending HELLO to GO at $goIp:8888")
//
//                    val socket = Socket(goIp, /*8888*/PORT)
//                    val writer = PrintWriter(socket.getOutputStream(), true)
//                    writer.println("HELLO_FROM_${Build.MODEL}")
//                    writer.flush()
//                    socket.close()
//
//                    Log.d("TCP_SYNC", "HELLO sent to GO at $goIp")
//                } catch (e: Exception) {
//                    Log.e("TCP_SYNC", "GM failed to connect to GO", e)
//                }
//            }
//        }
//    }


    private fun handleClient(
        socket: Socket,
        onMessageReceived: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    Log.d("TCP", "Received (raw): $line")

                    // Attempt to decrypt if JSON has "ciphertext" and "iv"
                    val lineStr = try {
                        val obj = JSONObject(line)
                        if (obj.has("ciphertext") && obj.has("iv")) {
                            val senderIp = socket.inetAddress.hostAddress!!
                            val aesKey = peerAESKeys[senderIp]
                            if (aesKey != null) {
                                val decrypted = AESGCMHelper.decrypt(
                                    aesKey,
                                    Base64.decode(obj.getString("ciphertext"), Base64.NO_WRAP),
                                    Base64.decode(obj.getString("iv"), Base64.NO_WRAP)
                                )
                                Log.d("DECRYPT", "Decrypted from $senderIp: $decrypted")
                                decrypted
                            } else {
                                Log.w("DECRYPT", "No AES key for $senderIp")
                                line
                            }
                        } else {
                            line
                        }
                    } catch (e: Exception) {
                        Log.e("DECRYPT", "Failed to parse or decrypt: ${e.message}")
                        line
                    }

                    try {
                        when {
                            lineStr!!.startsWith("DEVICE_LIST:") -> {
                                val jsonArrayString = lineStr.removePrefix("DEVICE_LIST:")
                                val jsonArray = JSONArray(jsonArrayString)
                                val parsedDevices = mutableListOf<DeviceInfo>()

                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    val device = DeviceInfo(
                                        deviceId = obj.optInt("deviceId"),
                                        name = obj.getString("name"),
                                        ip = obj.getString("ip"),
                                        isGroupOwner = obj.getBoolean("isGroupOwner"),
                                        timestamp = obj.getLong("timestamp")
                                    )
                                    parsedDevices.add(device)
                                }

                                parsedDevices.forEach { device ->
                                    val exists =
                                        isDuplicateDevice(device.name, device.ip, device.timestamp)
                                    if (!exists) deviceInfoDao.insertDevice(device)
                                }

                                Log.d("GM_RECEIVER", "Parsed DEVICE_LIST broadcast: $parsedDevices")
                            }

                            lineStr.startsWith("{") && lineStr.endsWith("}") -> {
                                val obj = JSONObject(lineStr)

                                when {
                                    obj.optString("type") == "KEY_EXCHANGE" -> {
                                        try {
                                            val rawIp = socket.inetAddress.hostAddress ?: "unknown"
                                            val senderIp = if (rawIp == "::1") "127.0.0.1" else rawIp
                                            val base64PublicKey = obj.getString("publicKey")

                                            Log.d("KEY_EXCHANGE", "Received public key from $senderIp: $base64PublicKey")

                                            // 1. Convert Base64 to PublicKey
                                            val peerPublicKey = KeyStoreManager.base64ToPublicKey(base64PublicKey)

                                            // 2. Derive AES key
                                            val aesKey = KeyStoreManager.deriveSharedAESKey(peerPublicKey)

                                            // 3. Store in memory
                                            Constants.peerAESKeys[senderIp] = aesKey
                                            Constants.peerPublicKeys[senderIp] = peerPublicKey

                                            Log.d("KEY_EXCHANGE", "Shared AES key stored for $senderIp")

                                            // 4. Save peer public key to Room (if not already exists)
                                            CoroutineScope(Dispatchers.IO).launch {
                                                try {
                                                    val existing = peerPublicKeyDao.getKeyByIp(senderIp)
                                                    if (existing == null) {
                                                        peerPublicKeyDao.insertKey(
                                                            PeerPublicKeyEntity(
                                                                ip = senderIp,
                                                                base64Key = base64PublicKey
                                                            )
                                                        )
                                                        Log.d("E2EE", "Saved peer public key to Room for $senderIp")
                                                    } else {
                                                        Log.d("E2EE", "Peer public key already exists in Room for $senderIp")
                                                    }
                                                } catch (e: Exception) {
                                                    Log.e("E2EE", "Failed to save public key to Room: ${e.message}")
                                                }
                                            }
//
                                            // 5. Send our own public key back
                                            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                                            val response = JSONObject().apply {
                                                put("type", "KEY_EXCHANGE")
                                                put("publicKey", KeyStoreManager.getOwnPublicKeyBase64())
                                            }
                                            writer.write(response.toString() + "\n")
                                            writer.flush()

                                            Log.d("KEY_EXCHANGE", "Sent GO public key to $senderIp")
                                        } catch (e: Exception) {
                                            Log.e("KEY_EXCHANGE", "Failed to handle key exchange: ${e.message}", e)
                                        }
                                    }

                                    obj.optString("type") == "HELLO" -> {
                                        val devicesArray = obj.getJSONArray("devices")

                                        for (i in 0 until devicesArray.length()) {
                                            val device = devicesArray.getJSONObject(i)
                                            val name = device.getString("name")
                                            val ip = device.getString("ip")
                                            val isGroupOwner = device.getBoolean("isGroupOwner")
                                            val timestamp = device.getLong("timestamp")

                                            val duplicate =
                                                deviceInfoDao.isDuplicateDevice(name, ip, timestamp)
                                            if (!duplicate) {
                                                val newDevice = DeviceInfo(
                                                    name = name,
                                                    ip = ip,
                                                    isGroupOwner = isGroupOwner,
                                                    timestamp = timestamp
                                                )
                                                deviceInfoDao.insertDevice(newDevice)
                                                Log.d("GO_RECEIVER", "Inserted device: $newDevice")
                                            } else {
                                                Log.d(
                                                    "GO_RECEIVER",
                                                    "Skipped duplicate: $name - $ip"
                                                )
                                            }
                                        }

                                        Log.d("GM_RECEIVER", "Parsed HELLO message")

                                        // After processing HELLO, send GO public key
                                        try {
                                            val writer =
                                                BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                                            val response = JSONObject().apply {
                                                put("type", "KEY_EXCHANGE")
                                                put(
                                                    "publicKey",
                                                    KeyStoreManager.getOwnPublicKeyBase64()
                                                )
                                            }
                                            writer.write(response.toString() + "\n")
                                            writer.flush()
                                            Log.d(
                                                "KEY_EXCHANGE",
                                                "Auto-sent GO public key to GM after HELLO"
                                            )
                                        } catch (e: Exception) {
                                            Log.e(
                                                "KEY_EXCHANGE",
                                                "Failed to send public key after HELLO: ${e.message}"
                                            )
                                        }

                                        legacyClientCallback()
                                    }

                                    obj.has("name") && obj.has("ip") && obj.has("timestamp") -> {
                                        val name = obj.getString("name")
                                        val ip = obj.getString("ip")
                                        val isGO = obj.getBoolean("isGroupOwner")
                                        val timestamp = obj.getLong("timestamp")

                                        val device = DeviceInfo(
                                            name = name,
                                            ip = ip,
                                            isGroupOwner = isGO,
                                            timestamp = timestamp
                                        )
                                        val exists = isDuplicateDevice(name, ip, timestamp)
                                        if (!exists) deviceInfoDao.insertDevice(device)

                                        Log.d(
                                            "GM_RECEIVER",
                                            "Parsed fallback device broadcast: $device"
                                        )
                                    }

                                    (obj.has("ciphertext") && obj.has("iv")) -> {
                                        val senderIp = socket.inetAddress.hostAddress ?: return@launch

                                        var aesKey = peerAESKeys[senderIp]

                                        if (aesKey == null) {
                                            Log.w("DECRYPT", "AES key not in memory for $senderIp. Trying Room DB...")

                                            // Try loading from Room DB
                                            val entity = peerPublicKeyDao.getKeyByIp(senderIp)
                                            if (entity != null) {
                                                val peerPublicKey = KeyStoreManager.base64ToPublicKey(entity.base64Key)
                                                aesKey = KeyStoreManager.deriveSharedAESKey(peerPublicKey)

                                                // Cache it
                                                peerAESKeys[senderIp] = aesKey
                                                Constants.peerPublicKeys[senderIp] = peerPublicKey

                                                Log.d("DECRYPT", "Derived AES key from DB for $senderIp")
                                            } else {
                                                Log.e("DECRYPT", "No public key found in DB for $senderIp")
                                            }
                                        }

                                        if (aesKey != null) {
                                            try {
                                                val ciphertext = Base64.decode(obj.getString("ciphertext"), Base64.NO_WRAP)
                                                val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)

                                                val decrypted = AESGCMHelper.decrypt(aesKey, ciphertext, iv)

                                                Log.d("CHAT", "Decrypted message from $senderIp: $decrypted")

                                                Handler(Looper.getMainLooper()).post {
                                                    Constants.chatCallback(decrypted)
                                                }

                                            } catch (e: Exception) {
                                                Log.e("DECRYPT", "Decryption failed from $senderIp: ${e.message}", e)
                                            }
                                        } else {
                                            Log.w("DECRYPT", "Still no AES key available for $senderIp")
                                        }
                                    }

                                }
                            }

                            else -> {
                                val obj = JSONObject(lineStr)

                                // Handle encrypted chat message
                                if (obj.has("ciphertext") && obj.has("iv")) {
                                    val senderIp = socket.inetAddress.hostAddress ?: return@launch

                                    var aesKey = peerAESKeys[senderIp]

                                    if (aesKey == null) {
                                        Log.w("DECRYPT", "AES key not in memory for $senderIp. Trying Room DB...")

                                        // Try loading from Room DB
                                        val entity = peerPublicKeyDao.getKeyByIp(senderIp)
                                        if (entity != null) {
                                            val peerPublicKey = KeyStoreManager.base64ToPublicKey(entity.base64Key)
                                            aesKey = KeyStoreManager.deriveSharedAESKey(peerPublicKey)

                                            // Cache it
                                            peerAESKeys[senderIp] = aesKey
                                            Constants.peerPublicKeys[senderIp] = peerPublicKey

                                            Log.d("DECRYPT", "Derived AES key from DB for $senderIp")
                                        } else {
                                            Log.e("DECRYPT", "No public key found in DB for $senderIp")
                                        }
                                    }

                                    if (aesKey != null) {
                                        try {
                                            val ciphertext = Base64.decode(obj.getString("ciphertext"), Base64.NO_WRAP)
                                            val iv = Base64.decode(obj.getString("iv"), Base64.NO_WRAP)

                                            val decrypted = AESGCMHelper.decrypt(aesKey, ciphertext, iv)

                                            Log.d("CHAT", "Decrypted message from $senderIp: $decrypted")

                                            Handler(Looper.getMainLooper()).post {
                                                Constants.chatCallback(decrypted)
                                            }

                                        } catch (e: Exception) {
                                            Log.e("DECRYPT", "Decryption failed from $senderIp: ${e.message}", e)
                                        }
                                    } else {
                                        Log.w("DECRYPT", "Still no AES key available for $senderIp")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MSG_PARSE", "Error parsing message: ${e.message}")
                    }

                    onMessageReceived(lineStr!!)
                }

                socket.close()
                clientSockets.remove(socket)

            } catch (e: IOException) {
                Log.e("TCP", "Client disconnected or error: ${e.message}")
            } finally {
                try {
                    socket.close()
                    clientSockets.remove(socket)
                } catch (e: IOException) {
                    Log.e("TCP", "Error closing socket: ${e.message}")
                }
            }
        }
    }

//    private fun broadcastLegacyDeviceList(context: Context) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
////                added deferred value here, first test this and keep tracking from here
////                logs are not coming in Dispathers.IO
//
//                val deferredValue = async(Dispatchers.IO) { getOwnIp(applicationContext) }
//                val ownIp = deferredValue.await()
////                    delay(5000)
//
//                if (ownIp.isNullOrBlank() || ownIp == "0.0.0.0") {
////                    Toast.makeText(
////                        applicationContext,
////                        "Failed to get valid IP",
////                        Toast.LENGTH_SHORT
////                    ).show()
//
//                    Handler(Looper.getMainLooper()).post {
//                        Log.d("LEGACY_GO", "Failed to get vvalid IP")
//                    }
//                    return@launch
//                }
////                val ownIp = getOwnIp(context, true) ?: return@launch
//                val ownName = "GO_Device"
//                val timestamp = System.currentTimeMillis()
//
//                val duplicate = deviceInfoDao.isDuplicateDevice(ownName, ownIp, timestamp)
//                if (!duplicate) {
//                    val goDevice = DeviceInfo(
//                        name = ownName,
//                        ip = ownIp,
//                        isGroupOwner = true,
//                        timestamp = timestamp
//                    )
//                    deviceInfoDao.insertDevice(goDevice)
//                    Handler(Looper.getMainLooper()).post {
//                        Log.d("LEGACY_GO", "Saved own device info: $goDevice")
//                    }
//                }
//
//                delay(300) // Give DB time to commit
//
//                val deviceList = deviceInfoDao.getAllDevices().first()
//                val json = buildJsonForDeviceList(deviceList)
//
//                broadcastMessageToAllGMs(json)
//
//                Handler(Looper.getMainLooper()).post {
//                    Log.d("LEGACY_GO", "Broadcasted device list to GMs: $json")
//                }
//            } catch (e: Exception) {
//                Handler(Looper.getMainLooper()).post {
//                    Log.e("LEGACY_GO", "Error broadcasting legacy device list: ${e.message}")
//                }
//            }
//        }
//    }


//    private fun insertNew(device: DeviceInfo) {
//
//    }

    private suspend fun isDuplicateDevice(name: String, ip: String, timestamp: Long): Boolean {
        val byName = deviceInfoDao.findByName(name)
        val byIp = deviceInfoDao.findByIp(ip)
        val recent = deviceInfoDao.findRecent(timestamp)

        return byName.any { d ->
            byIp.any { it.deviceId == d.deviceId } &&
                    recent.any { it.deviceId == d.deviceId }
        }
    }

//    fun stopSilentReceiver() {
//        try {
//            serverSocket?.close()
//            Log.d("TCP", "Silent receiver socket closed")
//        } catch (e: IOException) {
//            Log.e("TCP", "Error closing silent socket: ${e.message}")
//        }
//    }

    fun broadcastMessageToAllGMs(dataToSend: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val allDevices = deviceInfoDao.getAllDevicesOnce() // Suspended function

            allDevices.forEach { device ->
                if (!device.isGroupOwner) {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(device.ip, PORT), 5000)
                        val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                        writer.write(dataToSend)
                        writer.newLine()
                        writer.flush()

                        socket.close()
                        Handler(Looper.getMainLooper()).post {
                            Log.d("BROADCAST", "Sent to ${device.ip}")
                        }
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).post {
                            Log.e("BROADCAST", "Failed to send to ${device.ip}: ${e.message}")
                        }
                    }
                }
            }
        }
    }

//    private fun startUdpReceiverOnGO() {
//        Thread {
//            try {
//                val socket = DatagramSocket(9876)
//                val buffer = ByteArray(1024)
//                val packet = DatagramPacket(buffer, buffer.size)
//
//                while (true) {
//                    socket.receive(packet)
//
//                    val receivedMessage = String(packet.data, 0, packet.length)
//                    val senderIp = packet.address.hostAddress
//
//                    Log.d("UDP_RECEIVER", "Received from $senderIp: $receivedMessage")
//
//                    Handler(Looper.getMainLooper()).post {
//                        Toast.makeText(
//                            this,
//                            /*"From $senderIp: $receivedMessage"*/ receivedMessage,
//                            Toast.LENGTH_LONG
//                        ).show()
//                    }
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }.start()
//    }

//    fun startListeningForGMHello(context: Context) {
//        Thread {
//            try {
//                val socket = DatagramSocket(9876)
//                val buffer = ByteArray(1024)
//                while (true) {
//                    val packet = DatagramPacket(buffer, buffer.size)
//                    socket.receive(packet)
//                    val message = String(packet.data, 0, packet.length)
//                    if (message.trim() == "HELLO Thanks for connecting through Legacy Wifi") {
////                        runOnUiThread {
////                            Toast.makeText(context, "GM connected!", Toast.LENGTH_SHORT).show()
////                            context.startActivity(Intent(context, ChatActivity::class.java))
////                        }
//                        break
//                    }
//                }
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }.start()
//    }

    fun sendMessageToServer(hostAddress: String, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(hostAddress, PORT), 10000)
                ipLcGo = hostAddress
                socket.getOutputStream().bufferedWriter().use {
                    it.write(message)
                    it.newLine()
                    it.flush()
                }
                socket.close()
            } catch (e: IOException) {
                Log.e("TCP", "Send failed: ${e.message}")
            }
        }
    }

    fun connectToPeerAndSendMessage(goIp: String, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket(goIp, PORT)
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val existingKey = peerAESKeys[goIp]
                if (existingKey == null) {
                    // Send our public key as KEY_EXCHANGE
                    val request = JSONObject().apply {
                        put("type", "KEY_EXCHANGE")
                        put("publicKey", KeyStoreManager.getOwnPublicKeyBase64())
                    }
                    writer.write(request.toString() + "\n")
                    writer.flush()
                    Log.d("E2EE", "Sent our public key to $goIp")

                    // Read GO's public key response
                    val reply = reader.readLine() ?: return@launch
                    val replyObj = JSONObject(reply)

                    if (replyObj.optString("type") == "KEY_EXCHANGE") {
                        val base64GoPubKey = replyObj.getString("publicKey")
                        val goPubKey = KeyStoreManager.base64ToPublicKey(base64GoPubKey)

                        val aesKey = KeyStoreManager.deriveSharedAESKey(goPubKey)
                        peerAESKeys[goIp] = aesKey
                        KeyStoreManager.addPeerPublicKey(goIp, goPubKey)

                        // SAVE to Room
                        try {
                            val existing = peerPublicKeyDao.getKeyByIp(goIp)
                            if (existing == null) {
                                peerPublicKeyDao.insertKey(
                                    PeerPublicKeyEntity(
                                        ip = goIp,
                                        base64Key = Base64.encodeToString(
                                            goPubKey.encoded,
                                            Base64.NO_WRAP
                                        )
                                    )
                                )
                            } else {
                                Log.d("E2EE", "Saved peer public key to Room for $goIp")
                            }
                        } catch (e: Exception) {
                            Log.e("E2EE", "Failed to save public key to Room: ${e.message}")
                        }
                        Log.d("KEY_EXCHANGE", "Stored GO public key and AES key for $goIp")
                    }
                }

                // Encrypt the actual message
                val aesKey = peerAESKeys[goIp]
                if (aesKey != null) {
                    val (ciphertext, iv) = AESGCMHelper.encrypt(aesKey, message)
                    val wrappedJson = JSONObject().apply {
                        put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                        put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
                    }
                    writer.write(wrappedJson.toString() + "\n")
                    writer.flush()
                    Log.d("E2EE", "Encrypted message sent to $goIp")
                }

                socket.close()
            } catch (e: Exception) {
                Log.e("E2EE", "Failed to connect/send to $goIp: ${e.message}")
            }
        }
    }

}

