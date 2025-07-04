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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fordem.indifi.R
import org.fordem.indifi.ui.utils.Constants.PORT
import org.fordem.indifi.ui.utils.Constants.PREF_SYNC_PORT
import org.fordem.indifi.ui.utils.Constants.SILENTPORT
import org.fordem.indifi.ui.utils.Constants.connectedGMIPs
import org.fordem.indifi.ui.utils.Constants.ipLcGo
import org.fordem.indifi.ui.utils.Constants.isGOviaWFD
import org.fordem.indifi.ui.utils.Constants.isGoViaLegacy
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.crypto.SecretKey

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

                    if (isGOviaWFD) {
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
                    if (isGOviaWFD) {
                        Constants.DummyLCMessage = line.toString()

                        if (line.toString()
                                .isNotEmpty() && line.toString().isNotBlank()
                        ) {
                            Handler(mainLooper).postDelayed({
                                ipLcGo?.let {
                                    MessageRouterHelper.messageRouterService?.sendMessageToServer(
                                        hostAddress = /*getHotspotGatewayIP(context)!!*/ it,
                                        message = Constants.DummyLCMessage /*line.toString()*/
                                    )
                                }

                            }, 5000)
                        }
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

    fun stopSilentReceiver() {
        try {
            serverSocket?.close()
            Log.d("TCP", "Silent receiver socket closed")
        } catch (e: IOException) {
            Log.e("TCP", "Error closing silent socket: ${e.message}")
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
                socket.connect(InetSocketAddress(hostAddress, PORT), 5000)
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

}

