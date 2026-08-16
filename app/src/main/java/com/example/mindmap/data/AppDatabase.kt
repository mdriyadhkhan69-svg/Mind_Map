package com.example.mindmap.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NodeEntity::class, SectionEntity::class, LineEntity::class, MediaEntity::class, CalendarEventEntity::class], version = 12)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): NodeDao
    abstract fun sectionDao(): SectionDao
    abstract fun lineDao(): LineDao
    abstract fun mediaDao(): MediaDao
    abstract fun calendarDao(): CalendarEventDao
    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE nodes ADD COLUMN widthScale REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE nodes ADD COLUMN heightScale REAL NOT NULL DEFAULT 1.0")
                database.execSQL("ALTER TABLE lines ADD COLUMN strokeWidth REAL NOT NULL DEFAULT 4.0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE nodes ADD COLUMN textSizeSp REAL NOT NULL DEFAULT 16.0")
                database.execSQL("ALTER TABLE nodes ADD COLUMN textWeight INTEGER NOT NULL DEFAULT 400")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE nodes ADD COLUMN textColorArgb INTEGER")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE nodes ADD COLUMN connectorColorArgb INTEGER")
                database.execSQL("ALTER TABLE nodes ADD COLUMN connectorStrokeWidth REAL NOT NULL DEFAULT 3.0")
                database.execSQL("ALTER TABLE nodes ADD COLUMN isConnectorHidden INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE nodes ADD COLUMN completionLineColorArgb INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sectionId` INTEGER NOT NULL, " +
                        "`nodeId` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`uri` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, " +
                        "FOREIGN KEY(`nodeId`) REFERENCES `nodes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`sectionId`) REFERENCES `sections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_nodeId` ON `media` (`nodeId`)")
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_media_sectionId` ON `media` (`sectionId`)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE media ADD COLUMN rotationDegrees REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `calendar_events` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`dateKey` TEXT NOT NULL, " +
                            "`text` TEXT NOT NULL, " +
                            "`hasTimer` INTEGER NOT NULL, " +
                            "`timerHour` INTEGER NOT NULL, " +
                            "`timerMinute` INTEGER NOT NULL, " +
                            "`isCompleted` INTEGER NOT NULL, " +
                            "`createdAtMillis` INTEGER NOT NULL)"
                )
            }
        }
    }
}
