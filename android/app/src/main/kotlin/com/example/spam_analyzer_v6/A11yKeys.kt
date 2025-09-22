package com.example.spam_analyzer_v6

import android.content.Context
import android.content.Intent

object A11yKeys {
    // Broadcasts for a11y state
    const val ACTION_A11Y_STATE = "com.example.spam_analyzer_v6.ACTION_A11Y_STATE"
    const val EXTRA_BOUND = "extra_bound"

    // Optional init hook – abhi no-op (future me use ho sakta)
    fun init(@Suppress("UNUSED_PARAMETER") ctx: Context) {
        // no-op
    }

    // Helper to broadcast current accessibility “bound” state
    fun sendA11yState(ctx: Context, bound: Boolean) {
        ctx.sendBroadcast(
            Intent(ACTION_A11Y_STATE).putExtra(EXTRA_BOUND, bound)
        )
    }
}
