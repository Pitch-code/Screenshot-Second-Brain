package com.shelfie.core.database

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
    version = 1,
    exportSchema = true,
)
@TypeConverters(ShelfieConverters::class)
abstract class ShelfieDatabase : RoomDatabase() {
    abstract fun screenshotDao(): ScreenshotDao

    companion object {
        const val NAME = "shelfie.db"
    }
}
