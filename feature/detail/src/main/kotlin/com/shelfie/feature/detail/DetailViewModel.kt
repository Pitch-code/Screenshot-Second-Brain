package com.shelfie.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfie.core.classify.EntityExtractor
import com.shelfie.core.classify.ExtractedEntities
import com.shelfie.core.media.ScreenshotMetadata
import com.shelfie.core.media.ScreenshotMetadataReader
import com.shelfie.core.media.ScreenshotRepository
import com.shelfie.core.model.Folder
import com.shelfie.core.model.FolderIcon
import com.shelfie.core.model.Screenshot
import com.shelfie.core.model.ScreenshotCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
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
    private val metadataReader: ScreenshotMetadataReader,
) : ViewModel() {

    private val screenshotId = MutableStateFlow<Long?>(null)
    private val text = MutableStateFlow<String?>(null)
    private val metadata = MutableStateFlow<ScreenshotMetadata?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DetailUiState> = screenshotId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(DetailUiState(isLoading = true))
            } else {
                combine(
                    repository.observeScreenshot(id),
                    text,
                    repository.observeFolders(),
                    metadata,
                ) { screenshot, recognisedText, folders, fileFacts ->
                    DetailUiState(
                        screenshot = screenshot,
                        text = recognisedText,
                        metadata = fileFacts,
                        // Re-extracted on the fly rather than stored: it is cheap
                        // on a single string, and an improved extractor then
                        // benefits every existing screenshot with no migration.
                        entities = recognisedText?.let(EntityExtractor::extract)
                            ?: ExtractedEntities.Empty,
                        folders = folders,
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
        metadata.value = null
        viewModelScope.launch { text.value = repository.textFor(id) }
    }

    /**
     * Loads the file facts, on first opening the details panel.
     *
     * Not part of [load], because it is a MediaStore query and the overwhelming
     * majority of opens never expand details — paying for it on every tap of a
     * screenshot would be a query per tap for something usually unseen.
     */
    fun loadMetadata() {
        if (metadata.value != null) return
        val id = screenshotId.value ?: return

        viewModelScope.launch {
            val mediaStoreId = repository.observeScreenshot(id).firstOrNull()?.mediaStoreId
                ?: return@launch
            metadata.value = runCatching { metadataReader.read(mediaStoreId) }.getOrNull()
        }
    }

    fun onCategoryChanged(category: ScreenshotCategory) {
        val id = screenshotId.value ?: return
        viewModelScope.launch { repository.setCategory(id, category) }
    }

    /** Files this screenshot into an existing folder, or unfiles it when null. */
    fun onFolderChanged(folderId: Long?) {
        val id = screenshotId.value ?: return
        viewModelScope.launch { repository.setFolder(id, folderId) }
    }

    /**
     * Creates a folder and immediately files this screenshot into it.
     *
     * One step rather than two, because the user only reached this dialog in order
     * to move *this* screenshot — creating an empty folder and leaving them to move
     * it themselves would be a pointless extra tap.
     */
    fun onCreateFolderAndFile(name: String, icon: FolderIcon) {
        val id = screenshotId.value ?: return
        viewModelScope.launch {
            val folder = repository.createFolder(name, icon) ?: return@launch
            repository.setFolder(id, folder.id)
        }
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
    val metadata: ScreenshotMetadata? = null,
    val entities: ExtractedEntities = ExtractedEntities.Empty,
    val folders: List<Folder> = emptyList(),
    val isLoading: Boolean = false,
) {
    /** The folder this screenshot is filed in, if any. */
    val folder: Folder?
        get() = screenshot?.folderId?.let { id -> folders.firstOrNull { it.id == id } }
}
