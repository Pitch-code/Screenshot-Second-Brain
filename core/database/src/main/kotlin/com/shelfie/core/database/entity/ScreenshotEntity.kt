package com.shelfie.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shelfie.core.model.IndexState
import com.shelfie.core.model.Screenshot
import com.shelfie.core.model.ScreenshotAction
import com.shelfie.core.model.ScreenshotCategory

/**
 * The main screenshot row. Kept deliberately narrow — the extracted OCR text
 * lives in a separate table so that listing the shelf never pulls kilobytes of
 * text per row into memory.
 */
@Entity(
    tableName = "screenshots",
    indices = [
        Index(value = ["media_store_id"], unique = true),
        // The shelf is always newest-first, and Cleanup filters on state.
        Index(value = ["date_added"]),
        Index(value = ["index_state"]),
        Index(value = ["category"]),
        Index(value = ["is_deleted"]),
    ],
)
data class ScreenshotEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "media_store_id")
    val mediaStoreId: Long,

    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "relative_path")
    val relativePath: String,

    /** Epoch seconds from MediaStore. Drives the newest-first ordering. */
    @ColumnInfo(name = "date_added")
    val dateAdded: Long,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,

    @ColumnInfo(name = "width")
    val width: Int,

    @ColumnInfo(name = "height")
    val height: Int,

    @ColumnInfo(name = "index_state")
    val indexState: IndexState = IndexState.PENDING,

    @ColumnInfo(name = "category")
    val category: ScreenshotCategory = ScreenshotCategory.NOT_SORTED,

    @ColumnInfo(name = "category_confidence")
    val categoryConfidence: Float = 0f,

    @ColumnInfo(name = "primary_value")
    val primaryValue: String? = null,

    @ColumnInfo(name = "primary_action")
    val primaryAction: ScreenshotAction? = null,

    /** Perceptual hash, for duplicate detection in Cleanup. */
    @ColumnInfo(name = "perceptual_hash")
    val perceptualHash: String? = null,

    /** Laplacian variance; low values indicate an unreadable capture. */
    @ColumnInfo(name = "blur_score")
    val blurScore: Float? = null,

    @ColumnInfo(name = "indexed_at")
    val indexedAt: Long? = null,

    /** Number of failed OCR attempts, used to back off retries. */
    @ColumnInfo(name = "attempt_count")
    val attemptCount: Int = 0,

    /** Soft delete, so Cleanup can offer a 30-day recovery window. */
    @ColumnInfo(name = "is_deleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)

fun ScreenshotEntity.toDomain(): Screenshot = Screenshot(
    id = id,
    mediaStoreId = mediaStoreId,
    uri = uri,
    displayName = displayName,
    dateAdded = dateAdded,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    indexState = indexState,
    category = category,
    primaryValue = primaryValue,
    primaryAction = primaryAction,
    indexedAt = indexedAt,
    isDeleted = isDeleted,
)
