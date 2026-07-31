package com.shelfie.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfie.core.classify.EntityExtractor
import com.shelfie.core.classify.ExtractedEntities
import com.shelfie.core.media.ScreenshotRepository
import com.shelfie.core.model.Screenshot
import com.shelfie.core.model.ScreenshotCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Detail state holder.
 *
 * Takes the screenshot id through [load] rather than from navigation arguments,
 * so the sheet can be rendered as an overlay on top of the shelf. Routing detail
 * as its own destination would blank the screen behind the scrim.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: ScreenshotRepository,
) : ViewModel() {

    private val screenshotId = MutableStateFlow<Long?>(null)
    private val text = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DetailUiState> = screenshotId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(DetailUiState(isLoading = true))
            } else {
                combine(repository.observeScreenshot(id), text) { screenshot, recognisedText ->
                    DetailUiState(
                        screenshot = screenshot,
                        text = recognisedText,
                        // Re-extracted on the fly rather than stored: it is cheap
                        // on a single string, and an improved extractor then
                        // benefits every existing screenshot with no migration.
                        entities = recognisedText?.let(EntityExtractor::extract)
                            ?: ExtractedEntities.Empty,
                        isLoading = screenshot == null,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DetailUiState(isLoading = true),
        )

    fun load(id: Long) {
        if (screenshotId.value == id) return

        screenshotId.value = id
        text.value = null
        viewModelScope.launch { text.value = repository.textFor(id) }
    }

    fun onCategoryChanged(category: ScreenshotCategory) {
        val id = screenshotId.value ?: return
        viewModelScope.launch { repository.setCategory(id, category) }
    }

    /**
     * Creates a standing rule from this screenshot.
     *
     * Offered at the moment the user notices a wrong category, because that is
     * the only moment they are motivated to fix it — and one rule then corrects
     * every future screenshot containing the same term.
     */
    fun onCreateRule(keyword: String, category: ScreenshotCategory) {
        val id = screenshotId.value ?: return
        viewModelScope.launch {
            repository.addRule(keyword, category)
            repository.setCategory(id, category)
        }
    }
}

data class DetailUiState(
    val screenshot: Screenshot? = null,
    val text: String? = null,
    val entities: ExtractedEntities = ExtractedEntities.Empty,
    val isLoading: Boolean = false,
)
