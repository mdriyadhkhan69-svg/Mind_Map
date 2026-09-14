package com.example.mindmap.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nodes",
    foreignKeys = [
        ForeignKey(
            entity = NodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("parentId"), Index("sectionId")]
)
data class NodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: Long = 0,
    val parentId: Long? = null,
    val label: String,
    val orderIndex: Int = 0,
    val x: Float,
    val y: Float,
    val isExpanded: Boolean = false,
    val isDone: Boolean = false,
    val colorArgb: Long? = null,
    val widthScale: Float = 1f,
    val heightScale: Float = 1f,
    val textSizeSp: Float = 16f,
    val textWeight: Int = 400,
    val textColorArgb: Long? = null,
    val connectorColorArgb: Long? = null,
    val connectorStrokeWidth: Float = 3f,
    val isConnectorHidden: Boolean = false,
    val completionLineColorArgb: Long? = null,
    // Reminder state belongs to the box that owns the queue. The queued task
    // itself always remains an ordinary node, so completion still has one
    // authoritative source: isDone.
    val reminderEnabled: Boolean = false,
    val reminderActiveTaskId: Long? = null,
    val reminderEscalationMinutes: Int = 0,
    val reminderDeliveryCount: Int = 0,
    val reminderIntervalMinutes: Int = 30,
    val reminderNextTriggerMillis: Long = 0L,
    val reminderQueueActive: Boolean = false
)
