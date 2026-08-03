package com.shelfie.feature.shelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.shelfie.feature.shelf.R
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.shelfie.core.designsystem.action.ActionResult
import com.shelfie.core.designsystem.action.ScreenshotActionLauncher
import com.shelfie.core.designsystem.component.EmptyState
import com.shelfie.core.designsystem.component.IndexProblemCard
import com.shelfie.core.designsystem.component.IndexStatusStrip
import com.shelfie.core.designsystem.component.LimitedModeBanner
import com.shelfie.core.designsystem.component.ScreenshotTile
import com.shelfie.core.designsystem.component.SortMenuButton
import com.shelfie.core.media.RescanResult
import com.shelfie.core.model.Folder
import com.shelfie.core.model.Screenshot
import com.shelfie.core.model.ScreenshotAction
import com.shelfie.core.model.ShelfSortOrder
import kotlinx.coroutines.launch

@Composable
fun ShelfScreen(
    onScreenshotClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShelfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()

    // Re-check access on resume, so granting or revoking it in system Settings is
    // reflected without needing a restart.
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    val context = LocalContext.current
    val launcher = remember(context) { ScreenshotActionLauncher(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val copiedLabel = stringResource(com.shelfie.core.designsystem.R.string.copied)
    val nothingToCopyLabel =
        stringResource(com.shelfie.core.designsystem.R.string.nothing_to_copy)
    val scope = rememberCoroutineScope()

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK),
    ) { uris -> viewModel.onImagesPicked(uris) }

    // A refresh that reports nothing is indistinguishable from a broken one, so
    // the outcome is always announced — including "no new screenshots".
    val rescanFoundNone = stringResource(R.string.shelf_refresh_none)
    val rescanNoAccess = stringResource(R.string.shelf_refresh_no_access)
    val refreshResult = state.refreshResult
    LaunchedEffect(refreshResult) {
        val message = when (refreshResult) {
            null -> null
            is RescanResult.NoAccess -> rescanNoAccess
            is RescanResult.Failed -> refreshResult.reason
            is RescanResult.Completed -> {
                // Removals are reported as prominently as additions. A user who has
                // just deleted screenshots from their gallery and pressed refresh is
                // asking about removals specifically, and "no new screenshots" would
                // read as a failure.
                val added = refreshResult.added
                val removed = refreshResult.removed
                val parts = buildList {
                    if (added > 0) {
                        add(
                            context.resources.getQuantityString(
                                R.plurals.shelf_refresh_found,
                                added,
                                added,
                            ),
                        )
                    }
                    if (removed > 0) {
                        add(
                            context.resources.getQuantityString(
                                R.plurals.shelf_refresh_removed,
                                removed,
                                removed,
                            ),
                        )
                    }
                }
                if (parts.isEmpty()) rescanFoundNone else parts.joinToString(" · ")
            }
        }
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onRefreshMessageShown()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.onResumed() }

    val openPicker = {
        pickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Limited Mode is a working state, not an error, so the banner sits
            // above content without blocking it.
            LimitedModeBanner(
                access = state.access,
                visibleCount = state.pickedCount,
                onAddMore = openPicker,
                onGrantFullAccess = {
                    permissionLauncher.launch(
                        com.shelfie.feature.shelf.requestedMediaPermissions(),
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )

            // Placed above everything, because if this is showing then nothing
            // else on the screen is working.
            if (state.hasIndexingProblem) {
                IndexProblemCard(
                    stateSummary = state.stateSummary,
                    lastError = state.lastError,
                    onRetry = viewModel::onRetryIndexing,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            if (state.showStatusStrip) {
                IndexStatusStrip(
                    progress = state.progress,
                    onDismiss = viewModel::onStatusDismissed,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            // Folder and category chips moved to the Find tab. The shelf is now
            // purely "everything, newest first", so all this row carries is the
            // ordering control and a manual rescan.
            ShelfToolbar(
                sort = state.sortOrder,
                isRefreshing = state.isRefreshing,
                onSortChange = viewModel::onSortSelected,
                onRefresh = viewModel::onRefresh,
            )

            when {
                state.isEmpty && state.access.isLimited -> EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.shelf_limited_empty_title),
                    description = stringResource(R.string.shelf_limited_empty_body),
                    actionLabel = stringResource(R.string.shelf_limited_empty_cta),
                    onAction = openPicker,
                )

                state.isEmpty -> EmptyState(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = stringResource(R.string.shelf_empty_title),
                    description = stringResource(R.string.shelf_empty_body),
                )

                else -> ShelfGrid(
                    items = items,
                    onScreenshotClick = onScreenshotClick,
                    folderFor = viewModel::folderFor,
                    onAction = { screenshot, action ->
                        scope.launch {
                            val ctx = viewModel.actionContext(screenshot.id)
                            val result = launcher.launch(
                                action = action,
                                primaryValue = screenshot.primaryValue,
                                fullText = ctx.fullText,
                            )
                            result.messageOrNull(copiedLabel, nothingToCopyLabel)
                                ?.let { snackbarHostState.showSnackbar(it) }
                        }
                    },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ShelfGrid(
    items: LazyPagingItems<ShelfListItem>,
    onScreenshotClick: (Long) -> Unit,
    folderFor: (Screenshot) -> Folder?,
    onAction: (Screenshot, ScreenshotAction) -> Unit,
) {
    LazyVerticalGrid(
        // Adaptive rather than fixed, so tablets and foldables widen instead of
        // stretching three oversized tiles.
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
                // Date headers span the row. peek() avoids triggering a page load
                // just to compute a span.
                if (items.peek(index) is ShelfListItem.DateHeader) {
                    GridItemSpan(maxLineSpan)
                } else {
                    GridItemSpan(1)
                }
            },
        ) { index ->
            when (val item = items[index]) {
                is ShelfListItem.DateHeader -> Text(
                    text = item.label.resolve(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                )

                is ShelfListItem.Item -> ScreenshotTile(
                    screenshot = item.screenshot,
                    onClick = { onScreenshotClick(item.screenshot.id) },
                    onAction = { action -> onAction(item.screenshot, action) },
                    folder = folderFor(item.screenshot),
                )

                null -> Box(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

private fun ActionResult.messageOrNull(copiedLabel: String, nothingToCopy: String): String? = when (this) {
    is ActionResult.Failed -> message
    is ActionResult.Copied -> if (showConfirmation) copiedLabel else null
    ActionResult.NothingToDo -> nothingToCopy
    ActionResult.Launched -> null
}

/**
 * Mirrors the onboarding permission set.
 *
 * Duplicated deliberately: `:feature:shelf` must not depend on
 * `:feature:onboarding`, and this array is small enough that pushing it into a
 * core module would be more indirection than it is worth.
 */
internal fun requestedMediaPermissions(): Array<String> = when {
    android.os.Build.VERSION.SDK_INT >= 34 -> arrayOf(
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
    )

    android.os.Build.VERSION.SDK_INT >= 33 -> arrayOf("android.permission.READ_MEDIA_IMAGES")

    else -> arrayOf("android.permission.READ_EXTERNAL_STORAGE")
}

/** Photo picker selection cap. */
private const val MAX_PICK = 100

/** Resolves a date group heading to localised text. */
@Composable
private fun DateLabel.resolve(): String = when (this) {
    DateLabel.Today -> stringResource(R.string.shelf_date_today)
    DateLabel.Yesterday -> stringResource(R.string.shelf_date_yesterday)
    is DateLabel.Formatted -> text
}


/**
 * Sort control plus a manual rescan.
 *
 * The rescan button is deliberately always visible rather than appearing only when
 * something looks wrong. The previous design gated its only retry affordance behind
 * "nothing has ever been indexed", which meant a working library missing a single
 * new screenshot offered the user no way to do anything about it.
 */
@Composable
private fun ShelfToolbar(
    sort: ShelfSortOrder,
    isRefreshing: Boolean,
    onSortChange: (ShelfSortOrder) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        if (isRefreshing) {
            // Occupies the same slot as the button so the row does not reflow.
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = stringResource(R.string.shelf_refresh),
                )
            }
        }

        SortMenuButton(sort = sort, onSortChange = onSortChange)
    }
}
