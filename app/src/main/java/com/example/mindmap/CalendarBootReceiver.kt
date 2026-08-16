package com.example.mindmap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.example.mindmap.data.AppDatabase
import com.example.mindmap.data.CalendarAlarmScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class CalendarBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val db = Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "mindmap_db")
            .addMigrations(
                AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6, AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8, AppDatabase.MIGRATION_8_9, AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11, AppDatabase.MIGRATION_11_12
            )
            .fallbackToDestructiveMigration()
            .build()
        runBlocking {
            runCatching { db.calendarDao().getAllEvents().first() }
                .getOrNull()
                ?.filterNot { it.isCompleted }
                ?.forEach { CalendarAlarmScheduler.schedule(context, it) }
            db.close()
        }
    }
}