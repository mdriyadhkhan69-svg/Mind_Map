package com.example.mindmap.data

import android.content.Context

/** Shared default for new queues. Active queues retain their own interval. */
object MindMapReminderSettings {
    private const val Preferences = "mind_map_reminder_settings"
    private const val IntervalKey = "interval_minutes"
    const val DefaultIntervalMinutes = 30

    fun intervalMinutes(context: Context): Int =
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .getInt(IntervalKey, DefaultIntervalMinutes)
            .coerceIn(1, 59 * 60 + 59)

    fun setIntervalMinutes(context: Context, value: Int) {
        context.getSharedPreferences(Preferences, Context.MODE_PRIVATE)
            .edit().putInt(IntervalKey, value.coerceIn(1, 59 * 60 + 59)).apply()
    }
}
