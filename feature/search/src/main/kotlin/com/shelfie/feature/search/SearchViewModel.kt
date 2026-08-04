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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.IntentSender
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
    private val deleter: ScreenshotDeleter,
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

    // ---- Folder selection -------------------------------------------------------
    //
    // Folders used to carry a permanent ✕, which put an irreversible action one
    // stray tap away from the row you tap to open the folder — and it fired with no
    // confirmation at all. Deleting is now something you have to enter a mode to do,
    // matching how selecting screenshots already works on this screen.

    private val selectedFolderIds = MutableStateFlow<Set<Long>>(emptySet())
    val folderSelection: StateFlow<Set<Long>> = selectedFolderIds

    /** Screenshots inside the current folder selection, for the confirmation text. */
    private val _affectedScreenshotCount = MutableStateFlow(0)
    val affectedScreenshotCount: StateFlow<Int> = _affectedScreenshotCount

    fun onFolderLongPress(folderId: Long) {
        selectedFolderIds.update { it + folderId }
    }

    fun onFolderToggle(folderId: Long) {
        selectedFolderIds.update { if (folderId in it) it - folderId else it + folderId }
    }

    fun clearFolderSelection() {
        selectedFolderIds.value = emptySet()
    }

    /**
     * Counts what is at stake, before asking.
     *
     * Called when the confirmation opens rather than kept continuously up to date,
     * because it is only ever read at that one moment and a live count would mean a
     * query on every tap of a folder row.
     */
    fun onDeleteFoldersRequested() {
        viewModelScope.launch {
            _affectedScreenshotCount.value =
                runCatching { repository.screenshotIdsInFolders(selectedFolderIds.value.toList()) }
                    .getOrDefault(emptyList())
                    .size
        }
    }

    /**
     * Deletes the folders and keeps every screenshot.
     *
     * The screenshots return to their automatic category, which is what the app has
     * always done on folder deletion. Offered alongside the destructive option
     * because "I no longer want this grouping" and "I no longer want these pictures"
     * are completely different intentions, and a single confirm button would force
     * the second on anyone who meant the first.
     */
    fun onDeleteFoldersOnly() {
        val ids = selectedFolderIds.value.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            runCatching { repository.deleteFolders(ids) }
            leaveDeletedFolders(ids)
            selectedFolderIds.value = emptySet()
        }
    }

    /**
     * Deletes the folders and permanently deletes everything inside them.
     *
     * Uses the permanent request rather than the bin used elsewhere, because the
     * stated intent is that these pictures stop existing, and binning them would
     * leave them in the gallery's recently-deleted for a month.
     *
     * The folder rows are removed only *after* the system confirms. Deleting them
     * first would mean a cancelled dialog had still destroyed the folders, leaving
     * their screenshots scattered back into categories with no way to tell what had
     * been grouped — a cancel that changed things.
     *
     * @param launch shows the system's own confirmation, which Android requires for
     *   deleting media this app did not create. Two prompts is unavoidable; ours
     *   exists because the system's cannot mention folders.
     */
    fun onDeleteFoldersAndScreenshots(launch: (IntentSender) -> Unit) {
        val folderIds = selectedFolderIds.value.toList()
        if (folderIds.isEmpty()) return

        viewModelScope.launch {
            val screenshotIds = runCatching { repository.screenshotIdsInFolders(folderIds) }
                .getOrNull()
                ?: return@launch

            if (screenshotIds.isEmpty()) {
                // Nothing inside, so there is nothing to ask about.
                runCatching { repository.deleteFolders(folderIds) }
                leaveDeletedFolders(folderIds)
                selectedFolderIds.value = emptySet()
                return@launch
            }

            val sender = runCatching { deleter.buildDeleteRequest(screenshotIds) }.getOrNull()

            if (sender == null) {
                // No system request available — below API 30, or an imported-only
                // selection this app has no file access to. Removing the folders and
                // index rows is still honest; the files simply stay on the device.
                pendingPermanentDelete = folderIds to screenshotIds
                onPermanentDeleteConfirmed(filesRemoved = false)
                return@launch
            }

            pendingPermanentDelete = folderIds to screenshotIds
            launch(sender)
        }
    }

    /** Held between issuing the system request and its result coming back. */
    private var pendingPermanentDelete: Pair<List<Long>, List<Long>>? = null

    fun onPermanentDeleteConfirmed(filesRemoved: Boolean = true) {
        val (folderIds, screenshotIds) = pendingPermanentDelete ?: return
        pendingPermanentDelete = null

        viewModelScope.launch {
            // Guarded individually. Removing files touches the media store and can
            // fail on its own — a revoked permission, a file already gone — and an
            // exception escaping a viewModelScope coroutine takes the whole app down.
            // The folders should still go in that case, since that is the part the
            // user was asked about and the part this app fully controls.
            if (filesRemoved) runCatching { deleter.finalizeDeletion(screenshotIds) }
            runCatching { repository.deleteFolders(folderIds) }
            leaveDeletedFolders(folderIds)
            selectedFolderIds.value = emptySet()
        }
    }

    fun onPermanentDeleteCancelled() {
        // Nothing to undo: by design nothing was changed before the system asked.
        pendingPermanentDelete = null
    }

    /** Stops browsing a folder that has just ceased to exist. */
    private fun leaveDeletedFolders(deleted: List<Long>) {
        val current = selectedFilter.value
        if (current is ShelfFilter.InFolder && current.folderId in deleted) {
            selectedFilter.value = null
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
