package com.example.mindmap.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lines")
data class LineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: Long = 0,
    val nodeAId: Long? = null,   // null hole eta loose/disconnected end
    val nodeBId: Long? = null,
    val looseAX: Float = 0f,
    val looseAY: Float = 0f,
    val looseBX: Float = 0f,
    val looseBY: Float = 0f,
    val colorArgb: Long = 0xFF64FFDA,
    val strokeWidth: Float = 4f
)
