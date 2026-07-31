package com.shelfie.feature.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.shelfie.core.database.dao.CategoryCount
import com.shelfie.core.media.ImmediateIndexer
import com.shelfie.core.media.ScreenshotRepository
import com.shelfie.core.model.IndexProgress
import com.shelfie.core.model.MediaAccess
import com.shelfie.core.model.ScreenshotAction
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
    private val immediateIndexer: ImmediateIndexer,
) : ViewModel() {

    private val selectedCategory = MutableStateFlow<ScreenshotCategory?>(null)
    private val statusDismissed = MutableStateFlow(false)

    val uiState: StateFlow<ShelfUiState> = combine(
        repository.observeProgress(),
        repository.observeCategoryCounts(),
        selectedCategory,
        statusDismissed,
    ) { progress, categories, selected, dismissed ->
        ShelfUiState(
            progress = progress,
            categories = categories,
            selectedCategory = selected,
            statusDismissed = dismissed,
            access = repository.currentAccess(),
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
     * configuration changes so rotating the device does not restart paging.
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
        .map { paging -> paging.map<com.shelfie.core.model.Screenshot, ShelfListItem> { ShelfListItem.Item(it) } }
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

    fun onCategorySelected(category: ScreenshotCategory?) {
        selectedCategory.value = category
    }

    fun onStatusDismissed() {
        statusDismissed.update { true }
    }

    /** Records a manual re-categorisation and offers to make it a standing rule. */
    fun onRecategorise(screenshotId: Long, category: ScreenshotCategory) {
        viewModelScope.launch { repository.setCategory(screenshotId, category) }
    }

    /** Values the action layer needs, fetched lazily rather than held per tile. */
    suspend fun actionContext(screenshotId: Long): ActionContext {
        val text = repository.textFor(screenshotId)
        return ActionContext(fullText = text)
    }
}

data class ActionContext(val fullText: String?)

data class ShelfUiState(
    val progress: IndexProgress = IndexProgress.Complete,
    val categories: List<CategoryCount> = emptyList(),
    val selectedCategory: ScreenshotCategory? = null,
    val statusDismissed: Boolean = false,
    val access: MediaAccess = MediaAccess.DENIED,
) {
    val isIndexing: Boolean get() = !progress.isComplete
    val showStatusStrip: Boolean get() = isIndexing && !statusDismissed
}

/** Actions available without opening the detail sheet. */
val quickActions: Set<ScreenshotAction> = setOf(
    ScreenshotAction.COPY_CODE,
    ScreenshotAction.OPEN_LINK,
    ScreenshotAction.DIAL_NUMBER,
)
