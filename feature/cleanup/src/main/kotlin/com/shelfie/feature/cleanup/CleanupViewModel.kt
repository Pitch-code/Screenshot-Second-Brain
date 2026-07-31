package com.shelfie.feature.cleanup

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfie.core.media.CleanupAnalyzer
import com.shelfie.core.media.CleanupReport
import com.shelfie.core.media.ScreenshotDeleter
import com.shelfie.core.model.Screenshot
import dagger.hilt.android.lifecycle.HiltViewModel
import com.shelfie.core.designsystem.component.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CleanupViewModel @Inject constructor(
    private val analyzer: CleanupAnalyzer,
    private val deleter: ScreenshotDeleter,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CleanupUiState(canDeleteFiles = deleter.canDeleteFiles()),
    )
    val state = _state.asStateFlow()

    /** Ids awaiting the system delete confirmation. */
    private var pendingIds: List<Long> = emptyList()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val report = analyzer.analyze()
            _state.update { it.copy(report = report, isLoading = false) }
        }
    }

    fun onCategoryOpened(group: CleanupGroup) {
        _state.update { it.copy(openGroup = group) }
    }

    fun onCategoryClosed() {
        _state.update { it.copy(openGroup = null, selectedIds = emptySet()) }
    }

    fun onToggleSelected(id: Long) {
        _state.update { current ->
            val selected = current.selectedIds.toMutableSet()
            if (!selected.add(id)) selected.remove(id)
            current.copy(selectedIds = selected)
        }
    }

    fun onSelectAll(ids: List<Long>) {
        _state.update { it.copy(selectedIds = ids.toSet()) }
    }

    fun onClearSelection() {
        _state.update { it.copy(selectedIds = emptySet()) }
    }

    /**
     * Starts deletion.
     *
     * The rows are soft-deleted first so the action is immediately reversible,
     * then the system confirmation is requested to actually reclaim the storage.
     */
    fun onDeleteSelected(onConfirmationRequired: (IntentSender) -> Unit) {
        val ids = _state.value.selectedIds.toList()
        if (ids.isEmpty()) return

        viewModelScope.launch {
            deleter.moveToRecentlyDeleted(ids)
            pendingIds = ids

            val request = deleter.buildDeleteRequest(ids)
            if (request != null) {
                onConfirmationRequired(request)
            } else {
                // No system confirmation available: on Android 10 and below we
                // cannot delete another app's media without a broad write
                // permission we deliberately do not request. The rows leave the
                // index, but the files stay.
                _state.update {
                    it.copy(
                        selectedIds = emptySet(),
                        openGroup = null,
                        lastMessage = UiMessage.Text(
                            if (deleter.canDeleteFiles()) {
                                R.string.cleanup_removed_from_shelfie
                            } else {
                                R.string.cleanup_removed_needs_android_11
                            },
                        ),
                    )
                }
                refresh()
            }
        }
    }

    /** The user accepted the system delete dialog. */
    fun onDeletionConfirmed() {
        val ids = pendingIds
        pendingIds = emptyList()

        viewModelScope.launch {
            val removed = deleter.finalizeDeletion(ids)
            _state.update {
                it.copy(
                    selectedIds = emptySet(),
                    openGroup = null,
                    lastMessage = UiMessage.Plural(R.plurals.cleanup_deleted, removed, listOf(removed)),
                )
            }
            refresh()
        }
    }

    /** The user declined the system delete dialog, so restore the soft delete. */
    fun onDeletionCancelled() {
        val ids = pendingIds
        pendingIds = emptyList()

        viewModelScope.launch {
            deleter.restore(ids)
            _state.update { it.copy(selectedIds = emptySet(), lastMessage = null) }
            refresh()
        }
    }

    fun onMessageShown() {
        _state.update { it.copy(lastMessage = null) }
    }
}

enum class CleanupGroup { DUPLICATES, BLURRY, OLD }

data class CleanupUiState(
    val report: CleanupReport = CleanupReport(),
    val isLoading: Boolean = true,
    val openGroup: CleanupGroup? = null,
    val selectedIds: Set<Long> = emptySet(),
    val canDeleteFiles: Boolean = true,
    val lastMessage: UiMessage? = null,
) {
    /** Items in the currently open group, excluding any marked to keep. */
    fun itemsFor(group: CleanupGroup): List<Screenshot> = when (group) {
        CleanupGroup.DUPLICATES -> report.duplicateGroups.flatMap { it.removable }
        CleanupGroup.BLURRY -> report.blurry
        CleanupGroup.OLD -> report.oldAndUnopened
    }
}
