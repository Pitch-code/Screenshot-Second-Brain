package com.shelfie.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.shelfie.core.designsystem.R
import com.shelfie.core.designsystem.category.icon
import com.shelfie.core.designsystem.category.labelRes
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
                                    // A resource, not `option.name`. These chips are
                                    // icon-only, so this string is the entire label a
                                    // screen reader has to work with, and it was
                                    // announcing the raw enum constant — "STAR",
                                    // "HOME_WORK" — untranslated.
                                    contentDescription = stringResource(option.labelRes),
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

// FolderPickerRows and its PickerRow helper lived here and had no callers. The move
// picker they were written for is MoveToFolderDialog in SelectionControls.kt, which
// grew its own rows so it could offer automatic categories alongside folders.

