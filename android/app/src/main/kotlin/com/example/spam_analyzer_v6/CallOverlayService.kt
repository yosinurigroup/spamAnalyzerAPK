package com.example.spam_analyzer_v6

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.util.* // TimeZone, Date, Locale
import java.text.SimpleDateFormat

// java.time for API 26+
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CallOverlayService : Service() {

    companion object {
        private const val OVERLAY_CHANNEL_ID = "OverlayServiceChannel"
        private const val OVERLAY_NOTIFICATION_ID = 2001
        private var lastShotAt = 0L
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    // ─────────────────────────── Time helpers (device TZ) ───────────────────────────
    private fun currentTzId(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ZoneId.systemDefault().id                 // IANA tz e.g. "America/New_York"
        } else {
            TimeZone.getDefault().id                  // e.g. "Asia/Karachi"
        }
    }

    private fun localIsoNow(tzId: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val zdt = ZonedDateTime.now(ZoneId.of(tzId))
            zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) // 2025-09-09T04:12:00-04:00
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone(tzId)
            sdf.format(Date())
        }
    }
    // ────────────────────────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(OVERLAY_CHANNEL_ID, "Overlay Service", NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(
            OVERLAY_NOTIFICATION_ID,
            NotificationCompat.Builder(this, OVERLAY_CHANNEL_ID)
                .setOngoing(true)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Caller overlay active")
                .setContentText("Showing caller info overlay")
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.e("CallOverlayService", "Overlay permission missing; not showing overlay")
            stopSelf(); return START_NOT_STICKY
        }

        val number = intent?.getStringExtra("incomingNumber") ?: "Unknown"
        val callId = intent?.getStringExtra("callId") ?: "N/A"

        // ✅ device/emulator timezone & local time (ISO with offset)
        val tzId = currentTzId()
        val localISO = localIsoNow(tzId)

        // if caller provided timestamp use it, else our localISO (device time)
        val timestamp = intent?.getStringExtra("timestamp") ?: localISO
        val callTo = intent?.getStringExtra("callTo") ?: "Personal"
        val carrier = intent?.getStringExtra("carrier") ?: "defaultCarrier"

        Handler(mainLooper).postDelayed({
            // show overlay with device-local time
            showOverlay(number, callId, timestamp, callTo, carrier)

            // trigger capture and PASS tz/time so your uploader can send to API
            triggerScreenshotCapture(tzId, localISO)
        }, 250)

        return START_NOT_STICKY
    }

    private fun showOverlay(number: String, callId: String, timestamp: String, callTo: String, carrier: String) {
        overlayView?.let { v -> try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } catch (_: Exception) {} }
        overlayView = null

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_call_box, null)

        overlayView?.findViewById<TextView>(R.id.caller_number)?.text = "📞 $number"
        overlayView?.findViewById<TextView>(R.id.call_id)?.text = "ID: $callId"
        overlayView?.findViewById<TextView>(R.id.call_time)?.text = "Time: $timestamp" // already local ISO
        overlayView?.findViewById<TextView>(R.id.call_to)?.text = "To: $callTo"
        overlayView?.findViewById<TextView>(R.id.carrier)?.text = "Carrier: $carrier"
        overlayView?.findViewById<ImageView>(R.id.close_button)?.setOnClickListener { removeOverlayAndStop() }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON),
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        try { windowManager?.addView(overlayView, params) } catch (t: Throwable) {
            Log.e("CallOverlayService", "addView failed", t)
        }
    }

    // ⬇️ pass tz/time to capture/uploader via Intent extras (no other logic touched)
    private fun triggerScreenshotCapture(tzId: String, localISO: String) {
        val now = System.currentTimeMillis()
        if (now - lastShotAt < 2000) return
        lastShotAt = now

        Handler(mainLooper).postDelayed({
            try {
                val svc = AssistCaptureService.instance
                if (svc != null) {
                    // If your AssistCaptureService has an API to receive extras, set them there as well.
                    AssistCaptureService.requestCapture()
                    // (Optional) you can set static fields/singleton to read tz/time, if needed.
                    Toast.makeText(this, "Capturing (direct)…", Toast.LENGTH_SHORT).show()
                    // Also broadcast for listeners that rely on Intent extras:
                    sendBroadcast(Intent(AssistCaptureService.ACTION_CAPTURE_NOW).apply {
                        setClassName(packageName, "com.example.spam_analyzer_v6.CaptureTriggerReceiver")
                        putExtra("tz", tzId)           // 👈 device timezone
                        putExtra("localISO", localISO)  // 👈 device local time
                    })
                    return@postDelayed
                }

                val action = AssistCaptureService.ACTION_CAPTURE_NOW
                // explicit receiver
                sendBroadcast(Intent(action).apply {
                    setClassName(packageName, "com.example.spam_analyzer_v6.CaptureTriggerReceiver")
                    putExtra("tz", tzId)
                    putExtra("localISO", localISO)
                })
                // package-scoped
                sendBroadcast(Intent(action).setPackage(packageName).apply {
                    putExtra("tz", tzId)
                    putExtra("localISO", localISO)
                })

                Toast.makeText(this, "Capturing (broadcast)…", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Log.e("CallOverlayService", "Failed to trigger screenshot: ${t.message}", t)
            }
        }, 900)
    }

    private fun removeOverlayAndStop() {
        overlayView?.let { v -> try { windowManager?.removeView(v) } catch (_: Exception) {} }
        overlayView = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(Service.STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        try { removeOverlayAndStop() } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
