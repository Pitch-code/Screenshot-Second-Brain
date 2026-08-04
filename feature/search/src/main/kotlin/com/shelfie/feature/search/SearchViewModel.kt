package com.shelfie.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.shelfie.core.designsystem.component.ShelfChip
import com.shelfie.core.media.ScreenshotDeleter
import com.shelfie.core.media.ScreenshotRepository
import com.shelfie.core.media.ScreenshotSelection
import com.shelfie.core.model.Folder
import com.shelfie.core.model.Screenshot
import com.shelfie.core.model.ShelfFilter
import com.shelfie.core.model.ShelfSortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the Find tab: browse by folder or category, and search inside text.
 *
 * Folders and categories live here rather than on the shelf because they answer the
 * same question the search box does — "where is that thing I remember?" — whereas
 * the shelf answers "what have I got, most recent first". Putting them together
 * means one place to look for something, instead of a filter row on one tab and a
 * search box on another.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ScreenshotRepository,
    deleter: ScreenshotDeleter,
) : ViewModel() {

    /**
     * The same multi-select as the shelf, from the same controller.
     *
     * Selecting inside a folder used to be impossible: all of this lived in the shelf
     * and nowhere else, so the Find tab could show a folder's contents but not act on
     * them. Sharing the controller rather than copying it means a fix to delete or
     * move behaviour applies to both screens by construction.
     */
    val selection = ScreenshotSelection(repository, deleter, viewModelScope)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val selectedFilter = MutableStateFlow<ShelfFilter?>(null)

    /**
     * Search results.
     *
     * Debounced by 200ms so typing does not fire a query per keystroke, and
     * `flatMapLatest` cancels the previous query the moment a newer one arrives.
     */
    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val results: Flow<PagingData<Screenshot>> = _query
        .debounce(DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .flatMapLatest { raw ->
            repository.search(raw) ?: flowOf(PagingData.empty())
        }
        .cachedIn(viewModelScope)

    /** Contents of the folder or category currently being browsed. */
    @OptIn(ExperimentalCoroutinesApi::class)
    // No distinctUntilChanged: StateFlow already conflates equal values.
    val browseItems: Flow<PagingData<Screenshot>> = selectedFilter
        .flatMapLatest { filter ->
            if (filter == null) {
                flowOf(PagingData.empty())
            } else {
                // Newest first regardless of the shelf's sort preference: inside a
                // folder the user is looking for something specific, and recency is
                // the most useful default. The shelf's ordering is its own concern.
                repository.pagedShelf(filter, ShelfSortOrder.NEWEST_FIRST)
            }
        }
        .cachedIn(viewModelScope)

    val uiState: StateFlow<FindUiState> = combine(
        repository.observeFolderCounts(),
        repository.observeCategoryCounts(),
        selectedFilter,
    ) { folders, categories, filter ->
        FindUiState(
            folders = folders.map { entry ->
                ShelfChip(
                    filter = ShelfFilter.InFolder(entry.folder.id),
                    count = entry.count,
                    folder = entry.folder,
                )
            },
            categories = categories.map { entry ->
                ShelfChip(
                    filter = ShelfFilter.Category(entry.category),
                    count = entry.count,
                    category = entry.category,
                )
            },
            selectedFilter = filter,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = FindUiState(),
    )

    /** Folders, for resolving a browsed folder's name in the header. */
    val folders: StateFlow<List<Folder>> = repository.observeFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun onQueryChange(value: String) {
        _query.value = value
        // Typing is a different intent from browsing, so a query clears the
        // folder view rather than filtering within it — which would silently
        // hide matches that live elsewhere.
        if (value.isNotBlank()) selectedFilter.value = null
    }

    fun onClear() {
        _query.value = ""
    }

    fun onFilterSelected(filter: ShelfFilter?) {
        selectedFilter.value = filter
        if (filter != null) _query.value = ""
    }

    fun onDeleteFolder(folderId: Long) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            if (selectedFilter.value == ShelfFilter.InFolder(folderId)) {
                selectedFilter.value = null
            }
        }
    }

    /** Recognised text for a result, used for the snippet and copy actions. */
    suspend fun textFor(id: Long): String? = repository.textFor(id)

    private companion object {
        const val DEBOUNCE_MILLIS = 200L
    }
}

data class FindUiState(
    val folders: List<ShelfChip> = emptyList(),
    val categories: List<ShelfChip> = emptyList(),
    val selectedFilter: ShelfFilter? = null,
)
