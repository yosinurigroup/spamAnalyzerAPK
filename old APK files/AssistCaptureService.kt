package com.example.spam_analyzer_v6

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Bitmap
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
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AssistCaptureService : AccessibilityService() {

    companion object {
        private const val TAG = "AssistCaptureService"

        const val ACTION_CAPTURE_NOW = "com.example.spam_analyzer_v6.CAPTURE_NOW"
        const val ACTION_CAPTURED_OK = "com.example.spam_analyzer_v6.CAPTURED_OK"
        const val ACTION_CAPTURED_ERR = "com.example.spam_analyzer_v6.CAPTURED_ERR"
        const val ACTION_REFRESH_KEYWORDS = "com.example.spam_analyzer_v6.REFRESH_KEYWORDS"

        @Volatile internal var instance: AssistCaptureService? = null

        @Volatile private var lastHandledSessionId: String? = null
        @Volatile private var lastSavedAt: Long = 0L
        private const val MIN_GAP_MS = 3000L
        private const val CAPTURE_DELAY_MS = 4000L

        /** ✅ Static keywords only (no API) */
        private val STATIC_KEYWORDS: Set<String> = setOf(
            "unknown",
            "scam alert",
            "likely",
            "telemarketing",
            "spam",
            "suspected spam",
            "suspected"
        )

        fun requestCapture() = requestCapture(null, CAPTURE_DELAY_MS)
        fun requestCapture(sessionId: String?) = requestCapture(sessionId, CAPTURE_DELAY_MS)
        fun requestCapture(sessionId: String?, @Suppress("UNUSED_PARAMETER") delayMs: Long) {
            instance?.scheduleSingle(sessionId, CAPTURE_DELAY_MS)
                ?: Log.w(TAG, "requestCapture: instance null (Accessibility OFF?)")
        }

        fun onCallRinging() { instance?.postRefreshKeywords() }
        fun refreshKeywordsNow() { instance?.postRefreshKeywords() }
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

    private fun joinUrl(base: String, endpoint: String) =
        "${base.trimEnd('/')}/${endpoint.trimStart('/')}"

    private fun currentTzId(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ZoneId.systemDefault().id
        else TimeZone.getDefault().id

    private fun localIsoNow(tzId: String): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ZonedDateTime.now(ZoneId.of(tzId)).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone(tzId)
            sdf.format(java.util.Date())
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
        val th = HandlerThread("assist-cap"); th.start()
        workerHandler = Handler(th.looper)

        // (static) warm-up log
        workerHandler.post { safeRefreshKeywordsBlocking() }
    }

    override fun onUnbind(intent: android.content.Intent?) = run {
        instance = null
        super.onUnbind(intent)
    }
    override fun onDestroy() { instance = null; super.onDestroy() }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { Log.w(TAG, "⚠️ Accessibility interrupted") }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CAPTURE_NOW -> {
                val sid = intent.getStringExtra("sessionId")
                intent.getStringExtra("tz")?.let { tzForNext = it }
                intent.getStringExtra("localISO")?.let { localISOForNext = it }
                scheduleSingle(sid, CAPTURE_DELAY_MS)
            }
            ACTION_REFRESH_KEYWORDS -> postRefreshKeywords()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun postRefreshKeywords() {
        workerHandler.post {
            Log.i(TAG, "🔄 (static) keywords refresh requested — using built-in list")
            safeRefreshKeywordsBlocking()
        }
    }

    private fun scheduleSingle(sessionId: String?, @Suppress("UNUSED_PARAMETER") delayMs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastSavedAt < MIN_GAP_MS) { Log.d(TAG, "Too soon → drop"); return }
        if (sessionId != null && sessionId == lastHandledSessionId) { Log.d(TAG, "Already handled sid=$sessionId → drop"); return }
        if (capInFlight) { Log.d(TAG, "Capture in-flight → drop"); return }

        currentSessionId = sessionId
        savedThisAttempt.set(false)
        capInFlight = true
        Log.i(TAG, "📅 scheduling single capture in ${CAPTURE_DELAY_MS}ms (sid=$sessionId)")
        workerHandler.removeCallbacksAndMessages(null)
        workerHandler.postDelayed({ tryCaptureOnce() }, CAPTURE_DELAY_MS)
    }

    private fun tryCaptureOnce() {
        val tag = "single"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Log.w(TAG, "[$tag] API < 33, not supported"); sendCaptureError(-10); finishAttempt(); return
        }
        Log.i(TAG, "[$tag] tryCapture start")
        try {
            val exec = Executors.newSingleThreadExecutor()
            takeScreenshot(0, exec, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    if (!savedThisAttempt.compareAndSet(false, true)) { Log.d(TAG, "[$tag] ignored"); return }
                    try {
                        val bmp = screenshot.hardwareBuffer?.use { Bitmap.wrapHardwareBuffer(it, screenshot.colorSpace) }
                        if (bmp != null) {
                            ocrThenUpload(bmp)
                            lastSavedAt = System.currentTimeMillis()
                            lastHandledSessionId = currentSessionId
                            Log.i(TAG, "[$tag] ✅ OCR done → proceeding to upload")
                            sendCaptureOk("direct-upload")
                        } else { Log.e(TAG, "[$tag] bitmap null"); sendCaptureError(-2) }
                    } catch (t: Throwable) { Log.e(TAG, "[$tag] exception: ${t.message}", t); sendCaptureError(-3) }
                    finally { finishAttempt() }
                }
                override fun onFailure(errorCode: Int) { Log.w(TAG, "[$tag] ❌ onFailure errorCode=$errorCode"); sendCaptureError(errorCode); finishAttempt() }
            })
        } catch (t: Throwable) { Log.e(TAG, "[$tag] tryCapture exception: ${t.message}", t); sendCaptureError(-99); finishAttempt() }
    }

    private fun finishAttempt() {
        workerHandler.removeCallbacksAndMessages(null)
        capInFlight = false
        currentSessionId = null
    }

    // -------- Token from FlutterSharedPreferences --------
    private fun getBearerToken(): String? = try {
        val sp = applicationContext.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val raw = sp.getString("flutter.auth_token", null)?.trim()
        if (!raw.isNullOrEmpty()) { Log.d(TAG, "Bearer token present (len=${raw.length})"); raw } else { Log.w(TAG, "No token found"); null }
    } catch (t: Throwable) { Log.e(TAG, "Token read error: ${t.message}", t); null }

    // ===================== KEYWORDS (NORMAL ONLY) =====================

    private fun logKw(msg: String) = Log.d(TAG, "[KW] $msg")

    private fun normalizeForMatch(s: String?): String {
        if (s == null) return ""
        val nfkc = try { Normalizer.normalize(s, Normalizer.Form.NFKC) } catch (_: Throwable) { s }
        return nfkc.lowercase().replace(Regex("\\s+"), " ").trim()
    }

    /** whitespace-tolerant exact matcher: "scam alert" -> "scam\\s+alert" */
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

    /** return (hit, matchedValue) using only exact regex (no fuzzy) */
    private fun containsKeyword(fullTextNorm: String, kw: Set<String>): Pair<Boolean, String?> {
        val exactPat = buildKeywordsPattern(kw)
        val m = exactPat.find(fullTextNorm)
        return if (m != null) true to m.value else false to null
    }

    private fun firstHitInLines(result: Text, kw: Set<String>): String? {
        val pat = buildKeywordsPattern(kw)
        for (b in result.textBlocks) for (line in b.lines) {
            val txt = normalizeForMatch(line.text ?: "")
            val m = pat.find(txt)
            if (m != null) return m.value
        }
        return null
    }

    private val staticKeywordsNormalized: Set<String> by lazy {
        STATIC_KEYWORDS.map { normalizeForMatch(it) }.toSet()
    }

    /** For old flow compatibility (no network/caching now) */
    private fun getKeywordsCached(): Set<String> = staticKeywordsNormalized

    private fun safeRefreshKeywordsBlocking() {
        logKw("using static keywords only; nothing to refresh")
    }

    // ===================== OCR + UPLOAD =====================

    private fun ocrThenUpload(bitmap: Bitmap) {
        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(image)
                .addOnSuccessListener { result: Text ->
                    val rawText = result.text ?: ""
                    val fullTextNorm = normalizeForMatch(rawText)

                    Log.i(TAG, "📝 OCR raw (first 400): ${rawText.take(400).replace("\n", "\\n")}")
                    Log.i(TAG, "📝 OCR normalized (first 400): ${fullTextNorm.take(400)}")

                    val keywords = getKeywordsCached()
                    Log.i(TAG, "📦 Keywords in use (${keywords.size}): ${keywords.joinToString(", ")}")

                    val (hit, matched) = containsKeyword(fullTextNorm, keywords)
                    val firstHitLine = if (hit) matched ?: firstHitInLines(result, keywords) else firstHitInLines(result, keywords)

                    if (hit) {
                        Log.i(TAG, "🔎 MATCHED keyword: '${firstHitLine ?: matched}'")
                    } else {
                        Log.i(TAG, "⚪ OCR: no keyword found in ${keywords.size} keywords")
                    }

                    directUploadBitmap(bitmap, hit)
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
        } catch (t: Throwable) { Log.e(TAG, "directUploadBitmap crash: ${t.message}", t) }
    }

    private fun sendCaptureOk(token: String) {
        sendBroadcast(android.content.Intent(ACTION_CAPTURED_OK).apply { putExtra("path", token) })
    }
    private fun sendCaptureError(code: Int) {
        sendBroadcast(android.content.Intent(ACTION_CAPTURED_ERR).apply { putExtra("code", code) })
    }
}
