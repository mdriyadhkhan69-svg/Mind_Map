package com.example.mindmap.data

import android.content.Context
import androidx.room.Room

/** Shared database construction for process-independent alarm and boot receivers. */
object MindMapReminderStore {
    fun open(context: Context): AppDatabase = Room.databaseBuilder(
        context.applicationContext, AppDatabase::class.java, "mindmap_db"
    ).addMigrations(
        AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7,
        AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10,
        AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13
    ).fallbackToDestructiveMigration().build()
}
