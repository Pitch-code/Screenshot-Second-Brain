package com.shelfie.core.media

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.database.entity.ScreenshotEntity
import com.shelfie.core.model.ScreenshotSource
import com.shelfie.core.ocr.BitmapDecoder
import com.shelfie.core.ocr.ThumbnailStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Imports images chosen through the system photo picker.
 *
 * This is the Limited Mode path, which Google Play's Photo and Video Permissions
 * policy requires: an app that asks for broad media access must still be useful
 * when the user declines. Everything downstream — search, categories, actions —
 * works identically on picker-imported screenshots.
 *
 * Each image is copied locally before indexing, because picker grants are
 * temporary and cannot be persisted.
 */
@Singleton
class PickerImporter @Inject constructor(
    private val dao: ScreenshotDao,
    private val contentResolver: ContentResolver,
    private val thumbnailStore: ThumbnailStore,
    private val bitmapDecoder: BitmapDecoder,
    private val indexer: ScreenshotIndexer,
    private val repository: ScreenshotRepository,
) {

    /**
     * Imports and indexes [uris]. Returns how many were successfully added.
     *
     * Deliberately sequential: the OCR engine already serialises internally, and
     * importing one at a time keeps peak memory flat regardless of how many
     * images the user selected.
     */
    suspend fun import(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) return@withContext 0

        val rules = runCatching { repository.currentRules() }.getOrDefault(emptyList())
        var imported = 0

        for (uri in uris) {
            val entity = buildEntity(uri) ?: continue

            val rowId = runCatching { dao.upsert(entity) }.getOrNull() ?: continue
            // upsert returns -1 when the row already existed; look it up so a
            // re-import updates rather than duplicating.
            val id = if (rowId > 0) rowId else continue

            runCatching { indexer.index(entity.copy(id = id), rules) }
            imported++
        }
        imported
    }

    private suspend fun buildEntity(uri: Uri): ScreenshotEntity? {
        // Picker URIs are opaque, so derive a stable key from the URI itself.
        val key = stableKey(uri)

        val localPath = thumbnailStore.save(key, uri) ?: return null
        val bounds = bitmapDecoder.readBounds(uri)
        val (name, size) = queryNameAndSize(uri)

        return ScreenshotEntity(
            mediaStoreId = key.toLong(),
            // Point at the durable copy: the picker grant will not outlive this
            // process, so storing the original URI as the display source would
            // leave a blank tile after the next launch.
            uri = localPath,
            displayName = name ?: "Picked image",
            relativePath = "",
            dateAdded = System.currentTimeMillis() / 1000,
            sizeBytes = size ?: 0L,
            width = bounds?.first ?: 0,
            height = bounds?.second ?: 0,
            source = ScreenshotSource.PICKER,
            localThumbnailPath = localPath,
        )
    }

    /**
     * A stable positive id derived from the URI, so re-picking the same image
     * updates its row instead of creating a duplicate.
     *
     * Negated to keep picker ids clearly separate from real MediaStore ids.
     */
    private fun stableKey(uri: Uri): String =
        "-${uri.toString().hashCode().toLong().absoluteValue}"

    private fun queryNameAndSize(uri: Uri): Pair<String?, Long?> = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null to null

            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

            val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                cursor.getString(nameIndex)
            } else {
                null
            }
            val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                cursor.getLong(sizeIndex)
            } else {
                null
            }
            name to size
        } ?: (null to null)
    }.getOrDefault(null to null)
}
