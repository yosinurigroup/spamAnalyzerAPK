package com.example.spam_analyzer_v6

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class WatchdogAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Tick → poke service to run health check (optional)
        val svc = Intent(context, AccessibilityWatchdogService::class.java)
            .setAction(AccessibilityWatchdogService.ACTION_TICK)
        try { context.startService(svc) } catch (_: Throwable) {}

        // reschedule again
        schedule(context)
    }

    companion object {
        private const val REQ_CODE = 9901
        private const val INTERVAL_MS = 15 * 60 * 1000L // 15 min: doze-friendly

        fun schedule(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                ctx, REQ_CODE,
                Intent(ctx, WatchdogAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val t = System.currentTimeMillis() + INTERVAL_MS

            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else true

            try {
                if (canExact) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        am.setExact(AlarmManager.RTC_WAKEUP, t, pi)
                    } else {
                        am.set(AlarmManager.RTC_WAKEUP, t, pi)
                    }
                } else {
                    // inexact fallback
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        am.setWindow(AlarmManager.RTC_WAKEUP, t, 60_000L, pi)
                    } else {
                        am.set(AlarmManager.RTC_WAKEUP, t, pi)
                    }
                }
            } catch (_: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    am.setWindow(AlarmManager.RTC_WAKEUP, t, 60_000L, pi)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, t, pi)
                }
            }
        }
    }
}
