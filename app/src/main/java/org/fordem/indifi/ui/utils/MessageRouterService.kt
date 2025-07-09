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
import org.fordem.indifi.ui.db.DeviceInfo
import org.fordem.indifi.ui.db.DeviceInfoDao
import org.fordem.indifi.ui.utils.Constants.PORT
import org.fordem.indifi.ui.utils.Constants.PREF_SYNC_PORT
import org.fordem.indifi.ui.utils.Constants.SILENTPORT
import org.fordem.indifi.ui.utils.Constants.connectedGMIPs
import org.fordem.indifi.ui.utils.Constants.deviceConnectionCallback
import org.fordem.indifi.ui.utils.Constants.ipLcGo
import org.fordem.indifi.ui.utils.Constants.isChatMessage
import org.fordem.indifi.ui.utils.Constants.isGOViaWFD
import org.fordem.indifi.ui.utils.Constants.isGoViaLegacy
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
    //    private var clientSocket: Socket? = null
    private val clientSockets = mutableListOf<Socket>()
    private var lastClientAddress: InetAddress? = null
    private val gmAddresses = mutableSetOf<Socket>()  // All connected GMs
    private var serverSocket: ServerSocket? = null

    //    private var sharedAESKey: SecretKey? = null
//    private val peerAESKeys = mutableMapOf<String, SecretKey>()
    private var isServerRunning = false
    private val binder = LocalBinder()

    @Inject
    lateinit var deviceInfoDao: DeviceInfoDao

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

//        deviceInfoDao = DeviceInfoDao


        // 🟢 Start listening here
        startChatServer(
            onMessageReceived = { message ->
                Log.d("SERVICE", "Message received at startup: $message")
                // Handle message if needed

//                Constants.chatCallback(message)
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
//                    delay(1000)

                    clientSockets.add(clientSocket)

                    lastClientAddress = clientSocket.inetAddress  // ← Save GM's IP
                    gmAddresses.add(clientSocket)
                    connectedGMIPs.add(clientSocket.inetAddress.hostAddress!!) // Save GM IP
//                    delay(1000)

                    if (isGOViaWFD) {
                        deviceConnectionCallback(
                            clientSocket.inetAddress.hostAddress!!
                        )
                    }
                    Log.d("TCP", "Client connected: ${clientSocket.inetAddress.hostAddress}")
//                    delay(1000)

                    handleClient(clientSocket, onMessageReceived)
                }
            } catch (e: IOException) {
                Log.e("TCP", "Server error: ${e.message}")
            }
        }
    }

//    fun startClientReceiver(context: Context) {
//        CoroutineScope(Dispatchers.IO).launch {
//            serverSocket = ServerSocket(PORT)
//            val socket = serverSocket?.accept()
//
//            BufferedReader(InputStreamReader(socket!!.getInputStream())).use { reader ->
//                var line: String?
//                while (reader.readLine().also { line = it } != null) {
//                    Log.d("TCP", "Received Pref JSON: $line")
//                    val json = JSONObject(line!!)
//                    val prefs = context.getSharedPreferences("group_info", MODE_PRIVATE).edit()
//                    json.keys().forEach { key ->
//                        prefs.putString(key, json.getString(key))
//                    }
//                    prefs.apply()
//                }
//            }
//        }
//    }
//
//    fun startSilentReceiver(context: Context) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                serverSocket = ServerSocket(SILENTPORT) // Use a separate port for silent sync
//                while (true) {
//                    val socket = serverSocket!!.accept()
//                    CoroutineScope(Dispatchers.IO).launch {
//                        try {
//                            socket.soTimeout = 5000
//                            socket.use {
//                                val msg = it.getInputStream().bufferedReader().readLine()
//                                if (msg.startsWith("PREF_UPDATE:")) {
//                                    val json = msg.removePrefix("PREF_UPDATE:")
//                                    val prefs =
//                                        context.getSharedPreferences(
//                                            "group_info",
//                                            MODE_PRIVATE
//                                        )
//                                    val jsonObject = JSONObject(json)
//                                    val editor = prefs.edit()
//                                    jsonObject.keys().forEach { key ->
//                                        editor.putString(key, jsonObject.getString(key))
//                                    }
//                                    editor.apply()
//                                    Log.d("TCP", "Silent prefs synced: $json")
//                                }
//                            }
//                        } catch (e: Exception) {
//                            Log.e("TCP", "Silent receiver socket error: ${e.message}")
//                        } finally {
//                            try {
//                                socket.close()
//                            } catch (_: Exception) {
//                            }
//                        }
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e("TCP", "Silent receiver failed to start: ${e.message}")
//            }
//        }
//    }

    fun startPrefSyncServer(context: Context, info: WifiP2pInfo) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val serverSocket = ServerSocket(8888)
//                while (true) {
//                    val socket = serverSocket.accept()
//                    val gmIP = socket.inetAddress.hostAddress ?: continue
//                    connectedGMIPs.add(gmIP)
//                    Log.d("GO_SERVER", "GM connected from $gmIP")
//
//                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
//                    val message = reader.readLine()
//                    Log.d("GO_SERVER", "Received: $message")
//
//                    // Optionally respond or update prefs here
//
//                    socket.close()
//                }
//            } catch (e: Exception) {
//                Log.e("GO_SERVER", "Error in TCP server", e)
//            }
//        }


        if (info.groupOwnerAddress == null) {
            Log.e("TCP_SYNC", "Group owner address is null. Aborting sync setup.")
            return
        }

        val goIp = info.groupOwnerAddress.hostAddress ?: return

        if (info.isGroupOwner) {
            // GO Device: Start server
            if (isServerRunning) return

            isServerRunning = true

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    serverSocket = ServerSocket(/*8888*/PREF_SYNC_PORT)
                    Log.d("TCP_SYNC", "GO Server started on port 8888")

                    while (true) {
                        val socket = serverSocket!!.accept()
                        val remoteIP = socket.inetAddress.hostAddress
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val message = reader.readLine()

                        Log.d("TCP_SYNC", "Received from $remoteIP: $message")

                        if (remoteIP != null) connectedGMIPs.add(remoteIP)
                        socket.close()
                    }
                } catch (e: Exception) {
                    Log.e("TCP_SYNC", "GO Server error", e)
                }
            }

        } else {
            // GM Device: Send HELLO message to GO
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("TCP_SYNC", "GM sending HELLO to GO at $goIp:8888")

                    val socket = Socket(goIp, /*8888*/PORT)
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    writer.println("HELLO_FROM_${Build.MODEL}")
                    writer.flush()
                    socket.close()

                    Log.d("TCP_SYNC", "HELLO sent to GO at $goIp")
                } catch (e: Exception) {
                    Log.e("TCP_SYNC", "GM failed to connect to GO", e)
                }
            }
        }
    }

    private fun handleClient(
        socket: Socket,
        onMessageReceived: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
//            delay(1000)

            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var line: String?
//                delay(1000)


                while (reader.readLine().also { line = it } != null) {
                    Log.d("TCP", "Received: $line")
                    Handler(Looper.getMainLooper()).post {
                        Log.d("GO_RECEIVER", "Received raw line: $line")
                    }

//                    if (isGOViaWFD) {
//                        Constants.DummyLCMessage = line.toString()
//
////                        Handler(Looper.getMainLooper()).post {
////                            Toast.makeText(applicationContext, "$line", Toast.LENGTH_SHORT).show()
////                        }
//
//                        if (line.toString().isNotEmpty() && isChatMessage) {
//                            //For WFD chat
//                            Handler(Looper.getMainLooper()).post {
//                                // You can either use onMessageReceived or Constants.chatCallback
//                                Constants.chatCallback(line.toString())
//                            }
//                        }
//
//
////                        // For Legacy Wifi Network
////                        if (line.toString().isNotEmpty() && line.toString().isNotBlank()) {
////                            Handler(mainLooper).postDelayed({
////                                ipLcGo?.let {
////                                    MessageRouterHelper.messageRouterService?.sendMessageToServer(
////                                        hostAddress = /*getHotspotGatewayIP(context)!!*/ it,
////                                        message = Constants.DummyLCMessage /*line.toString()*/
////                                    )
////                                }
////
////                            }, 5000)
////                        }
//
////                        delay(1000)
//
//                    }
//                    else if (isGoViaLegacy) {
//                        try {
                            val lineStr = line.toString()
//                            delay(1000)

                            when {
                                lineStr.startsWith("DEVICE_LIST:") -> {
                                    // Handle full device list
                                    val jsonArrayString = lineStr.removePrefix("DEVICE_LIST:")
                                    val jsonArray = JSONArray(jsonArrayString)
                                    val parsedDevices = mutableListOf<DeviceInfo>()

                                    for (i in 0 until jsonArray.length()) {
                                        val obj = jsonArray.getJSONObject(i)
                                        val device = DeviceInfo(
                                            deviceId = obj.optInt("deviceId"),
                                            name = obj.getString("name"),
                                            ip = obj.getString("ip"),
                                            isGroupOwner = /*false*/obj.getBoolean("isGroupOwner"), // Always false for GM
                                            timestamp = obj.getLong("timestamp")
                                        )
                                        parsedDevices.add(device)
                                    }

                                    parsedDevices.forEach { device ->
                                        val exists =
                                            isDuplicateDevice(
                                                device.name,
                                                device.ip,
                                                device.timestamp
                                            )
                                        if (!exists) {
                                            deviceInfoDao.insertDevice(device)
                                        }
                                    }

                                    Log.d(
                                        "GM_RECEIVER",
                                        "Parsed DEVICE_LIST broadcast: $parsedDevices"
                                    )
//                                    delay(1000)

                                }

                                lineStr.startsWith("{") && lineStr.endsWith("}") -> {
                                    val obj = JSONObject(lineStr)
//                                    delay(1000)

                                    // Handle HELLO type
                                    when {
                                        obj.optString("type") == "HELLO" -> {
                                            val devicesArray = obj.getJSONArray("devices")

                                            for (i in 0 until devicesArray.length()) {
                                                val device = devicesArray.getJSONObject(i)
                                                val name = device.getString("name")
                                                val ip = device.getString("ip")
                                                val isGroupOwner = device.getBoolean("isGroupOwner")
                                                val timestamp = device.getLong("timestamp")

                                                val duplicate = deviceInfoDao.isDuplicateDevice(name, ip, timestamp)
                                                if (!duplicate) {
                                                    val newDevice = DeviceInfo(name = name, ip = ip, isGroupOwner = isGroupOwner, timestamp = timestamp)
                                                    deviceInfoDao.insertDevice(newDevice)
                                                    Log.d("GO_RECEIVER", "Inserted device: $newDevice")
                                                } else {
                                                    Log.d("GO_RECEIVER", "Skipped duplicate device: $name - $ip")
                                                }
                                            }


//                                            Toast.makeText(
//                                                applicationContext,
//                                                "Hello Message Received",
//                                                Toast.LENGTH_SHORT
//                                            ).show()

//                                            val name = obj.getString("name")
//                                            val ip = obj.getString("ip")
//                                            val isGO = obj.getBoolean("isGroupOwner")
//                                            val timestamp = obj.getLong("timestamp")
//
//                                            val device = DeviceInfo(
//                                                name = name,
//                                                ip = ip,
//                                                isGroupOwner = isGO,
//                                                timestamp = timestamp
//                                            )
//
//                                            val exists = isDuplicateDevice(name, ip, timestamp)
//                                            if (!exists) {
//                                                deviceInfoDao.insertDevice(device)
////                                                Toast.makeText(
////                                                    applicationContext,
////                                                    "GM info saved",
////                                                    Toast.LENGTH_SHORT
////                                                ).show()
//                                            }

                                            Log.d("GM_RECEIVER", "Parsed HELLO message: $devicesArray[1]")
//                                            delay(1000)

                                            legacyClientCallback()
                                        }
                                        // Handle fallback single device broadcast
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
                                            if (!exists) {
                                                deviceInfoDao.insertDevice(device)
                                            }

                                            Log.d(
                                                "GM_RECEIVER",
                                                "Parsed fallback device broadcast: $device"
                                            )
//                                            delay(1000)

                                        }

                                    }
                                }
                                else -> {
                                    if (lineStr.isNotEmpty() /*&& isChatMessage*/) {
                                        Log.d("CHAT", "Received chat message: $lineStr")

                                        Handler(Looper.getMainLooper()).post {
                                            // You can either use onMessageReceived or Constants.chatCallback
//                                            onMessageReceived(chatMessage)
                                            Constants.chatCallback(lineStr)
                                        }
                                    }
                                }
                            }
//                        } catch (e: Exception) {
//                            Log.e("GM_RECEIVER", "Failed to parse broadcast", e)
//                        }
//                        delay(1000)

//                        Handler().postDelayed({
//                        Handler(Looper.getMainLooper()).post {
//                            Log.d("GO_RECEIVER", "Calling legacyClientCallback after HELLO")
//                        }
//                        broadcastLegacyDeviceList(
//                            context = applicationContext
//                        )
//                        }, 5000)
//                    }
//                    else {
//                        // Check for DEVICE_LIST broadcast here
////                        if (line.toString().startsWith("DEVICE_LIST:")) {
////
////                        delay(1000)
//
//                        try {
//                            val lineStr = line.toString()
//
//                            when {
//                                lineStr.startsWith("DEVICE_LIST:") -> {
////                                    delay(1000)
//
//                                    // Handle full device list
//                                    val jsonArrayString = lineStr.removePrefix("DEVICE_LIST:")
//                                    val jsonArray = JSONArray(jsonArrayString)
//                                    val parsedDevices = mutableListOf<DeviceInfo>()
//
//                                    for (i in 0 until jsonArray.length()) {
//                                        val obj = jsonArray.getJSONObject(i)
//                                        val device = DeviceInfo(
//                                            deviceId = obj.optInt("deviceId"),
//                                            name = obj.getString("name"),
//                                            ip = obj.getString("ip"),
//                                            isGroupOwner = /*false*/obj.getBoolean("isGroupOwner"), // Always false for GM
//                                            timestamp = obj.getLong("timestamp")
//                                        )
//                                        parsedDevices.add(device)
//                                    }
////                                    delay(1000)
//
//
//                                    parsedDevices.forEach { device ->
//                                        val exists =
//                                            isDuplicateDevice(
//                                                device.name,
//                                                device.ip,
//                                                device.timestamp
//                                            )
//                                        if (!exists) {
//                                            deviceInfoDao.insertDevice(device)
////                                            delay(1000)
//
//                                        }
//                                    }
//
//                                    Log.d(
//                                        "GM_RECEIVER",
//                                        "Parsed DEVICE_LIST broadcast: $parsedDevices"
//                                    )
//                                }
//
//                                lineStr.startsWith("{") && lineStr.endsWith("}") -> {
//                                    val obj = JSONObject(lineStr)
////                                    delay(1000)
//
//                                    // Handle HELLO type
//                                    when {
//                                        obj.optString("type") == "HELLO" -> {
//                                            val name = obj.getString("name")
//                                            val ip = obj.getString("ip")
//                                            val isGO = obj.getBoolean("isGroupOwner")
//                                            val timestamp = obj.getLong("timestamp")
//
//                                            val device = DeviceInfo(
//                                                name = name,
//                                                ip = ip,
//                                                isGroupOwner = isGO,
//                                                timestamp = timestamp
//                                            )
////                                            delay(1000)
//
//                                            val exists = isDuplicateDevice(name, ip, timestamp)
//                                            if (!exists) {
//                                                deviceInfoDao.insertDevice(device)
////                                                delay(1000)
//
//                                            }
//
//                                            Log.d("GM_RECEIVER", "Parsed HELLO message: $device")
//                                        }
//                                        // Handle fallback single device broadcast
//                                        obj.has("name") && obj.has("ip") && obj.has("timestamp") -> {
////                                            delay(1000)
//
//                                            val name = obj.getString("name")
//                                            val ip = obj.getString("ip")
//                                            val isGO = obj.getBoolean("isGroupOwner")
//                                            val timestamp = obj.getLong("timestamp")
//
//                                            val device = DeviceInfo(
//                                                name = name,
//                                                ip = ip,
//                                                isGroupOwner = isGO,
//                                                timestamp = timestamp
//                                            )
////                                            delay(1000)
//
//                                            val exists = isDuplicateDevice(name, ip, timestamp)
//                                            if (!exists) {
//                                                deviceInfoDao.insertDevice(device)
////                                                delay(1000)
//
//                                            }
//
//                                            Log.d(
//                                                "GM_RECEIVER",
//                                                "Parsed fallback device broadcast: $device"
//                                            )
//                                        }
//                                    }
//                                }
//
//                                else -> {
//                                    if (lineStr.isNotEmpty()) {
//                                        Log.d("CHAT", "Received chat message: $lineStr")
////                                        delay(1000)
//
//                                        Handler(Looper.getMainLooper()).post {
//                                            // You can either use onMessageReceived or Constants.chatCallback
////                                            onMessageReceived(chatMessage)
//                                            Constants.chatCallback(lineStr)
//                                        }
//                                    }
//                                }
//                            }
//                        } catch (e: Exception) {
//                            Log.e("GM_RECEIVER", "Failed to parse broadcast", e)
//                        }
//                    }
                    onMessageReceived(line!!)
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

    private fun broadcastLegacyDeviceList(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
//                added deferred value here, first test this and keep tracking from here
//                logs are not coming in Dispathers.IO

                val deferredValue = async(Dispatchers.IO) { getOwnIp(applicationContext) }
                val ownIp = deferredValue.await()
//                    delay(5000)

                if (ownIp.isNullOrBlank() || ownIp == "0.0.0.0") {
//                    Toast.makeText(
//                        applicationContext,
//                        "Failed to get valid IP",
//                        Toast.LENGTH_SHORT
//                    ).show()

                    Handler(Looper.getMainLooper()).post {
                    Log.d("LEGACY_GO", "Failed to get vvalid IP")
                    }
                    return@launch
                }
//                val ownIp = getOwnIp(context, true) ?: return@launch
                val ownName = "GO_Device"
                val timestamp = System.currentTimeMillis()

                val duplicate = deviceInfoDao.isDuplicateDevice(ownName, ownIp, timestamp)
                if (!duplicate) {
                    val goDevice = DeviceInfo(
                        name = ownName,
                        ip = ownIp,
                        isGroupOwner = true,
                        timestamp = timestamp
                    )
                    deviceInfoDao.insertDevice(goDevice)
                    Handler(Looper.getMainLooper()).post {
                        Log.d("LEGACY_GO", "Saved own device info: $goDevice")
                    }
                }

                delay(300) // Give DB time to commit

                val deviceList = deviceInfoDao.getAllDevices().first()
                val json = buildJsonForDeviceList(deviceList)

                broadcastMessageToAllGMs(json)

                Handler(Looper.getMainLooper()).post {
                    Log.d("LEGACY_GO", "Broadcasted device list to GMs: $json")
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Log.e("LEGACY_GO", "Error broadcasting legacy device list: ${e.message}")
                }
            }
        }
    }


    private fun insertNew(device: DeviceInfo) {

    }

    private suspend fun isDuplicateDevice(name: String, ip: String, timestamp: Long): Boolean {
        val byName = deviceInfoDao.findByName(name)
        val byIp = deviceInfoDao.findByIp(ip)
        val recent = deviceInfoDao.findRecent(timestamp)

        return byName.any { d ->
            byIp.any { it.deviceId == d.deviceId } &&
                    recent.any { it.deviceId == d.deviceId }
        }
    }

    fun stopSilentReceiver() {
        try {
            serverSocket?.close()
            Log.d("TCP", "Silent receiver socket closed")
        } catch (e: IOException) {
            Log.e("TCP", "Error closing silent socket: ${e.message}")
        }
    }

    fun broadcastMessageToAllGMs(dataToSend: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val allDevices = deviceInfoDao.getAllDevicesOnce() // Suspended function

            allDevices.forEach { device ->
                if (!device.isGroupOwner) {
                    try {
                        val socket = Socket()
                        socket.connect(InetSocketAddress(device.ip, PORT), 5000)

//                        Log.d("GO_BROADCAST", "Sending to ${device.inetAddress.hostAddress}")
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

    private fun startUdpReceiverOnGO() {
        Thread {
            try {
                val socket = DatagramSocket(9876)
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                while (true) {
                    socket.receive(packet)

                    val receivedMessage = String(packet.data, 0, packet.length)
                    val senderIp = packet.address.hostAddress

                    Log.d("UDP_RECEIVER", "Received from $senderIp: $receivedMessage")

                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            this,
                            /*"From $senderIp: $receivedMessage"*/ receivedMessage,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun startListeningForGMHello(context: Context) {
        Thread {
            try {
                val socket = DatagramSocket(9876)
                val buffer = ByteArray(1024)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    if (message.trim() == "HELLO Thanks for connecting through Legacy Wifi") {
//                        runOnUiThread {
//                            Toast.makeText(context, "GM connected!", Toast.LENGTH_SHORT).show()
//                            context.startActivity(Intent(context, ChatActivity::class.java))
//                        }
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun sendMessageToServer(hostAddress: String, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket()
//                delay(1000)

                socket.connect(InetSocketAddress(hostAddress, PORT), 10000)
                ipLcGo = hostAddress
//                delay(1000)

                socket.getOutputStream().bufferedWriter().use {
//                    delay(1000)

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

    fun sendMessageToClient(message: String, gm_ip: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                gm_ip.let { clientIp ->
                    val socket = Socket()
                    socket.connect(InetSocketAddress(clientIp, PORT), 5000)

                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                    writer.write(message)
                    writer.newLine()
                    writer.flush()
                    socket.close()
                }
            } catch (e: IOException) {
                Log.e("TCP", "Send to GM failed: ${e.message}")
            }
        }
    }

//    fun connectToPeerAndSendMessage(ip: String, message: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val socket = Socket()
//                socket.connect(InetSocketAddress(ip, PORT), 5000)
//
//                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
//                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
//
//                // Step 1: Send our public key
//                val myPubKey = KeyStoreManager.getPublicKey().toBase64()
//                writer.write("ECDH_PUBLIC:$myPubKey\n")
//                writer.flush()
//
//                // Step 2: Receive their public key
//                val response = reader.readLine()
//                if (response.startsWith("ECDH_PUBLIC:")) {
//                    val peerKey = base64ToPublicKey(response.removePrefix("ECDH_PUBLIC:"))
//                    val aesKey = deriveSharedAESKey(peerKey)
//                    peerAESKeys[ip] = aesKey
//                    Log.d("TCP", "AES key stored for $ip")
//                }
//
//                // Step 3: Encrypt and send
//                val aesKey = peerAESKeys[ip]
//                if (aesKey != null) {
//                    val encrypted = AESGCMHelper.encrypt(message, aesKey)
//                    writer.write("$encrypted\n")
//                    writer.flush()
//                }
//
//                socket.close()
//            } catch (e: Exception) {
//                Log.e("TCP", "Connection/send failed to $ip: ${e.message}")
//            }
//        }
//    }

}

