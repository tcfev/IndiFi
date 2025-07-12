package org.fordem.indifi.ui.service//package org.fordem.indifi.ui.utils
//
//import android.app.Service
//import android.content.Intent
//import android.os.IBinder
//import android.widget.Toast
//import org.fordem.indifi.ui.activity.ChatActivity
//import java.net.DatagramPacket
//import java.net.DatagramSocket
//
//class UdpListenerService : Service() {
//
//    private var listeningThread: Thread? = null
//    private var isRunning = false
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        startListening()
//        return START_STICKY
//    }
//
//    private fun startListening() {
//        if (isRunning) return
//
//        isRunning = true
//        listeningThread = Thread {
//            try {
//                val socket = DatagramSocket(9876)
//                val buffer = ByteArray(1024)
//                while (isRunning) {
//                    val packet = DatagramPacket(buffer, buffer.size)
//                    socket.receive(packet)
//                    val message = String(packet.data, 0, packet.length).trim()
//                    if (message == "HELLO") {
//                        runOnUiThreadSafe {
//                            Toast.makeText(applicationContext, "GM connected!", Toast.LENGTH_SHORT).show()
//                            val chatIntent = Intent(applicationContext, ChatActivity::class.java).apply {
//                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                            }
//                            startActivity(chatIntent)
//                        }
//                        break
//                    }
//                }
//                socket.close()
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }
//        listeningThread?.start()
//    }
//
//    private fun runOnUiThreadSafe(action: () -> Unit) {
//        val handler = android.os.Handler(mainLooper)
//        handler.post { action() }
//    }
//
//    override fun onDestroy() {
//        isRunning = false
//        listeningThread?.interrupt()
//        super.onDestroy()
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//}
