package com.shelfie.feature.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfie.core.database.dao.CategoryCount
import com.shelfie.core.media.ImmediateIndexer
import com.shelfie.core.media.ScreenshotRepository
import com.shelfie.core.model.IndexProgress
import com.shelfie.core.model.MediaAccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Shelf state holder.
 *
 * Kicks off the Tier 1 warm-up on creation, so the first thing that happens when
 * the user lands on the shelf is that their newest screenshots start becoming
 * searchable. Everything older is handed to the background tiers.
 */
@HiltViewModel
class ShelfViewModel @Inject constructor(
    private val repository: ScreenshotRepository,
    private val immediateIndexer: ImmediateIndexer,
) : ViewModel() {

    val uiState: StateFlow<ShelfUiState> = combine(
        repository.observeProgress(),
        repository.observeCategoryCounts(),
    ) { progress, categories ->
        ShelfUiState(
            progress = progress,
            categories = categories,
            access = repository.currentAccess(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShelfUiState(),
    )

    init {
        // Idempotent: no-ops after the first run in this process.
        immediateIndexer.warmUp(viewModelScope)
    }
}

data class ShelfUiState(
    val progress: IndexProgress = IndexProgress.Complete,
    val categories: List<CategoryCount> = emptyList(),
    val access: MediaAccess = MediaAccess.DENIED,
) {
    /** Drives the non-blocking status strip. Informational only. */
    val isIndexing: Boolean get() = !progress.isComplete
}
