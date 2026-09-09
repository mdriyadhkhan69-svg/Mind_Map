package com.example.mindmap.ui.screens

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Shared with Timer settings so the AI affordance follows the same durable
 * preference mechanism as the rest of the timer controls.
 */
internal object AiFloatingSettingsState {
    private const val PREFS = "timer_settings"
    private const val VISIBLE = "ai_floating_icon_visible"
    private const val X_RATIO = "ai_floating_icon_x_ratio"
    private const val Y_RATIO = "ai_floating_icon_y_ratio"

    private var loaded = false
    var enabled by mutableStateOf(true)
        private set
    var xRatio by mutableFloatStateOf(0.84f)
        private set
    var yRatio by mutableFloatStateOf(0.78f)
        private set

    fun ensureLoaded(context: Context) {
        if (loaded) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(VISIBLE, true)
        xRatio = prefs.getFloat(X_RATIO, 0.84f).coerceIn(0f, 1f)
        yRatio = prefs.getFloat(Y_RATIO, 0.78f).coerceIn(0f, 1f)
        loaded = true
    }

    fun setEnabled(context: Context, value: Boolean) {
        ensureLoaded(context)
        enabled = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(VISIBLE, value).apply()
    }

    fun setPosition(context: Context, x: Float, y: Float) {
        ensureLoaded(context)
        xRatio = x.coerceIn(0f, 1f)
        yRatio = y.coerceIn(0f, 1f)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(X_RATIO, xRatio)
            .putFloat(Y_RATIO, yRatio)
            .apply()
    }
}
