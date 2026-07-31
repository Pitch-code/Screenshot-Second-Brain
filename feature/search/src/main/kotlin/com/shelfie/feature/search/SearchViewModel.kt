package com.shelfie.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.shelfie.core.media.ScreenshotRepository
import com.shelfie.core.model.Screenshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ScreenshotRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

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

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onClear() {
        _query.value = ""
    }

    /** Recognised text for a result, used for the snippet and copy actions. */
    suspend fun textFor(id: Long): String? = repository.textFor(id)

    private companion object {
        const val DEBOUNCE_MILLIS = 200L
    }
}
