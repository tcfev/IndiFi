package org.fordem.indifi.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import org.fordem.indifi.ui.state.P2pConnectionState
import org.fordem.indifi.ui.utils.Constants

class P2pHeartbeatService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wifiP2pManager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null

    override fun onCreate() {
        super.onCreate()
        wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = wifiP2pManager?.initialize(this, mainLooper, null)
        startForegroundServiceNotification()
        startHeartbeat()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "p2p_heartbeat_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "P2P Heartbeat",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("P2P Heartbeat Running")
            .setContentText("Monitoring connection state")
//            .setSmallIcon(R.drawable.ic_connection) // Replace with your app icon
            .build()

        startForeground(1, notification)
    }

    private fun startHeartbeat() {
        scope.launch {
            while (isActive) {
                checkConnection()
                delay(5000) // check every 5 seconds
            }
        }
    }

    private fun checkConnection() {
        wifiP2pManager?.requestConnectionInfo(channel) { info: WifiP2pInfo? ->
            if (info != null && info.groupFormed) {
                // Post the connection status as CONNECTED with group owner IP
                P2pConnectionState.postConnectionStatus(Constants.P2PConnectionStatus.CONNECTED)
                // Optionally, you could post groupOwnerAddress as part of group LiveData or extend your model
            } else {
                // Post disconnected status
                P2pConnectionState.postConnectionStatus(Constants.P2PConnectionStatus.DISCONNECTED)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}