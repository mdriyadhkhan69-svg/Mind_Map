package com.example.mindmap.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.mindmap.MindMapReminderReceiver

/** One exact alarm per reminder-owner node; rescheduling replaces the old one. */
object MindMapReminderScheduler {
    private fun requestCode(ownerNodeId: Long): Int = (ownerNodeId xor (ownerNodeId ushr 32)).toInt()

    private fun pendingIntent(context: Context, ownerNodeId: Long): PendingIntent {
        val intent = Intent(context, MindMapReminderReceiver::class.java).apply {
            action = "com.example.mindmap.REMINDER_ALARM"
            putExtra("reminder_owner_id", ownerNodeId)
        }
        return PendingIntent.getBroadcast(
            context, requestCode(ownerNodeId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, owner: NodeEntity) {
        if (!owner.reminderEnabled || !owner.reminderQueueActive || owner.reminderNextTriggerMillis <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = pendingIntent(context, owner.id)
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        runCatching {
            if (exactAllowed) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, owner.reminderNextTriggerMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, owner.reminderNextTriggerMillis, pending)
            }
        }
    }

    fun cancel(context: Context, ownerNodeId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { alarmManager.cancel(pendingIntent(context, ownerNodeId)) }
    }
}
