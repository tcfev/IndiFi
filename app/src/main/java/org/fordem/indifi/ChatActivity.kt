package org.fordem.indifi

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.example.wifidirectcommunicationapp.MessageAdapter
import org.fordem.indifi.Constants.connectedGMIPs

class ChatActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()

    private var groupOwnerAddress: String? = null
    private var isGroupOwner = false

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
        isGroupOwner = intent.getBooleanExtra("isGroupOwner", false)
        groupOwnerAddress = intent.getStringExtra("groupOwnerAddress")


//        if (!isGroupOwner && groupOwnerAddress != null) {
//            TcpHelper.sendMessageToServer(groupOwnerAddress!!, "__JOINED__")
//            Log.d("TCP", "Sent __JOINED__ signal to GO")
//        }


//        if (isGroupOwner) {
        TcpHelper.startChatServer { msg ->

            Log.e("TAG", connectedGMIPs.toString())

//                if (msg.contains("__JOINED__")) {
//                    val prefs = getSharedPreferences("group_info", Context.MODE_PRIVATE)
//                    val allPrefs = prefs.all.map { "${it.key}=${it.value}" }.joinToString("&")
//                    TcpHelper.broadcastSharedPrefsToClients(allPrefs)
//                    Log.d("TCP", "Broadcasted updated SharedPrefs to all GMs")
//                } else if (msg.contains("__LEFT__")) {
//                    // Handle future disconnect logic
//                    val deviceName = msg.removePrefix("__LEFT__:")
//                    val prefs = getSharedPreferences("group_info", Context.MODE_PRIVATE)
//                    prefs.edit().remove(deviceName).apply()
//                    val allPrefs = prefs.all.map { "${it.key}=${it.value}" }.joinToString("&")
//                    TcpHelper.broadcastSharedPrefsToClients("PREF_UPDATE:$allPrefs")
//                } else {
            runOnUiThread {
                messages.add(Message(msg, false))
                adapter.notifyDataSetChanged()
            }
//                }
        }
//        } else {
//            TcpHelper.startServer { msg ->
////                    if (msg.startsWith("PREF_UPDATE:")) {
////                        val updatedData = msg.removePrefix("PREF_UPDATE:")
////                        updateLocalPrefsFromBroadcast(updatedData)
////                        Log.d("TCP", "Updated local SharedPrefs with: $updatedData")
////                    } else {
//                runOnUiThread {
//
//                    messages.add(Message(msg, false))
//                    adapter.notifyDataSetChanged()
////                    adapter.notifyDataSetInvalidated()
//
//                    Log.d("TCP", "Server started on GO")
//                }
////                }
//
////                startClientReceiver(this)
//            }
//        }

        sendButton.setOnClickListener {

            val msg = inputField.text.toString()
            if (msg.isNotBlank()) {
                messages.add(Message(msg, true))
                adapter.notifyDataSetChanged()

//                if (groupOwnerAddress != null) {
//                    TcpHelper.sendMessageToServer(groupOwnerAddress.toString(), msg)
//                }

                if (isGroupOwner) {
                    TcpHelper.sendMessageToClient(msg) // Send to GM
//                    TcpHelper.sendMessageToServer(groupOwnerAddress.toString(), msg)
                } else {
                    groupOwnerAddress?.let { TcpHelper.sendMessageToServer(it, msg) } //Send to GO
                }

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
            TcpHelper.sendMessageToServer(groupOwnerAddress!!, "__LEFT__:$deviceName")
        }
    }

    private fun getDeviceName(): String {
        return "${Build.MANUFACTURER}_${Build.MODEL}_${Build.DEVICE}"
    }
}
