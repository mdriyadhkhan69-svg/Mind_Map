package com.example.mindmap

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.mindmap.ui.screens.OccasionSeparator

class CalendarAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dateKey = intent.getStringExtra("date_key").orEmpty()
        val rawText = intent.getStringExtra("text").orEmpty()
        val text = rawText.split(OccasionSeparator).map { it.trim() }.filter { it.isNotBlank() }.joinToString(", ")
        val eventId = intent.getLongExtra("event_id", 0L)

        val channelId = "calendar_reminders"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Calendar Reminders", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context, eventId.toInt(), openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(dateKey)
            .setContentText(text.ifBlank { "Reminder" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.ifBlank { "Reminder" }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingOpen)
            .build()
        manager.notify(eventId.toInt(), notification)
    }
}