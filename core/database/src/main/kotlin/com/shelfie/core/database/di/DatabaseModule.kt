package com.shelfie.core.database.di

import android.content.Context
import androidx.room.Room
import com.shelfie.core.database.ShelfieDatabase
import com.shelfie.core.database.dao.ScreenshotDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): ShelfieDatabase = Room.databaseBuilder(
        context = context,
        klass = ShelfieDatabase::class.java,
        name = ShelfieDatabase.NAME,
    )
        // No fallbackToDestructiveMigration: losing a user's index silently is
        // worse than failing loudly. Every schema change gets a real Migration.
        .build()

    @Provides
    fun provideScreenshotDao(database: ShelfieDatabase): ScreenshotDao = database.screenshotDao()
}
