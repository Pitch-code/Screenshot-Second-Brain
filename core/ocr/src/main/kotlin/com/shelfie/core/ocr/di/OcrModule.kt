package com.shelfie.core.ocr.di

import android.content.ContentResolver
import android.content.Context
import com.shelfie.core.ocr.MlKitTextRecognitionEngine
import com.shelfie.core.ocr.TextRecognitionEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface OcrModule {

    @Binds
    @Singleton
    fun bindTextRecognitionEngine(impl: MlKitTextRecognitionEngine): TextRecognitionEngine

    companion object {
        @Provides
        @Singleton
        fun provideContentResolver(
            @ApplicationContext context: Context,
        ): ContentResolver = context.contentResolver
    }
}
