package com.shelfie.feature.shelf

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.shelfie.core.database.dao.CategoryCount
import com.shelfie.core.database.dao.IndexStateCount
import com.shelfie.core.model.IndexState
import com.shelfie.core.media.ImmediateIndexer
import com.shelfie.core.media.PickerImporter
import com.shelfie.core.media.ScreenshotRepository
import com.shelfie.core.model.IndexProgress
import com.shelfie.core.model.MediaAccess
import com.shelfie.core.model.Screenshot
import com.shelfie.core.model.ScreenshotCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<ScreenshotCategory?>(null)
    private val statusDismissed = MutableStateFlow(false)
    private val isImporting = MutableStateFlow(false)

    /**
     * Access is re-read on every refresh rather than cached.
     *
     * Permissions can be revoked while the app is running, and a stale "granted"
     * value turns straight into a SecurityException.
     */
    private val accessRefresh = MutableStateFlow(0)

    val uiState: StateFlow<ShelfUiState> = combine(
        repository.observeProgress(),
        repository.observeCategoryCounts(),
        combine(selectedCategory, statusDismissed, isImporting) { a, b, c -> Triple(a, b, c) },
        repository.observePickedCount(),
        combine(
            repository.observeStateCounts(),
            repository.observeLastError(),
            accessRefresh,
        ) { counts, error, _ -> counts to error },
    ) { progress, categories, (selected, dismissed, importing), pickedCount, diagnostics ->
        val (stateCounts, lastError) = diagnostics
        ShelfUiState(
            progress = progress,
            categories = categories,
            selectedCategory = selected,
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
    val items: Flow<PagingData<ShelfListItem>> = selectedCategory
        .flatMapLatest { category ->
            if (category == null) {
                repository.pagedShelf()
            } else {
                repository.pagedByCategory(category)
            }
        }
        .map { paging -> paging.map<Screenshot, ShelfListItem> { ShelfListItem.Item(it) } }
        .map { paging ->
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

    fun onCategorySelected(category: ScreenshotCategory?) {
        selectedCategory.value = category
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

    /** Values the action layer needs, fetched lazily rather than held per tile. */
    suspend fun actionContext(screenshotId: Long): ActionContext =
        ActionContext(fullText = repository.textFor(screenshotId))
}

data class ActionContext(val fullText: String?)

data class ShelfUiState(
    val progress: IndexProgress = IndexProgress.Complete,
    val categories: List<CategoryCount> = emptyList(),
    val selectedCategory: ScreenshotCategory? = null,
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
