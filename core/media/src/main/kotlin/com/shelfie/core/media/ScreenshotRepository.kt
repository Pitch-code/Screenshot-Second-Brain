package com.shelfie.core.media

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.shelfie.core.classify.UserRule
import com.shelfie.core.database.dao.CategoryCount
import com.shelfie.core.database.dao.FolderCount
import com.shelfie.core.database.dao.IndexStateCount
import com.shelfie.core.database.dao.SQL_ID_CHUNK
import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.database.entity.ScreenshotEntity
import com.shelfie.core.database.entity.newFolderEntity
import com.shelfie.core.database.entity.toDomain
import com.shelfie.core.datastore.ShelfiePreferences
import com.shelfie.core.datastore.UserRuleStore
import com.shelfie.core.model.Folder
import com.shelfie.core.model.FolderIcon
import com.shelfie.core.model.FolderWithCount
import com.shelfie.core.model.IndexProgress
import com.shelfie.core.model.IndexTier
import com.shelfie.core.model.MediaAccess
import com.shelfie.core.model.MediaFolder
import com.shelfie.core.model.Screenshot
import com.shelfie.core.model.ScreenshotCategory
import com.shelfie.core.model.SearchQuery
import com.shelfie.core.model.ShelfFilter
import com.shelfie.core.model.ShelfSortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val ruleStore: UserRuleStore,
    private val accessChecker: MediaAccessChecker,
) {

    // ------------------------------------------------------------------ paging

    /** The shelf feed for a given filter and ordering. */
    fun pagedShelf(
        filter: ShelfFilter = ShelfFilter.All,
        sort: ShelfSortOrder = ShelfSortOrder.Default,
    ): Flow<PagingData<Screenshot>> = pager { dao.pagedShelf(filter, sort) }

    /**
     * Full-text search. Returns null when [rawQuery] has no usable tokens, so
     * callers can show the unfiltered shelf instead of an empty result set.
     */
    fun search(rawQuery: String): Flow<PagingData<Screenshot>>? {
        val match = SearchQuery.toFtsMatch(rawQuery) ?: return null
        return pager { dao.search(match) }
    }

    private fun pager(
        source: () -> androidx.paging.PagingSource<Int, ScreenshotEntity>,
    ): Flow<PagingData<Screenshot>> = Pager(
        config = PagingConfig(
            // A screen holds roughly 15 tiles, so a 60-item page keeps several
            // screens buffered while never pulling 5,000 rows into memory.
            pageSize = 60,
            prefetchDistance = 30,
            initialLoadSize = 60,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = source,
    ).flow.map { data -> data.map(ScreenshotEntity::toDomain) }

    // ------------------------------------------------------------------ detail

    fun observeScreenshot(id: Long): Flow<Screenshot?> =
        dao.observeById(id).map { it?.toDomain() }

    /** The recognised text, for the detail sheet. */
    suspend fun textFor(id: Long): String? = dao.textFor(id)

    /**
     * Applies a manual re-categorisation.
     *
     * Also clears any folder, because picking an automatic category is the user
     * saying "this belongs there instead" — leaving it filed would make the choice
     * appear to have been ignored, since folders take precedence in the UI.
     */
    suspend fun setCategory(id: Long, category: ScreenshotCategory) {
        dao.setCategory(id, category)
        dao.setFolder(id, null)
    }

    // ----------------------------------------------------------------- folders

    fun observeFolders(): Flow<List<Folder>> =
        dao.observeFolders().map { rows -> rows.map { it.toDomain() } }

    /**
     * Folders with how many screenshots each holds.
     *
     * Mapped to the domain type here so the database row shape stays inside the
     * data layer and callers never touch [FolderCount].
     */
    fun observeFolderCounts(): Flow<List<FolderWithCount>> =
        dao.observeFolderCounts().map { rows ->
            rows.map { row ->
                FolderWithCount(
                    folder = Folder(
                        id = row.folderId,
                        name = row.name,
                        icon = FolderIcon.fromNameOrDefault(row.icon),
                    ),
                    count = row.count,
                )
            }
        }

    /**
     * Creates a folder, or returns the existing one with the same name.
     *
     * Idempotent by design: a double tap on "Create" must not produce two folders
     * that look identical. Uniqueness is enforced by the database's `name_key`
     * index, so this cannot race — the INSERT is ignored and the existing row is
     * read back.
     */
    suspend fun createFolder(
        rawName: String,
        icon: FolderIcon = FolderIcon.FOLDER,
        nowSeconds: Long = System.currentTimeMillis() / 1000,
    ): Folder? {
        if (!Folder.isValidName(rawName)) return null

        val entity = newFolderEntity(rawName, icon, nowSeconds)
        val id = dao.insertFolder(entity)

        // -1 means the unique index rejected it: a folder with this name is already
        // there, which is a success from the user's point of view.
        val row = if (id == -1L) dao.folderByNameKey(entity.nameKey) else entity.copy(id = id)
        return row?.toDomain()
    }

    /** Files a screenshot into a folder, or clears it when [folderId] is null. */
    suspend fun setFolder(id: Long, folderId: Long?) = dao.setFolder(id, folderId)

    /**
     * Files several screenshots at once, or clears their folder when null.
     *
     * Chunked for the same reason the prune is: a large selection would otherwise
     * bind more parameters than SQLite allows on older devices.
     */
    suspend fun setFolderForAll(ids: List<Long>, folderId: Long?) {
        ids.chunked(SQL_ID_CHUNK).forEach { chunk -> dao.setFolderForIds(chunk, folderId) }
    }

    /** Re-categorises several screenshots at once, clearing any folder. */
    suspend fun setCategoryForAll(ids: List<Long>, category: ScreenshotCategory) {
        ids.chunked(SQL_ID_CHUNK).forEach { chunk -> dao.setCategoryForIds(chunk, category) }
    }

    /** Deletes a folder; its screenshots return to their automatic category. */
    suspend fun deleteFolder(id: Long) = dao.deleteFolder(id)

    // ------------------------------------------------------------------- rules

    fun observeRules(): Flow<List<UserRule>> = ruleStore.rules.map { stored ->
        stored.map(::toUserRule)
    }

    /**
     * Creates a rule from the detail sheet's "always sort <keyword> here".
     *
     * Captured at the exact moment the user notices a wrong category, which is
     * the only moment they are motivated to fix it.
     */
    suspend fun addRule(keyword: String, category: ScreenshotCategory) =
        ruleStore.add(keyword, category)

    suspend fun removeRule(id: Long) = ruleStore.remove(id)

    suspend fun currentRules(): List<UserRule> = ruleStore.current().map(::toUserRule)

    private fun toUserRule(rule: com.shelfie.core.datastore.StoredRule) = UserRule(
        id = rule.id,
        keyword = rule.keyword,
        category = rule.category,
        enabled = rule.enabled,
    )

    // ------------------------------------------------------------------ export

    /**
     * Plain-text dump of the index, for the "Export my data" setting.
     *
     * Exists to satisfy the access and portability rights described in the
     * privacy policy. Since nothing is held on a server, exporting is entirely
     * local and there is no request for anyone to process.
     */
    suspend fun exportIndex(): String = buildString {
        appendLine("Shelfie index export")
        appendLine("Generated: ${java.time.Instant.now()}")
        appendLine()

        val rows = dao.exportRows()
        appendLine("${rows.size} screenshots")
        appendLine()

        rows.forEach { row ->
            appendLine("---")
            appendLine("Name: ${row.displayName}")
            appendLine("Date added (epoch seconds): ${row.dateAdded}")
            appendLine("Category: ${row.category}")
            row.primaryValue?.let { appendLine("Key value: $it") }
            appendLine("Text:")
            appendLine(row.text.orEmpty().ifBlank { "(no text recognised)" })
            appendLine()
        }
    }

    // ---------------------------------------------------------------- progress

    fun observeProgress(): Flow<IndexProgress> = combine(
        dao.observeTotalCount(),
        dao.observeIndexedCount(),
        dao.observeOutstandingCount(),
    ) { total, indexed, outstanding ->
        IndexProgress(
            indexed = indexed,
            total = total,
            outstanding = outstanding,
            tier = if (outstanding <= 0) IndexTier.IDLE else IndexTier.BACKLOG,
        )
    }

    fun observeCategoryCounts(): Flow<List<CategoryCount>> = dao.observeCategoryCounts()

    /**
     * Diagnostics.
     *
     * The state distribution alone identifies a systematic indexing failure
     * without any logging, which matters because this app ships with no crash
     * reporting or analytics of any kind.
     */
    fun observeStateCounts(): Flow<List<IndexStateCount>> = dao.observeStateCounts()

    fun observeLastError(): Flow<String?> = dao.observeLastError()

    /**
     * Returns rows abandoned mid-index to the queue. Called on every start, so a
     * crash or hang cannot orphan a screenshot permanently.
     */
    suspend fun requeueStaleWork(): Int = dao.requeueStaleInProgress()

    /** Requeues failed and skipped rows, e.g. after a fix or a permission change. */
    suspend fun requeueFailed(): Int = dao.requeueFailed()

    /** Requeues indexed rows so a changed text pipeline is applied to them. */
    suspend fun requeueAllIndexed(): Int = dao.requeueAllIndexed()

    /** Ids of the live screenshots filed in any of these folders. */
    suspend fun screenshotIdsInFolders(folderIds: List<Long>): List<Long> =
        if (folderIds.isEmpty()) emptyList() else dao.screenshotIdsInFolders(folderIds)

    /**
     * Deletes several folders, returning their screenshots to their categories.
     *
     * One folder at a time because each [ScreenshotDao.deleteFolder] is already
     * transactional in the order that matters — assignments cleared before the row
     * goes, so a failure part way through can never leave a screenshot pointing at a
     * folder that no longer exists, which would hide it from both views.
     */
    suspend fun deleteFolders(ids: List<Long>) {
        ids.forEach { dao.deleteFolder(it) }
    }

    /**
     * Records a pipeline-level error not attributable to one screenshot.
     *
     * Attached to the newest row so the existing diagnostics card surfaces it. The
     * app ships with no crash reporting by design, so an error with nowhere to go
     * is an error the user can never report and I can never see.
     */
    suspend fun recordGlobalError(message: String) {
        val newest = runCatching { dao.newestRowId() }.getOrNull() ?: return
        runCatching { dao.setLastError(newest, message) }
    }

    /** How many screenshots Limited Mode can currently see. */
    fun observePickedCount(): Flow<Int> = dao.observePickedCount()

    fun observeTotalCount(): Flow<Int> = dao.observeTotalCount()

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
        val found = mediaStore.queryScreenshotsSince(watermark, limit, chosenFolders())
        if (found.isEmpty()) return 0

        dao.upsertAll(found.map { it.toPendingEntity() })

        // Advance only after a successful write, so a crash mid-insert causes a
        // harmless re-scan rather than permanently skipped screenshots.
        found.maxOfOrNull { it.dateAdded }?.let { preferences.advanceWatermark(it) }

        return found.size
    }

    /**
     * Discovers the entire library, ignoring the watermark.
     *
     * The watermark-based [discoverNew] can only ever find screenshots *newer*
     * than what is already stored. Since the first pass deliberately seeds the
     * newest batch, the watermark immediately jumps to the newest screenshot on
     * the device and every later watermark scan matches nothing — leaving the
     * whole backlog permanently undiscovered.
     *
     * This is a cursor walk with no image work, so it is cheap enough to run on
     * every background pass.
     */
    suspend fun discoverAll(): Int {
        if (!accessChecker.canReadAnyMedia()) return 0

        val found = mediaStore.queryScreenshotsSince(
            sinceDateAddedSeconds = 0,
            limit = 0,
            includeFolders = chosenFolders(),
        )
        if (found.isEmpty()) return 0

        dao.upsertAll(found.map { it.toPendingEntity() })
        found.maxOfOrNull { it.dateAdded }?.let { preferences.advanceWatermark(it) }
        return found.size
    }

    // ------------------------------------------------------------ folder choice

    /**
     * Folders the user has opted into, or an empty set.
     *
     * Read on each discovery pass rather than cached, so ticking a folder takes
     * effect on the next scan without needing a restart.
     */
    private suspend fun chosenFolders(): Set<String> =
        runCatching { preferences.extraFolders.first() }.getOrDefault(emptySet())

    /** Every image folder on the device, with counts. Backs the folder picker. */
    suspend fun availableFolders(): List<MediaFolder> =
        runCatching { mediaStore.queryFolders() }.getOrDefault(emptyList())

    fun observeChosenFolders(): Flow<Set<String>> = preferences.extraFolders

    /**
     * Saves the folder choice and rediscovers immediately.
     *
     * The rediscovery matters: without it, newly included folders would only appear
     * after some later background pass, and the setting would look broken.
     */
    suspend fun setChosenFolders(folderKeys: Set<String>) {
        preferences.setExtraFolders(folderKeys)
        runCatching { discoverAll() }
    }

    /**
     * Removes rows whose underlying image no longer exists on the device.
     *
     * Compares the app's own known ids against what MediaStore still reports, rather
     * than the other way round: the app knows about far fewer images than the device
     * holds, so this direction binds a handful of parameters instead of tens of
     * thousands.
     *
     * ### Two guards, both of which prevent deleting a user's index
     *
     * **Full access only.** Under Android 14's partial access, MediaStore reports
     * only the handful of images the user hand-picked. Every other row would then
     * look deleted, and the app would wipe an index built while full access was
     * granted. A permission downgrade must never destroy data.
     *
     * **Never act on an empty result.** A `SecurityException` mid-query returns an
     * empty set, which is indistinguishable from "the gallery is empty". Treating
     * that as "everything was deleted" would clear the whole library on a transient
     * failure.
     */
    private suspend fun pruneDeletedFiles(): Int {
        if (accessChecker.current() != MediaAccess.FULL) return 0

        val liveIds = runCatching { mediaStore.queryAllImageIds() }.getOrDefault(emptySet())
        if (liveIds.isEmpty()) return 0

        val known = runCatching { dao.allMediaStoreIds() }.getOrDefault(emptyList())
        val missing = known.filterNot { it in liveIds }
        if (missing.isEmpty()) return 0

        return runCatching { dao.removeByMediaStoreIds(missing) }.getOrDefault(0)
    }

    /** Records a diagnostic message against one screenshot. */
    suspend fun recordError(id: Long, message: String) = dao.setLastError(id, message)

    /**
     * Forced rescan, for the manual refresh control.
     *
     * Ignores the watermark entirely and reports what it found, because the whole
     * point of a refresh the user pressed is to bypass every optimisation that
     * might be the reason things look stale — and then to say plainly whether it
     * worked. Discovery failures are returned rather than swallowed.
     */
    suspend fun forceRescan(): RescanResult = runCatching {
        if (!accessChecker.canReadAnyMedia()) return RescanResult.NoAccess

        val before = dao.totalCount()
        discoverAll()
        val after = dao.totalCount()

        // Pruning belongs here as much as discovery does. Refresh previously only
        // ever added, so a user who deleted screenshots from their gallery and
        // pressed refresh saw nothing happen — the app had no way to notice a
        // deletion outside the six-hourly reconcile.
        val removed = pruneDeletedFiles()

        RescanResult.Completed(
            added = (after - before).coerceAtLeast(0),
            removed = removed,
        )
    }.getOrElse { error ->
        // Cancellation is not a failure to report; it means the screen went away.
        if (error is kotlinx.coroutines.CancellationException) throw error
        RescanResult.Failed(error.message ?: error::class.simpleName ?: "unknown error")
    }

    /**
     * Seeds the very first batch: the newest [limit] screenshots regardless of
     * watermark, so the shelf has content within seconds of first launch.
     */
    /** How many screenshots Shelfie knows about, searchable or not. */
    suspend fun discoveredCount(): Int = dao.discoveredCount()

    /**
     * True when any of these is filed in a folder.
     *
     * Drives whether the move picker offers "Remove from folder": offering it for a
     * selection that is not in any folder is an action that would visibly do nothing.
     */
    suspend fun anyFiled(ids: List<Long>): Boolean =
        ids.chunked(SQL_ID_CHUNK).any { chunk -> dao.filedCount(chunk) > 0 }

    suspend fun discoverNewest(limit: Int): Int {
        if (!accessChecker.canReadAnyMedia()) return 0

        val found = mediaStore.queryNewest(limit, chosenFolders())
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

        // Full scan, not watermark-based: this is the safety net that finds
        // anything earlier passes missed.
        val discovered = discoverAll()

        val pruned = pruneDeletedFiles()

        val purged = dao.purgeDeletedBefore(nowSeconds - RECOVERY_WINDOW_SECONDS)
        preferences.setLastReconcileAt(nowSeconds)

        return ReconcileReport.Completed(discovered = discovered, pruned = pruned, purged = purged)
    }

    /**
     * Newest known MediaStore timestamp, clamped to now.
     *
     * The clamp is not paranoia. `date_added` is copied verbatim from the media
     * provider, and some OEM providers (and restored or cloud-synced media) report
     * it in milliseconds or with a timestamp in the future. A single such row makes
     * `MAX(date_added)` astronomically large, and every later
     * `DATE_ADDED >= watermark` scan then matches nothing — permanently, and
     * silently. Symptom: the first launch works, and no screenshot is ever found
     * again.
     */
    private suspend fun currentWatermark(
        nowSeconds: Long = System.currentTimeMillis() / 1000,
    ): Long {
        val newest = runCatching { dao.newestDateAdded() }.getOrNull() ?: 0L
        return newest.coerceIn(0L, nowSeconds)
    }

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

/**
 * Outcome of a user-triggered rescan.
 *
 * Deliberately reports the failure text: a refresh that silently does nothing is
 * exactly the experience this control exists to fix.
 */
sealed interface RescanResult {
    data class Completed(val added: Int, val removed: Int = 0) : RescanResult
    data object NoAccess : RescanResult
    data class Failed(val reason: String) : RescanResult
}
