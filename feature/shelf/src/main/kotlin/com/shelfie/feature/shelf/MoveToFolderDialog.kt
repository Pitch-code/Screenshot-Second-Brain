package com.shelfie.feature.shelf

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
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.shelfie.core.designsystem.category.icon
import com.shelfie.core.model.Folder

/**
 * Moves a whole selection into a folder.
 *
 * Includes "Remove from folder" as a first-class option rather than only offering
 * destinations. Correcting a mistaken move is the reason someone opens this, and
 * sometimes the correction is "put it back where it was".
 */
@Composable
fun MoveToFolderDialog(
    selectedCount: Int,
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onMoveTo: (Long?) -> Unit,
    onCreateNew: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                pluralStringResource(
                    R.plurals.shelf_move_title,
                    selectedCount,
                    selectedCount,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                folders.forEach { folder ->
                    MoveRow(
                        icon = folder.icon.icon,
                        label = folder.name,
                        onClick = { onMoveTo(folder.id) },
                    )
                }

                if (folders.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                MoveRow(
                    icon = Icons.Outlined.CreateNewFolder,
                    label = stringResource(
                        com.shelfie.core.designsystem.R.string.folder_new,
                    ),
                    onClick = onCreateNew,
                    emphasised = true,
                )

                MoveRow(
                    icon = Icons.Outlined.FolderOff,
                    label = stringResource(R.string.shelf_move_remove),
                    onClick = { onMoveTo(null) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(com.shelfie.core.designsystem.R.string.folder_cancel),
                )
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
