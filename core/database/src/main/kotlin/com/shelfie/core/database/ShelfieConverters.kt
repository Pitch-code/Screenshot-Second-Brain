package com.shelfie.core.database

import androidx.room.TypeConverter
import com.shelfie.core.model.IndexState
import com.shelfie.core.model.ScreenshotAction
import com.shelfie.core.model.ScreenshotCategory

/**
 * Enums are stored as their names rather than ordinals on purpose: reordering an
 * enum must never silently re-map existing user data.
 *
 * Unknown values decode to a safe default so that a downgrade, or a row written
 * by a newer version, can never crash the app.
 */
class ShelfieConverters {

    @TypeConverter
    fun fromIndexState(value: IndexState): String = value.name

    @TypeConverter
    fun toIndexState(value: String): IndexState =
        runCatching { IndexState.valueOf(value) }.getOrDefault(IndexState.PENDING)

    @TypeConverter
    fun fromCategory(value: ScreenshotCategory): String = value.name

    @TypeConverter
    fun toCategory(value: String): ScreenshotCategory =
        runCatching { ScreenshotCategory.valueOf(value) }
            .getOrDefault(ScreenshotCategory.NOT_SORTED)

    @TypeConverter
    fun fromAction(value: ScreenshotAction?): String? = value?.name

    @TypeConverter
    fun toAction(value: String?): ScreenshotAction? =
        value?.let { runCatching { ScreenshotAction.valueOf(it) }.getOrNull() }
}
