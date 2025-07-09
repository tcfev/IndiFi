package org.fordem.indifi.ui.activity

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.fordem.indifi.R
import org.fordem.indifi.ui.adapter.MessageAdapter
import org.fordem.indifi.ui.db.DeviceInfoViewModel
import org.fordem.indifi.ui.model.ChatMessage
import org.fordem.indifi.ui.model.Message
import org.fordem.indifi.ui.utils.Constants
import org.fordem.indifi.ui.utils.Constants.isChatMessage
import org.fordem.indifi.ui.utils.MessageRouterHelper
import org.fordem.indifi.ui.viewmodel.ChatViewModel

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

//                if (groupOwnerAddress != null) {
//                    TcpHelper.sendMessageToServer(groupOwnerAddress.toString(), msg)
//                }

                val chat = ChatMessage(senderName = getDeviceName(), senderIp = gm_ip.toString(), message = msg, timestamp = System.currentTimeMillis(), isIncoming = true)
                chatViewModel.insertMessage(chat)

                if (groupOwnerAddress != gm_ip) {
                    if (isGroupOwner) {
                        isChatMessage = true
                        MessageRouterHelper.messageRouterService?.sendMessageToClient(
                            msg,
                            gm_ip!!
                        ) // Send to GM
//                    TcpHelper.sendMessageToServer(groupOwnerAddress.toString(), msg)
                    } else {
                        groupOwnerAddress?.let {
                            isChatMessage = true

//                        TcpHelper.sendMessageToServer(it, msg)
                            MessageRouterHelper.messageRouterService?.sendMessageToServer(it, msg)
                        } //Send to GO
                    }
                }

//                // 1. Encrypt the message using AES-GCM and the peer's AES key
//                val peerPublicKey = KeyStoreManager.getPeerPublicKey(gm_ip.toString())
//                if (peerPublicKey == null) {
//                    Toast.makeText(this, "No public key for $gm_ip", Toast.LENGTH_SHORT).show()
//                    return@setOnClickListener
//                }
//
//                val aesKey = KeyStoreManager.getOrCreateSharedAESKey(context = this, peerIp = gm_ip.toString(), peerPublicKey)
//                val (encryptedMsg, iv) = AESGCMHelper.encrypt(aesKey, msg)
//
//                // 2. Wrap the message as JSON
//                val encryptedJson = EncryptedMessageWrapper.createJson(encryptedMsg, iv)
//
//                // 3. Send the encrypted JSON to the peer
//                TcpHelper.connectToPeerAndSendMessage(gm_ip.toString(), encryptedJson)


                inputField.text.clear()
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

    private fun updateLocalPrefsFromBroadcast(data: String) {
        val prefs = getSharedPreferences("group_info", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.clear()

        val entries = data.split("&")
        for (entry in entries) {
            val (key, value) = entry.split("=")
            editor.putString(key, value)
        }

        editor.apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isGroupOwner && groupOwnerAddress != null) {
            val deviceName = getDeviceName()
//            MessageRouterService().sendMessageToGo(groupOwnerAddress!!, "__LEFT__:$deviceName")
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
