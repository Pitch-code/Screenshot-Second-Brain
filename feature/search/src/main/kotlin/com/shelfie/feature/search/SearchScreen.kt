package com.shelfie.feature.search

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.shelfie.core.designsystem.component.FolderCreateDialog
import com.shelfie.core.designsystem.component.MoveToFolderDialog
import com.shelfie.core.designsystem.component.SelectionBar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    val selectedIds by viewModel.selection.selection.collectAsStateWithLifecycle()
    val moveFolders by viewModel.selection.folders.collectAsStateWithLifecycle()
    val undoable by viewModel.selection.undoableDelete.collectAsStateWithLifecycle()
    val movedCount by viewModel.selection.lastMovedCount.collectAsStateWithLifecycle()

    var showMoveDialog by remember { mutableStateOf(false) }
    var showCreateFolder by remember { mutableStateOf(false) }
    var anyFiled by remember { mutableStateOf(false) }

    val selectedFolderIds by viewModel.folderSelection.collectAsStateWithLifecycle()
    val affectedScreenshotCount by viewModel.affectedScreenshotCount.collectAsStateWithLifecycle()
    var showFolderDeleteConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.selection.onDeleteConfirmed()
        } else {
            viewModel.selection.onDeleteCancelled()
        }
    }
    val undoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { }

    val folderDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onPermanentDeleteConfirmed()
        } else {
            viewModel.onPermanentDeleteCancelled()
        }
    }

    BackHandler(enabled = selectedIds.isNotEmpty(), onBack = viewModel.selection::clear)

    BackHandler(
        enabled = selectedFolderIds.isNotEmpty(),
        onBack = viewModel::clearFolderSelection,
    )

    if (showFolderDeleteConfirm) {
        FolderDeleteDialog(
            folderCount = selectedFolderIds.size,
            screenshotCount = affectedScreenshotCount,
            onDismiss = { showFolderDeleteConfirm = false },
            onDeleteFoldersOnly = {
                showFolderDeleteConfirm = false
                viewModel.onDeleteFoldersOnly()
            },
            onDeleteEverything = {
                showFolderDeleteConfirm = false
                viewModel.onDeleteFoldersAndScreenshots { sender ->
                    folderDeleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            },
        )
    }

    // Back leaves the folder you are browsing rather than the whole tab.
    //
    // Browsing into a folder is view-model state, not a navigation destination, so
    // the system back gesture used to fall straight through to the nav stack and
    // land on the shelf — losing the folder, the tab, and any idea of where you
    // were. Registered after the selection handler so that clearing a selection
    // still wins while one is active, since Compose offers the gesture to the most
    // recently registered enabled handler first.
    BackHandler(
        enabled = selectedIds.isEmpty() && state.selectedFilter != null,
        onBack = { viewModel.onFilterSelected(null) },
    )

    LaunchedEffect(showMoveDialog) {
        if (showMoveDialog) anyFiled = viewModel.selection.selectionHasFiledItems()
    }

    val undoLabel = stringResource(com.shelfie.core.designsystem.R.string.selection_undo)
    LaunchedEffect(undoable) {
        if (undoable.isEmpty()) return@LaunchedEffect
        val count = undoable.size
        val result = snackbarHostState.showSnackbar(
            message = context.resources.getQuantityString(
                com.shelfie.core.designsystem.R.plurals.selection_deleted,
                count,
                count,
            ),
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long,
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.selection.undo { sender ->
                undoLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
        } else {
            viewModel.selection.onUndoDismissed()
        }
    }

    LaunchedEffect(movedCount) {
        if (movedCount == 0) return@LaunchedEffect
        snackbarHostState.showSnackbar(
            context.resources.getQuantityString(
                com.shelfie.core.designsystem.R.plurals.selection_moved,
                movedCount,
                movedCount,
            ),
        )
        viewModel.selection.onMoveMessageShown()
    }

    if (showMoveDialog) {
        MoveToFolderDialog(
            selectedCount = selectedIds.size,
            folders = moveFolders,
            showRemoveOption = anyFiled,
            onDismiss = { showMoveDialog = false },
            onMoveTo = { folderId ->
                viewModel.selection.moveTo(folderId)
                showMoveDialog = false
            },
            onMoveToCategory = { category ->
                viewModel.selection.moveToCategory(category)
                showMoveDialog = false
            },
            onCreateNew = {
                showMoveDialog = false
                showCreateFolder = true
            },
        )
    }

    if (showCreateFolder) {
        FolderCreateDialog(
            onDismiss = { showCreateFolder = false },
            onCreate = { name, icon ->
                viewModel.selection.createFolderAndMove(name, icon)
                showCreateFolder = false
            },
        )
    }

    val selected = state.selectedFilter
    val browsingFolder = (selected as? ShelfFilter.InFolder)
        ?.let { f -> folders.firstOrNull { it.id == f.folderId } }
    val browsingCategoryRes = (selected as? ShelfFilter.Category)?.category?.labelRes

    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
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
                if (selectedIds.isNotEmpty()) {
                    // Replaces the folder header while selecting, so the same actions
                    // are available here as on the shelf.
                    SelectionBar(
                        count = selectedIds.size,
                        onClear = viewModel.selection::clear,
                        onMove = { showMoveDialog = true },
                        onDelete = {
                            viewModel.selection.delete { sender ->
                                deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            }
                        },
                    )
                } else {
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
                                    onClick = {
                                        if (selectedIds.isEmpty()) {
                                            onScreenshotClick(screenshot.id)
                                        } else {
                                            viewModel.selection.onToggle(screenshot.id)
                                        }
                                    },
                                    onAction = {},
                                    folder = browsingFolder,
                                    onLongClick = {
                                        viewModel.selection.onLongPress(screenshot.id)
                                    },
                                    selected = screenshot.id in selectedIds,
                                    selectionActive = selectedIds.isNotEmpty(),
                                )
                            }
                        }
                    }
                }
            }

            else -> Column(modifier = Modifier.fillMaxSize()) {
                if (selectedFolderIds.isNotEmpty()) {
                    FolderSelectionBar(
                        count = selectedFolderIds.size,
                        onClear = viewModel::clearFolderSelection,
                        onDelete = {
                            viewModel.onDeleteFoldersRequested()
                            showFolderDeleteConfirm = true
                        },
                    )
                }
                BrowseList(
                    folders = state.folders,
                    categories = state.categories,
                    selectedFolderIds = selectedFolderIds,
                    onSelect = viewModel::onFilterSelected,
                    onFolderLongPress = viewModel::onFolderLongPress,
                    onFolderToggle = viewModel::onFolderToggle,
                )
            }
        }
    }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Folders first, then automatic categories. */
@Composable
private fun BrowseList(
    folders: List<ShelfChip>,
    categories: List<ShelfChip>,
    selectedFolderIds: Set<Long>,
    onSelect: (ShelfFilter) -> Unit,
    onFolderLongPress: (Long) -> Unit,
    onFolderToggle: (Long) -> Unit,
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
                val folderId = chip.folder?.id
                BrowseRow(
                    chip = chip,
                    selected = folderId != null && folderId in selectedFolderIds,
                    onClick = {
                        // While a selection is active a tap adjusts it instead of
                        // opening, which is how selection already behaves for
                        // screenshots — opening a folder mid-selection would abandon
                        // the selection with no way to tell it had happened.
                        if (selectedFolderIds.isNotEmpty() && folderId != null) {
                            onFolderToggle(folderId)
                        } else {
                            onSelect(chip.filter)
                        }
                    },
                    onLongClick = folderId?.let { id -> { onFolderLongPress(id) } },
                )
            }
        }

        if (categories.isNotEmpty()) {
            item(key = "categories-header") {
                SectionLabel(stringResource(R.string.find_section_categories))
            }
            items(items = categories, key = { it.key }) { chip ->
                // Automatic categories are not selectable: they are not user-created,
                // there is nothing to delete, and an empty one reappears the moment
                // something is classified into it.
                BrowseRow(
                    chip = chip,
                    selected = false,
                    onClick = { onSelect(chip.filter) },
                    onLongClick = null,
                )
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
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val label = chip.folder?.name ?: chip.category?.let { stringResource(it.labelRes) } ?: return
    val leading = chip.folder?.icon?.icon ?: chip.category?.icon

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // combinedClickable rather than Card(onClick), which has no long-press
            // parameter at all.
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
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
            if (selected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Header shown while folders are selected.
 *
 * Mirrors the screenshot [SelectionBar] rather than reusing it: that one offers Move,
 * which is meaningless for a folder, and passing it a disabled action would leave a
 * dead button on screen.
 */
@Composable
private fun FolderSelectionBar(
    count: Int,
    onClear: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.find_folder_selection_clear),
            )
        }
        Text(
            text = pluralStringResource(R.plurals.find_folder_selection_count, count, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.find_folder_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Confirmation for deleting folders, with the two intentions separated.
 *
 * "Delete the folder" and "delete the pictures in it" are different decisions, and
 * the old ✕ silently chose the first while looking like it might mean the second.
 * Both are offered explicitly, with the counts stated, and the destructive one is
 * the only one coloured as destructive.
 *
 * Android will show its own confirmation after this one for the permanent option. It
 * has to: the app did not create these files. Ours still earns its place, because the
 * system dialog can only talk about a number of images and cannot mention folders at
 * all.
 */
@Composable
private fun FolderDeleteDialog(
    folderCount: Int,
    screenshotCount: Int,
    onDismiss: () -> Unit,
    onDeleteFoldersOnly: () -> Unit,
    onDeleteEverything: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(pluralStringResource(R.plurals.find_folder_delete_title, folderCount, folderCount))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (screenshotCount == 0) {
                        stringResource(R.string.find_folder_delete_body_empty)
                    } else {
                        pluralStringResource(
                            R.plurals.find_folder_delete_body,
                            screenshotCount,
                            screenshotCount,
                        )
                    },
                )

                Spacer(Modifier.size(8.dp))

                /*
                 * The two choices are rows in the body, not buttons.
                 *
                 * A dialog's button area lays its buttons out in a single row and is
                 * built for one or two short ones. Three actions, one of them a
                 * five-word sentence, overflowed it — the buttons stacked, and Cancel
                 * was pushed outside the dialog's own bounds and clipped.
                 *
                 * Full-width rows also make the choice easier to read: the two
                 * outcomes sit one above the other in the same shape, so they can be
                 * compared, rather than being scattered across a row where position
                 * implies a priority that does not exist.
                 */
                ChoiceRow(
                    icon = Icons.Outlined.Delete,
                    label = if (screenshotCount == 0) {
                        stringResource(R.string.find_folder_delete_confirm_empty)
                    } else {
                        stringResource(R.string.find_folder_delete_confirm_all)
                    },
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDeleteEverything,
                )

                // Only worth offering when there is something to keep.
                if (screenshotCount > 0) {
                    ChoiceRow(
                        icon = Icons.Outlined.FolderOff,
                        label = stringResource(R.string.find_folder_delete_keep),
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = onDeleteFoldersOnly,
                    )
                }
            }
        },
        // Cancel is the only thing left in the button area, so it always fits, and
        // the dialog has exactly one way out that is not destructive.
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.find_folder_delete_cancel))
            }
        },
    )
}

/** One full-width choice inside a dialog body. */
@Composable
private fun ChoiceRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint)
            Text(text = label, color = tint, style = MaterialTheme.typography.bodyLarge)
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
