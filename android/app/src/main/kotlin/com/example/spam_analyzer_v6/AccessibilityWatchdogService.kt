package com.example.spam_analyzer_v6

import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat

class AccessibilityWatchdogService : Service() {

    companion object {
        private const val TAG = "A11yWatchdog"
        private const val CH = "a11y_guard"
        private const val NOTIF_ID = 77

        const val ACTION_A11Y_STATE = AssistCaptureService.ACTION_A11Y_STATE
        const val EXTRA_BOUND = AssistCaptureService.EXTRA_BOUND
        const val ACTION_TICK = "com.example.spam_analyzer_v6.A11Y_TICK"

        private const val HEARTBEAT_GRACE_MS = 5 * 60_000L
        private const val HARD_TIMEOUT_MS    = 12 * 60_000L
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
        createChannel()
        startForeground(NOTIF_ID, buildNotif("Watching accessibility…"))

        val f = IntentFilter().apply { addAction(ACTION_A11Y_STATE); addAction(ACTION_TICK) }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(recv, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION") registerReceiver(recv, f)
        }

        // first tick
        scheduleTick(1_000L)
    }

    override fun onDestroy() {
        try { unregisterReceiver(recv) } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onBind(i: Intent?) = null

    private fun checkNow() {
        val now = System.currentTimeMillis()
        val enabled = isOurServiceEnabled()

        if (!enabled) {
            showFixAction("Accessibility is OFF. Tap to enable.")
            return
        }

        val stale = now - lastBeatAt > HEARTBEAT_GRACE_MS
        if (!lastBound || stale) {
            Log.w(TAG, "suspect stale: bound=$lastBound stale=$stale")
            // poke app to bring to foreground (rebinding nudge)
            try {
                val pi = PendingIntent.getActivity(
                    this, 10,
                    Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                pi.send()
            } catch (_: Throwable) {}

            if (now - lastBeatAt > HARD_TIMEOUT_MS) {
                showFixAction("Tap to restart Accessibility service")
            }
        }
        updateNotif()
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
        } catch (_: Throwable) { false }
    }

    private fun showFixAction(text: String) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val pi = PendingIntent.getActivity(this, 11, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Spam Analyzer")
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIF_ID + 1, n)
    }

    private fun scheduleTick(delayMs: Long) {
        val am = getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            this, 1, Intent(ACTION_TICK).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val t = System.currentTimeMillis() + delayMs
        if (Build.VERSION.SDK_INT >= 31) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
        } else {
            @Suppress("DEPRECATION")
            am.setExact(AlarmManager.RTC_WAKEUP, t, pi)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CH) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CH, "Accessibility Watchdog",
                        NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    private fun buildNotif(text: String) = Notification.Builder(this, CH)
        .setSmallIcon(android.R.drawable.presence_online)
        .setContentTitle("Accessibility Guard")
        .setContentText(text)
        .setOngoing(true)
        .build()

    private fun updateNotif() {
        val txt = if (lastBound) "Service bound · OK" else "Waiting for service…"
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotif(txt))
    }
}
