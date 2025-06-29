package org.fordem.indifi

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.*
import org.fordem.indifi.Constants.connectedGMIPs
import org.json.JSONObject
import java.io.*
import java.net.*

object TcpHelper {
    private const val PORT = 8888
    private const val SILENTPORT = 8899
    private var clientSocket: Socket? = null
    private val clientSockets = mutableListOf<Socket>()
    private var lastClientAddress: InetAddress? = null
    private val gmAddresses = mutableSetOf<Socket>()  // All connected GMs
    private var serverSocket: ServerSocket? = null

//    fun startServer(onMessageReceived: (String) -> Unit) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val serverSocket = ServerSocket(PORT)
//                Log.d("TCP", "Server started on port $PORT")
//
//                while (true) {
//                    val clientSocket = serverSocket.accept()
//                    clientSockets.add(clientSocket)
//                    Log.d("TCP", "Client connected: ${clientSocket.inetAddress.hostAddress}")
//
//                    handleClient(clientSocket, onMessageReceived)
//                }
//            } catch (e: IOException) {
//                Log.e("TCP", "Server error: ${e.message}")
//            }
//        }
//    }

    fun startChatServer(onMessageReceived: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
//            try {
            serverSocket = ServerSocket(PORT)

            while (true) {
                val socket = serverSocket!!.accept()
                lastClientAddress = socket.inetAddress  // ← Save GM's IP
                gmAddresses.add(socket)
                connectedGMIPs.add(socket.inetAddress.hostAddress!!) // Save GM IP

                CoroutineScope(Dispatchers.IO).launch {
                    socket.use {
                        try {
                            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
//                        val message = reader.readLine()
//                        onMessageReceived(message)

                            var line: String?
                            while (reader.readLine().also { it1 -> line = it1 } != null) {
                                onMessageReceived(line!!)
                            }
                        } catch (e: Exception) {
                            Log.e("TCP", "Client error: ${e.message}")
                        } finally {
                            gmAddresses.remove(socket)
                            connectedGMIPs.remove(socket.inetAddress.hostAddress!!) // Cleanup
                            socket.close()
                        }
                    }
                }
            }

//            } catch (_: Exception){}
        }
    }
    private var syncServerRunning = false

    fun startPrefSyncServer(applicationContext: Context) {
//        if (syncServerRunning) return // Prevent double start
//        syncServerRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(SILENTPORT) // Silent sync port
                while (true) {
                    val socket = serverSocket!!.accept()
//                    lastClientAddress = socket.inetAddress  // ← Save GM's IP
//                    gmAddresses.add(socket)
                    connectedGMIPs.add(socket.inetAddress.hostAddress!!) // Save GM IP

                    CoroutineScope(Dispatchers.IO).launch {
                    socket.use {
                        val msg = it.getInputStream().bufferedReader().readLine()
                        if (msg.startsWith("PREF_UPDATE:")) {
                            val updatedData = msg.removePrefix("PREF_UPDATE:")
                            val prefs = applicationContext.getSharedPreferences(
                                "group_info",
                                Context.MODE_PRIVATE
                            )
                            val jsonObject = JSONObject(updatedData)
                            val editor = prefs.edit()
                            jsonObject.keys().forEach { key ->
                                editor.putString(key, jsonObject.getString(key))
                            }
                            editor.apply()
                            Log.d("BroadcastService", "Prefs updated silently in background")
                        }
                    }
                    }
                }
            } catch (e: IOException) {
                Log.e("BroadcastService", "PrefSync server failed to start: ${e.message}")
            }
        }
    }

    private fun handleClient(socket: Socket, onMessageReceived: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    Log.d("TCP", "Received: $line")
                    onMessageReceived(line!!)
                }

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

    private fun broadcastToAll(message: String) {
        for (client in clientSockets.toList()) {
            try {
                val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream()))
                writer.write(message)
                writer.newLine()
                writer.flush()
            } catch (e: IOException) {
                Log.e("TCP", "Broadcast failed: ${e.message}")
            }
        }
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

//    fun sendMessageToClient(response: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                clientSocket?.let {
//                    val writer = BufferedWriter(OutputStreamWriter(it.getOutputStream()))
//                    writer.write(response)
//                    writer.newLine()
//                    writer.flush()
//                }
//            } catch (e: IOException) {
//                Log.e("TCP", "Server response error: ${e.message}")
//            }
//        }
//    }

    fun sendMessageToClient(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                lastClientAddress?.let { clientIp ->
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

    fun broadcastSharedPrefsToClients(data: String) {
        for (client in clientSockets) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream()))
                    writer.write(data)
                    writer.newLine()
                    writer.flush()
                } catch (e: Exception) {
                    Log.e("TCP", "Failed to send prefs: ${e.message}")
                }
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
                        context.getSharedPreferences("group_info", Context.MODE_PRIVATE).edit()
                    json.keys().forEach { key ->
                        prefs.putString(key, json.getString(key))
                    }
                    prefs.apply()
                }
            }
        }
    }

    fun broadcastToGMs(message: String) {
        for (ip in connectedGMIPs) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress(ip, SILENTPORT), 5000)
                    socket.getOutputStream().bufferedWriter().use {
                        it.write(message)
                        it.newLine()
                        it.flush()
                    }
                    socket.close()


//                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
//                    writer.write(message)
//                    writer.newLine()
//                    writer.flush()
                } catch (e: IOException) {
                    Log.e("TCP", "Failed to send to GM: ${e.message}")
                }
            }
        }
    }

    fun broadcastGMListToAll(gmList: List<String>) {
        val message = "GMLIST:" + gmList.joinToString(",")
        for (client in clientSockets) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val writer = BufferedWriter(OutputStreamWriter(client.getOutputStream()))
                    writer.write(message)
                    writer.newLine()
                    writer.flush()
                    Log.d("TCP", "Broadcasted GM list to ${client.inetAddress.hostAddress}")
                } catch (e: Exception) {
                    Log.e("TCP", "Failed to send GM list: ${e.message}")
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
                        socket.use {
                            val msg = it.getInputStream().bufferedReader().readLine()
                            if (msg.startsWith("PREF_UPDATE:")) {
                                val json = msg.removePrefix("PREF_UPDATE:")
                                val prefs =
                                    context.getSharedPreferences("group_info", Context.MODE_PRIVATE)
                                val jsonObject = JSONObject(json)
                                val editor = prefs.edit()
                                jsonObject.keys().forEach { key ->
                                    editor.putString(key, jsonObject.getString(key))
                                }
                                editor.apply()
                                Log.d("TCP", "Silent prefs synced: $json")
                            }
//                            it.close()
                        }
//                    socket.close()
                    }
                }
            } catch (e: Exception) {
                Log.e("TCP", "Silent receiver failed to start: ${e.message}")
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
}
