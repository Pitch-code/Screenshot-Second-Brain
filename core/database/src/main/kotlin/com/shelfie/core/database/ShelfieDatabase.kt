package com.shelfie.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.database.entity.ScreenshotEntity
import com.shelfie.core.database.entity.ScreenshotTextEntity

@Database(
    entities = [
        ScreenshotEntity::class,
        ScreenshotTextEntity::class,
    ],
    version = 3,
    exportSchema = true,
    // Adding nullable columns with defaults is expressible as an auto-migration,
    // so Room generates the SQL from the exported schemas. Never
    // fallbackToDestructiveMigration: silently wiping a user's index is worse
    // than failing loudly.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(ShelfieConverters::class)
abstract class ShelfieDatabase : RoomDatabase() {
    abstract fun screenshotDao(): ScreenshotDao

    companion object {
        const val NAME = "shelfie.db"
    }
}
