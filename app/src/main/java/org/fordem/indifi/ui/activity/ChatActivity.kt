package org.fordem.indifi.ui.activity

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import org.fordem.indifi.R
import org.fordem.indifi.ui.adapter.MessageAdapter
import org.fordem.indifi.ui.model.Message
import org.fordem.indifi.ui.utils.MessageRouterHelper

class ChatActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()


    private var gm_ip: String? = null
    private var groupOwnerAddress: String? = null
    private var isGroupOwner = false

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

        sendButton.setOnClickListener {
            val msg = inputField.text.toString()
            if (msg.isNotBlank()) {
                messages.add(Message(msg, true))
                adapter.notifyDataSetChanged()

//                if (groupOwnerAddress != null) {
//                    TcpHelper.sendMessageToServer(groupOwnerAddress.toString(), msg)
//                }

                if (groupOwnerAddress != gm_ip) {
                    if (isGroupOwner) {
                        MessageRouterHelper.messageRouterService?.sendMessageToClient(
                            msg,
                            gm_ip!!
                        ) // Send to GM
//                    TcpHelper.sendMessageToServer(groupOwnerAddress.toString(), msg)
                    } else {
                        groupOwnerAddress?.let {
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
