package com.shelfie.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.shelfie.core.designsystem.R
import com.shelfie.core.designsystem.category.icon
import com.shelfie.core.model.Folder
import com.shelfie.core.model.FolderIcon

/**
 * Names a new folder and picks its icon.
 *
 * Icon choice is offered up front rather than hidden behind an edit step, because
 * a row of folder chips is scanned by shape long before the text is read — an
 * icon-less folder list is markedly slower to use.
 */
@Composable
fun FolderCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, icon: FolderIcon) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(FolderIcon.FOLDER) }

    val canCreate = Folder.isValidName(name)
    val submit = {
        if (canCreate) {
            onCreate(name, icon)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folder_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    // Trimming is left to the repository so the user can still type
                    // a space mid-name; only the length is capped here, to stop the
                    // field silently accepting text the folder will not keep.
                    onValueChange = { if (it.length <= Folder.MAX_NAME_LENGTH) name = it },
                    label = { Text(stringResource(R.string.folder_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = stringResource(R.string.folder_pick_icon),
                    style = MaterialTheme.typography.labelMedium,
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items = FolderIcon.entries, key = { it.name }) { option ->
                        FilterChip(
                            selected = option == icon,
                            onClick = { icon = option },
                            label = {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = option.name,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = canCreate) {
                Text(stringResource(R.string.folder_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.folder_cancel))
            }
        },
    )
}

/** Row of existing folders plus a create option, used inside the move picker. */
@Composable
fun FolderPickerRows(
    folders: List<Folder>,
    onPick: (Folder) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        folders.forEach { folder ->
            PickerRow(
                icon = folder.icon.icon,
                label = folder.name,
                onClick = { onPick(folder) },
            )
        }

        PickerRow(
            icon = Icons.Outlined.CreateNewFolder,
            label = stringResource(R.string.folder_new),
            onClick = onCreateNew,
            emphasised = true,
        )
    }
}

@Composable
private fun PickerRow(
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

