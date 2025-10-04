package com.example.spam_analyzer_v6

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log

class AccessibilityWatchdogService : Service() {

    companion object {
        private const val TAG = "A11yWatchdog"
        private const val CH = "a11y_guard"
        private const val NOTIF_ID = 77

        const val ACTION_A11Y_STATE = AssistCaptureService.ACTION_A11Y_STATE
        const val EXTRA_BOUND = AssistCaptureService.EXTRA_BOUND
        const val ACTION_TICK = "com.example.spam_analyzer_v6.A11Y_TICK"

        private const val HEARTBEAT_GRACE_MS = 5 * 60_000L  // 5 minutes
        private const val HARD_TIMEOUT_MS = 12 * 60_000L    // 12 minutes
    }

    private val mainH = Handler(Looper.getMainLooper())
    private var lastBeatAt = 0L
    private var lastBound = false

    private val recv = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                ACTION_A11Y_STATE -> {
                    lastBound = i.getBooleanExtra(EXTRA_BOUND, false)
                    lastBeatAt = System.currentTimeMillis()
                    Log.d(TAG, "beat: bound=$lastBound")
                    updateNotif()
                }
                ACTION_TICK -> {
                    scheduleTick(60_000L)
                    checkNow()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createChannel()
            startForeground(NOTIF_ID, buildNotif("Watching accessibility…"))

            val f = IntentFilter().apply {
                addAction(ACTION_A11Y_STATE)
                addAction(ACTION_TICK)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(recv, f, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(recv, f)
            }

            // First tick
            scheduleTick(1_000L)
            Log.i(TAG, "Watchdog service started")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(recv)
            mainH.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}", e)
        }
        super.onDestroy()
    }

    override fun onBind(i: Intent?) = null

    private fun checkNow() {
        try {
            val now = System.currentTimeMillis()
            val enabled = isOurServiceEnabled()

            if (!enabled) {
                showFixAction("Accessibility is OFF. Tap to enable.")
                return
            }

            val stale = now - lastBeatAt > HEARTBEAT_GRACE_MS
            if (!lastBound || stale) {
                Log.w(TAG, "suspect stale: bound=$lastBound stale=$stale")
                
                // Poke app to bring to foreground (rebinding nudge)
                try {
                    val pi = PendingIntent.getActivity(
                        this, 10,
                        Intent(this, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    pi.send()
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending pending intent: ${e.message}")
                }

                if (now - lastBeatAt > HARD_TIMEOUT_MS) {
                    showFixAction("Tap to restart Accessibility service")
                }
            }
            updateNotif()
        } catch (e: Exception) {
            Log.e(TAG, "Error in checkNow: ${e.message}", e)
        }
    }

    private fun isOurServiceEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val cn = ComponentName(this, AssistCaptureService::class.java)
            val f1 = cn.flattenToShortString()
            val f2 = cn.flattenToString()
            enabled.split(':').any { it.equals(f1, true) || it.equals(f2, true) }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking service enabled: ${e.message}", e)
            false
        }
    }

    private fun showFixAction(text: String) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val pi = PendingIntent.getActivity(
                this, 11, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val n = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CH)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle("Spam Analyzer")
                    .setContentText(text)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle("Spam Analyzer")
                    .setContentText(text)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build()
            }
            
            getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID + 1, n)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing fix action: ${e.message}", e)
        }
    }

    private fun scheduleTick(delayMs: Long) {
        try {
            val am = getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                this, 1, Intent(ACTION_TICK).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val t = System.currentTimeMillis() + delayMs
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am?.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
            } else {
                @Suppress("DEPRECATION")
                am?.setExact(AlarmManager.RTC_WAKEUP, t, pi)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling tick: ${e.message}", e)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val nm = getSystemService(NotificationManager::class.java)
                if (nm?.getNotificationChannel(CH) == null) {
                    nm?.createNotificationChannel(
                        NotificationChannel(
                            CH,
                            "Accessibility Watchdog",
                            NotificationManager.IMPORTANCE_LOW
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating channel: ${e.message}", e)
            }
        }
    }

    private fun buildNotif(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.presence_online)
                .setContentTitle("Accessibility Guard")
                .setContentText(text)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.presence_online)
                .setContentTitle("Accessibility Guard")
                .setContentText(text)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotif() {
        try {
            val txt = if (lastBound) "Service bound · OK" else "Waiting for service…"
            getSystemService(NotificationManager::class.java)
                ?.notify(NOTIF_ID, buildNotif(txt))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification: ${e.message}", e)
        }
    }
}
