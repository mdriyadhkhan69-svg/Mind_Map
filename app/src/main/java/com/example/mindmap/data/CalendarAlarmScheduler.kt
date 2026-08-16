package com.example.mindmap.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Locale

object CalendarAlarmScheduler {
    private fun pendingIntent(context: Context, event: CalendarEventEntity): PendingIntent {
        val intent = Intent(context, com.example.mindmap.CalendarAlarmReceiver::class.java).apply {
            putExtra("event_id", event.id)
            putExtra("date_key", event.dateKey)
            putExtra("text", event.text)
        }
        return PendingIntent.getBroadcast(
            context, event.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // Timer দেওয়া থাকলে ঠিক সেই সময়ে; শুধু text থাকলে সকাল ৯টায় reminder
    fun triggerAtMillis(event: CalendarEventEntity): Long? {
        if (event.text.isBlank() && !event.hasTimer) return null
        val hour = if (event.hasTimer) event.timerHour else 9
        val minute = if (event.hasTimer) event.timerMinute else 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = runCatching { sdf.parse(event.dateKey) }.getOrNull() ?: return null
        val cal = java.util.Calendar.getInstance()
        cal.time = date
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour)
        cal.set(java.util.Calendar.MINUTE, minute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun schedule(context: Context, event: CalendarEventEntity) {
        if (event.isCompleted) { cancel(context, event); return }
        val triggerAt = triggerAtMillis(event) ?: return
        if (triggerAt <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        val pi = pendingIntent(context, event)
        runCatching {
            if (canExact) alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            else alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context, event: CalendarEventEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { alarmManager.cancel(pendingIntent(context, event)) }
    }
}