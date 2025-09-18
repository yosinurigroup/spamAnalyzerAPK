package com.example.spam_analyzer_v6

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class CaptureRow(
    val id: Long,
    val path: String,
    val createdAt: Long,
    val uploaded: Int
)

class CaptureStore(ctx: Context): SQLiteOpenHelper(ctx, "captures.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS captures(
              _id INTEGER PRIMARY KEY AUTOINCREMENT,
              path TEXT NOT NULL,
              created_at INTEGER NOT NULL,
              uploaded INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {}

    fun insert(path: String, createdAt: Long, uploaded: Int = 0): Long {
        val cv = ContentValues().apply {
            put("path", path)
            put("created_at", createdAt)
            put("uploaded", uploaded)
        }
        return writableDatabase.insert("captures", null, cv)
    }

    fun markUploaded(id: Long) {
        val cv = ContentValues().apply { put("uploaded", 1) }
        writableDatabase.update("captures", cv, "_id=?", arrayOf(id.toString()))
    }

    fun listAll(): List<CaptureRow> {
        val out = mutableListOf<CaptureRow>()
        val c: Cursor = readableDatabase.query(
            "captures",
            arrayOf("_id", "path", "created_at", "uploaded"),
            null, null, null, null, "created_at DESC"
        )
        c.use {
            while (it.moveToNext()) {
                out.add(
                    CaptureRow(
                        id = it.getLong(0),
                        path = it.getString(1),
                        createdAt = it.getLong(2),
                        uploaded = it.getInt(3)
                    )
                )
            }
        }
        return out
    }

    fun delete(id: Long): Boolean {
        return writableDatabase.delete("captures", "_id=?", arrayOf(id.toString())) > 0
    }
}
