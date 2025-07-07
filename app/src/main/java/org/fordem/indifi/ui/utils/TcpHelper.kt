package org.fordem.indifi.ui.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import org.fordem.indifi.ui.utils.Constants.connectedGMIPs
import org.fordem.indifi.ui.encryption.AESGCMHelper
import org.fordem.indifi.ui.encryption.KeyStoreManager
import org.fordem.indifi.ui.encryption.KeyStoreManager.toBase64
import org.fordem.indifi.ui.utils.Constants.PORT
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.util.Base64
import java.security.MessageDigest
import org.json.JSONObject
import java.io.*
import java.net.*
import java.security.KeyFactory
import java.security.PublicKey

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


    //    @RequiresApi(Build.VERSION_CODES.O)
//    @SuppressLint("NewApi")
//    fun startChatServer(
//        lastDeviceInfo: String,
//        context: Context,
//        isGO: Boolean,
//        onMessageReceived: (String) -> Unit
//    ) {
////        CoroutineScope(Dispatchers.IO).launch {
////            try {
////                serverSocket = ServerSocket(PORT)
////
//////            while (true) {
////                val socket = serverSocket!!.accept()
////                lastClientAddress = socket.inetAddress  // ← Save GM's IP
////                gmAddresses.add(socket)
////                connectedGMIPs.add(socket.inetAddress.hostAddress!!) // Save GM IP
////
////                displayedPeersList.find { it.ip.isEmpty() }?.let { peer ->
////                    val updated = peer.copy(ip = socket.inetAddress.hostAddress!!)
////                    displayedPeersList[displayedPeersList.indexOf(peer)] = updated
////                }
////
////                Constants.deviceConnectionCallback(socket.inetAddress.toString())
////
////                CoroutineScope(Dispatchers.IO).launch {
////                    socket.use {
////                        try {
//////                            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
//////                        val message = reader.readLine()
//////                        onMessageReceived(message)
//////
//////                            var line: String?
//////                            while (reader.readLine().also { it1 -> line = it1 } != null) {
//////                                onMessageReceived(line!!)
//////                            }
////
////
////                            val peerIp = socket.inetAddress.hostAddress!!
////                            val reader = BufferedReader(InputStreamReader(it.getInputStream()))
////                            val writer = BufferedWriter(OutputStreamWriter(it.getOutputStream()))
////
////                            // Step 1: Read peer public key
////                            val incoming = reader.readLine()
////                            if (incoming.startsWith("ECDH_PUBLIC:")) {
////                                val peerBase64 = incoming.removePrefix("ECDH_PUBLIC:")
////                                val peerPubKey = base64ToPublicKey(peerBase64)
////                                sharedAESKey = deriveSharedAESKey(peerPubKey)
////
////                                // Step 2: Send our public key back
////                                val myPub = KeyStoreManager.getPublicKey().toBase64()
////                                writer.write("ECDH_PUBLIC:$myPub\n")
////                                writer.flush()
////
////                                // Step 3: Derive shared AES key and save it
////                                peerAESKeys[peerIp] = sharedAESKey!!
////                                Log.d("TCP", "AES key established with $peerIp")
////                            }
////
////                            // Step 3: Wait for encrypted chat messages
////                            var line: String?
////                            while (reader.readLine().also { it1 -> line = it1 } != null) {
////                                val key = peerAESKeys[peerIp]
////                                if (key != null) {
////                                    try {
//////                                        val decrypted = sharedAESKey?.let { key ->
//////                                            AESGCMHelper.decrypt(line!!, key)
//////                                        }
//////                                        onMessageReceived(decrypted ?: "Failed to decrypt")
////
//////                                        val decrypted = AESGCMHelper.decrypt(line!!, key)
//////                                        onMessageReceived(peerIp, decrypted)
////
////                                        val json = JSONObject(line!!)
////                                        val ciphertext = Base64.getDecoder().decode(json.getString("ciphertext"))
////                                        val iv = Base64.getDecoder().decode(json.getString("iv"))
////
////                                        val aesKey = peerAESKeys[peerIp]
////                                        if (aesKey != null) {
////                                            val decrypted = AESGCMHelper.decrypt(aesKey, iv, ciphertext)
////                                            onMessageReceived(peerIp, decrypted)
////                                        } else {
////                                            Log.e("TCP", "No AES key found for $peerIp")
////                                        }
////                                    } catch (e: Exception) {
////                                        Log.e("TCP", "Failed to decrypt from $peerIp: ${e.message}")
////                                    }
////                                }
////                            }
////                            peerAESKeys.remove(peerIp)
////                        } catch (e: Exception) {
////                            Log.e("TCP", "Client error: ${e.message}")
////                        } finally {
////                            try {
////                                socket.close()
////                            } catch (_: Exception) {
////                            }
////                            gmAddresses.remove(socket)
////                            connectedGMIPs.remove(socket.inetAddress.hostAddress!!) // Cleanup
////                        }
////                    }
////                }
//////            }
////
////            } catch (e: Exception) {
////                Log.e("TCP", "Chat server failed: ${e.message}")
////            }
////        }
//
//
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val serverSocket = ServerSocket(PORT)
//                Log.d("TCP", "Server started on port $PORT")
//
//                while (true) {
//                    val clientSocket = serverSocket.accept()
//                    clientSockets.add(clientSocket)
//
//                    lastClientAddress = clientSocket.inetAddress  // ← Save GM's IP
//                    gmAddresses.add(clientSocket)
//                    connectedGMIPs.add(clientSocket.inetAddress.hostAddress!!) // Save GM IP
//
//                    if (isGO) {
//                        Constants.deviceConnectionCallback(
//                            lastDeviceInfo,
//                            clientSocket.inetAddress.hostAddress!!
//                        )
//                    }
//                    Log.d("TCP", "Client connected: ${clientSocket.inetAddress.hostAddress}")
//
//                    handleClient(context, isGO, clientSocket, onMessageReceived)
//                }
//            } catch (e: IOException) {
//                Log.e("TCP", "Server error: ${e.message}")
//            }
//        }
//    }

    fun deriveSharedAESKey(peerPublicKey: PublicKey): SecretKey {
        val privateKey = KeyStoreManager.getPrivateKey()

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(peerPublicKey, true)

        val sharedSecret = keyAgreement.generateSecret()

        // Derive 256-bit AES key using SHA-256 hash
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(sharedSecret)
        return SecretKeySpec(keyBytes, 0, 32, "AES")
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun base64ToPublicKey(base64: String): PublicKey {
        val decoded = Base64.getDecoder().decode(base64)
        val keySpec = X509EncodedKeySpec(decoded)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec)
    }


    @RequiresApi(Build.VERSION_CODES.O)
    fun connectToPeerAndSendMessage(ip: String, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, PORT), 5000)

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))

                // Step 1: Send our public key
                val myPubKey = KeyStoreManager.getPublicKey().toBase64()
                writer.write("ECDH_PUBLIC:$myPubKey\n")
                writer.flush()

                // Step 2: Receive their public key
                val response = reader.readLine()
                if (response.startsWith("ECDH_PUBLIC:")) {
                    val peerKey = base64ToPublicKey(response.removePrefix("ECDH_PUBLIC:"))
                    val aesKey = deriveSharedAESKey(peerKey)
                    peerAESKeys[ip] = aesKey
                    Log.d("TCP", "AES key stored for $ip")
                }

                // Step 3: Encrypt and send
                val aesKey = peerAESKeys[ip]
                if (aesKey != null) {
                    val encrypted = AESGCMHelper.encrypt(aesKey, message)
                    writer.write("$encrypted\n")
                    writer.flush()
                }

                socket.close()
            } catch (e: Exception) {
                Log.e("TCP", "Connection/send failed to $ip: ${e.message}")
            }
        }
    }


    private var syncServerRunning = false

//    fun startPrefSyncServer(applicationContext: Context) {
////        if (syncServerRunning) return // Prevent double start
////        syncServerRunning = true
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                serverSocket = ServerSocket(SILENTPORT) // Silent sync port
//                while (true) {
//                    val socket = serverSocket!!.accept()
////                    lastClientAddress = socket.inetAddress  // ← Save GM's IP
////                    gmAddresses.add(socket)
//                    connectedGMIPs.add(socket.inetAddress.hostAddress!!) // Save GM IP
//
//                    CoroutineScope(Dispatchers.IO).launch {
//                    socket.use {
//                        val msg = it.getInputStream().bufferedReader().readLine()
//                        if (msg.startsWith("PREF_UPDATE:")) {
//                            val updatedData = msg.removePrefix("PREF_UPDATE:")
//                            val prefs = applicationContext.getSharedPreferences(
//                                "group_info",
//                                Context.MODE_PRIVATE
//                            )
//                            val jsonObject = JSONObject(updatedData)
//                            val editor = prefs.edit()
//                            jsonObject.keys().forEach { key ->
//                                editor.putString(key, jsonObject.getString(key))
//                            }
//                            editor.apply()
//                            Log.d("BroadcastService", "Prefs updated silently in background")
//                        }
//                    }
//                    }
//                }
//            } catch (e: IOException) {
//                Log.e("BroadcastService", "PrefSync server failed to start: ${e.message}")
//            }
//        }
//    }

    var isServerRunning = false

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
                    val serverSocket = ServerSocket(8888)
                    Log.d("TCP_SYNC", "GO Server started on port 8888")

                    while (true) {
                        val socket = serverSocket.accept()
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

                    val socket = Socket(goIp, 8888)
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

//    private fun handleClient(
//        context: Context,
//        isGO: Boolean,
//        socket: Socket,
//        onMessageReceived: (String) -> Unit
//    ) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
//                var line: String?
//
//                while (reader.readLine().also { line = it } != null) {
//                    Log.d("TCP", "Received: $line")
//                    if (isGO) {
////                        Constants.dummyLegacyClientCallback(line!!)
//                        Constants.DummyLCMessage = line.toString()
//                    }
//                    onMessageReceived(line!!)
//                }
//
//            } catch (e: IOException) {
//                Log.e("TCP", "Client disconnected or error: ${e.message}")
//            } finally {
//                try {
//                    socket.close()
//                    clientSockets.remove(socket)
//                } catch (e: IOException) {
//                    Log.e("TCP", "Error closing socket: ${e.message}")
//                }
//            }
//        }
//    }

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

//    fun sendMessageToServer(hostAddress: String, message: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val socket = Socket()
//                socket.connect(InetSocketAddress(hostAddress, PORT), 5000)
//                socket.getOutputStream().bufferedWriter().use {
//                    it.write(message)
//                    it.newLine()
//                    it.flush()
//                }
//
//                socket.close()
//            } catch (e: IOException) {
//                Log.e("TCP", "Send failed: ${e.message}")
//            }
//        }
//    }

//    @RequiresApi(Build.VERSION_CODES.O)
//    fun sendMessageToServer(hostAddress: String, message: String) {
//        CoroutineScope(Dispatchers.IO).launch {
//            val socket = Socket()
//            try {
//                socket.connect(InetSocketAddress(hostAddress, PORT), 5000)
////                socket.getOutputStream().bufferedWriter().use {
////                    it.write(message)
////                    it.newLine()
////                    it.flush()
////                }
//
//
//                val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
//                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
//
//                // Step 0: Ensure keypair is available
//                KeyStoreManager.getOrCreateKeyPair()
//
//                // Step 1: Send our public key
//                val myPub = KeyStoreManager.getPublicKey().toBase64()
//                writer.write("ECDH_PUBLIC:$myPub\n")
//                writer.flush()
//
//                // Step 2: Wait for their public key
//                val theirPubRaw = reader.readLine()
//                if (theirPubRaw != null && theirPubRaw.startsWith("ECDH_PUBLIC:")) {
//                    val theirKey = base64ToPublicKey(theirPubRaw.removePrefix("ECDH_PUBLIC:"))
//                    sharedAESKey = deriveSharedAESKey(theirKey)
//                }
//
//                // Step 3: Encrypt and send message
//                sharedAESKey?.let { key ->
//                    val encrypted = AESGCMHelper.encrypt(key, message)
//                    writer.write("$encrypted\n")
//                    writer.flush()
//                }
//
//                socket.close()
//            } catch (e: IOException) {
//                Log.e("TCP", "Send failed: ${e.message}")
//            } finally {
//                try {
//                    socket.close()
//                } catch (_: Exception) {
//                }
//            }
//        }
//    }

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
                val socket = Socket()

                try {
                    socket.connect(InetSocketAddress(ip, PORT), 5000)
                    socket.getOutputStream().bufferedWriter().use {
                        it.write(message)
                        it.newLine()
                        it.flush()
                    }


//                    val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
//                    writer.write(message)
//                    writer.newLine()
//                    writer.flush()
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

    fun startSilentReceiver(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(PORT) // Use a separate port for silent sync
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
                                            Context.MODE_PRIVATE
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

    fun stopSilentReceiver() {
        try {
            serverSocket?.close()
            Log.d("TCP", "Silent receiver socket closed")
        } catch (e: IOException) {
            Log.e("TCP", "Error closing silent socket: ${e.message}")
        }
    }
}
