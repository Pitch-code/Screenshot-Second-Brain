package com.shelfie.core.media

import android.content.ContentResolver
import android.content.IntentSender
import android.net.Uri
import androidx.core.net.toUri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.ChecksSdkIntAtLeast
import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.model.ScreenshotSource
import com.shelfie.core.ocr.ThumbnailStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletion, in two stages.
 *
 * 1. **Soft delete** in our index straight away, which moves rows into the
 *    30-day Recently Deleted window. Reversible.
 * 2. **Reclaim storage** by deleting the underlying files, which needs the user's
 *    explicit system confirmation.
 *
 * Splitting the two is deliberate: one accidental bulk delete with no way back
 * produces a permanent one-star review.
 */
@Singleton
class ScreenshotDeleter @Inject constructor(
    private val dao: ScreenshotDao,
    private val contentResolver: ContentResolver,
    private val thumbnailStore: ThumbnailStore,
) {

    /** Stage 1. Reversible for [RECOVERY_WINDOW_DAYS] days. */
    suspend fun moveToRecentlyDeleted(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.softDelete(ids, System.currentTimeMillis() / 1000)
    }

    suspend fun restore(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.restore(ids)
    }

    /**
     * Stage 2. Builds the system delete confirmation for MediaStore-backed rows.
     *
     * Returns null when there is nothing the system needs to confirm — either the
     * selection is entirely picker-imported (our own files, which we can simply
     * delete), or the device predates the API.
     *
     * On Android 10 and below, deleting another app's media requires
     * `WRITE_EXTERNAL_STORAGE`. We deliberately do not request it: a broad write
     * permission would undermine the whole privacy position for a minority of
     * devices. Those users get index-only cleanup, and the UI says so plainly.
     */
    suspend fun buildDeleteRequest(ids: List<Long>): IntentSender? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext null

            val uris = dao.byIds(ids)
                .filter { it.source == ScreenshotSource.MEDIA_STORE }
                .map { it.uri.toUri() }

            if (uris.isEmpty()) return@withContext null

            runCatching {
                MediaStore.createDeleteRequest(contentResolver, uris).intentSender
            }.getOrNull()
        }

    /**
     * Builds a request to move files to the system bin, or to take them back out.
     *
     * This is what makes an undo honest. [buildDeleteRequest] destroys the file, so
     * "restore" could only ever put back a database row pointing at nothing — a tile
     * that renders as a broken box. Trashing keeps the file recoverable at OS level
     * for the same kind of window Shelfie uses for its own rows, so undo can restore
     * both halves and actually mean something.
     *
     * Used by the shelf's delete, where the user is tidying up and mistakes are
     * likely. Cleanup keeps using [buildDeleteRequest], because its entire purpose is
     * reclaiming storage and trashing would not free any.
     *
     * @param trash true to bin the files, false to restore them.
     */
    suspend fun buildTrashRequest(ids: List<Long>, trash: Boolean): IntentSender? =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext null

            val uris = dao.byIds(ids)
                .filter { it.source == ScreenshotSource.MEDIA_STORE }
                .map { it.uri.toUri() }

            if (uris.isEmpty()) return@withContext null

            runCatching {
                MediaStore.createTrashRequest(contentResolver, uris, trash).intentSender
            }.getOrNull()
        }

    /**
     * Finalises deletion after the system dialog was accepted, and removes local
     * copies for picker-imported rows.
     *
     * Returns how many rows were removed from the index.
     */
    suspend fun finalizeDeletion(ids: List<Long>): Int = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext 0

        val rows = dao.byIds(ids)

        // Our own copies, so we own deleting them.
        rows.filter { it.source == ScreenshotSource.PICKER }
            .forEach { thumbnailStore.delete(it.mediaStoreId.toString()) }

        dao.hardDelete(ids)
    }

    /** True when this device can actually reclaim storage. */
    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    fun canDeleteFiles(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    companion object {
        const val RECOVERY_WINDOW_DAYS = 30
    }
}
