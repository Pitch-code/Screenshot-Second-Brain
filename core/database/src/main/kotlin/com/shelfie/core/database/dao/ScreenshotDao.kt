package com.shelfie.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.shelfie.core.database.ShelfQuery
import com.shelfie.core.database.entity.FolderEntity
import com.shelfie.core.database.entity.ScreenshotEntity
import com.shelfie.core.database.entity.ScreenshotTextEntity
import com.shelfie.core.model.IndexState
import com.shelfie.core.model.ScreenshotCategory
import com.shelfie.core.model.ShelfFilter
import com.shelfie.core.model.ShelfSortOrder
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

    // -------------------------------------------------------------- diagnostics

    /**
     * Row counts per index state.
     *
     * This is the single most useful diagnostic in the app: the distribution alone
     * identifies the failure class without any logging. All PENDING means the
     * pipeline never ran; all FAILED points at the recogniser; all SKIPPED points
     * at image decoding; all INDEXED with no text means OCR returned nothing.
     */
    @Query(
        """
        SELECT index_state AS state, COUNT(*) AS count FROM screenshots
        WHERE is_deleted = 0
        GROUP BY index_state
        """,
    )
    fun observeStateCounts(): Flow<List<IndexStateCount>>

    @Query(
        """
        SELECT last_error FROM screenshots
        WHERE last_error IS NOT NULL
        ORDER BY id DESC LIMIT 1
        """,
    )
    fun observeLastError(): Flow<String?>

    @Query(
        """
        UPDATE screenshots
        SET index_state = 'INDEXED',
            category = :category,
            category_confidence = :confidence,
            primary_value = :primaryValue,
            indexed_at = :indexedAt,
            -- Clears any error from an earlier attempt. Without this a screenshot
            -- that failed once and then succeeded kept its stale error forever,
            -- and the diagnostics panel reported long-fixed problems as current.
            last_error = NULL
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

    /**
     * Marks a row as being worked on.
     *
     * Deliberately does **not** touch attempt_count. Counting an attempt at the
     * start meant every app launch burned one, so after three launches an item
     * became permanently ineligible for indexing without a single real failure
     * having occurred.
     */
    @Query("UPDATE screenshots SET index_state = 'IN_PROGRESS' WHERE id = :id")
    suspend fun markStarted(id: Long)

    /** Returns one row to the queue without consuming a retry attempt. */
    @Query("UPDATE screenshots SET index_state = 'PENDING' WHERE id = :id")
    suspend fun requeue(id: Long)

    /**
     * Requeues everything that previously failed, clearing the attempt counter so
     * the retry limit does not immediately block it again.
     */
    @Query(
        """
        UPDATE screenshots
        SET index_state = 'PENDING', attempt_count = 0
        WHERE index_state IN ('FAILED', 'SKIPPED')
        """,
    )
    suspend fun requeueFailed(): Int

    /** Records a diagnostic message without changing state or attempt count. */
    @Query("UPDATE screenshots SET last_error = :error WHERE id = :id")
    suspend fun setLastError(id: Long, error: String?)

    /** Records a failed attempt, with the reason, and increments the counter. */
    @Query(
        """
        UPDATE screenshots
        SET index_state = :state,
            attempt_count = attempt_count + 1,
            last_error = :error
        WHERE id = :id
        """,
    )
    suspend fun markFailed(id: Long, state: IndexState, error: String?)

    /**
     * Returns rows abandoned mid-index to the queue.
     *
     * A row left IN_PROGRESS by a crash, a hang, or the process being killed was
     * previously orphaned forever, because the work queue only looks at PENDING
     * and FAILED. Called on every start.
     */
    @Query("UPDATE screenshots SET index_state = 'PENDING' WHERE index_state = 'IN_PROGRESS'")
    suspend fun requeueStaleInProgress(): Int

    /**
     * Returns every successfully indexed row to the queue so its text is produced
     * again by the current pipeline.
     *
     * For when the extraction algorithm itself changes: the stored text is not wrong
     * in a way the app can detect, it is simply the output of an older algorithm, and
     * nothing else would ever cause it to be re-read. Deliberately narrow —
     * INDEXED only:
     *
     *  - FAILED and SKIPPED rows have no text to regenerate, and requeueing them here
     *    would quietly hand them fresh retry attempts that the failure path decided
     *    they had exhausted.
     *  - QUOTA_HELD rows were never read in the first place, and releasing them would
     *    index past the free limit.
     *
     * The attempt counter is left alone. These rows succeeded, so it is already at
     * or near zero, and resetting it would forgive genuine flakiness that the retry
     * limit exists to catch.
     */
    @Query(
        """
        UPDATE screenshots
        SET index_state = 'PENDING'
        WHERE is_deleted = 0 AND index_state = 'INDEXED'
        """,
    )
    suspend fun requeueAllIndexed(): Int

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

    /** Row ids for a set of MediaStore ids, so their text can be removed too. */
    @Query("SELECT id FROM screenshots WHERE media_store_id IN (:mediaStoreIds)")
    suspend fun idsForMediaStoreIds(mediaStoreIds: List<Long>): List<Long>

    @Query("DELETE FROM screenshots WHERE media_store_id IN (:mediaStoreIds)")
    suspend fun deleteByMediaStoreIds(mediaStoreIds: List<Long>): Int

    /**
     * Removes screenshots whose underlying file is gone, along with their
     * recognised text.
     *
     * Takes the ids to *remove* rather than the ids to keep. The previous approach
     * bound every image id on the device into a `NOT IN (...)` clause, which meant a
     * phone with a large gallery bound tens of thousands of parameters — past
     * SQLite's limit on older versions, where it throws rather than pruning. The
     * number of rows this app knows about is always far smaller than the number of
     * images on the device, so comparing in that direction is both correct and
     * bounded.
     *
     * Deleting the FTS rows is not optional. `screenshot_text_fts` is a standalone
     * FTS4 table, not external-content, so its rows outlive the `screenshots` rows
     * that referenced them. Without this, text extracted from a deleted screenshot —
     * one-time passcodes, bank messages — stayed in the database indefinitely, which
     * contradicts the privacy policy's promise that deleting removes everything.
     */
    @Transaction
    suspend fun removeByMediaStoreIds(mediaStoreIds: List<Long>): Int {
        if (mediaStoreIds.isEmpty()) return 0

        var removed = 0
        // Chunked to stay well inside SQLite's bound-parameter limit regardless of
        // the device's SQLite version.
        mediaStoreIds.chunked(SQL_ID_CHUNK).forEach { chunk ->
            deleteTextFor(idsForMediaStoreIds(chunk))
            removed += deleteByMediaStoreIds(chunk)
        }
        return removed
    }

    // ----------------------------------------------------------------- reads

    /**
     * The shelf feed, for any combination of filter and sort order.
     *
     * A raw query rather than eight near-identical `@Query` methods: SQLite cannot
     * parameterise ORDER BY, so four sort orders across three filter shapes would
     * otherwise mean a dozen hand-maintained copies of the same SELECT. The SQL is
     * assembled by [ShelfQuery] from closed enums only — no user input reaches it,
     * and filter values are bound as arguments.
     */
    @RawQuery(observedEntities = [ScreenshotEntity::class])
    fun pagedShelfRaw(query: SupportSQLiteQuery): PagingSource<Int, ScreenshotEntity>

    /**
     * Typed entry point for the shelf feed.
     *
     * Exists so `SupportSQLiteQuery` never escapes this module — callers ask in
     * terms of a filter and a sort order, not SQL.
     */
    fun pagedShelf(
        filter: ShelfFilter,
        sort: ShelfSortOrder,
    ): PagingSource<Int, ScreenshotEntity> = pagedShelfRaw(ShelfQuery.shelf(filter, sort))

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
    suspend fun nextPending(
        limit: Int,
        maxAttempts: Int = MAX_INDEX_ATTEMPTS,
    ): List<ScreenshotEntity>

    @Query("SELECT COUNT(*) FROM screenshots WHERE is_deleted = 0")
    fun observeTotalCount(): Flow<Int>

    /** One-shot equivalent, for measuring what a rescan actually added. */
    @Query("SELECT COUNT(*) FROM screenshots WHERE is_deleted = 0")
    suspend fun totalCount(): Int

    @Query("SELECT COUNT(*) FROM screenshots WHERE index_state = 'INDEXED' AND is_deleted = 0")
    fun observeIndexedCount(): Flow<Int>

    /**
     * Rows that can still change state.
     *
     * Mirrors [nextPending]'s eligibility rules exactly, because this is what
     * decides whether the shelf's progress banner has any reason to be on screen.
     * Counting *all* rows instead meant the banner stayed up forever for free
     * users, whose held-back rows can never reach INDEXED.
     */
    @Query(
        """
        SELECT COUNT(*) FROM screenshots
        WHERE is_deleted = 0
          AND (
            index_state IN ('PENDING', 'IN_PROGRESS')
            OR (index_state = 'FAILED' AND attempt_count < :maxAttempts)
          )
        """,
    )
    fun observeOutstandingCount(maxAttempts: Int = MAX_INDEX_ATTEMPTS): Flow<Int>

    /**
     * Categories to list in the Find tab, with how many screenshots each holds.
     *
     * ### The predicate must match [ShelfQuery]'s category filter exactly
     *
     * This is the bug that made a manual move disappear. The list was computed with
     * `index_state = 'INDEXED'` while the view it links to had no state filter at all,
     * so the two disagreed: a category could be absent from the list while holding
     * screenshots, or show a count lower than what opening it revealed.
     *
     * It bit hardest on the free tier. With a window of 50, most of a larger library
     * is QUOTA_HELD — those screenshots are still on the shelf and still browsable,
     * only their text is not searchable — so moving one into a category counted for
     * nothing and the category stayed hidden.
     *
     * The rule now: if opening a category would show it, the count includes it.
     *
     * ### Why the threshold is 1
     *
     * It was 3, to stop low-confidence automatic guesses cluttering the list with
     * one-item categories. Reasonable for guesses, wrong for choices: a screenshot the
     * user deliberately moved into OTP codes must appear there immediately, and
     * anything else reads as the app having lost it. The count is displayed beside each
     * category, so a small one is visible rather than misleading.
     */
    @Query(
        """
        SELECT category, COUNT(*) AS count FROM screenshots
        WHERE is_deleted = 0 AND folder_id IS NULL
        GROUP BY category
        HAVING count >= :minimumMatches
        ORDER BY count DESC
        """,
    )
    fun observeCategoryCounts(minimumMatches: Int = 1): Flow<List<CategoryCount>>

    // ----------------------------------------------------------------- folders

    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    /**
     * Returns the new row id, or -1 when a folder with this name already exists.
     *
     * IGNORE rather than REPLACE: replacing would allocate a new id and orphan
     * every screenshot already filed under the old one.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("SELECT * FROM folders WHERE name_key = :nameKey LIMIT 1")
    suspend fun folderByNameKey(nameKey: String): FolderEntity?

    @Query("UPDATE screenshots SET folder_id = :folderId WHERE id = :id")
    suspend fun setFolder(id: Long, folderId: Long?)

    /**
     * Files several screenshots at once, or clears their folder when null.
     *
     * One statement rather than a loop so a bulk move is atomic — a partially applied
     * move would leave the user's selection split across two folders with no obvious
     * way to tell which went where.
     */
    @Query("UPDATE screenshots SET folder_id = :folderId WHERE id IN (:ids)")
    suspend fun setFolderForIds(ids: List<Long>, folderId: Long?)

    /**
     * Re-categorises several screenshots at once.
     *
     * Clears folder_id for the same reason the single-row version does: choosing an
     * automatic category is the user saying "this belongs there instead", and leaving
     * them filed would hide them from the category they just picked.
     *
     * Confidence is 1.0 because an explicit choice is not a guess.
     */
    @Query(
        """
        UPDATE screenshots
        SET category = :category, category_confidence = 1.0, folder_id = NULL
        WHERE id IN (:ids)
        """,
    )
    suspend fun setCategoryForIds(ids: List<Long>, category: ScreenshotCategory)

    /**
     * Counts per folder, including empty folders.
     *
     * Unlike categories there is no minimum: the user made this folder on purpose,
     * so hiding it until it reaches a threshold would look like the app lost it.
     */
    @Query(
        """
        SELECT f.id AS folderId,
               f.name AS name,
               f.icon AS icon,
               COUNT(s.id) AS count
        FROM folders AS f
        LEFT JOIN screenshots AS s
          ON s.folder_id = f.id AND s.is_deleted = 0
        GROUP BY f.id
        ORDER BY f.name COLLATE NOCASE ASC
        """,
    )
    fun observeFolderCounts(): Flow<List<FolderCount>>

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderRow(id: Long)

    @Query("UPDATE screenshots SET folder_id = NULL WHERE folder_id = :id")
    suspend fun clearFolderAssignments(id: Long)

    /**
     * Deletes a folder and returns its screenshots to their automatic category.
     *
     * Transactional and in this order so a crash can never leave rows pointing at
     * a folder that no longer exists, which would make them invisible: they would
     * be excluded from their category as "filed" while belonging to nothing.
     */
    @Transaction
    suspend fun deleteFolder(id: Long) {
        clearFolderAssignments(id)
        deleteFolderRow(id)
    }

    @Query("SELECT * FROM screenshots WHERE id = :id")
    fun observeById(id: Long): Flow<ScreenshotEntity?>

    /**
     * Watermark source. Restricted to MEDIA_STORE rows: picker imports are
     * stamped with the import time, so counting them would advance the watermark
     * to "now" and permanently skip older screenshots still on disk.
     */
    // ------------------------------------------------------------------ export

    /** Flattened rows for the data export, joined with their recognised text. */
    @Query(
        """
        SELECT s.display_name AS displayName,
               s.date_added AS dateAdded,
               s.category AS category,
               s.primary_value AS primaryValue,
               f.text AS text
        FROM screenshots AS s
        LEFT JOIN screenshot_text_fts AS f ON s.id = f.rowid
        WHERE s.is_deleted = 0
        ORDER BY s.date_added DESC
        """,
    )
    suspend fun exportRows(): List<ExportRow>

    // --------------------------------------------------------- free-tier quota

    /**
     * Ids of indexed screenshots outside the newest [limit].
     *
     * Newest-first, so the free tier always keeps the most recent window — the
     * screenshots a user is actually likely to search for.
     */
    @Query(
        """
        SELECT id FROM screenshots
        WHERE index_state = 'INDEXED' AND is_deleted = 0
        ORDER BY date_added DESC
        LIMIT -1 OFFSET :limit
        """,
    )
    suspend fun indexedIdsBeyond(limit: Int): List<Long>

    /**
     * The [limit]th newest screenshot's timestamp, or null if there are fewer.
     *
     * Used to avoid recognising text that is about to be thrown away. On the free
     * tier the window keeps only the newest N, so a pending screenshot older than
     * this boundary would be recognised and then immediately held back — on a library
     * of a few thousand that is hours of battery spent producing nothing.
     */
    @Query(
        """
        SELECT date_added FROM screenshots
        WHERE is_deleted = 0 AND index_state IN ('INDEXED', 'QUOTA_HELD')
        ORDER BY date_added DESC
        LIMIT 1 OFFSET :limit
        """,
    )
    suspend fun dateAddedAtRank(limit: Int): Long?

    /**
     * Holds a row back without recognising it.
     *
     * Distinct from the post-recognition path: there is no text to delete, because
     * none was ever extracted.
     */
    @Query("UPDATE screenshots SET index_state = 'QUOTA_HELD' WHERE id = :id")
    suspend fun holdWithoutIndexing(id: Long)

    /** Whether any of these screenshots is currently filed in a folder. */
    @Query("SELECT COUNT(*) FROM screenshots WHERE id IN (:ids) AND folder_id IS NOT NULL")
    suspend fun filedCount(ids: List<Long>): Int

    /** Screenshots known about but never processed, for the "found N" total. */
    @Query("SELECT COUNT(*) FROM screenshots WHERE is_deleted = 0")
    suspend fun discoveredCount(): Int

    @Query("UPDATE screenshots SET index_state = 'QUOTA_HELD' WHERE id IN (:ids)")
    suspend fun markQuotaHeld(ids: List<Long>)

    /** Frees every held row for re-indexing, after the full version is unlocked. */
    @Query("UPDATE screenshots SET index_state = 'PENDING' WHERE index_state = 'QUOTA_HELD'")
    suspend fun releaseQuotaHolds(): Int

    /** One-shot indexed count, for deciding how much room the free window has. */
    @Query("SELECT COUNT(*) FROM screenshots WHERE index_state = 'INDEXED' AND is_deleted = 0")
    suspend fun indexedCount(): Int

    /**
     * Returns up to [limit] held-back screenshots to the queue, newest first.
     *
     * Newest-first because that is the direction the free window rolls: if only some
     * can come back, they should be the most recent of the held set.
     *
     * attempt_count is cleared as well. Being held back is not a failure, so a row
     * that had used up retries before it was ever held must not stay permanently
     * ineligible once it is released.
     */
    @Query(
        """
        UPDATE screenshots
        SET index_state = 'PENDING', attempt_count = 0
        WHERE id IN (
          SELECT id FROM screenshots
          WHERE index_state = 'QUOTA_HELD' AND is_deleted = 0
          ORDER BY date_added DESC
          LIMIT :limit
        )
        """,
    )
    suspend fun releaseNewestQuotaHolds(limit: Int): Int

    // is_deleted filter matters: without it, deleting a held screenshot left it
    // counted in the upgrade prompt, offering to unlock rows that no longer exist.
    @Query("SELECT COUNT(*) FROM screenshots WHERE index_state = 'QUOTA_HELD' AND is_deleted = 0")
    fun observeQuotaHeldCount(): Flow<Int>

    /** Drops the searchable text for rows rolled out of the free window. */
    @Query("DELETE FROM screenshot_text_fts WHERE rowid IN (:ids)")
    suspend fun deleteTextFor(ids: List<Long>)

    /**
     * Rolls rows out of the free window atomically, so a crash can never leave a
     * row marked INDEXED with its text already gone.
     */
    @Transaction
    suspend fun holdBeyondQuota(limit: Int): Int {
        val ids = indexedIdsBeyond(limit)
        if (ids.isEmpty()) return 0

        deleteTextFor(ids)
        markQuotaHeld(ids)
        return ids.size
    }

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

    /** Somewhere to hang a pipeline-level diagnostic message. */
    @Query("SELECT id FROM screenshots ORDER BY id DESC LIMIT 1")
    suspend fun newestRowId(): Long?

    @Query("SELECT media_store_id FROM screenshots WHERE source = 'MEDIA_STORE'")
    suspend fun allMediaStoreIds(): List<Long>

    /** Keys of picker-imported rows, so orphaned local copies can be pruned. */
    @Query("SELECT media_store_id FROM screenshots WHERE source = 'PICKER'")
    suspend fun allPickerIds(): List<Long>

    @Query("SELECT COUNT(*) FROM screenshots WHERE source = 'PICKER' AND is_deleted = 0")
    fun observePickedCount(): Flow<Int>
}

/**
 * Retry budget per screenshot.
 *
 * Shared by the work queue and the outstanding-work count so the banner can never
 * disagree with the queue about whether a row still has a chance of being read.
 */
const val MAX_INDEX_ATTEMPTS = 3

/**
 * How many ids to bind in one statement.
 *
 * SQLite's `SQLITE_MAX_VARIABLE_NUMBER` was 999 before version 3.32 and 32766 after,
 * and which one a device has depends on its Android version. 500 is comfortably below
 * the older limit, so the same code is safe on every supported release.
 */
const val SQL_ID_CHUNK = 500

/** Row count for one index state, used by the diagnostics panel. */
data class IndexStateCount(
    val state: IndexState,
    val count: Int,
)

/** One row of the plain-text data export. */
data class ExportRow(
    val displayName: String,
    val dateAdded: Long,
    val category: ScreenshotCategory,
    val primaryValue: String?,
    val text: String?,
)

/** Backs the category chips; only categories with enough matches are shown. */
data class CategoryCount(
    val category: ScreenshotCategory,
    val count: Int,
)

/** Backs the folder chips. Unlike categories, zero-count folders are still shown. */
data class FolderCount(
    val folderId: Long,
    val name: String,
    val icon: String,
    val count: Int,
)
