package com.example.mindmap

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.mindmap.data.MindMapReminderScheduler
import com.example.mindmap.data.MindMapReminderStore
import com.example.mindmap.data.NodeEntity
import kotlinx.coroutines.runBlocking

/**
 * Alarm endpoint. It re-reads Room before showing anything, therefore an old
 * PendingIntent can never resurrect a completed, removed, or moved task.
 */
class MindMapReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.example.mindmap.REMINDER_ALARM") return
        val ownerId = intent.getLongExtra("reminder_owner_id", 0L)
        if (ownerId == 0L) return
        val pendingResult = goAsync()
        Thread {
            runBlocking {
                val db = MindMapReminderStore.open(context)
                try {
                    val nodes = db.dao().getNodesNow()
                    val owner = nodes.firstOrNull { it.id == ownerId } ?: run {
                        MindMapReminderScheduler.cancel(context, ownerId)
                        return@runBlocking
                    }
                    if (!owner.reminderEnabled || !owner.reminderQueueActive) {
                        MindMapReminderScheduler.cancel(context, ownerId)
                        return@runBlocking
                    }
                    // Ignore a stale broadcast after this owner has already been
                    // rescheduled. This is the last line of defence against an
                    // accidental duplicate PendingIntent delivery.
                    if (owner.reminderNextTriggerMillis > System.currentTimeMillis() + 30_000L) return@runBlocking
                    val directIncomplete = nodes.asSequence()
                        .filter { it.parentId == owner.id && !it.isDone }
                        .sortedWith(compareBy<NodeEntity> { it.orderIndex }.thenBy { it.id })
                        .toList()
                    val activeTask = directIncomplete.firstOrNull { it.id == owner.reminderActiveTaskId }
                        ?: directIncomplete.firstOrNull()
                    if (activeTask == null) {
                        db.dao().update(owner.copy(
                            reminderEnabled = false,
                            reminderQueueActive = false,
                            reminderActiveTaskId = null,
                            reminderNextTriggerMillis = 0L
                        ))
                        MindMapReminderScheduler.cancel(context, owner.id)
                        return@runBlocking
                    }
                    // A node change may make a different direct child active between
                    // schedule and delivery; reset that child's escalation cleanly.
                    val isNewTask = activeTask.id != owner.reminderActiveTaskId
                    val activeOwner = if (isNewTask) owner.copy(
                        reminderActiveTaskId = activeTask.id,
                        reminderEscalationMinutes = 0,
                        reminderDeliveryCount = 0
                    ) else owner

                    showNotification(context, activeOwner, activeTask)

                    val interval = activeOwner.reminderIntervalMinutes.coerceAtLeast(1)
                    val reachedMinutes = if (activeOwner.reminderEscalationMinutes < interval) {
                        minOf(interval, activeOwner.reminderEscalationMinutes + 10)
                    } else interval
                    val nextDelayMinutes = if (reachedMinutes < interval) {
                        minOf(10, interval - reachedMinutes).coerceAtLeast(1)
                    } else interval
                    val updated = activeOwner.copy(
                        reminderActiveTaskId = activeTask.id,
                        reminderEscalationMinutes = reachedMinutes,
                        reminderDeliveryCount = activeOwner.reminderDeliveryCount + 1,
                        reminderNextTriggerMillis = System.currentTimeMillis() + nextDelayMinutes * 60_000L,
                        reminderQueueActive = true
                    )
                    db.dao().update(updated)
                    MindMapReminderScheduler.schedule(context, updated)
                } finally {
                    db.close()
                    pendingResult.finish()
                }
            }
        }.start()
    }

    private fun showNotification(context: Context, owner: NodeEntity, task: NodeEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "mind_map_reminders_v1"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val resourceId = context.resources.getIdentifier("noti_notification", "raw", context.packageName)
                .takeIf { it != 0 }
                ?: context.resources.getIdentifier("notification", "raw", context.packageName)
            val sound = resourceId.takeIf { it != 0 }?.let { id ->
                Uri.parse("android.resource://${context.packageName}/$id")
            }
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Mind Map Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders for incomplete Mind Map tasks"
                    setSound(sound, attributes)
                    enableVibration(true)
                }
            )
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_mind_map_reminder", true)
            putExtra("reminder_section_id", owner.sectionId)
            putExtra("reminder_node_id", task.id)
        }
        val requestCode = (owner.id xor task.id).toInt()
        val openPendingIntent = PendingIntent.getActivity(
            context, requestCode, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = reminderText(task.label, owner.reminderDeliveryCount)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Mind Map Reminder")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Open Task", openPendingIntent)
            .setAutoCancel(true)
            .build()
        manager.notify("mind_map_reminder_${owner.id}", requestCode, notification)
    }

    private fun reminderText(label: String, deliveryCount: Int): String = when {
        deliveryCount <= 0 -> "তুমি '$label' টাস্কটা দিয়েছো। এটা কি কমপ্লিট করেছো? কমপ্লিট না করলে কমপ্লিট করো।"
        deliveryCount == 1 -> "তুমি '$label' টাস্কটা দিয়েছো, কিন্তু এখনো কমপ্লিট করোনি। কমপ্লিট করে ফেলো।"
        else -> "তুই '$label' টাস্কটা দিয়েছিস, এখনো কমপ্লিট করিস নাই কেন? তাড়াতাড়ি কমপ্লিট কর।"
    }
}
