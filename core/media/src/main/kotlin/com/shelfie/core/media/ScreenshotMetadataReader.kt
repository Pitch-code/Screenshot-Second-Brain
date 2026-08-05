package com.shelfie.core.media

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File facts about a screenshot, as the gallery would report them.
 */
data class ScreenshotMetadata(
    /** Epoch milliseconds. */
    val capturedAtMillis: Long?,
    /**
     * Whether [capturedAtMillis] is the real capture time.
     *
     * False when it had to be derived from when the file appeared or was last
     * modified. Surfaced rather than hidden so the UI can avoid claiming a screenshot
     * was "taken" at a time that is really only when it was noticed.
     */
    val capturedAtIsExact: Boolean,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    /** Folder path, as shown by a file manager. Null when it cannot be determined. */
    val path: String?,
)

/**
 * Reads a screenshot's metadata from MediaStore on demand.
 *
 * Read live rather than from the app's own row, because the app's row is a snapshot
 * taken at index time and MediaStore is the authority the gallery itself reports
 * from. If the two ever disagree — a file moved, renamed, or recompressed by another
 * app — the gallery is right and showing our stale copy would be showing a
 * fabrication.
 *
 * Not stored, for the same reason: caching it would recreate the staleness this
 * exists to avoid, and it is one indexed lookup by id.
 */
@Singleton
class ScreenshotMetadataReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun read(mediaStoreId: Long): ScreenshotMetadata? = withContext(Dispatchers.IO) {
        val projection = buildList {
            add(MediaStore.Images.Media.DATE_TAKEN)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.SIZE)
            add(MediaStore.Images.Media.DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Images.Media.DATA)
            }
        }.toTypedArray()

        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media._ID} = ?",
                arrayOf(mediaStoreId.toString()),
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null

                /*
                 * DATE_TAKEN is milliseconds; DATE_ADDED and DATE_MODIFIED are
                 * seconds. Mixing the two units is the classic bug here and produces
                 * dates in 1970 or in the far future, so each is converted at the
                 * point it is read rather than anywhere later.
                 *
                 * DATE_TAKEN is also frequently absent for screenshots — it comes
                 * from image metadata that a screen capture has no reason to write —
                 * so it is treated as missing when absent or zero, not trusted
                 * because the column exists.
                 */
                val takenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                val taken = takenIndex
                    .takeIf { it >= 0 && !cursor.isNull(it) }
                    ?.let { cursor.getLong(it) }
                    ?.takeIf { it > 0L }

                val addedSeconds = cursor.longOrNull(MediaStore.Images.Media.DATE_ADDED)
                val modifiedSeconds = cursor.longOrNull(MediaStore.Images.Media.DATE_MODIFIED)

                val fallbackMillis = (addedSeconds ?: modifiedSeconds)
                    ?.takeIf { it > 0L }
                    ?.times(1000L)

                val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val relative = cursor.stringOrNull(MediaStore.Images.Media.RELATIVE_PATH)
                    val name = cursor.stringOrNull(MediaStore.Images.Media.DISPLAY_NAME)
                    listOfNotNull(relative?.trimEnd('/'), name)
                        .takeIf { it.isNotEmpty() }
                        ?.joinToString("/")
                } else {
                    @Suppress("DEPRECATION")
                    cursor.stringOrNull(MediaStore.Images.Media.DATA)
                }

                ScreenshotMetadata(
                    capturedAtMillis = taken ?: fallbackMillis,
                    capturedAtIsExact = taken != null,
                    width = cursor.longOrNull(MediaStore.Images.Media.WIDTH)?.toInt() ?: 0,
                    height = cursor.longOrNull(MediaStore.Images.Media.HEIGHT)?.toInt() ?: 0,
                    sizeBytes = cursor.longOrNull(MediaStore.Images.Media.SIZE) ?: 0L,
                    path = path,
                )
            }
        }.getOrNull()
        // Null on failure rather than throwing: a screenshot imported through the
        // photo picker has no MediaStore row this app may query, and a missing
        // details panel is not worth taking the viewer down for.
    }

    private fun android.database.Cursor.longOrNull(column: String): Long? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }

    private fun android.database.Cursor.stringOrNull(column: String): String? {
        val index = getColumnIndex(column)
        return if (index >= 0 && !isNull(index)) getString(index)?.takeIf { it.isNotBlank() } else null
    }
}
