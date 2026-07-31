package com.shelfie.feature.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.shelfie.core.designsystem.action.ActionResult
import com.shelfie.core.designsystem.action.ScreenshotActionLauncher
import com.shelfie.core.designsystem.component.CategoryFilterRow
import com.shelfie.core.designsystem.component.EmptyState
import com.shelfie.core.designsystem.component.IndexStatusStrip
import com.shelfie.core.designsystem.component.ScreenshotTile
import com.shelfie.core.model.MediaAccess
import kotlinx.coroutines.launch

@Composable
fun ShelfScreen(
    onScreenshotClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShelfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()

    val context = LocalContext.current
    val launcher = remember(context) { ScreenshotActionLauncher(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            if (state.showStatusStrip) {
                IndexStatusStrip(
                    progress = state.progress,
                    onDismiss = viewModel::onStatusDismissed,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            if (state.categories.isNotEmpty()) {
                CategoryFilterRow(
                    counts = state.categories.map { it.category to it.count },
                    selected = state.selectedCategory,
                    onSelect = viewModel::onCategorySelected,
                )
            }

            when {
                state.access == MediaAccess.DENIED -> EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "Shelfie can't see your screenshots yet",
                    description = "Grant access and your newest screenshots become " +
                        "searchable in a few seconds.",
                )

                items.itemCount == 0 && state.progress.total == 0 -> EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "Nothing on the shelf yet",
                    description = "Take a screenshot and it'll land here automatically.",
                )

                else -> ShelfGrid(
                    items = items,
                    onScreenshotClick = onScreenshotClick,
                    onAction = { screenshot, action ->
                        scope.launch {
                            val ctx = viewModel.actionContext(screenshot.id)
                            val result = launcher.launch(
                                action = action,
                                primaryValue = screenshot.primaryValue,
                                fullText = ctx.fullText,
                            )
                            result.messageOrNull()?.let { snackbarHostState.showSnackbar(it) }
                        }
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ShelfGrid(
    items: androidx.paging.compose.LazyPagingItems<ShelfListItem>,
    onScreenshotClick: (Long) -> Unit,
    onAction: (com.shelfie.core.model.Screenshot, com.shelfie.core.model.ScreenshotAction) -> Unit,
) {
    LazyVerticalGrid(
        // 3 columns on a phone; widens automatically on tablets and foldables.
        columns = GridCells.Adaptive(minSize = 116.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            count = items.itemCount,
            key = items.itemKey { it.key },
            span = { index ->
                // Date headers span the full row. peek() avoids triggering a load
                // just to work out the span.
                if (items.peek(index) is ShelfListItem.DateHeader) {
                    GridItemSpan(maxLineSpan)
                } else {
                    GridItemSpan(1)
                }
            },
        ) { index ->
            when (val item = items[index]) {
                is ShelfListItem.DateHeader -> Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                )

                is ShelfListItem.Item -> ScreenshotTile(
                    screenshot = item.screenshot,
                    onClick = { onScreenshotClick(item.screenshot.id) },
                    onAction = { action -> onAction(item.screenshot, action) },
                )

                // Placeholder slot while a page loads.
                null -> Box(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun ActionResult.messageOrNull(): String? = when (this) {
    is ActionResult.Failed -> message
    is ActionResult.Copied -> if (showConfirmation) "Copied" else null
    ActionResult.NothingToDo -> "Nothing to copy here"
    ActionResult.Launched -> null
}
