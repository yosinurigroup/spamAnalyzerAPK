package com.example.spam_analyzer_v6

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.MethodChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class CallReceiver : BroadcastReceiver() {
    companion object {
        var channel: MethodChannel? = null
        var latestIncomingNumber: String? = null

        private const val TAG = "CallReceiver"
        private const val MIN_GAP_MS = 7000L
        @Volatile private var lastRingAt = 0L
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val pr = goAsync()
        try {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val rawNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            Log.d(TAG, "State: $state, Number: $rawNumber")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (!rawNumber.isNullOrEmpty()) latestIncomingNumber = rawNumber
                    val displayNumber = rawNumber ?: latestIncomingNumber ?: "Unknown"

                    // Flutter stream
                    try { channel?.invokeMethod("incomingCall", displayNumber) } catch (_: Throwable) {}

                    // Build overlay meta used by AssistCaptureService
                    val callId = UUID.randomUUID().toString()
                    val formattedTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    val callTo = MainActivity.storedCallTo ?: "Personal"
                    val carrier = MainActivity.storedCarrier ?: "defaultCarrier"

                    LastOverlayInfo.set(
                        incomingNumber = displayNumber,
                        callId = callId,
                        timestamp = formattedTime,
                        callTo = callTo,
                        carrier = carrier
                    )

                    // ✅ NEW: force-refresh keywords as soon as phone rings
                    // Try direct helper (if service instance is already alive)…
                    try {
                        AssistCaptureService.onCallRinging()
                    } catch (_: Throwable) {
                        // ignore
                    }
                    // …and also send an explicit startService with action to be safe.
                    try {
                        val svcIntent = Intent(context.applicationContext, AssistCaptureService::class.java).apply {
                            action = AssistCaptureService.ACTION_REFRESH_KEYWORDS
                        }
                        context.startService(svcIntent)
                        Log.i(TAG, "RINGING → requested keywords refresh via service action")
                    } catch (t: Throwable) {
                        Log.w(TAG, "Could not start AssistCaptureService for keywords refresh: ${t.message}")
                    }

                    // (optional) show overlay
                    val overlayIntent = Intent(context.applicationContext, CallOverlayService::class.java).apply {
                        putExtra("incomingNumber", displayNumber)
                        putExtra("callId", callId)
                        putExtra("timestamp", formattedTime)
                        putExtra("callTo", callTo)
                        putExtra("carrier", carrier)
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(context, overlayIntent)
                        } else {
                            context.startService(overlayIntent)
                        }
                    } catch (_: Throwable) {}

                    // Throttle duplicate RINGING
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastRingAt < MIN_GAP_MS) {
                        Log.d(TAG, "RINGING duplicate throttle → skip")
                        return
                    }
                    lastRingAt = now

                    // Schedule screenshot capture after a short delay
                    val sessionId = "${displayNumber}-${now}"
                    context.sendBroadcast(
                        Intent(AssistCaptureService.ACTION_CAPTURE_NOW).apply {
                            setClassName(context.packageName, "com.example.spam_analyzer_v6.CaptureTriggerReceiver")
                            putExtra("sessionId", sessionId)
                            putExtra("delayMs", 1500L)
                        }
                    )
                    Log.d(TAG, "RINGING → requested accessibility capture (sid=$sessionId, +1500ms)")
                }

                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    // no-op for now
                }

                TelephonyManager.EXTRA_STATE_IDLE -> {
                    try { channel?.invokeMethod("callEnded", "") } catch (_: Throwable) {}
                    try { context.stopService(Intent(context, CallOverlayService::class.java)) } catch (_: Throwable) {}
                    latestIncomingNumber = null
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "onReceive crash", t)
        } finally {
            pr.finish()
        }
    }
}
