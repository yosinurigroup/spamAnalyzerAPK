package com.example.spam_analyzer_v6

import android.app.*
import android.content.*
import android.os.*
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
                // App ko zinda rakhne ke liye thoda poke:
                pokeAssist()
            }
            // next tick
            handler.postDelayed(this, CHECK_MS)
            // ensure alarm re-schedule (agar OS handler ko throttle kare)
            WatchdogAlarmReceiver.schedule(this@AccessibilityWatchdogService)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
        startForeground(101, baseNotif("Watching accessibility"))
        // listen accessibility bound/unbound
        registerReceiver(stateReceiver, IntentFilter(AssistCaptureService.ACTION_A11Y_STATE))
        handler.post(tick)
        // first alarm
        WatchdogAlarmReceiver.schedule(this)
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

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
        // Keywords refresh broadcast (safe no-op)
        sendBroadcast(Intent(AssistCaptureService.ACTION_REFRESH_KEYWORDS).setPackage(packageName))
        // foreground companions ko bhi alive rakho
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
            .setSmallIcon(R.drawable.ic_notification) // make sure this exists
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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Spam Analyzer service running")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }
}
