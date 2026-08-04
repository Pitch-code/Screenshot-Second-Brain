package com.shelfie.core.media

import android.content.IntentSender
import com.shelfie.core.model.Folder
import com.shelfie.core.model.FolderIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Multi-select, move and delete, shared by every screen that shows a grid of
 * screenshots.
 *
 * Extracted because the shelf had all of this and the Find tab had none of it, so
 * selecting inside a folder was impossible — and the obvious fix, copying it across,
 * would have meant two copies of a two-stage delete with system confirmations, an undo
 * window and a bulk move. That is precisely the kind of logic that drifts: a fix
 * applied to one copy and not the other produces a bug that only appears on one
 * screen, which is exactly what happened here in the first place.
 *
 * Constructed per ViewModel with that ViewModel's scope, rather than injected as a
 * singleton: a selection made on the shelf should not appear in the Find tab.
 */
class ScreenshotSelection(
    private val repository: ScreenshotRepository,
    private val deleter: ScreenshotDeleter,
    private val scope: CoroutineScope,
) {

    private val selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = selectedIds

    private val pendingDelete = MutableStateFlow<List<Long>>(emptyList())

    private val undoable = MutableStateFlow<List<Long>>(emptyList())

    /** Non-empty while an undo is still on offer. */
    val undoableDelete: StateFlow<List<Long>> = undoable

    private val moved = MutableStateFlow(0)

    /** Non-zero once a move completes, so the UI can confirm it. */
    val lastMovedCount: StateFlow<Int> = moved

    /** Folders available as move destinations. */
    val folders: StateFlow<List<Folder>> = repository.observeFolders()
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ------------------------------------------------------------- selecting

    fun onLongPress(id: Long) = selectedIds.update { it + id }

    fun onToggle(id: Long) = selectedIds.update { if (id in it) it - id else it + id }

    fun clear() {
        selectedIds.value = emptySet()
    }

    /** True when a tap should toggle selection rather than open the screenshot. */
    val isActive: Boolean get() = selectedIds.value.isNotEmpty()

    // --------------------------------------------------------------- deleting

    /**
     * Deletes the selection, in two stages.
     *
     * Rows are soft-deleted first so they leave the grid at once and land in the
     * 30-day Recently Deleted window, then the system is asked to confirm. Declining
     * restores them, so cancelling genuinely cancels rather than leaving the index and
     * the gallery disagreeing.
     *
     * Trashes rather than destroys, so [undo] has something real to restore.
     */
    fun delete(launch: (IntentSender) -> Unit) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return

        scope.launch {
            deleter.moveToRecentlyDeleted(ids)
            pendingDelete.value = ids
            selectedIds.value = emptySet()

            val sender = runCatching { deleter.buildTrashRequest(ids, trash = true) }.getOrNull()
            if (sender != null) {
                launch(sender)
            } else {
                // Nothing for the system to confirm: our own picker-imported copies,
                // or a device predating the API. The file is untouched either way.
                pendingDelete.value = emptyList()
                undoable.value = ids
            }
        }
    }

    fun onDeleteConfirmed() {
        val ids = pendingDelete.value
        pendingDelete.value = emptyList()

        // Left soft-deleted rather than hard-deleted, so both the row and the file
        // stay recoverable for comparable windows.
        undoable.value = ids
    }

    fun onDeleteCancelled() {
        val ids = pendingDelete.value
        pendingDelete.value = emptyList()
        scope.launch { runCatching { deleter.restore(ids) } }
    }

    /**
     * Puts back what was just deleted: the rows return from Recently Deleted, and the
     * files come back out of the system bin.
     *
     * The second half needs its own confirmation on Android 11+, which is unavoidable
     * — taking files out of the bin is as much a media change as putting them in.
     */
    fun undo(launch: (IntentSender) -> Unit) {
        val ids = undoable.value
        if (ids.isEmpty()) return
        undoable.value = emptyList()

        scope.launch {
            deleter.restore(ids)
            val sender = runCatching { deleter.buildTrashRequest(ids, trash = false) }.getOrNull()
            if (sender != null) launch(sender)
        }
    }

    fun onUndoDismissed() {
        undoable.value = emptyList()
    }

    /** False on Android 10 and below, where the file itself cannot be removed. */
    fun canDeleteFiles(): Boolean = deleter.canDeleteFiles()

    // ------------------------------------------------------------------ moving

    /** Moves the selection into [folderId], or out of any folder when null. */
    fun moveTo(folderId: Long?) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return

        scope.launch {
            runCatching { repository.setFolderForAll(ids, folderId) }
            selectedIds.value = emptySet()
            moved.value = ids.size
        }
    }

    /** Creates a folder and moves the whole selection into it in one step. */
    fun createFolderAndMove(name: String, icon: FolderIcon) {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return

        scope.launch {
            val folder = repository.createFolder(name, icon) ?: return@launch
            runCatching { repository.setFolderForAll(ids, folder.id) }
            selectedIds.value = emptySet()
            moved.value = ids.size
        }
    }

    fun onMoveMessageShown() {
        moved.value = 0
    }

    /** True when at least one selected screenshot is currently filed in a folder. */
    suspend fun selectionHasFiledItems(): Boolean {
        val ids = selectedIds.value.toList()
        if (ids.isEmpty()) return false
        return runCatching { repository.anyFiled(ids) }.getOrDefault(false)
    }
}
