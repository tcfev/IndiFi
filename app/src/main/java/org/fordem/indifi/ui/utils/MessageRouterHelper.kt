package org.fordem.indifi.ui.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import org.fordem.indifi.ui.service.IndifiService
import org.fordem.indifi.ui.service.MulticastService

object MessageRouterHelper {
    private var appContext: Context? = null
    var indifiService: IndifiService? = null
    var multicastService: MulticastService? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            Log.d("MessageRouter", "Helper initialized")

        }
    }

    /**
     * Start the background message service (must be called once, e.g. from main activity)
     */
    fun startIndifiService() {
        appContext?.let {
            val intent = Intent(it, IndifiService::class.java)
            ContextCompat.startForegroundService(it, intent)
        }
    }

    fun startMulticastService() {
        appContext?.let {
            val intent = Intent(it, MulticastService::class.java)
            ContextCompat.startForegroundService(it, intent)
        }
    }

    /**
     * Send message to GO (Legacy Wi-Fi client)
     */
//    fun sendMessageToGo(goIp: String, message: String) {
//        IndifiService.sendMessageToServer(goIp, message)
//    }

    /**
     * Send message to GM (GO or peer device sends to specific GM)
     */
//    fun sendMessageToGM(message: String) {
//        TcpHelper.sendMessageToClient(message)
//    }

    /**
     * Broadcast message to all GMs (only for GO)
     */
//    fun broadcastToAllGMs(message: String) {
//        TcpHelper.broadcastToGMs(message)
//    }

    /**
     * Send preferences update silently
     */
//    fun broadcastPrefsUpdateToAll(data: String) {
//        TcpHelper.broadcastSharedPrefsToClients(data)
//    }

    /**
     * Send custom GM list (GO to all GMs)
     */
//    fun broadcastGMList(gmList: List<String>) {
//        TcpHelper.broadcastGMListToAll(gmList)
//    }

    /**
     * Optional: send a one-time hello to GO (for Legacy Wi-Fi)
     */
//    fun sendHelloToGO(gatewayIp: String) {
//        IndifiService.sendMessageToServer(gatewayIp, Constants.DummyLCMessage)
//    }

//    var IndifiService: IndifiService? = null
    var isServiceBound = false
    var isMulticastServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as IndifiService.LocalBinder
            indifiService = localBinder.getService()
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            indifiService = null
            isServiceBound = false
        }
    }


    private val multicastServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MulticastService.LocalBinder
            multicastService = localBinder.getService()
            isMulticastServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            multicastService = null
            isMulticastServiceBound = false
        }
    }

    fun bindService(context: Context) {
        if (!isServiceBound) {
            val intent = Intent(context, IndifiService::class.java)
            ContextCompat.startForegroundService(context, intent) // optional: keep it running
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun bindMulticastService(context: Context) {
        if (!isServiceBound) {
            val intent = Intent(context, MulticastService::class.java)
            ContextCompat.startForegroundService(context, intent) // optional: keep it running
            context.bindService(intent, multicastServiceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService(context: Context) {
        if (isServiceBound) {
            context.unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}
