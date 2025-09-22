package com.example.spam_analyzer_v6

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object A11yToggler {

    fun hasSecureWrite(ctx: Context): Boolean {
        return ctx.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun isAccessibilityEnabled(ctx: Context, svc: Class<*>): Boolean {
        val enabled = Settings.Secure.getInt(
            ctx.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0
        ) == 1
        if (!enabled) return false
        val colon = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val entry = "${ctx.packageName}/${svc.name}"
        return colon.split(':').any { it.equals(entry, true) }
    }

    /**
     * Force-enable our AccessibilityService.
     * Works ONLY if WRITE_SECURE_SETTINGS is granted via ADB/Shizuku.
     * Returns true if we wrote settings successfully.
     */
    fun enableMyService(ctx: Context): Boolean {
        return try {
            val cr = ctx.contentResolver
            val entry = "${ctx.packageName}/${AssistCaptureService::class.java.name}"

            val current = Settings.Secure.getString(
                cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()

            // Build a unique colon-separated set including our entry
            val set = current.split(':').filter { it.isNotBlank() }.toMutableSet()
            set.add(entry)
            val joined = set.joinToString(":")

            val ok1 = Settings.Secure.putString(
                cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, joined
            )
            val ok2 = Settings.Secure.putInt(
                cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1
            )
            ok1 && ok2
        } catch (_: SecurityException) {
            false
        } catch (_: Throwable) {
            false
        }
    }
}
