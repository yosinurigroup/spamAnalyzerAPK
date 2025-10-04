// WatchdogAlarmReceiver.kt
package com.example.spam_analyzer_v6

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class WatchdogAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // do your tick work, then reschedule
        schedule(context)
    }

    companion object {
        private const val REQ_CODE = 9901
        private const val INTERVAL_MS = 3 * 60 * 1000L

        fun schedule(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                ctx,
                REQ_CODE,
                Intent(ctx, WatchdogAlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + INTERVAL_MS

            // ✅ Use exact only if allowed; otherwise fall back to inexact
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else {
                true
            }

            try {
                if (canExact) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    } else {
                        am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    }
                } else {
                    // ⏳ Inexact fallback (no special access needed)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, /*windowLength*/ 60_000L, pi)
                    } else {
                        am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    }
                }
            } catch (_: SecurityException) {
                // final safety: never crash; fall back inexact if anything slips through
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 60_000L, pi)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            }
        }
    }
}
