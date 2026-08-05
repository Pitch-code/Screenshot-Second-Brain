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
import com.shelfie.core.model.FolderIcon
import com.shelfie.core.model.Screenshot
import com.shelfie.core.model.ScreenshotCategory
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
            // "Not sorted yet" is deliberately absent.
            //
            // It is not a category anyone browses *to* — it is the absence of one, and
            // as the largest group it sat at the top of Find claiming the most
            // prominent position for the screenshots the app understood least.
            // Everything in it is still on the shelf, still searchable, and still
            // selectable there, so nothing becomes unreachable.
            categories = categories
                .filterNot { it.category == ScreenshotCategory.NOT_SORTED }
                .map { entry ->
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

    /**
     * The pending folder-delete confirmation, or null when none is open.
     *
     * Both counts travel together in one object, and the dialog exists only when this
     * does. That is the fix for a wrong number in the prompt: the count used to be a
     * separate flow that the screen read as soon as the dialog opened, so the dialog
     * could render before the query finished — showing zero, or worse, the count from
     * a previous selection. The title's folder count came straight from the live
     * selection meanwhile, so the two halves of the sentence could describe different
     * things.
     *
     * Counting first and opening afterwards makes that unrepresentable. A dialog about
     * permanently deleting someone's pictures is the last place to state a figure the
     * app has not actually established.
     */
    private val _folderDeletePrompt = MutableStateFlow<FolderDeletePrompt?>(null)
    val folderDeletePrompt: StateFlow<FolderDeletePrompt?> = _folderDeletePrompt

    fun onFolderLongPress(folderId: Long) {
        selectedFolderIds.update { it + folderId }
    }

    fun onFolderToggle(folderId: Long) {
        selectedFolderIds.update { if (folderId in it) it - folderId else it + folderId }
    }

    fun clearFolderSelection() {
        selectedFolderIds.value = emptySet()
        // A prompt describing a selection that no longer exists must not survive it.
        _folderDeletePrompt.value = null
    }

    /** A folder deletion awaiting confirmation, with what it would affect. */
    data class FolderDeletePrompt(val folderCount: Int, val screenshotCount: Int)

    /**
     * Counts what is at stake, before asking.
     *
     * Called when the confirmation opens rather than kept continuously up to date,
     * because it is only ever read at that one moment and a live count would mean a
     * query on every tap of a folder row.
     */
    fun onDeleteFoldersRequested() {
        val folderIds = selectedFolderIds.value.toList()
        if (folderIds.isEmpty()) return

        viewModelScope.launch {
            // Deliberately opens nothing if the count cannot be read. Asking someone
            // to confirm a permanent deletion without being able to say what it covers
            // is worse than appearing not to respond, and this is a local indexed
            // read — if it fails, the folder list on screen is not trustworthy either.
            val affected = runCatching { repository.screenshotIdsInFolders(folderIds) }
                .getOrNull()
                ?: return@launch

            _folderDeletePrompt.value = FolderDeletePrompt(
                // Snapshotted alongside the count so the title and the body can never
                // describe different selections.
                folderCount = folderIds.size,
                screenshotCount = affected.size,
            )
        }
    }

    fun onFolderDeleteDismissed() {
        _folderDeletePrompt.value = null
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
        _folderDeletePrompt.value = null
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
        _folderDeletePrompt.value = null
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

    /**
     * Creates an empty folder, with nothing to put in it yet.
     *
     * Folders could previously only be created while moving a screenshot, which meant
     * the only way to set up a filing scheme was to file something first. Someone who
     * knows they want "Bills" and "Warranties" before they start sorting had no way to
     * say so.
     */
    fun onCreateEmptyFolder(name: String, icon: FolderIcon) {
        viewModelScope.launch { runCatching { repository.createFolder(name, icon) } }
    }

    /**
     * A few screenshots to show on a browse card.
     *
     * A plain suspend function rather than another flow in the ui state: there is one
     * call per card that scrolls into view, and folding twenty of these into the state
     * would query every category on every count change whether visible or not.
     */
    suspend fun previewsFor(filter: ShelfFilter): List<Screenshot> =
        runCatching { repository.previews(filter, PREVIEW_COUNT) }.getOrDefault(emptyList())

    /** Recognised text for a result, used for the snippet and copy actions. */
    suspend fun textFor(id: Long): String? = repository.textFor(id)

    private companion object {
        const val DEBOUNCE_MILLIS = 200L

        /**
         * Previews per browse card.
         *
         * Enough to fill the strip past the edge of the screen, so it reads as "and
         * more" rather than as the complete contents of the folder.
         */
        const val PREVIEW_COUNT = 8
    }
}

data class FindUiState(
    val folders: List<ShelfChip> = emptyList(),
    val categories: List<ShelfChip> = emptyList(),
    val selectedFilter: ShelfFilter? = null,
)
