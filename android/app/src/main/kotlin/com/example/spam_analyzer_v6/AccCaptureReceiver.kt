package com.example.spam_analyzer_v6

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AccCaptureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == AssistCaptureService.ACTION_CAPTURE_NOW) {
            Log.i("AccCaptureReceiver", "➡️ ACTION_CAPTURE_NOW received")
            AssistCaptureService.requestCapture()
        }
    }
}
