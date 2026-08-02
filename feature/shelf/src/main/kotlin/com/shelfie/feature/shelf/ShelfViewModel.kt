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
) : ViewModel() {

    private val selectedFilter = MutableStateFlow<ShelfFilter>(ShelfFilter.All)
    private val statusDismissed = MutableStateFlow(false)
    private val isImporting = MutableStateFlow(false)

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
        combine(
            repository.observeCategoryCounts(),
            repository.observeFolderCounts(),
        ) { categories, folders ->
            // Folders first: the user's own filing outranks the app's guesses.
            val folderChips = folders.map { entry ->
                ShelfChip(
                    filter = ShelfFilter.InFolder(entry.folder.id),
                    count = entry.count,
                    folder = entry.folder,
                )
            }
            val categoryChips = categories.map { entry ->
                ShelfChip(
                    filter = ShelfFilter.Category(entry.category),
                    count = entry.count,
                    category = entry.category,
                )
            }
            folderChips + categoryChips
        },
        combine(selectedFilter, statusDismissed, isImporting) { a, b, c -> Triple(a, b, c) },
        repository.observePickedCount(),
        combine(
            repository.observeStateCounts(),
            repository.observeLastError(),
            accessRefresh,
            sortOrder,
        ) { counts, error, _, sort -> Triple(counts, error, sort) },
    ) { progress, chips, (selected, dismissed, importing), pickedCount, diagnostics ->
        val (stateCounts, lastError, sort) = diagnostics
        ShelfUiState(
            progress = progress,
            chips = chips,
            selectedFilter = selected,
            sortOrder = sort,
            statusDismissed = dismissed,
            isImporting = importing,
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
        combine(selectedFilter, sortOrder) { filter, sort -> filter to sort }
            .distinctUntilChanged()
            .flatMapLatest { (filter, sort) ->
                repository.pagedShelf(filter, sort)
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
        immediateIndexer.warmUp(viewModelScope)
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

    fun onFilterSelected(filter: ShelfFilter) {
        selectedFilter.value = filter
    }

    fun onSortSelected(order: ShelfSortOrder) {
        viewModelScope.launch { preferences.setShelfSortOrder(order) }
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

    /** Resolves the folder badge for a tile, if the screenshot is filed. */
    fun folderFor(screenshot: Screenshot): Folder? =
        screenshot.folderId?.let { foldersById.value[it] }

    /** Values the action layer needs, fetched lazily rather than held per tile. */
    suspend fun actionContext(screenshotId: Long): ActionContext =
        ActionContext(fullText = repository.textFor(screenshotId))
}

data class ActionContext(val fullText: String?)

data class ShelfUiState(
    val progress: IndexProgress = IndexProgress.Complete,
    val chips: List<ShelfChip> = emptyList(),
    val selectedFilter: ShelfFilter = ShelfFilter.All,
    val sortOrder: ShelfSortOrder = ShelfSortOrder.Default,
    val statusDismissed: Boolean = false,
    val isImporting: Boolean = false,
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
