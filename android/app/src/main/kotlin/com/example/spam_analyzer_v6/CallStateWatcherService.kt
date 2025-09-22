package com.example.spam_analyzer_v6

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CallStateWatcherService : Service() {

    companion object {
        private const val CH_ID = "CallWatcher"
        private const val N_ID = 1401
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CH_ID, "Call Watcher", NotificationManager.IMPORTANCE_LOW)
            )
        }
        // Foreground notification (POST_NOTIFICATIONS not required for FGS on 33+)
        startForeground(
            N_ID,
            NotificationCompat.Builder(this, CH_ID)
                .setOngoing(true)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("Watching call state")
                .setContentText("Listening for incoming calls")
                .build()
        )
        // TODO: Telephony listener to start/stop CallOverlayService as needed.
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
