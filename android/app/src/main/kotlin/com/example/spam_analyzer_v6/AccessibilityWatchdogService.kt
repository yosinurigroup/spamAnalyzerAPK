package com.example.spam_analyzer_v6

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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class AccessibilityWatchdogService : Service() {
    private val TAG = "A11yWatchdog"
    private val CH_ID = "a11y_watchdog"
    private val CHECK_MS = 3 * 60 * 1000L

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var boundFromBroadcast = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == AssistCaptureService.ACTION_A11Y_STATE) {
                boundFromBroadcast = i.getBooleanExtra(AssistCaptureService.EXTRA_BOUND, false)
                Log.i(TAG, "A11y bound broadcast = $boundFromBroadcast")
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val enabled = isServiceEnabled()
            val bound = boundFromBroadcast
            if (!enabled || !bound) {
                showFixNotif(needsToggle = !enabled)
                pokeAssist()
            }
            handler.postDelayed(this, CHECK_MS)
            WatchdogAlarmReceiver.schedule(this@AccessibilityWatchdogService)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startForeground(101, baseNotif("Watching accessibility"))

        val filter = IntentFilter(AssistCaptureService.ACTION_A11Y_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                stateReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(stateReceiver, filter)
        }

        handler.post(tick)
        WatchdogAlarmReceiver.schedule(this)
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        try { unregisterReceiver(stateReceiver) } catch (_: Throwable) {}
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(i: Intent?) = null

    private fun isServiceEnabled(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val cn = ComponentName(this, AssistCaptureService::class.java)
            val f1 = cn.flattenToShortString()
            val f2 = cn.flattenToString()
            enabled.split(':').any { s -> s.equals(f1, true) || s.equals(f2, true) }
        } catch (_: Throwable) { false }
    }

    private fun pokeAssist() {
        // Explicit in-app broadcast
        sendBroadcast(
            Intent(AssistCaptureService.ACTION_REFRESH_KEYWORDS)
                .setPackage(packageName)
        )
        try {
            ContextCompat.startForegroundService(this, Intent(this, CallStateWatcherService::class.java))
        } catch (_: Throwable) {}
    }

    private fun showFixNotif(needsToggle: Boolean) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val text = if (needsToggle)
            "Turn ON 'Spam Analyzer' in Accessibility"
        else
            "Rebind Accessibility (tap)"

        val n = NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Keep Spam Analyzer alive")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(102, n)
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            mgr.createNotificationChannel(
                NotificationChannel(CH_ID, "Accessibility Watchdog", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun baseNotif(text: String): Notification {
        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Spam Analyzer service running")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }
}
