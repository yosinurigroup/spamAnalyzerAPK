package com.example.spam_analyzer_v6

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log

class ScreenshotObserver(
    private val ctx: Context,
    private val onNew: (Uri, String) -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val baseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    override fun onChange(selfChange: Boolean, changed: Uri?) {
        super.onChange(selfChange, changed)
        queryLatest()
    }

    fun queryLatest() {
        // ✅ LIMIT ko query param se do (no "LIMIT 1" in sortOrder)
        val uri = baseUri.buildUpon()
            .appendQueryParameter("limit", "1")
            .build()

        val proj = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        // API < 29 par RELATIVE_PATH column nahi hota → name based fallback
        val (sel, args) = if (Build.VERSION.SDK_INT >= 29) {
            Pair(
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR " +
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?",
                arrayOf("DCIM/Screenshots%", "Pictures/Screenshots%")
            )
        } else {
            Pair(
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("%Screenshot%")
            )
        }

        try {
            ctx.contentResolver.query(uri, proj, sel, args, sort)?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val name = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: "screenshot.jpg"
                    val u = ContentUris.withAppendedId(baseUri, id)
                    Log.i("ScreenshotObserver", "New screenshot: $name @ $u")
                    onNew(u, name)
                }
            }
        } catch (t: Throwable) {
            Log.w("ScreenshotObserver", "queryLatest failed: ${t.message}")
        }
    }
}
