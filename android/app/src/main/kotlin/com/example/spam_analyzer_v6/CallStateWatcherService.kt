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
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                if (nm?.getNotificationChannel(CH_ID) == null) {
                    nm?.createNotificationChannel(
                        NotificationChannel(CH_ID, "Call Watcher", NotificationManager.IMPORTANCE_LOW)
                    )
                }
            }
            startForeground(
                N_ID,
                NotificationCompat.Builder(this, CH_ID)
                    .setOngoing(true)
                    .setSmallIcon(android.R.drawable.sym_call_incoming)
                    .setContentTitle("Watching call state")
                    .setContentText("Listening for incoming calls")
                    .build()
            )
        } catch (e: Exception) {
            android.util.Log.e("CallStateWatcherService", "Error in onCreate: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            android.util.Log.e("CallStateWatcherService", "Error in onDestroy: ${e.message}", e)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
