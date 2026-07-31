package com.shelfie.core.media

/** A raw MediaStore row, before it becomes a database record. */
data class MediaStoreScreenshot(
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    val relativePath: String,
    /** Epoch seconds. */
    val dateAdded: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
)
