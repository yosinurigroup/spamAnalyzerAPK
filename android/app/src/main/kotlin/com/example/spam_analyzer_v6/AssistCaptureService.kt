package com.example.spam_analyzer_v6

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MultipartBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.*
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class AssistCaptureService : AccessibilityService() {

    companion object {
        private const val TAG = "AssistCaptureService"

        const val ACTION_CAPTURE_NOW = "com.example.spam_analyzer_v6.CAPTURE_NOW"
        const val ACTION_CAPTURED_OK = "com.example.spam_analyzer_v6.CAPTURED_OK"
        const val ACTION_CAPTURED_ERR = "com.example.spam_analyzer_v6.CAPTURED_ERR"

        // ⚠️ DEV/TEST ONLY: hardcoded JWT; remove or guard with BuildConfig.DEBUG before release
        private const val STATIC_BEARER =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjY4Yzk3MGUxOGRjMTEwYTE3ZGM1NjcwZSIsImVtYWlsIjoiMzA1ODMzMzUyNyIsIm5hbWUiOiIzMDU4MzMzNTI3IiwiaWF0IjoxNzU4MTc4MTYyLCJleHAiOjE3NTg3ODI5NjJ9.hdjokZE6AlN_tRXAZ7Eu3hYKRj-MCh9XscJw1uASyIw"

        @Volatile internal var instance: AssistCaptureService? = null
        @Volatile private var lastHandledSessionId: String? = null
        @Volatile private var lastSavedAt: Long = 0L

        private const val MIN_GAP_MS = 3000L
        private const val CAPTURE_DELAY_MS = 4000L

        fun requestCapture() = requestCapture(null, CAPTURE_DELAY_MS)
        fun requestCapture(sessionId: String?) = requestCapture(sessionId, CAPTURE_DELAY_MS)

        fun requestCapture(sessionId: String?, @Suppress("UNUSED_PARAMETER") delayMs: Long) {
            val svc = instance
            if (svc == null) {
                Log.w(TAG, "requestCapture: instance null (Accessibility OFF?)")
            } else {
                svc.scheduleSingle(sessionId, CAPTURE_DELAY_MS)
            }
        }
    }

    private lateinit var workerHandler: Handler
    @Volatile private var capInFlight = false
    @Volatile private var currentSessionId: String? = null
    private val savedThisAttempt = AtomicBoolean(false)

    @Volatile private var tzForNext: String? = null
    @Volatile private var localISOForNext: String? = null

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private val BASE_URL = "https://spam-analyzer-backend-zr1v.onrender.com"
    private val ENDPOINT = "/api/screenshot/postscreenshot?debug=1"

    private fun joinUrl(base: String, endpoint: String): String {
        val b = base.trimEnd('/')
        val e = endpoint.trimStart('/')
        return "$b/$e"
    }

    private fun currentTzId(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ZoneId.systemDefault().id
        else TimeZone.getDefault().id

    private fun localIsoNow(tzId: String): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ZonedDateTime.now(ZoneId.of(tzId)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone(tzId)
            sdf.format(Date())
        }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "✅ Accessibility service connected")

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }

        val th = HandlerThread("assist-cap")
        th.start()
        workerHandler = Handler(th.looper)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { Log.w(TAG, "⚠️ Accessibility interrupted") }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CAPTURE_NOW) {
            val sid = intent.getStringExtra("sessionId")
            intent.getStringExtra("tz")?.let { tzForNext = it }
            intent.getStringExtra("localISO")?.let { localISOForNext = it }
            scheduleSingle(sid, CAPTURE_DELAY_MS)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun scheduleSingle(sessionId: String?, @Suppress("UNUSED_PARAMETER") delayMs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastSavedAt < MIN_GAP_MS) {
            Log.d(TAG, "scheduleSingle: too soon since last capture → drop"); return
        }
        if (sessionId != null && sessionId == lastHandledSessionId) {
            Log.d(TAG, "scheduleSingle: already handled sid=$sessionId → drop"); return
        }
        if (capInFlight) {
            Log.d(TAG, "scheduleSingle: capture in-flight → drop"); return
        }

        currentSessionId = sessionId
        savedThisAttempt.set(false)
        capInFlight = true
        val effectiveDelay = CAPTURE_DELAY_MS
        Log.i(TAG, "📅 scheduling single capture in ${effectiveDelay}ms (sid=$sessionId)")
        workerHandler.removeCallbacksAndMessages(null)
        workerHandler.postDelayed({ tryCaptureOnce() }, effectiveDelay)
    }

    private fun tryCaptureOnce() {
        val tag = "single"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.w(TAG, "[$tag] API < 33, not supported")
            sendCaptureError(-10); finishAttempt(); return
        }
        Log.i(TAG, "[$tag] tryCapture start")

        try {
            val exec = Executors.newSingleThreadExecutor()
            takeScreenshot(0, exec, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    if (!savedThisAttempt.compareAndSet(false, true)) {
                        Log.d(TAG, "[$tag] success ignored (already handled)"); return
                    }
                    try {
                        val bmp = screenshot.hardwareBuffer?.use {
                            Bitmap.wrapHardwareBuffer(it, screenshot.colorSpace)
                        }
                        if (bmp != null) {
                            ocrThenUpload(bmp)
                            lastSavedAt = System.currentTimeMillis()
                            lastHandledSessionId = currentSessionId
                            Log.i(TAG, "[$tag] ✅ OCR done → proceeding to upload")
                            sendCaptureOk("direct-upload")
                        } else {
                            Log.e(TAG, "[$tag] bitmap null"); sendCaptureError(-2)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "[$tag] exception: ${t.message}", t)
                        sendCaptureError(-3)
                    } finally { finishAttempt() }
                }
                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "[$tag] ❌ onFailure errorCode=$errorCode")
                    sendCaptureError(errorCode)
                    finishAttempt()
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "[$tag] tryCapture exception: ${t.message}", t)
            sendCaptureError(-99)
            finishAttempt()
        }
    }

    private fun finishAttempt() {
        workerHandler.removeCallbacksAndMessages(null)
        capInFlight = false
        currentSessionId = null
    }

    // ✅ Always return the static token for dev/testing
    private fun getBearerToken(): String? {
        return STATIC_BEARER
    }

    private fun containsSpam(text: String): Boolean {
        val re = Regex("""\bspam\b""", RegexOption.IGNORE_CASE)
        return re.containsMatchIn(text)
    }

    private fun ocrThenUpload(bitmap: Bitmap) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { result: Text ->
                    val fullText = result.text ?: ""
                    val isSpam = containsSpam(fullText)

                    var firstHit: String? = null
                    outer@ for (block in result.textBlocks) {
                        for (line in block.lines) {
                            if (containsSpam(line.text)) { firstHit = line.text; break@outer }
                        }
                    }
                    if (isSpam) Log.i(TAG, " OCR FLAG: Found 'Spam'${firstHit?.let { " (line='$it')" } ?: ""}")
                    else Log.i(TAG, "⚪ OCR: 'Spam' not found")

                    directUploadBitmap(bitmap, isSpam)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR failure: ${e.message}", e)
                    directUploadBitmap(bitmap, false)
                }
        } catch (t: Throwable) {
            Log.e(TAG, "ocrThenUpload crash: ${t.message}", t)
            directUploadBitmap(bitmap, false)
        }
    }

    private fun directUploadBitmap(bitmap: Bitmap, isSpam: Boolean) {
        try {
            val tzId = tzForNext ?: currentTzId()
            val localISO = localISOForNext ?: localIsoNow(tzId)
            Log.i(TAG, "🕒 using tz=$tzId localISO=$localISO")

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val bytes = baos.toByteArray()

            val imageReqBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", "screenshot.jpg", imageReqBody)

            // ✅ RAM ONLY (values expected from your app's runtime singletons)
            val toNumber       = (LastOverlayInfo.callTo ?: MainActivity.storedCallTo ?: "").ifBlank { "" }
            val carrier        = (LastOverlayInfo.carrier ?: MainActivity.storedCarrier ?: "").ifBlank { "" }
            val incomingNumber = LastOverlayInfo.incomingNumber ?: "Unknown"
            val callId         = LastOverlayInfo.callId ?: "N/A"
            val timestamp      = LastOverlayInfo.timestamp ?: "N/A"

            val name = toNumber.ifBlank { "Unknown" }

            Log.i(TAG, "Upload → name=$name, toNumber=$toNumber, carrier=$carrier, incoming=$incomingNumber, callId=$callId, ts=$timestamp, isSpam=$isSpam")

            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addPart(imagePart)
                .addFormDataPart("name", name)
                .addFormDataPart("toNumber", toNumber)
                .addFormDataPart("carrier", carrier)
                .addFormDataPart("incomingNumber", incomingNumber)
                .addFormDataPart("extractedNumber", incomingNumber)
                .addFormDataPart("callId", callId)
                .addFormDataPart("timestamp", timestamp)
                .addFormDataPart("tz", tzId)
                .addFormDataPart("localISO", localISO)
                .addFormDataPart("isSpam", isSpam.toString())
                .build()

            val finalUrl = joinUrl(BASE_URL, ENDPOINT)
            Log.i(TAG, "➡️ Uploading to: $finalUrl")

            val builder = Request.Builder()
                .url(finalUrl)
                .post(form)
                .addHeader("Accept", "application/json")
                .addHeader("x-tz", tzId)

            getBearerToken()?.let { token ->
                if (token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer $token")
                    Log.d(TAG, "Auth header set (len=${token.length})")
                }
            }

            httpClient.newCall(builder.build()).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    Log.e(TAG, "⬇️ upload failed: ${e.message}", e)
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val body = it.body?.string()
                        Log.i(TAG, "⬆️ HTTP ${it.code} ${it.message}")
                        Log.i(TAG, "⬆️ URL was: ${call.request().url}")
                        if (!it.isSuccessful) Log.e(TAG, "Body: $body") else Log.i(TAG, "Body: $body")
                    }
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "directUploadBitmap crash: ${t.message}", t)
        }
    }

    private fun sendCaptureOk(token: String) {
        sendBroadcast(android.content.Intent(ACTION_CAPTURED_OK).apply { putExtra("path", token) })
    }
    private fun sendCaptureError(code: Int) {
        sendBroadcast(android.content.Intent(ACTION_CAPTURED_ERR).apply { putExtra("code", code) })
    }
}
