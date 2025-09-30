package com.example.spam_analyzer_v6

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AssistCaptureService : AccessibilityService() {

    companion object {
        private const val TAG = "AssistCaptureService"

        // intents (ALL)
        const val ACTION_CAPTURE_NOW = "com.example.spam_analyzer_v6.CAPTURE_NOW"
        const val ACTION_CAPTURED_OK = "com.example.spam_analyzer_v6.CAPTURED_OK"
        const val ACTION_CAPTURED_ERR = "com.example.spam_analyzer_v6.CAPTURED_ERR"
        const val ACTION_PROCESS_EXTERNAL_SCREENSHOT =
            "com.example.spam_analyzer_v6.PROCESS_EXTERNAL_SCREENSHOT"
        const val ACTION_REFRESH_KEYWORDS = "com.example.spam_analyzer_v6.REFRESH_KEYWORDS"

        @Volatile internal var instance: AssistCaptureService? = null

        private const val MIN_GAP_MS = 3000L
        private const val DEFAULT_DELAY_MS = 4000L
        @Volatile private var lastSavedAt: Long = 0L
        @Volatile private var lastHandledSessionId: String? = null

        fun requestCapture() = requestCapture(null, DEFAULT_DELAY_MS)
        fun requestCapture(sessionId: String?) = requestCapture(sessionId, DEFAULT_DELAY_MS)

        // overload used by CaptureTriggerReceiver (sid, delay)
        fun requestCapture(sessionId: String?, delayMs: Long) {
            instance?.scheduleSingle(sessionId, delayMs)
                ?: Log.w(TAG, "requestCapture: instance null (Accessibility OFF?)")
        }

        /** System ka built-in screenshot (Android 11+) */
        fun requestSystemScreenshot(): Boolean {
            val svc = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                svc.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            } else false
        }

        fun onCallRinging() { instance?.postRefreshKeywords() }
        fun refreshKeywordsNow() { instance?.postRefreshKeywords() }
    }

    // ---- backend ----
    private val BASE_URL = "https://spam-analyzer-backend-zr1v.onrender.com"
    private val ENDPOINT = "/api/screenshot/postscreenshot?debug=1"
    private fun joinUrl(base: String, endpoint: String) =
        "${base.trimEnd('/')}/${endpoint.trimStart('/')}"

    // ---- worker ----
    private lateinit var workerHandler: Handler
    @Volatile private var capInFlight = false
    @Volatile private var currentSessionId: String? = null
    private val savedThisAttempt = AtomicBoolean(false)

    // ---- http ----
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    // toggle to bypass OCR and force upload (debugging)
    private val DEBUG_UPLOAD_WITHOUT_OCR = false

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

        val th = HandlerThread("assist-cap"); th.start()
        workerHandler = Handler(th.looper)
    }

    override fun onUnbind(intent: Intent?) = run {
        instance = null
        super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { Log.w(TAG, "⚠️ Accessibility interrupted") }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CAPTURE_NOW -> {
                val sid = intent.getStringExtra("sessionId")
                val delay = intent.getLongExtra("delayMs", DEFAULT_DELAY_MS)
                scheduleSingle(sid, delay)
            }
            ACTION_PROCESS_EXTERNAL_SCREENSHOT -> {
                val path = intent.getStringExtra("path")
                if (!path.isNullOrBlank()) {
                    workerHandler.post { processExternalScreenshot(path) }
                }
            }
            ACTION_REFRESH_KEYWORDS -> postRefreshKeywords()
        }
        return START_NOT_STICKY
    }

    private fun postRefreshKeywords() {
        Log.d(TAG, "Keywords refresh requested (static only; no-op)")
    }

    private fun scheduleSingle(sessionId: String?, delayMs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastSavedAt < MIN_GAP_MS) { Log.d(TAG, "Too soon → drop"); return }
        if (sessionId != null && sessionId == lastHandledSessionId) { Log.d(TAG, "Duplicate sid → drop"); return }
        if (capInFlight) { Log.d(TAG, "Capture in-flight → drop"); return }

        currentSessionId = sessionId
        savedThisAttempt.set(false)
        capInFlight = true
        Log.i(TAG, "📅 scheduling capture in ${delayMs}ms (sid=$sessionId)")
        workerHandler.removeCallbacksAndMessages(null)
        workerHandler.postDelayed({ tryCaptureOnce() }, delayMs)
    }

    private fun tryCaptureOnce() {
        val tag = "single"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.w(TAG, "[$tag] API < 33, Accessibility.takeScreenshot not supported")
            sendCaptureError(-10); finishAttempt(); return
        }
        Log.i(TAG, "[$tag] tryCapture start")
        try {
            val exec = Executors.newSingleThreadExecutor()
            takeScreenshot(0, exec, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    if (!savedThisAttempt.compareAndSet(false, true)) { Log.d(TAG, "[$tag] ignored"); return }
                    try {
                        // ✅ Hardware → Software conversion WITHOUT Canvas (prevents crash)
                        val bmpHw = screenshot.hardwareBuffer?.use {
                            Bitmap.wrapHardwareBuffer(it, screenshot.colorSpace)
                        }
                        if (bmpHw != null) {
                            val bmpSw = bmpHw.copy(Bitmap.Config.ARGB_8888, /*mutable*/ false)
                            bmpHw.recycle()

                            if (bmpSw != null) {
                                ocrThenUpload(bmpSw)
                                lastSavedAt = System.currentTimeMillis()
                                lastHandledSessionId = currentSessionId
                                Log.i(TAG, "[$tag] ✅ OCR done → upload started")
                                sendCaptureOk("direct-upload")
                            } else {
                                Log.e(TAG, "[$tag] copy() returned null")
                                sendCaptureError(-4)
                            }
                        } else {
                            Log.e(TAG, "[$tag] bitmap null")
                            sendCaptureError(-2)
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

    // ---------- OCR + upload ----------
    private fun ocrThenUpload(bitmap: Bitmap) {
        if (DEBUG_UPLOAD_WITHOUT_OCR) {
            Log.i(TAG, "🧪 DEBUG: skipping OCR → direct upload")
            directUploadBitmap(bitmap, false)
            return
        }
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { result: Text ->
                    val rawText = result.text ?: ""
                    val fullTextNorm = normalizeForMatch(rawText)
                    Log.i(TAG, "📝 OCR (first 300): ${rawText.take(300).replace("\n","\\n")}")
                    val isSpam = containsKeyword(fullTextNorm, STATIC_KEYWORDS_NORM).first
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
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val bytes = baos.toByteArray()

        val imageReqBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val imagePart = MultipartBody.Part.createFormData("image", "screenshot.jpg", imageReqBody)
        // 🔁 Pull from LastOverlayInfo (set by CallReceiver) with sane fallbacks
        val toNumber       = (LastOverlayInfo.callTo ?: MainActivity.storedCallTo ?: "").ifBlank { "" }
        val carrier        = (LastOverlayInfo.carrier ?: MainActivity.storedCarrier ?: "").ifBlank { "" }
        val incomingNumber = (LastOverlayInfo.incomingNumber ?: "").ifBlank { "Unknown" }
        val callId         = (LastOverlayInfo.callId ?: "").ifBlank { "N/A" }
        val timestamp      = (LastOverlayInfo.timestamp ?: "").ifBlank { System.currentTimeMillis().toString() }
        val tzId = currentTzId()
        val localISO = localIsoNow(tzId)
        Log.i(TAG, "Uploading → in=$incomingNumber, to=$toNumber, carrier=$carrier, callId=$callId, ts=$timestamp, spam=$isSpam")
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addPart(imagePart)
            .addFormDataPart("name", if (toNumber.isBlank()) "Unknown" else toNumber)
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
        val builder = Request.Builder()
            .url(finalUrl)
            .post(form)
            .addHeader("Accept", "application/json")
            .addHeader("x-tz", tzId)

        getBearerToken()?.let { token ->
            if (token.isNotBlank()) builder.addHeader("Authorization", "Bearer $token")
        }

        httpClient.newCall(builder.build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                Log.e(TAG, "⬇️ upload failed: ${e.message}", e)
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string()
                    Log.i(TAG, "⬆️ HTTP ${it.code} ${it.message}")
                    if (!it.isSuccessful) Log.e(TAG, "Body: $body") else Log.i(TAG, "Body: $body")
                }
            }
        })
    } catch (t: Throwable) {
        Log.e(TAG, "directUploadBitmap crash: ${t.message}", t)
    }
}


    // External screenshot (from watchdog)
    private fun processExternalScreenshot(path: String) {
        try {
            val bmp = BitmapFactory.decodeFile(path)
            if (bmp == null) {
                Log.e(TAG, "processExternalScreenshot: decode null for $path")
                sendCaptureError(-22)
            } else {
                ocrThenUpload(bmp)
                lastSavedAt = System.currentTimeMillis()
                Log.i(TAG, "processExternalScreenshot: ✅ processed and uploaded")
                sendCaptureOk(path)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "processExternalScreenshot crash: ${t.message}", t)
            sendCaptureError(-23)
        }
    }

    // ---------- helpers ----------
    private fun getBearerToken(): String? = try {
        val sp = applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        sp.getString("flutter.auth_token", null)?.trim()
    } catch (_: Throwable) { null }

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

    private fun normalizeForMatch(s: String?): String {
        if (s == null) return ""
        val nfkc = try { Normalizer.normalize(s, Normalizer.Form.NFKC) } catch (_: Throwable) { s }
        return nfkc.lowercase().replace(Regex("\\s+"), " ").trim()
    }

    private fun buildKeywordsPattern(kw: Set<String>): Regex {
        val escapedAlternatives = kw.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }.map { k ->
            val parts = k.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { Regex.escape(it) }
            if (parts.isEmpty()) null else parts.joinToString("\\s+")
        }.filterNotNull()
        return if (escapedAlternatives.isEmpty()) Regex("""a\A""") else {
            val union = escapedAlternatives.joinToString("|")
            Regex("""\b(?:$union)\b""", RegexOption.IGNORE_CASE)
        }
    }

    private fun containsKeyword(fullTextNorm: String, kw: Set<String>): Pair<Boolean, String?> {
        val m = buildKeywordsPattern(kw).find(fullTextNorm)
        return if (m != null) true to m.value else false to null
    }

    private val STATIC_KEYWORDS = setOf(
        "unknown", "scam alert", "likely", "telemarketing",
        "spam", "suspected spam", "suspected"
    )
    private val STATIC_KEYWORDS_NORM: Set<String> by lazy {
        STATIC_KEYWORDS.map { normalizeForMatch(it) }.toSet()
    }

    private fun sendCaptureOk(token: String) {
        try {
            // avoid StrictMode implicit-broadcast warning
            sendBroadcast(Intent(ACTION_CAPTURED_OK).setPackage(packageName).putExtra("path", token))
        } catch (_: Throwable) {}
    }
    private fun sendCaptureError(code: Int) {
        try {
            // avoid StrictMode implicit-broadcast warning
            sendBroadcast(Intent(ACTION_CAPTURED_ERR).setPackage(packageName).putExtra("code", code))
        } catch (_: Throwable) {}
    }
}
