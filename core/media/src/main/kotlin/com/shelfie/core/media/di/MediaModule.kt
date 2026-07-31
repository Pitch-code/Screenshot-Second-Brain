package com.shelfie.core.media.di

import com.shelfie.core.classify.EntityExtractor
import com.shelfie.core.classify.ScreenshotClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MediaModule {

    @Provides
    @Singleton
    fun provideScreenshotClassifier(): ScreenshotClassifier =
        ScreenshotClassifier(extractor = EntityExtractor)
}
