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

<<<<<<< HEAD
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
=======
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
>>>>>>> ceb9980ba2af4edcdf811e5bfbbe1193ce56f153
        }
    }

    override fun onCreate() {
        super.onCreate()
<<<<<<< HEAD
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
=======
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
>>>>>>> ceb9980ba2af4edcdf811e5bfbbe1193ce56f153

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

<<<<<<< HEAD
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
=======
    private fun showFixAction(text: String) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val pi = PendingIntent.getActivity(this, 11, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = Notification.Builder(this, CH)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Spam Analyzer")
>>>>>>> ceb9980ba2af4edcdf811e5bfbbe1193ce56f153
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

<<<<<<< HEAD
    private fun baseNotif(text: String): Notification {
        return NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Spam Analyzer service running")
            .setContentText(text)
            .setOngoing(true)
            .build()
=======
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
>>>>>>> ceb9980ba2af4edcdf811e5bfbbe1193ce56f153
    }
}
