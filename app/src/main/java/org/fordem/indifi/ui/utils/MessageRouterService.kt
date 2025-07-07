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
import kotlinx.coroutines.launch
import org.fordem.indifi.R
import org.fordem.indifi.ui.db.DeviceInfo
import org.fordem.indifi.ui.db.DeviceInfoDao
import org.fordem.indifi.ui.utils.Constants.PORT
import org.fordem.indifi.ui.utils.Constants.PREF_SYNC_PORT
import org.fordem.indifi.ui.utils.Constants.SILENTPORT
import org.fordem.indifi.ui.utils.Constants.connectedGMIPs
import org.fordem.indifi.ui.utils.Constants.ipLcGo
import org.fordem.indifi.ui.utils.Constants.isGOViaWFD
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
    private var clientSocket: Socket? = null
    private val clientSockets = mutableListOf<Socket>()
    private var lastClientAddress: InetAddress? = null
    private val gmAddresses = mutableSetOf<Socket>()  // All connected GMs
    private var serverSocket: ServerSocket? = null
    private var sharedAESKey: SecretKey? = null
    private val peerAESKeys = mutableMapOf<String, SecretKey>()
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

    override fun onBind(intent: Intent?): IBinder? = binder

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
                        Constants.deviceConnectionCallback(
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

    fun startClientReceiver(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            serverSocket = ServerSocket(PORT)
            val socket = serverSocket?.accept()

            BufferedReader(InputStreamReader(socket!!.getInputStream())).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.d("TCP", "Received Pref JSON: $line")
                    val json = JSONObject(line!!)
                    val prefs =
                        context.getSharedPreferences("group_info", MODE_PRIVATE).edit()
                    json.keys().forEach { key ->
                        prefs.putString(key, json.getString(key))
                    }
                    prefs.apply()
                }
            }
        }
    }

    fun startSilentReceiver(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(SILENTPORT) // Use a separate port for silent sync
                while (true) {
                    val socket = serverSocket!!.accept()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            socket.soTimeout = 5000
                            socket.use {
                                val msg = it.getInputStream().bufferedReader().readLine()
                                if (msg.startsWith("PREF_UPDATE:")) {
                                    val json = msg.removePrefix("PREF_UPDATE:")
                                    val prefs =
                                        context.getSharedPreferences(
                                            "group_info",
                                            MODE_PRIVATE
                                        )
                                    val jsonObject = JSONObject(json)
                                    val editor = prefs.edit()
                                    jsonObject.keys().forEach { key ->
                                        editor.putString(key, jsonObject.getString(key))
                                    }
                                    editor.apply()
                                    Log.d("TCP", "Silent prefs synced: $json")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("TCP", "Silent receiver socket error: ${e.message}")
                        } finally {
                            try {
                                socket.close()
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TCP", "Silent receiver failed to start: ${e.message}")
            }
        }
    }

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
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    Log.d("TCP", "Received: $line")
                    if (isGOViaWFD) {
                        Constants.DummyLCMessage = line.toString()
//                        Handler(Looper.getMainLooper()).post {
//                            Toast.makeText(applicationContext, "$line", Toast.LENGTH_SHORT).show()
//                        }

                        if (line.toString().isNotEmpty() && line.toString().isNotBlank()) {
                            Handler(mainLooper).postDelayed({
                                ipLcGo?.let {
                                    MessageRouterHelper.messageRouterService?.sendMessageToServer(
                                        hostAddress = /*getHotspotGatewayIP(context)!!*/ it,
                                        message = Constants.DummyLCMessage /*line.toString()*/
                                    )
                                }

                            }, 5000)
                        }
                    } else {
                        // Check for DEVICE_LIST broadcast here
//                        if (line.toString().startsWith("DEVICE_LIST:")) {
//

                        try {
                            val lineStr = line.toString()

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
                                            isDuplicateDevice(device.name, device.ip, device.timestamp)
                                        if (!exists) {
                                            deviceInfoDao.insertDevice(device)
                                        }
                                    }

                                    Log.d("GM_RECEIVER", "Parsed DEVICE_LIST broadcast: $parsedDevices")

                                }
                                else -> {
                                    val obj = JSONObject(lineStr)

                                    // Handle HELLO type
                                    when {
                                        obj.optString("type") == "HELLO" -> {
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

                                            Log.d("GM_RECEIVER", "Parsed HELLO message: $device")
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
                                        }
                                    }

                        //                                // Handle single device broadcast
                        //                                val obj = JSONObject(lineStr)
                        //                                val name = obj.getString("name")
                        //                                val ip = obj.getString("ip")
                        //                                val timestamp = obj.getLong("timestamp")
                        //
                        //                                val device = DeviceInfo(
                        //                                    name = name,
                        //                                    ip = ip,
                        //                                    isGroupOwner = false,
                        //                                    timestamp = timestamp
                        //                                )
                        //
                        //                                val exists = isDuplicateDevice(name, ip, timestamp)
                        //                                if (!exists) {
                        //                                    deviceInfoDao.insertDevice(device)
                        //                                }
                        //
                        //                                Log.d("GM_RECEIVER", "Parsed single device broadcast: $device")
                                }
                            }

                        } catch (e: Exception) {
                            Log.e("GM_RECEIVER", "Failed to parse broadcast", e)
                        }

                        //
                        //                        try {
//                            // Parse single device broadcast
//                            val obj = JSONObject(line.toString())
//
//                            // Optional: check type if needed
////                            if (obj.getString("type") == "device_broadcast") {
//                                val name = obj.getString("name")
//                                val ip = obj.getString("ip")
//                                val isGroupOwner = obj.getBoolean("isGroupOwner")
//                                val timestamp = obj.getLong("timestamp")
//
//                                val device = DeviceInfo(
//                                    name = name,
//                                    ip = ip,
//                                    isGroupOwner = false, // 🔑 Always save as GM from broadcast
//                                    timestamp = timestamp
//                                )
//
//                                val exists = isDuplicateDevice(name, ip, timestamp)
//                                if (!exists) {
//                                    deviceInfoDao.insertDevice(device)
////                                    insertNew(device)
//                                }
//
//                                Log.d("GM_RECEIVER", "Received device broadcast: $device")
////                            } else {
////                                Log.w("GM_RECEIVER", "Unknown broadcast type received.")
////                            }
//
//                        } catch (e: Exception) {
//                            Log.e("GM_RECEIVER", "Failed to parse DEVICE_LIST", e)
//                        }
//                        } else {
//                            startActivity(Intent(applicationContext, ChatActivity::class.java))
//                        }
                    }
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
//        CoroutineScope(Dispatchers.IO).launch {
//            connectedGMIPs.forEach { gmIp ->
//                try {
//                    val socket = Socket()
//                    socket.connect(InetSocketAddress(gmIp, PORT), 5000)
//
//                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
//                    writer.write(dataToSend)
//                    writer.newLine()
//                    writer.flush()
//
//                    socket.close()
//                    Log.d("BROADCAST", "Message sent to $gmIp")
//                } catch (e: IOException) {
//                    Log.e("BROADCAST", "Failed to send to $gmIp: ${e.message}")
//                }
//            }
//        }


        CoroutineScope(Dispatchers.IO).launch {
//            val db = AppDatabase.getInstance(context)
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
                        Log.d("BROADCAST", "Sent to ${device.ip}")
                    } catch (e: Exception) {
                        Log.e("BROADCAST", "Failed to send to ${device.ip}: ${e.message}")
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
                socket.connect(InetSocketAddress(hostAddress, PORT), 10000)
                socket.getOutputStream().bufferedWriter().use {
                    it.write(message)
                    it.newLine()
                    it.flush()
                }
                Constants.dummyLegacyClientCallback("Connection message sent to GO")
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
}

