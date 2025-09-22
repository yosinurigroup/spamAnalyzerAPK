package com.example.spam_analyzer_v6

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat

class WatchdogAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        // Ensure watchdog is alive
        ContextCompat.startForegroundService(c, Intent(c, AccessibilityWatchdogService::class.java))
        // Re-schedule
        schedule(c)
    }

    companion object {
        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = PendingIntent.getBroadcast(
                context, 1001,
                Intent(context, WatchdogAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = SystemClock.elapsedRealtime() + 15 * 60 * 1000L  // every 15 min
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        }
    }
}
