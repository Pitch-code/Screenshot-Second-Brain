package com.shelfie.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.shelfie.core.database.entity.ScreenshotEntity
import com.shelfie.core.database.entity.ScreenshotTextEntity
import com.shelfie.core.model.IndexState
import com.shelfie.core.model.ScreenshotCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ScreenshotDao {

    // ---------------------------------------------------------------- writes

    @Upsert
    suspend fun upsert(screenshot: ScreenshotEntity): Long

    @Upsert
    suspend fun upsertAll(screenshots: List<ScreenshotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putText(text: ScreenshotTextEntity)

    /**
     * Persist an indexing result atomically, so a process death mid-write can
     * never leave a row marked INDEXED with no searchable text.
     */
    @Transaction
    suspend fun saveIndexResult(
        screenshotId: Long,
        text: String,
        category: ScreenshotCategory,
        confidence: Float,
        primaryValue: String?,
        indexedAt: Long,
    ) {
        putText(ScreenshotTextEntity(screenshotId = screenshotId, text = text))
        markIndexed(
            id = screenshotId,
            category = category,
            confidence = confidence,
            primaryValue = primaryValue,
            indexedAt = indexedAt,
        )
    }

    @Query(
        """
        UPDATE screenshots
        SET index_state = 'INDEXED',
            category = :category,
            category_confidence = :confidence,
            primary_value = :primaryValue,
            indexed_at = :indexedAt
        WHERE id = :id
        """,
    )
    suspend fun markIndexed(
        id: Long,
        category: ScreenshotCategory,
        confidence: Float,
        primaryValue: String?,
        indexedAt: Long,
    )

    @Query(
        """
        UPDATE screenshots
        SET index_state = :state,
            attempt_count = attempt_count + 1
        WHERE id = :id
        """,
    )
    suspend fun markAttempt(id: Long, state: IndexState)

    /**
     * Applies a manual re-categorisation. Confidence is forced to 1.0 because a
     * user's explicit choice is not a guess.
     */
    @Query(
        """
        UPDATE screenshots
        SET category = :category, category_confidence = 1.0
        WHERE id = :id
        """,
    )
    suspend fun setCategory(id: Long, category: ScreenshotCategory)

    @Query(
        """
        UPDATE screenshots
        SET perceptual_hash = :hash, blur_score = :blurScore
        WHERE id = :id
        """,
    )
    suspend fun setQuality(id: Long, hash: String?, blurScore: Float?)

    @Query("UPDATE screenshots SET is_deleted = 1, deleted_at = :deletedAt WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Long>, deletedAt: Long)

    @Query("UPDATE screenshots SET is_deleted = 0, deleted_at = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    /** Purges rows soft-deleted before [threshold]; the 30-day recovery window. */
    @Query("DELETE FROM screenshots WHERE is_deleted = 1 AND deleted_at < :threshold")
    suspend fun purgeDeletedBefore(threshold: Long): Int

    /**
     * Removes rows whose underlying file no longer exists in MediaStore.
     *
     * Scoped to MEDIA_STORE rows on purpose: picker-imported screenshots have no
     * MediaStore id, so without the source filter every one of them would be
     * deleted on the first reconcile.
     */
    @Query(
        """
        DELETE FROM screenshots
        WHERE source = 'MEDIA_STORE' AND media_store_id NOT IN (:liveIds)
        """,
    )
    suspend fun pruneMissing(liveIds: List<Long>): Int

    // ----------------------------------------------------------------- reads

    /** The shelf feed: newest first, always. */
    @Query(
        """
        SELECT * FROM screenshots
        WHERE is_deleted = 0
        ORDER BY date_added DESC, id DESC
        """,
    )
    fun pagedShelf(): PagingSource<Int, ScreenshotEntity>

    @Query(
        """
        SELECT * FROM screenshots
        WHERE is_deleted = 0 AND category = :category
        ORDER BY date_added DESC, id DESC
        """,
    )
    fun pagedByCategory(category: ScreenshotCategory): PagingSource<Int, ScreenshotEntity>

    /**
     * Full-text search. Joins the FTS table on rowid, then orders by recency so
     * that equally-relevant matches surface the most recent screenshot first.
     */
    @Query(
        """
        SELECT s.* FROM screenshots AS s
        JOIN screenshot_text_fts AS f ON s.id = f.rowid
        WHERE screenshot_text_fts MATCH :query AND s.is_deleted = 0
        ORDER BY s.date_added DESC
        """,
    )
    fun search(query: String): PagingSource<Int, ScreenshotEntity>

    @Query("SELECT text FROM screenshot_text_fts WHERE rowid = :screenshotId")
    suspend fun textFor(screenshotId: Long): String?

    /**
     * The indexing work queue. Ordered newest-first so Tier 1 always processes
     * the screenshots the user cares about most.
     */
    @Query(
        """
        SELECT * FROM screenshots
        WHERE index_state IN ('PENDING', 'FAILED')
          AND is_deleted = 0
          AND attempt_count < :maxAttempts
        ORDER BY date_added DESC
        LIMIT :limit
        """,
    )
    suspend fun nextPending(limit: Int, maxAttempts: Int = 3): List<ScreenshotEntity>

    @Query("SELECT COUNT(*) FROM screenshots WHERE is_deleted = 0")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM screenshots WHERE index_state = 'INDEXED' AND is_deleted = 0")
    fun observeIndexedCount(): Flow<Int>

    @Query(
        """
        SELECT category, COUNT(*) AS count FROM screenshots
        WHERE is_deleted = 0 AND index_state = 'INDEXED'
        GROUP BY category
        HAVING count >= :minimumMatches
        ORDER BY count DESC
        """,
    )
    fun observeCategoryCounts(minimumMatches: Int = 3): Flow<List<CategoryCount>>

    @Query("SELECT * FROM screenshots WHERE id = :id")
    fun observeById(id: Long): Flow<ScreenshotEntity?>

    /**
     * Watermark source. Restricted to MEDIA_STORE rows: picker imports are
     * stamped with the import time, so counting them would advance the watermark
     * to "now" and permanently skip older screenshots still on disk.
     */
    // ------------------------------------------------------------- cleanup

    /**
     * Rows sharing a perceptual hash: byte-level duplicates after downscaling.
     * Ordered so the oldest of each group comes first, which is the one to keep.
     */
    @Query(
        """
        SELECT * FROM screenshots
        WHERE is_deleted = 0 AND perceptual_hash IS NOT NULL
          AND perceptual_hash IN (
            SELECT perceptual_hash FROM screenshots
            WHERE is_deleted = 0 AND perceptual_hash IS NOT NULL
            GROUP BY perceptual_hash HAVING COUNT(*) > 1
          )
        ORDER BY perceptual_hash, date_added ASC
        """,
    )
    suspend fun duplicateCandidates(): List<ScreenshotEntity>

    @Query(
        """
        SELECT * FROM screenshots
        WHERE is_deleted = 0 AND blur_score IS NOT NULL AND blur_score < :threshold
        ORDER BY blur_score ASC
        """,
    )
    suspend fun blurryScreenshots(threshold: Float): List<ScreenshotEntity>

    /**
     * Old screenshots that were never opened in Shelfie. The strongest signal
     * that something is clutter rather than something the user wants.
     */
    @Query(
        """
        SELECT * FROM screenshots
        WHERE is_deleted = 0 AND date_added < :olderThan
        ORDER BY date_added ASC
        """,
    )
    suspend fun olderThan(olderThan: Long): List<ScreenshotEntity>

    @Query("SELECT * FROM screenshots WHERE is_deleted = 1 ORDER BY deleted_at DESC")
    fun pagedDeleted(): PagingSource<Int, ScreenshotEntity>

    @Query("SELECT COUNT(*) FROM screenshots WHERE is_deleted = 1")
    fun observeDeletedCount(): Flow<Int>

    @Query("SELECT * FROM screenshots WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<ScreenshotEntity>

    /** Hard-removes rows after their file has actually been deleted. */
    @Query("DELETE FROM screenshots WHERE id IN (:ids)")
    suspend fun hardDelete(ids: List<Long>): Int

    @Query("SELECT MAX(date_added) FROM screenshots WHERE source = 'MEDIA_STORE'")
    suspend fun newestDateAdded(): Long?

    @Query("SELECT media_store_id FROM screenshots WHERE source = 'MEDIA_STORE'")
    suspend fun allMediaStoreIds(): List<Long>

    /** Keys of picker-imported rows, so orphaned local copies can be pruned. */
    @Query("SELECT media_store_id FROM screenshots WHERE source = 'PICKER'")
    suspend fun allPickerIds(): List<Long>

    @Query("SELECT COUNT(*) FROM screenshots WHERE source = 'PICKER' AND is_deleted = 0")
    fun observePickedCount(): Flow<Int>
}

/** Backs the category chips; only categories with enough matches are shown. */
data class CategoryCount(
    val category: ScreenshotCategory,
    val count: Int,
)
