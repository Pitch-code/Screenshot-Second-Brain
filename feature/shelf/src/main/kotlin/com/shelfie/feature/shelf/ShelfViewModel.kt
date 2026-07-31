package com.shelfie.feature.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.model.IndexProgress
import com.shelfie.core.model.IndexTier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Shelf state holder.
 *
 * At Phase 0 this exists to prove the full wiring works end to end —
 * Hilt injection into a Compose ViewModel, reading a Room Flow. The paged feed
 * and category chips arrive in Phase 2.
 */
@HiltViewModel
class ShelfViewModel @Inject constructor(
    screenshotDao: ScreenshotDao,
) : ViewModel() {

    val uiState: StateFlow<ShelfUiState> = combine(
        screenshotDao.observeTotalCount(),
        screenshotDao.observeIndexedCount(),
    ) { total, indexed ->
        ShelfUiState(
            progress = IndexProgress(
                indexed = indexed,
                total = total,
                tier = if (indexed >= total) IndexTier.IDLE else IndexTier.BACKLOG,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShelfUiState(),
    )
}

data class ShelfUiState(
    val progress: IndexProgress = IndexProgress.Complete,
)
