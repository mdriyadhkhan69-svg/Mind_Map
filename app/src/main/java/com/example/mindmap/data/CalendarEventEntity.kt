package com.example.mindmap.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateKey: String,           // "yyyy-MM-dd"
    val text: String = "",
    val hasTimer: Boolean = false,
    val timerHour: Int = -1,
    val timerMinute: Int = -1,
    val isCompleted: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)