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
import com.shelfie.core.media.RescanResult
import com.shelfie.core.media.ScreenshotRepository
import com.shelfie.core.model.Folder
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
