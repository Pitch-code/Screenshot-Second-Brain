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

    @Query("UPDATE screenshots SET is_deleted = 1, deleted_at = :deletedAt WHERE id IN (:ids)")
    suspend fun softDelete(ids: List<Long>, deletedAt: Long)

    @Query("UPDATE screenshots SET is_deleted = 0, deleted_at = NULL WHERE id IN (:ids)")
    suspend fun restore(ids: List<Long>)

    /** Purges rows soft-deleted before [threshold]; the 30-day recovery window. */
    @Query("DELETE FROM screenshots WHERE is_deleted = 1 AND deleted_at < :threshold")
    suspend fun purgeDeletedBefore(threshold: Long): Int

    /** Removes rows whose underlying file no longer exists in MediaStore. */
    @Query("DELETE FROM screenshots WHERE media_store_id NOT IN (:liveIds)")
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

    @Query("SELECT MAX(date_added) FROM screenshots")
    suspend fun newestDateAdded(): Long?

    @Query("SELECT media_store_id FROM screenshots")
    suspend fun allMediaStoreIds(): List<Long>
}

/** Backs the category chips; only categories with enough matches are shown. */
data class CategoryCount(
    val category: ScreenshotCategory,
    val count: Int,
)
