package com.example.spam_analyzer_v6

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File

class MainActivity : FlutterActivity() {

    private val CHANNEL = "com.example.call_detector/channel"

    companion object {
        var storedCallTo: String? = null
        var storedCarrier: String? = null
        private var lastTriggerAt = 0L
    }

    private var channel: MethodChannel? = null
    private var captureResultReceiver: BroadcastReceiver? = null
    private var startedServicesOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Receiver to bubble accessibility capture results back to Flutter
        captureResultReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    AssistCaptureService.ACTION_CAPTURED_OK -> {
                        val p = intent.getStringExtra("path")
                        Log.i("MainActivity", "📸 Accessibility result saved: $p")
                        try { channel?.invokeMethod("onAccessibilityScreenshotSaved", p) } catch (_: Throwable) {}
                    }
                    AssistCaptureService.ACTION_CAPTURED_ERR -> {
                        val code = intent.getIntExtra("code", -1)
                        Log.w("MainActivity", "⚠️ Accessibility screenshot failed, code=$code")
                        try { channel?.invokeMethod("onAccessibilityScreenshotFailed", code) } catch (_: Throwable) {}
                        Toast.makeText(this@MainActivity, "Screenshot failed code=$code", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val f = IntentFilter().apply {
            addAction(AssistCaptureService.ACTION_CAPTURED_OK)
            addAction(AssistCaptureService.ACTION_CAPTURED_ERR)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(captureResultReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(captureResultReceiver, f)
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        // start foreground services after activity is visible (safer on some ROMs)
        if (!startedServicesOnce) {
            startedServicesOnce = true
            safeStartServices()
        }
    }

    private fun safeStartServices() {
        try {
            ContextCompat.startForegroundService(
                this, Intent(this, CallStateWatcherService::class.java)
            )
        } catch (t: Throwable) {
            Log.e("MainActivity", "CallStateWatcherService start failed: ${t.message}", t)
        }
        try {
            ContextCompat.startForegroundService(
                this, Intent(this, AccessibilityWatchdogService::class.java)
            )
        } catch (t: Throwable) {
            Log.e("MainActivity", "AccessibilityWatchdogService start failed: ${t.message}", t)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(captureResultReceiver) } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        CallReceiver.channel = channel

        channel?.setMethodCallHandler { call, result ->
            when (call.method) {

                // --------- Call state ----------
                "getCallState" -> result.success(getCallStateSafe())
                "getIncomingNumber" -> result.success(CallReceiver.latestIncomingNumber)

                // --------- Accessibility ----------
                "isAccessibilityEnabled" -> result.success(isAccessibilityOn())
                "openAccessibilitySettings" -> {
                    try {
                        startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                        result.success(true)
                    } catch (_: Throwable) { result.success(false) }
                }

                // --------- Overlay ----------
                "ensureOverlayPermission" -> { ensureOverlayPermission(); result.success(true) }

                // --------- Exact alarm settings (optional) ----------
                "openExactAlarmSettings" -> {
                    try {
                        if (Build.VERSION.SDK_INT >= 31) {
                            startActivity(
                                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        result.success(true)
                    } catch (_: Throwable) { result.success(false) }
                }

                // --------- Capture ----------
                "requestScreenshotPermission" -> {
                    if (!isAccessibilityOn()) {
                        try {
                            startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: Throwable) {}
                    }
                    result.success(true)
                }
                "isProjectionReady" -> result.success(isAccessibilityOn())
                "triggerAccessibilityCapture" -> {
                    val now = System.currentTimeMillis()
                    if (now - lastTriggerAt < 800) { result.success(false); return@setMethodCallHandler }
                    lastTriggerAt = now

                    val ok = triggerAccCaptureSmart()
                    if (ok) Log.i("MainActivity", "capture trigger sent")
                    result.success(ok)
                }

                // --------- Screenshots ----------
                "getScreenshots" -> result.success(
                    scanScreenshotDir().mapIndexed { idx, file ->
                        mapOf(
                            "id" to idx.toLong(),
                            "path" to file.absolutePath,
                            "createdAt" to file.lastModified(),
                            "uploaded" to false
                        )
                    }
                )
                "deleteScreenshot" -> {
                    val args = call.arguments as? Map<*, *> ?: emptyMap<String, Any>()
                    val path = args["path"] as? String ?: ""
                    val okFs = try {
                        if (path.startsWith("content://"))
                            contentResolver.delete(Uri.parse(path), null, null) >= 1
                        else
                            File(path).delete()
                    } catch (_: Throwable) { false }
                    result.success(okFs)
                }

                // --------- Local call info sync ----------
                "setLocalCallInfo" -> {
                    val args = call.arguments as Map<*, *>
                    storedCallTo = args["callTo"] as? String
                    storedCarrier = args["carrier"] as? String
                    Log.d("MainActivity", "Stored callTo: $storedCallTo, carrier: $storedCarrier")
                    result.success(true)
                }
                "getLocalCallInfo" -> {
                    val info = mapOf(
                        "callTo" to (storedCallTo ?: "Personal"),
                        "carrier" to (storedCarrier ?: "defaultCarrier")
                    )
                    Log.d("MainActivity", "Retrieved callTo: ${info["callTo"]}, carrier: ${info["carrier"]}")
                    result.success(info)
                }

                // --------- Shizuku helpers ----------
                "shizukuStatus" -> {
                    val running = ShizukuGrant.isRunning()
                    val authorized = ShizukuGrant.hasShizukuPermission()
                    result.success(mapOf("running" to running, "authorized" to authorized))
                }
                "shizukuGrantSelf" -> {
                    ShizukuGrant.requestShizukuPermissionIfNeeded { _ ->
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            val done = if (ShizukuGrant.hasShizukuPermission())
                                ShizukuGrant.grantWriteSecureAndDump(packageName)
                            else false
                            result.success(done)
                        }, 1500)
                    }
                }
                "openShizuku" -> {
                    try {
                        var li = packageManager.getLaunchIntentForPackage("moe.shizuku.manager")
                        if (li == null) li = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (li != null) {
                            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(li)
                            result.success(true)
                        } else {
                            val ps = Intent(Intent.ACTION_VIEW).apply {
                                data = Uri.parse("market://details?id=moe.shizuku.manager")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(ps)
                            result.success(true)
                        }
                    } catch (_: Throwable) { result.success(false) }
                }
                "enableA11yViaShizuku" -> {
                    val ok = A11yToggler.enableMyService(this)
                    result.success(ok)
                }

                else -> result.notImplemented()
            }
        }
    }

    // ---------- helpers ----------

    private fun triggerAccCaptureSmart(): Boolean {
        return try {
            val svc = AssistCaptureService.instance
            if (svc != null) {
                AssistCaptureService.requestCapture()
                Toast.makeText(this, "Capturing (direct)…", Toast.LENGTH_SHORT).show()
                true
            } else {
                val action = AssistCaptureService.ACTION_CAPTURE_NOW
                // explicit to our receiver
                sendBroadcast(Intent(action).apply {
                    setClassName(packageName, "com.example.spam_analyzer_v6.CaptureTriggerReceiver")
                })
                // fallback: package-scoped
                sendBroadcast(Intent(action).setPackage(packageName))
                Toast.makeText(this, "Capturing (broadcast)…", Toast.LENGTH_SHORT).show()
                true
            }
        } catch (t: Throwable) {
            Log.e("MainActivity", "broadcast/trigger error: ${t.message}", t)
            false
        }
    }

    private fun hasReadPhoneState(): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_PHONE_STATE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun getCallStateSafe(): String {
        return try { if (!hasReadPhoneState()) "UNKNOWN" else getCallState() }
        catch (_: Throwable) { "UNKNOWN" }
    }

    private fun getCallState(): String {
        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        return when (tm.callState) {
            TelephonyManager.CALL_STATE_RINGING -> "RINGING"
            TelephonyManager.CALL_STATE_OFFHOOK -> "OFFHOOK"
            TelephonyManager.CALL_STATE_IDLE -> "IDLE"
            else -> "UNKNOWN"
        }
    }

    private fun ensureOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun isAccessibilityOn(): Boolean {
        return try {
            val enabled = Settings.Secure.getString(
                contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val cn = ComponentName(this, AssistCaptureService::class.java)
            val f1 = cn.flattenToShortString()
            val f2 = cn.flattenToString()
            if (enabled.split(':').any { s -> s.equals(f1, true) || s.equals(f2, true) }) return true

            val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val list = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            list.any { svc ->
                val si = svc.resolveInfo?.serviceInfo
                si?.packageName == packageName && si?.name == AssistCaptureService::class.java.name
            }
        } catch (_: Throwable) { false }
    }

    private fun scanScreenshotDir(): List<File> {
        val dir = File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "SpamAnalyzer")
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f ->
            f.isFile && (f.name.endsWith(".png", true) || f.name.endsWith(".jpg", true))
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
