package org.fordem.indifi.ui.activity

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fordem.indifi.R
import org.fordem.indifi.ui.adapter.MessageAdapter
import org.fordem.indifi.ui.db.AppDatabase
import org.fordem.indifi.ui.db.DeviceInfoViewModel
import org.fordem.indifi.ui.encryption.AESGCMHelper
import org.fordem.indifi.ui.encryption.EncryptedMessageWrapper
import org.fordem.indifi.ui.encryption.KeyStoreManager
import org.fordem.indifi.ui.encryption.KeyStoreManager.toBase64
import org.fordem.indifi.ui.model.ChatMessage
import org.fordem.indifi.ui.model.Message
import org.fordem.indifi.ui.utils.Constants
import org.fordem.indifi.ui.utils.Constants.isChatMessage
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.utils.TcpHelper
import org.fordem.indifi.ui.viewmodel.ChatViewModel
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.security.PublicKey
import java.util.UUID

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()


    private var gm_ip: String? = null
    private var groupOwnerAddress: String? = null
    private var isGroupOwner = false
    private val deviceInfoViewModel: DeviceInfoViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        Log.d("CHAT", "ChatActivity started")
        KeyStoreManager.debugPrintStoredKeys()

        listView = findViewById(R.id.lvChat)
        inputField = findViewById(R.id.etMessage)
        sendButton = findViewById(R.id.btnSend)

        adapter = MessageAdapter(this, messages)
        listView.adapter = adapter

        // Assume passed from WifiP2p connection
        gm_ip = intent.getStringExtra("device_ip")
        isGroupOwner = intent.getBooleanExtra("isGroupOwner", false)
        groupOwnerAddress = intent.getStringExtra("groupOwnerAddress")

        chatViewModel.viewModelScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                chatViewModel.allMessages.collect { savedMessages ->
                    messages.clear()
                    messages.addAll(savedMessages.map { Message(it.message, it.isIncoming) })
                    adapter.notifyDataSetChanged()
                }
            }
        }

        sendButton.setOnClickListener {
            val msg = inputField.text.toString()
            if (msg.isNotBlank()) {
                messages.add(Message(msg, true))
                adapter.notifyDataSetChanged()


                val chat = ChatMessage(senderName = getDeviceName(), senderIp = gm_ip.toString(), message = msg, timestamp = System.currentTimeMillis(), isIncoming = true)
                chatViewModel.insertMessage(chat)

                if (groupOwnerAddress != gm_ip) {
                    if (isGroupOwner) {
                        isChatMessage = true

                        chatViewModel.viewModelScope.launch {
                            val targetIp = gm_ip.toString()
                            var peerKey: PublicKey? = null
                            val messageId = UUID.randomUUID().toString().take(8)

                            // Step 1: Check in-memory first
                            Log.d("E2EE", "[$messageId][1] Checking in-memory public key for $targetIp")
                            peerKey = KeyStoreManager.getPeerPublicKey(targetIp)

                            if (peerKey == null) {
                                Log.w("E2EE", "[$messageId][2] Not found in memory. Checking Room DB...")

                                // Step 2: Check Room DB fallback
                                val entity = chatViewModel.getKey(targetIp)
                                if (entity != null) {
                                    peerKey = KeyStoreManager.base64ToPublicKey(entity.base64Key)
                                    KeyStoreManager.addPeerPublicKey(targetIp, peerKey!!) // re-cache
                                    Log.d("E2EE", "[$messageId][3] Loaded peer key from Room DB for $targetIp")
                                } else {
                                    Log.e("E2EE", "[$messageId][4] Peer public key not found in Room either — aborting.")
                                    Toast.makeText(this@ChatActivity, "Peer public key not available", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                            }

                            // Step 3: Derive or get AES key
                            val aesKey = Constants.peerAESKeys?.get(targetIp)
                                ?: KeyStoreManager.deriveSharedAESKey(peerKey).also {
                                    Constants.peerAESKeys?.set(targetIp, it)
                                    Constants.peerPublicKeys?.set(targetIp, peerKey!!)

                                    Log.d("E2EE", "[$messageId][5] Derived AES key for $targetIp")
                                    Log.d("E2EE", "[$messageId][5a] AES KEY (Base64): ${Base64.encodeToString(it.encoded, Base64.NO_WRAP)}")
                                }

                            // Step 4: Encrypt
                            val (encryptedMsg, iv) = AESGCMHelper.encrypt(aesKey, msg)
                            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                            val encryptedBase64 = Base64.encodeToString(encryptedMsg, Base64.NO_WRAP)

                            Log.d("E2EE", "[$messageId][6] IV: $ivBase64")
                            Log.d("E2EE", "[$messageId][6a] Encrypted: $encryptedBase64")

                            val encryptedJson = EncryptedMessageWrapper.createJson(encryptedMsg, iv)

                            Log.d("E2EE", "[$messageId][7] Sending encrypted message to $targetIp")
                            MessageRouterHelper.messageRouterService?.connectToPeerAndSendMessage(targetIp, encryptedJson)

                            inputField.text.clear()
                        }
                    } else {
                        groupOwnerAddress?.let {
                            isChatMessage = true

                            chatViewModel.viewModelScope.launch {
                                val targetIp = it
                                var peerKey: PublicKey?
                                val messageId = UUID.randomUUID().toString().take(8)

                                // Step 1: Check in-memory first
                                Log.d("E2EE", "[$messageId][1] Checking in-memory public key for $targetIp")
                                peerKey = KeyStoreManager.getPeerPublicKey(targetIp)

                                if (peerKey == null) {
                                    Log.w("E2EE", "[$messageId][2] Not found in memory. Checking Room DB...")

                                    // Step 2: Check Room DB fallback
                                    val entity = chatViewModel.getKey(targetIp)
                                    if (entity != null) {
                                        peerKey = KeyStoreManager.base64ToPublicKey(entity.base64Key)
                                        KeyStoreManager.addPeerPublicKey(targetIp, peerKey!!) // re-cache
                                        Log.d("E2EE", "[$messageId][3] Loaded peer key from Room DB for $targetIp")
                                    } else {
                                        Log.e("E2EE", "[$messageId][4] Peer public key not found in Room either — aborting.")
                                        Toast.makeText(this@ChatActivity, "Peer public key not available", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                }

                                // Step 3: Derive or get AES key
                                val aesKey = Constants.peerAESKeys?.get(targetIp)
                                    ?: KeyStoreManager.deriveSharedAESKey(peerKey!!).also {
                                        Constants.peerAESKeys?.set(targetIp, it)
                                        Constants.peerPublicKeys?.set(targetIp, peerKey!!)

                                        Log.d("E2EE", "[$messageId][5] Derived AES key for $targetIp")
                                        Log.d("E2EE", "[$messageId][5a] AES KEY (Base64): ${Base64.encodeToString(it.encoded, Base64.NO_WRAP)}")
                                    }

                                // Step 4: Encrypt
                                val (encryptedMsg, iv) = AESGCMHelper.encrypt(aesKey, msg)
                                val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                                val encryptedBase64 = Base64.encodeToString(encryptedMsg, Base64.NO_WRAP)

                                Log.d("E2EE", "[$messageId][6] IV: $ivBase64")
                                Log.d("E2EE", "[$messageId][6a] Encrypted: $encryptedBase64")

                                val encryptedJson = EncryptedMessageWrapper.createJson(encryptedMsg, iv)

                                Log.d("E2EE", "[$messageId][7] Sending encrypted message to $targetIp")
                                MessageRouterHelper.messageRouterService?.connectToPeerAndSendMessage(targetIp, encryptedJson)

                                inputField.text.clear()
                            }
                        } //Send to GO
                    }
                }
            }
        }

        Constants.chatCallback = {
            runOnUiThread {
                messages.add(Message(it, false))
                adapter.notifyDataSetChanged()

                val chat = ChatMessage(senderName = getDeviceName(), senderIp = gm_ip.toString(), message = it, timestamp = System.currentTimeMillis(), isIncoming = true)
                chatViewModel.insertMessage(chat)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isGroupOwner && groupOwnerAddress != null) {
            val deviceName = getDeviceName()
            MessageRouterHelper.messageRouterService?.sendMessageToServer(
                groupOwnerAddress!!,
                "__LEFT__:$deviceName"
            )
        }
    }

    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER}_${Build.MODEL}_${Build.DEVICE}"
    }
}
