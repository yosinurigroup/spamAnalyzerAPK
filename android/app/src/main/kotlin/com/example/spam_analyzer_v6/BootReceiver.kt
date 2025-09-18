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
                startCallWatcherService(context)
            }
        }
    }

    private fun startCallWatcherService(ctx: Context) {
        try {
            Handler(Looper.getMainLooper()).postDelayed({
                val svcIntent = Intent(ctx, CallStateWatcherService::class.java)
                ContextCompat.startForegroundService(ctx, svcIntent)
                Log.d("BootReceiver", "✅ CallStateWatcherService started.")
            }, 1500)
        } catch (e: Exception) {
            Log.e("BootReceiver", "❌ Failed to start CallStateWatcherService: ${e.message}", e)
        }
    }
}
