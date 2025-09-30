package com.example.spam_analyzer_v6

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CaptureTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AssistCaptureService.ACTION_CAPTURE_NOW) {
            val sid = intent.getStringExtra("sessionId")
            val delay = intent.getLongExtra("delayMs", 1500L)
            Log.i("CaptureTriggerReceiver", "➡️ ACTION_CAPTURE_NOW received (sid=$sid, delay=${delay}ms)")
            // ✅ ye ab compile hoga (overload add ho gaya)
            AssistCaptureService.requestCapture(sid, delay)
        }
    }
}
