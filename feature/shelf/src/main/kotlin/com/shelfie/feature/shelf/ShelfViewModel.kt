package com.shelfie.feature.shelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.shelfie.core.database.dao.IndexStateCount
import com.shelfie.core.datastore.ShelfiePreferences
import com.shelfie.core.designsystem.component.ShelfChip
import com.shelfie.core.media.ImmediateIndexer
import com.shelfie.core.media.PickerImporter
import android.app.Activity
import com.shelfie.core.billing.PurchaseResult
import com.shelfie.core.billing.ShelfieBilling
import com.shelfie.core.media.IndexingQuota
import android.content.IntentSender
import com.shelfie.core.media.RescanResult
import com.shelfie.core.media.ScreenshotDeleter
import com.shelfie.core.media.ScreenshotRepository
import com.shelfie.core.model.Folder
import com.shelfie.core.model.FolderIcon
import com.shelfie.core.model.IndexProgress
import com.shelfie.core.model.IndexState
import com.shelfie.core.model.MediaAccess
import com.shelfie.core.model.Screenshot
import com.shelfie.core.model.ScreenshotCategory
import com.shelfie.core.model.ShelfFilter
import com.shelfie.core.model.ShelfSortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShelfViewModel @Inject constructor(
    private val repository: ScreenshotRepository,
    private val pickerImporter: PickerImporter,
    private val immediateIndexer: ImmediateIndexer,
    private val preferences: ShelfiePreferences,
    private val quota: IndexingQuota,
    private val billing: ShelfieBilling,
    private val deleter: ScreenshotDeleter,
) : ViewModel() {

    private val statusDismissed = MutableStateFlow(false)
    private val isImporting = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)
    private val refreshResult = MutableStateFlow<RescanResult?>(null)

    /**
     * Access is re-read on every refresh rather than cached.
     *
     * Permissions can be revoked while the app is running, and a stale "granted"
     * value turns straight into a SecurityException.
     */
    private val accessRefresh = MutableStateFlow(0)

    /**
     * Sort order comes from disk, not from memory.
     *
     * `stateIn` with an eager default matters here: `items` reads this inside
     * `flatMapLatest`, and DataStore's first emission is asynchronous, so without a
     * synchronous starting value the grid would briefly have no paging source at
     * all and render as empty on every cold start.
     */
    private val sortOrder: StateFlow<ShelfSortOrder> = preferences.shelfSortOrder
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = ShelfSortOrder.Default,
        )

    /**
     * Folders, kept as a map so a tile can resolve its folder without a SQL join.
     *
     * There are only ever a handful, so this is cheaper than widening the paging
     * query and changing the row type.
     */
    private val foldersById: StateFlow<Map<Long, Folder>> = repository.observeFolders()
        .map { folders -> folders.associateBy { it.id } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap(),
        )

    val uiState: StateFlow<ShelfUiState> = combine(
        repository.observeProgress(),
        combine(statusDismissed, isImporting) { dismissed, importing -> dismissed to importing },
        combine(
            isRefreshing,
            refreshResult,
            repository.observePickedCount(),
        ) { refreshing, result, picked -> Triple(refreshing, result, picked) },
        combine(
            repository.observeStateCounts(),
            repository.observeLastError(),
            accessRefresh,
            sortOrder,
        ) { counts, error, _, sort -> Triple(counts, error, sort) },
    ) { progress, flags, refresh, diagnostics ->
        val (dismissed, importing) = flags
        val (stateCounts, lastError, sort) = diagnostics
        val (refreshing, result, pickedCount) = refresh
        ShelfUiState(
            progress = progress,
            sortOrder = sort,
            statusDismissed = dismissed,
            isImporting = importing,
            isRefreshing = refreshing,
            refreshResult = result,
            pickedCount = pickedCount,
            access = repository.currentAccess(),
            stateCounts = stateCounts,
            lastError = lastError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShelfUiState(),
    )

    /**
     * The paged feed, with date headers inserted.
     *
     * `insertSeparators` runs over the paged stream, so grouping never requires
     * loading the whole library. `cachedIn` keeps the list alive across
     * configuration changes so rotating does not restart paging.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val items: Flow<PagingData<ShelfListItem>> =
        sortOrder
            .flatMapLatest { sort ->
                // Always everything. Filtering by folder or category is the Find
                // tab's job now, so the shelf stays a single honest chronology.
                repository.pagedShelf(ShelfFilter.All, sort)
                    .map { paging -> paging.map<Screenshot, ShelfListItem> { ShelfListItem.Item(it) } }
                    .map { paging ->
                        // Date headers only make sense in date order. Under a size
                        // sort the list crosses days constantly, which would put a
                        // header above almost every tile — and repeat a day's
                        // header, producing duplicate Paging keys and a crash.
                        if (!sort.groupsByDate) {
                            paging
                        } else {
                            paging.insertSeparators { before, after ->
                                val beforeDate = (before as? ShelfListItem.Item)?.screenshot?.dateAdded
                                val afterDate = (after as? ShelfListItem.Item)?.screenshot?.dateAdded

                                if (ShelfDateFormatter.needsHeaderBetween(beforeDate, afterDate)) {
                                    ShelfListItem.DateHeader(ShelfDateFormatter.label(afterDate!!))
                                } else {
                                    null
                                }
                            }
                        }
                    }
            }
            .cachedIn(viewModelScope)

    init {
        // Tier 1 warm-up. Idempotent, so it no-ops after the first run.
        viewModelScope.launch {
            immediateIndexer.warmUp(viewModelScope).join()
            // First launch: offer the unlock once the initial scan has a real count.
            maybeOfferUnlock()
        }
    }

    /** Called when the screen resumes, to pick up permission changes made in Settings. */
    fun onResumed() {
        accessRefresh.update { it + 1 }
        immediateIndexer.warmUp(viewModelScope)
    }

    /** Requeues everything that failed and kicks the pipeline again. */
    fun onRetryIndexing() {
        viewModelScope.launch {
            runCatching { repository.requeueFailed() }
            immediateIndexer.retry(viewModelScope)
        }
    }

    fun onSortSelected(order: ShelfSortOrder) {
        viewModelScope.launch { preferences.setShelfSortOrder(order) }
    }

    /**
     * Manual rescan.
     *
     * Reports the outcome rather than just spinning, because "I pressed refresh and
     * nothing visibly happened" is indistinguishable from "refresh is broken" — and
     * that ambiguity is what made the missing-screenshots bug so hard to pin down.
     */
    fun onRefresh() {
        if (isRefreshing.value) return

        isRefreshing.value = true
        viewModelScope.launch {
            val result = immediateIndexer.refreshNow()
            isRefreshing.value = false
            refreshResult.value = result

            // After the scan, not before: the count in the prompt has to be the one
            // the scan just produced, or it reads as stale.
            maybeOfferUnlock()
        }
    }

    fun onRefreshMessageShown() {
        refreshResult.value = null
    }

    // ---------------------------------------------------------------- upsell

    private val upsell = MutableStateFlow<UpsellPrompt?>(null)

    /**
     * Decides whether to offer the unlock after a scan.
     *
     * Only when there is something concrete to gain: more screenshots exist than the
     * free window can keep searchable. Someone with 30 screenshots is not missing
     * anything and must never be asked.
     */
    private suspend fun maybeOfferUnlock() {
        if (quota.isUnlimited()) return

        val found = runCatching { repository.discoveredCount() }.getOrDefault(0)
        if (found <= IndexingQuota.FREE_INDEX_LIMIT) return

        upsell.value = UpsellPrompt(
            foundCount = found,
            freeLimit = IndexingQuota.FREE_INDEX_LIMIT,
        )
    }

    fun onUpsellDismissed() {
        upsell.value = null
    }

    /**
     * Starts the purchase. Requires an Activity because Play's billing flow does.
     *
     * The dialog closes only on success: leaving it open after a cancelled or failed
     * payment means the user can try again without having to trigger another scan.
     */
    fun onUnlockRequested(activity: Activity) {
        viewModelScope.launch {
            val result = runCatching { billing.purchase(activity) }.getOrNull()

            if (result is PurchaseResult.Success || result is PurchaseResult.AlreadyOwned) {
                runCatching { quota.releaseAll() }
                immediateIndexer.retry(viewModelScope)
                upsell.value = null
            }
        }
    }

    fun onStatusDismissed() {
        statusDismissed.update { true }
    }

    /** Limited Mode: index newly hand-picked screenshots. */
    fun onImagesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return

        isImporting.value = true
        viewModelScope.launch {
            pickerImporter.import(uris)
            isImporting.value = false
        }
    }

    fun onRecategorise(screenshotId: Long, category: ScreenshotCategory) {
        viewModelScope.launch { repository.setCategory(screenshotId, category) }
    }

    val upsellPrompt: StateFlow<UpsellPrompt?> = upsell

    // ------------------------------------------------------ multi-select delete

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = selectedIds

    private val deletePending = MutableStateFlow<List<Long>>(emptyList())

    fun onTileLongPress(id: Long) {
        selectedIds.update { it + id }
    }

    fun onTileToggled(id: Long) {
        selectedIds.update { if (id in it) it - id else it + id }
    }

    fun onSelectionCleared() {
        selectedIds.value = emptySet()
    }

    /**
     * Deletes the current selection, in two stages.
     *
     * Not over-engineering: the rows are soft-deleted first so they leave the shelf
     * at once and land in the 30-day Recently Deleted window, and only then is the
     * system asked to confirm removing the files. Declining that dialog restores
     * them, so cancelling genuinely cancels instead of leaving the index and the
     * gallery disagreeing.
     *
     * Android only lets an app delete media it does not own with explicit user
     * confirmation, so the IntentSender is handed to the UI via [launch].
     */
    fun onDeleteSelected(launch: (IntentSender) -> Unit) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            deleter.moveToRecentlyDeleted(ids)
            deletePending.value = ids
            selectedIds.value = emptySet()

            // Trash rather than delete, so undo has something to restore. A permanent
            // delete would leave undo able to put back only a row pointing at a file
            // that no longer exists.
            val sender = runCatching { deleter.buildTrashRequest(ids, trash = true) }.getOrNull()
            if (sender != null) {
                launch(sender)
            } else {
                // Nothing for the system to confirm: either the rows are our own
                // picker-imported copies, or the device predates the API. In both
                // cases the file is untouched, so the row alone is the deletion.
                deletePending.value = emptyList()
                lastDeleted.value = ids
            }
        }
    }

    fun onDeletionConfirmed() {
        val ids = deletePending.value
        deletePending.value = emptyList()

        // Rows stay soft-deleted rather than hard-deleted. They sit in Recently
        // Deleted for the same window the system bin uses, so both halves of the
        // deletion remain recoverable.
        lastDeleted.value = ids
    }

    /** Declining the system dialog puts everything back. */
    fun onDeletionCancelled() {
        val ids = deletePending.value
        deletePending.value = emptyList()
        viewModelScope.launch { runCatching { deleter.restore(ids) } }
    }

    // ------------------------------------------------------------------- undo

    private val lastDeleted = MutableStateFlow<List<Long>>(emptyList())

    /** Non-empty while an undo is still offered. */
    val undoableDelete: StateFlow<List<Long>> = lastDeleted

    /**
     * Puts back what was just deleted.
     *
     * Two halves: the rows return from Recently Deleted, and the files come back out
     * of the system bin. The second needs its own confirmation on Android 11+, which
     * is unavoidable — taking files out of the bin is as much a media change as
     * putting them in.
     */
    fun onUndoDelete(launch: (IntentSender) -> Unit) {
        val ids = lastDeleted.value
        if (ids.isEmpty()) return
        lastDeleted.value = emptyList()

        viewModelScope.launch {
            deleter.restore(ids)

            val sender = runCatching { deleter.buildTrashRequest(ids, trash = false) }.getOrNull()
            if (sender != null) launch(sender)
        }
    }

    fun onUndoDismissed() {
        lastDeleted.value = emptyList()
    }

    // -------------------------------------------------------------- bulk move

    /** Folders, for the move picker. */
    val folders: StateFlow<List<Folder>> = repository.observeFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Moves everything selected into [folderId], or out of any folder when null.
     *
     * Exists because the only way to correct a wrong move used to be opening each
     * screenshot and changing it one at a time — and a mistake made in bulk needs to
     * be fixable in bulk.
     */
    fun onMoveSelectionToFolder(folderId: Long?) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            runCatching { repository.setFolderForAll(ids, folderId) }
            selectedIds.value = emptySet()
            movedCount.value = ids.size
        }
    }

    /** Creates a folder and moves the whole selection into it in one step. */
    fun onCreateFolderForSelection(name: String, icon: FolderIcon) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            val folder = repository.createFolder(name, icon) ?: return@launch
            runCatching { repository.setFolderForAll(ids, folder.id) }
            selectedIds.value = emptySet()
            movedCount.value = ids.size
        }
    }

    /**
     * Confirmation for a completed move.
     *
     * A bulk move with no feedback leaves the user unsure whether it worked — and the
     * moved screenshots have just vanished from wherever they were looking, which
     * reads as a failure rather than a success.
     */
    private val movedCount = MutableStateFlow(0)
    val lastMovedCount: StateFlow<Int> = movedCount

    fun onMoveMessageShown() {
        movedCount.value = 0
    }

    /**
     * False on Android 10 and below, where the file itself cannot be removed.
     *
     * Deleting another app's media there needs WRITE_EXTERNAL_STORAGE, which this app
     * deliberately does not request. Those devices get index-only removal, and the UI
     * says so rather than implying the file is gone.
     */
    fun canDeleteFiles(): Boolean = deleter.canDeleteFiles()

    /** Resolves the folder badge for a tile, if the screenshot is filed. */
    fun folderFor(screenshot: Screenshot): Folder? =
        screenshot.folderId?.let { foldersById.value[it] }

    /** Values the action layer needs, fetched lazily rather than held per tile. */
    suspend fun actionContext(screenshotId: Long): ActionContext =
        ActionContext(fullText = repository.textFor(screenshotId))
}

data class ActionContext(val fullText: String?)

/** What the upsell dialog needs to describe the trade honestly. */
data class UpsellPrompt(
    val foundCount: Int,
    val freeLimit: Int,
)

data class ShelfUiState(
    val progress: IndexProgress = IndexProgress.Complete,
    val sortOrder: ShelfSortOrder = ShelfSortOrder.Default,
    val statusDismissed: Boolean = false,
    val isImporting: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshResult: RescanResult? = null,
    val pickedCount: Int = 0,
    val access: MediaAccess = MediaAccess.DENIED,
    val stateCounts: List<IndexStateCount> = emptyList(),
    val lastError: String? = null,
) {
    val isIndexing: Boolean get() = !progress.isComplete

    /**
     * True when screenshots were found but none could be read.
     *
     * The distinction that matters: plenty of rows exist, yet zero reached the
     * indexed state and nothing is still queued as in-progress.
     */
    val hasIndexingProblem: Boolean
        get() = progress.total > 0 &&
            progress.indexed == 0 &&
            stateCounts.any { it.state == IndexState.FAILED || it.state == IndexState.SKIPPED }

    /** Compact, screenshot-friendly state breakdown. */
    val stateSummary: String
        get() = stateCounts.joinToString("  ") { "${it.state.name}=${it.count}" }

    val showStatusStrip: Boolean get() = isIndexing && !statusDismissed

    /** True when the shelf is genuinely empty rather than merely still indexing. */
    val isEmpty: Boolean get() = progress.total == 0 && !isIndexing
}
