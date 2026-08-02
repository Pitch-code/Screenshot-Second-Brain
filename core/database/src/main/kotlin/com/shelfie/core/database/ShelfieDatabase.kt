package com.shelfie.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.database.entity.FolderEntity
import com.shelfie.core.database.entity.ScreenshotEntity
import com.shelfie.core.database.entity.ScreenshotTextEntity

@Database(
    entities = [
        ScreenshotEntity::class,
        ScreenshotTextEntity::class,
        FolderEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // Adding nullable columns with defaults is expressible as an auto-migration,
    // so Room generates the SQL from the exported schemas. Never
    // fallbackToDestructiveMigration: silently wiping a user's index is worse
    // than failing loudly.
    //
    // v4 adds the folders table, screenshots.folder_id, and indices on
    // size_bytes and folder_id. All are pure additions, so Room can generate the
    // whole migration and no existing row is rewritten.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
@TypeConverters(ShelfieConverters::class)
abstract class ShelfieDatabase : RoomDatabase() {
    abstract fun screenshotDao(): ScreenshotDao

    companion object {
        const val NAME = "shelfie.db"
    }
}
