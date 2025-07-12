//package org.fordem.indifi.ui
//
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.Service
//import android.content.Context
//import android.content.Intent
//import android.os.Build
//import android.os.IBinder
//
//class BroadcastSyncService : Service() {
//
//    override fun onCreate() {
//        super.onCreate()
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            createNotificationChannel()
//            val notification = Notification.Builder(this, "SYNC_CHANNEL")
//                .setContentTitle("Group sync running")
//                .setContentText("Listening to GO for updates")
//                .setSmallIcon(android.R.drawable.stat_notify_sync)
//                .build()
//
//            startForeground(101, notification)
//        }
//
//        startTcpReceiver() // works on all Android versions
//    }
//
//    private fun startTcpReceiver() {
////        CoroutineScope(Dispatchers.IO).launch {
////            TcpHelper.startPrefSyncServer(applicationContext)
////        }
//    }
//
//    private fun createNotificationChannel() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            val chan = NotificationChannel(
//                "SYNC_CHANNEL",
//                "Group Pref Sync",
//                NotificationManager.IMPORTANCE_LOW
//            )
//            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//            manager.createNotificationChannel(chan)
//        }
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        return START_STICKY // ensures service restarts if killed
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//}
