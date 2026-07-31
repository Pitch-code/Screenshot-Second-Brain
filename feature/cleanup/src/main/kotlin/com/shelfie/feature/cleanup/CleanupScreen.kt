package com.shelfie.feature.cleanup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.HistoryToggleOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shelfie.core.designsystem.component.EmptyState
import com.shelfie.core.designsystem.component.SelectableThumbnail
import com.shelfie.core.model.ByteFormat

@Composable
fun CleanupScreen(
    modifier: Modifier = Modifier,
    viewModel: CleanupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // System delete confirmation. Declining restores the soft-deleted rows, so
    // cancelling genuinely cancels.
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.onDeletionConfirmed()
        } else {
            viewModel.onDeletionCancelled()
        }
    }

    LaunchedEffect(state.lastMessage) {
        state.lastMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.onMessageShown()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.openGroup != null -> GroupDetail(
                group = state.openGroup!!,
                state = state,
                onToggle = viewModel::onToggleSelected,
                onSelectAll = viewModel::onSelectAll,
                onClear = viewModel::onClearSelection,
                onBack = viewModel::onCategoryClosed,
                onDelete = {
                    viewModel.onDeleteSelected { sender ->
                        deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    }
                },
            )

            state.report.isEmpty -> EmptyState(
                icon = Icons.Outlined.AutoAwesome,
                title = "Nothing to clean up",
                description = "No duplicates, no unreadable captures, nothing gathering " +
                    "dust. Check back after a few hundred more screenshots.",
            )

            else -> Overview(state = state, onOpen = viewModel::onCategoryOpened)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun Overview(state: CleanupUiState, onOpen: (CleanupGroup) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = ByteFormat.format(state.report.totalReclaimableBytes),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "could be freed up",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.canDeleteFiles) {
                    Text(
                        text = "On this version of Android, Shelfie can remove these from " +
                            "its own list but cannot delete the files themselves.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        item {
            CleanupCard(
                icon = Icons.Outlined.ContentCopy,
                title = "Duplicates",
                subtitle = "${state.report.duplicateCount} extra copies · " +
                    ByteFormat.format(state.report.duplicateBytes),
                detail = "One copy of each is always kept.",
                enabled = state.report.duplicateCount > 0,
                onClick = { onOpen(CleanupGroup.DUPLICATES) },
            )
        }
        item {
            CleanupCard(
                icon = Icons.Outlined.BlurOn,
                title = "Blurry or unreadable",
                subtitle = "${state.report.blurry.size} screenshots · " +
                    ByteFormat.format(state.report.blurryBytes),
                detail = "No readable text was found in these.",
                enabled = state.report.blurry.isNotEmpty(),
                onClick = { onOpen(CleanupGroup.BLURRY) },
            )
        }
        item {
            CleanupCard(
                icon = Icons.Outlined.HistoryToggleOff,
                title = "Older than 6 months",
                subtitle = "${state.report.oldAndUnopened.size} screenshots · " +
                    ByteFormat.format(state.report.oldBytes),
                detail = "Still searchable until you delete them.",
                enabled = state.report.oldAndUnopened.isNotEmpty(),
                onClick = { onOpen(CleanupGroup.OLD) },
            )
        }
    }
}

@Composable
private fun CleanupCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    detail: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = {
                Column {
                    Text(subtitle)
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}

/**
 * Preview-before-delete.
 *
 * Nothing is ever deleted from the overview: the user always sees exactly which
 * images will go, and how much space that frees, before confirming.
 */
@Composable
private fun GroupDetail(
    group: CleanupGroup,
    state: CleanupUiState,
    onToggle: (Long) -> Unit,
    onSelectAll: (List<Long>) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    val items = state.itemsFor(group)
    val selectedBytes = items.filter { it.id in state.selectedIds }.sumOf { it.sizeBytes }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Box(modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    if (state.selectedIds.size == items.size) onClear()
                    else onSelectAll(items.map { it.id })
                },
            ) {
                Text(if (state.selectedIds.size == items.size) "Clear" else "Select all")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = items, key = { it.id }) { screenshot ->
                SelectableThumbnail(
                    model = screenshot.displayUri,
                    selected = screenshot.id in state.selectedIds,
                    onClick = { onToggle(screenshot.id) },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${state.selectedIds.size} selected · " +
                    "${ByteFormat.format(selectedBytes)} to free",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(
                onClick = onDelete,
                enabled = state.selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.CleaningServices, contentDescription = null)
                Text("  Delete selected")
            }
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
            Text(
                text = "Deleted screenshots stay recoverable in Shelfie for 30 days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
