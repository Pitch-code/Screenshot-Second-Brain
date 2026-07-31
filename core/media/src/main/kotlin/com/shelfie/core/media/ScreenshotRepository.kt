package com.shelfie.core.media

import com.shelfie.core.database.dao.CategoryCount
import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.database.entity.ScreenshotEntity
import com.shelfie.core.datastore.ShelfiePreferences
import com.shelfie.core.model.IndexProgress
import com.shelfie.core.model.IndexTier
import com.shelfie.core.model.MediaAccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates MediaStore discovery with the local index.
 *
 * The reconcile step is what makes screenshot detection reliable. A
 * ContentObserver callback is only ever a hint: it is not delivered while the
 * process is dead, and OEM skins drop events. So the persisted watermark is the
 * real source of truth, and every entry point — app start, observer callback,
 * periodic worker — simply asks "what is newer than the watermark".
 */
@Singleton
class ScreenshotRepository @Inject constructor(
    private val dao: ScreenshotDao,
    private val mediaStore: MediaStoreScreenshotSource,
    private val preferences: ShelfiePreferences,
    private val accessChecker: MediaAccessChecker,
) {

    fun observeProgress(): Flow<IndexProgress> = combine(
        dao.observeTotalCount(),
        dao.observeIndexedCount(),
    ) { total, indexed ->
        IndexProgress(
            indexed = indexed,
            total = total,
            tier = if (indexed >= total) IndexTier.IDLE else IndexTier.BACKLOG,
        )
    }

    fun observeCategoryCounts(): Flow<List<CategoryCount>> = dao.observeCategoryCounts()

    fun currentAccess(): MediaAccess = accessChecker.current()

    /**
     * Discovers screenshots newer than the stored watermark and inserts them as
     * PENDING. Returns how many new rows were added.
     *
     * Idempotent: re-running discovers nothing new, because `upsert` is keyed on
     * the unique media store id.
     */
    suspend fun discoverNew(limit: Int = 0): Int {
        if (!accessChecker.canReadAnyMedia()) return 0

        val watermark = currentWatermark()
        val found = mediaStore.queryScreenshotsSince(watermark, limit)
        if (found.isEmpty()) return 0

        dao.upsertAll(found.map { it.toPendingEntity() })

        // Advance only after a successful write, so a crash mid-insert causes a
        // harmless re-scan rather than permanently skipped screenshots.
        found.maxOfOrNull { it.dateAdded }?.let { preferences.advanceWatermark(it) }

        return found.size
    }

    /**
     * Seeds the very first batch: the newest [limit] screenshots regardless of
     * watermark, so the shelf has content within seconds of first launch.
     */
    suspend fun discoverNewest(limit: Int): Int {
        if (!accessChecker.canReadAnyMedia()) return 0

        val found = mediaStore.queryNewest(limit)
        if (found.isEmpty()) return 0

        dao.upsertAll(found.map { it.toPendingEntity() })
        return found.size
    }

    /** Rows awaiting indexing, newest first. */
    suspend fun nextPending(limit: Int): List<ScreenshotEntity> = dao.nextPending(limit)

    /**
     * Removes rows whose underlying file has been deleted elsewhere, and purges
     * soft-deleted rows past the 30-day recovery window.
     */
    suspend fun reconcile(nowSeconds: Long = System.currentTimeMillis() / 1000): ReconcileReport {
        if (!accessChecker.canReadAnyMedia()) return ReconcileReport.Skipped

        val discovered = discoverNew()

        val liveIds = mediaStore.queryAllImageIds()
        val pruned = if (liveIds.isNotEmpty()) {
            dao.pruneMissing(liveIds.toList())
        } else {
            0
        }

        val purged = dao.purgeDeletedBefore(nowSeconds - RECOVERY_WINDOW_SECONDS)
        preferences.setLastReconcileAt(nowSeconds)

        return ReconcileReport.Completed(discovered = discovered, pruned = pruned, purged = purged)
    }

    private suspend fun currentWatermark(): Long =
        runCatching { dao.newestDateAdded() }.getOrNull() ?: 0L

    private fun MediaStoreScreenshot.toPendingEntity() = ScreenshotEntity(
        mediaStoreId = mediaStoreId,
        uri = uri,
        displayName = displayName,
        relativePath = relativePath,
        dateAdded = dateAdded,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
    )

    private companion object {
        /** 30 days, matching the Recently Deleted window in the product spec. */
        const val RECOVERY_WINDOW_SECONDS = 30L * 24 * 60 * 60
    }
}

sealed interface ReconcileReport {
    data object Skipped : ReconcileReport
    data class Completed(val discovered: Int, val pruned: Int, val purged: Int) : ReconcileReport
}
