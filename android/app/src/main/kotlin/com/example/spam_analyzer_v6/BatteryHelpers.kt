package com.example.spam_analyzer_v6

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

object BatteryHelpers {
    fun promptIgnoreBatteryOptimizations(ctx: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = ctx.getSystemService(PowerManager::class.java)
                if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(i)
                }
            }
        } catch (t: Throwable) {
            Log.w("BatteryHelpers", "ignore battery prompt failed: ${t.message}")
        }
    }

    fun openAppDetails(ctx: Context) {
        try {
            val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (t: Throwable) {
            Log.w("BatteryHelpers", "open details failed: ${t.message}")
        }
    }
}
