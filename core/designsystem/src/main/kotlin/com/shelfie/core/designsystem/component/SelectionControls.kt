package com.shelfie.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shelfie.core.designsystem.R
import com.shelfie.core.designsystem.category.icon
import com.shelfie.core.model.Folder

/**
 * Bar shown while screenshots are selected.
 *
 * Lives here rather than in one feature because the shelf and the Find tab both need
 * it, and having two copies is how the two screens ended up behaving differently in
 * the first place.
 */
@Composable
fun SelectionBar(
    count: Int,
    onClear: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = stringResource(R.string.selection_clear),
            )
        }

        Text(
            text = pluralStringResource(R.plurals.selection_count, count, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )

        IconButton(onClick = onMove) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.DriveFileMove,
                contentDescription = stringResource(R.string.selection_move),
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.selection_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Picks a destination folder for a selection.
 *
 * @param showRemoveOption only true when something in the selection is actually filed.
 *   Offering "Remove from folder" otherwise is an action that visibly does nothing,
 *   which teaches people not to trust the menu.
 */
@Composable
fun MoveToFolderDialog(
    selectedCount: Int,
    folders: List<Folder>,
    showRemoveOption: Boolean,
    onDismiss: () -> Unit,
    onMoveTo: (Long?) -> Unit,
    onCreateNew: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(pluralStringResource(R.plurals.selection_move_title, selectedCount, selectedCount))
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (folders.isEmpty()) {
                    // Without this the dialog is two rows and looks broken, which is
                    // exactly how it was reported.
                    Text(
                        text = stringResource(R.string.selection_move_no_folders),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else {
                    folders.forEach { folder ->
                        MoveRow(
                            icon = folder.icon.icon,
                            label = folder.name,
                            onClick = { onMoveTo(folder.id) },
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                MoveRow(
                    icon = Icons.Outlined.CreateNewFolder,
                    label = stringResource(R.string.folder_new),
                    onClick = onCreateNew,
                    emphasised = true,
                )

                if (showRemoveOption) {
                    MoveRow(
                        icon = Icons.Outlined.FolderOff,
                        label = stringResource(R.string.selection_move_remove),
                        onClick = { onMoveTo(null) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.folder_cancel))
            }
        },
    )
}

@Composable
private fun MoveRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    emphasised: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (emphasised) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (emphasised) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
