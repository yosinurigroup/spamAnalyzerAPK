package com.example.spam_analyzer_v6

import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.lang.reflect.Method

/**
 * Shizuku wrapper via reflection (no hard compile-time dep).
 */
object ShizukuGrant {
    private const val TAG = "ShizukuGrant"

    // Reflection cache
    private var clazz: Class<*>? = null
    private var mPingBinder: Method? = null
    private var mCheckSelfPerm: Method? = null
    private var mRequestPerm: Method? = null
    private var mNewProcess: Method? = null

    private fun ensureLoaded(): Boolean {
        if (clazz != null) return true
        return try {
            // ✅ correct runtime class/package
            val c = Class.forName("rikka.shizuku.Shizuku")
            clazz = c
            mPingBinder    = c.getMethod("pingBinder")
            mCheckSelfPerm = c.getMethod("checkSelfPermission")
            mRequestPerm   = if (Build.VERSION.SDK_INT >= 23)
                c.getMethod("requestPermission", Int::class.javaPrimitiveType)
            else null
            // ✅ correct signature: newProcess(String[] args, String[] env, java.io.File dir)
            mNewProcess    = c.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                java.io.File::class.java
            )
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Shizuku class not found: ${t.message}")
            false
        }
    }

    /** Is Shizuku service reachable? */
    fun isRunning(): Boolean {
        if (!ensureLoaded()) return false
        return try { mPingBinder?.invoke(null) as? Boolean ?: false } catch (_: Throwable) { false }
    }

    /** Do we already have Shizuku runtime permission? */
    fun hasShizukuPermission(): Boolean {
        if (!ensureLoaded()) return false
        return try {
            val perm = mCheckSelfPerm?.invoke(null) as? Int ?: PackageManager.PERMISSION_DENIED
            perm == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
    }

    /** Ask Shizuku for runtime permission (polling, no listener types). */
    fun requestShizukuPermissionIfNeeded(callback: (granted: Boolean) -> Unit) {
        if (!isRunning()) { callback(false); return }
        if (hasShizukuPermission()) { callback(true); return }

        try {
            if (Build.VERSION.SDK_INT >= 23 && mRequestPerm != null) {
                mRequestPerm!!.invoke(null, 1001)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "requestPermission reflection error: ${t.message}", t)
            callback(false); return
        }

        // Poll up to ~4s
        val handler = Handler(Looper.getMainLooper())
        var tries = 0
        fun tick() {
            if (hasShizukuPermission()) { callback(true); return }
            if (tries++ >= 8) { callback(false); return }
            handler.postDelayed({ tick() }, 500)
        }
        tick()
    }

    /** Run: pm grant <pkg> <perm> via Shizuku */
    private fun runGrantCommand(pkg: String, perm: String): Boolean {
        if (!ensureLoaded()) return false
        return try {
            val args = arrayOf("pm", "grant", pkg, perm)
            // env = null, dir = null
            val p = mNewProcess!!.invoke(null, args, null, null) as Process
            val code = p.waitFor()
            Log.i(TAG, "grant $perm exit=$code")
            code == 0
        } catch (t: Throwable) {
            Log.e(TAG, "grant failed ($perm): ${t.message}", t)
            false
        }
    }

    /** Grant both WRITE_SECURE_SETTINGS + DUMP (true only if both succeed). */
    fun grantWriteSecureAndDump(pkg: String): Boolean {
        if (!isRunning() || !hasShizukuPermission()) return false
        val a = runGrantCommand(pkg, "android.permission.WRITE_SECURE_SETTINGS")
        val b = runGrantCommand(pkg, "android.permission.DUMP")
        return a && b
    }
}
