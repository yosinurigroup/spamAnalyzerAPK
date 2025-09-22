package com.example.spam_analyzer_v6

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_UNLOCKED -> {
                Log.d("BootReceiver", "🔔 Action: ${intent.action}")
                startServices(context)
            }
        }
    }

    private fun startServices(ctx: Context) {
        try {
            Handler(Looper.getMainLooper()).postDelayed({
                ContextCompat.startForegroundService(ctx, Intent(ctx, CallStateWatcherService::class.java))
                ContextCompat.startForegroundService(ctx, Intent(ctx, AccessibilityWatchdogService::class.java))
                Log.d("BootReceiver", "✅ Services started (CallStateWatcher + Watchdog).")
            }, 1500)
        } catch (e: Exception) {
            Log.e("BootReceiver", "❌ Failed to start services: ${e.message}", e)
        }
    }
}
