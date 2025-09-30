package com.example.spam_analyzer_v6

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream

class AccessibilityWatchdogService : Service() {

    companion object {
        private const val NOTIF_ID = 1011
        const val ACTION_TICK = "com.example.spam_analyzer_v6.WATCHDOG_TICK"
    }

    private lateinit var observer: ScreenshotObserver

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotif())

        observer = ScreenshotObserver(this) { uri, name ->
            processScreenshot(uri, name)
        }

        // Saare images observe karo (hum latest screenshot filter kar lete hain)
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        // optional: boot pe ek dafa latest check
        observer.queryLatest()
    }

    override fun onDestroy() {
        try { contentResolver.unregisterContentObserver(observer) } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == ACTION_TICK) {
            Log.d("Watchdog", "Tick received")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotif(): Notification {
        val chId = "watchdog"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(chId, "Watchdog", NotificationManager.IMPORTANCE_MIN)
                )
        }
        return NotificationCompat.Builder(this, chId)
            .setContentTitle("Spam Analyzer running")
            .setContentText("Watching for screenshots")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .build()
    }

    private fun processScreenshot(uri: Uri, displayName: String) {
        Thread {
            try {
                val dir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SpamAnalyzer")
                dir.mkdirs()
                val out = File(dir, "shot_${System.currentTimeMillis()}_$displayName")
                contentResolver.openInputStream(uri)?.use { ins ->
                    FileOutputStream(out).use { outs -> ins.copyTo(outs) }
                }
                Log.i("Watchdog", "Saved copy: ${out.absolutePath}")

                // 1) UI ko notify
                sendBroadcast(Intent(AssistCaptureService.ACTION_CAPTURED_OK).apply {
                    putExtra("path", out.absolutePath)
                })

                // 2) OCR+upload same pipeline
                try {
                    val svcIntent = Intent(this, AssistCaptureService::class.java).apply {
                        action = AssistCaptureService.ACTION_PROCESS_EXTERNAL_SCREENSHOT
                        putExtra("path", out.absolutePath)
                    }
                    startService(svcIntent)
                } catch (t: Throwable) {
                    Log.e("Watchdog", "startService AssistCaptureService failed: ${t.message}", t)
                }
            } catch (t: Throwable) {
                Log.e("Watchdog", "Copy/notify failed: ${t.message}", t)
                sendBroadcast(Intent(AssistCaptureService.ACTION_CAPTURED_ERR).apply {
                    putExtra("code", -2)
                })
            }
        }.start()
    }
}
