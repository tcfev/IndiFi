package org.fordem.indifi.ui.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import org.fordem.indifi.ui.service.MessageRouterService

object MessageRouterHelper {
    private var appContext: Context? = null
    var messageRouterService: MessageRouterService? = null

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            Log.d("MessageRouter", "Helper initialized")

        }
    }

    /**
     * Start the background message service (must be called once, e.g. from main activity)
     */
    fun startMessageRouterService() {
        appContext?.let {
            val intent = Intent(it, MessageRouterService::class.java)
            it.startService(intent)
        }
    }

    /**
     * Send message to GO (Legacy Wi-Fi client)
     */
//    fun sendMessageToGo(goIp: String, message: String) {
//        MessageRouterService.sendMessageToServer(goIp, message)
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
//        MessageRouterService.sendMessageToServer(gatewayIp, Constants.DummyLCMessage)
//    }

//    var messageRouterService: MessageRouterService? = null
    var isServiceBound = false

    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MessageRouterService.LocalBinder
            messageRouterService = localBinder.getService()
            isServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            messageRouterService = null
            isServiceBound = false
        }
    }

    fun bindService(context: Context) {
        if (!isServiceBound) {
            val intent = Intent(context, MessageRouterService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            context.startService(intent) // optional: keep it running
        }
    }

    fun unbindService(context: Context) {
        if (isServiceBound) {
            context.unbindService(serviceConnection)
            isServiceBound = false
        }
    }
}
