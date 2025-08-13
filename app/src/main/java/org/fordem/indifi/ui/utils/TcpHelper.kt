package org.fordem.indifi.ui.utils

import android.util.Log
import kotlinx.coroutines.*
import org.fordem.indifi.ui.utils.Constants.connectedGMIPs
import org.fordem.indifi.ui.utils.Constants.UNICAST_PORT
import javax.crypto.SecretKey
import java.io.*
import java.net.*

object TcpHelper {
//    private const val PORT = 8888
//    private const val SILENTPORT = 8899
    private var clientSocket: Socket? = null
    private val clientSockets = mutableListOf<Socket>()
    var lastClientAddress: InetAddress? = null
    private val gmAddresses = mutableSetOf<Socket>()  // All connected GMs
    private var serverSocket: ServerSocket? = null

    private var sharedAESKey: SecretKey? = null
    private val peerAESKeys = mutableMapOf<String, SecretKey>()


    private var syncServerRunning = false

    var isServerRunning = false


    fun sendMessageToClient(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                lastClientAddress?.let { clientIp ->
                    val socket = Socket()
                    socket.connect(InetSocketAddress(clientIp, UNICAST_PORT), 5000)

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

    fun broadcastToGMs(message: String) {
        for (ip in connectedGMIPs) {
            CoroutineScope(Dispatchers.IO).launch {
                val socket = Socket()

                try {
                    socket.connect(InetSocketAddress(ip, UNICAST_PORT), 5000)
                    socket.getOutputStream().bufferedWriter().use {
                        it.write(message)
                        it.newLine()
                        it.flush()
                    }

                } catch (e: IOException) {
                    Log.e("TCP", "Failed to send to GM $ip: ${e.message}")
                } finally {
                    try {
                        socket.close()
                    } catch (_: Exception) {
                    }
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
}
