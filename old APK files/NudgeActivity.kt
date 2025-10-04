package com.example.spam_analyzer_v6

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout

class NudgeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        // Create a fully visible small dot (helps compositor render a fresh frame)
        val dot = View(this).apply {
            alpha = 1f
            setBackgroundColor(0xFFFF0000.toInt()) // bright red for debug; change if you want
        }
        val sizePx = (20 * resources.displayMetrics.density).toInt()
        setContentView(FrameLayout(this).apply {
            addView(dot, ViewGroup.LayoutParams(sizePx, sizePx))
        })

        // Keep the activity visible a bit longer than before (1.5 seconds)
        window.decorView.postDelayed({
            finish()
        }, 1500)
    }
}
