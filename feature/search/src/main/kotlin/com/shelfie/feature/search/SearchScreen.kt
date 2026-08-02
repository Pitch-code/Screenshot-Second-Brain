package com.shelfie.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.shelfie.core.designsystem.category.icon
import com.shelfie.core.designsystem.category.labelRes
import com.shelfie.core.designsystem.component.EmptyState
import com.shelfie.core.designsystem.component.HighlightedText
import com.shelfie.core.designsystem.component.ScreenshotTile
import com.shelfie.core.designsystem.component.ShelfChip
import com.shelfie.core.model.Screenshot
import com.shelfie.core.model.SearchQuery
import com.shelfie.core.model.ShelfFilter

/**
 * The Find tab: folders and categories to browse, plus search.
 *
 * Three mutually exclusive states, because trying to show all of them at once was
 * what made the old layout confusing:
 *  - nothing typed, nothing selected -> the browsable folder and category list
 *  - a folder or category selected   -> its contents
 *  - something typed                 -> search results
 */
@Composable
fun SearchScreen(
    onScreenshotClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val committedQuery by viewModel.query.collectAsStateWithLifecycle()
    val results = viewModel.results.collectAsLazyPagingItems()
    val browseItems = viewModel.browseItems.collectAsLazyPagingItems()
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    /*
     * The field's own text is local state, and the ViewModel is told about it
     * rather than owning it.
     *
     * This matters: when a TextField's `value` comes back from a Flow, every
     * keystroke round-trips through the ViewModel and returns a frame later. The
     * IME and the composable then disagree about the buffer, and fast typing shows
     * up as reordered or silently dropped characters. Keeping the text local means
     * the field is always authoritative about what was typed.
     */
    var fieldText by remember { mutableStateOf(committedQuery) }

    // Keeps the field honest when the ViewModel clears the query for its own
    // reasons, e.g. because a folder was opened.
    if (committedQuery.isEmpty() && fieldText.isNotEmpty() && state.selectedFilter != null) {
        fieldText = ""
    }

    val selected = state.selectedFilter
    val browsingFolder = (selected as? ShelfFilter.InFolder)
        ?.let { f -> folders.firstOrNull { it.id == f.folderId } }
    val browsingCategoryRes = (selected as? ShelfFilter.Category)?.category?.labelRes

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = fieldText,
            onValueChange = { value ->
                fieldText = value
                viewModel.onQueryChange(value)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (fieldText.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            fieldText = ""
                            viewModel.onClear()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.search_clear),
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        when {
            fieldText.isNotBlank() -> SearchResults(
                query = committedQuery,
                results = results,
                loadText = viewModel::textFor,
                onScreenshotClick = onScreenshotClick,
            )

            selected != null -> Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.onFilterSelected(null) }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.find_back),
                        )
                    }
                    Text(
                        text = browsingFolder?.name
                            ?: browsingCategoryRes?.let { stringResource(it) }
                            ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                }

                if (browseItems.itemCount == 0) {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = stringResource(R.string.find_folder_empty_title),
                        description = stringResource(R.string.find_folder_empty_body),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(FIND_GRID_COLUMNS),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            count = browseItems.itemCount,
                            key = browseItems.itemKey { it.id },
                        ) { index ->
                            browseItems[index]?.let { screenshot ->
                                ScreenshotTile(
                                    screenshot = screenshot,
                                    onClick = { onScreenshotClick(screenshot.id) },
                                    onAction = {},
                                    folder = browsingFolder,
                                )
                            }
                        }
                    }
                }
            }

            else -> BrowseList(
                folders = state.folders,
                categories = state.categories,
                onSelect = viewModel::onFilterSelected,
                onDeleteFolder = viewModel::onDeleteFolder,
            )
        }
    }
}

/** Folders first, then automatic categories. */
@Composable
private fun BrowseList(
    folders: List<ShelfChip>,
    categories: List<ShelfChip>,
    onSelect: (ShelfFilter) -> Unit,
    onDeleteFolder: (Long) -> Unit,
) {
    if (folders.isEmpty() && categories.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Search,
            title = stringResource(R.string.search_idle_title),
            description = stringResource(R.string.search_idle_body),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (folders.isNotEmpty()) {
            item(key = "folders-header") {
                SectionLabel(stringResource(R.string.find_section_folders))
            }
            items(items = folders, key = { it.key }) { chip ->
                BrowseRow(
                    chip = chip,
                    onClick = { onSelect(chip.filter) },
                    onDelete = chip.folder?.let { folder -> { onDeleteFolder(folder.id) } },
                )
            }
        }

        if (categories.isNotEmpty()) {
            item(key = "categories-header") {
                SectionLabel(stringResource(R.string.find_section_categories))
            }
            items(items = categories, key = { it.key }) { chip ->
                BrowseRow(chip = chip, onClick = { onSelect(chip.filter) }, onDelete = null)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun BrowseRow(
    chip: ShelfChip,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val label = chip.folder?.name ?: chip.category?.let { stringResource(it.labelRes) } ?: return
    val leading = chip.folder?.icon?.icon ?: chip.category?.icon

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            leading?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = pluralStringResource(
                        com.shelfie.core.designsystem.R.plurals.folder_item_count,
                        chip.count,
                        chip.count,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(
                            com.shelfie.core.designsystem.R.string.folder_delete,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    results: androidx.paging.compose.LazyPagingItems<Screenshot>,
    loadText: suspend (Long) -> String?,
    onScreenshotClick: (Long) -> Unit,
) {
    if (results.itemCount == 0) {
        EmptyState(
            icon = Icons.Outlined.Search,
            title = stringResource(R.string.search_no_results_title, query),
            description = stringResource(R.string.search_no_results_body),
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = results.itemCount,
            key = results.itemKey { it.id },
        ) { index ->
            results[index]?.let { screenshot ->
                SearchResultRow(
                    screenshot = screenshot,
                    query = query,
                    loadText = { loadText(screenshot.id) },
                    onClick = { onScreenshotClick(screenshot.id) },
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    screenshot: Screenshot,
    query: String,
    loadText: suspend () -> String?,
    onClick: () -> Unit,
) {
    // Text is fetched per visible row rather than joined into the paged query, so
    // scrolling never drags kilobytes of OCR text per item into memory.
    val text by produceState<String?>(initialValue = null, screenshot.id) {
        value = loadText()
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = screenshot.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 56.dp, height = 84.dp),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = screenshot.category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(screenshot.category.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                screenshot.primaryValue?.let { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }

                // Highlighting the matched words is what proves the index works,
                // rather than leaving the user to guess why this row appeared.
                val snippet = text?.let { SearchQuery.snippet(it, query) }
                if (snippet != null) {
                    HighlightedText(
                        text = snippet,
                        query = query,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private const val FIND_GRID_COLUMNS = 2
