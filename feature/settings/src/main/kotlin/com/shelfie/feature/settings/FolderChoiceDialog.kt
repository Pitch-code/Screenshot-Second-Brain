package com.shelfie.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shelfie.core.model.MediaFolder

/**
 * Picks which image folders to read in full.
 *
 * Counts are shown, and large folders carry an explicit warning, because the
 * difference between ticking a 200-image folder and a 5,000-image one is the
 * difference between seconds and hours of background text recognition. A picker that
 * hid that number would be inviting people to make a decision they cannot see the
 * cost of.
 *
 * Selection is held locally and only committed on Save, so cancelling genuinely
 * cancels rather than leaving half a change applied.
 */
@Composable
fun FolderChoiceDialog(
    folders: List<MediaFolder>,
    initiallySelected: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selected by remember(initiallySelected) { mutableStateOf(initiallySelected) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_section_folders)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings_folders_detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (folders.isEmpty()) {
                    Text(stringResource(R.string.settings_folders_empty))
                    return@Column
                }

                LazyColumn(
                    // Bounded so a device with many folders cannot push the dialog's
                    // buttons off screen.
                    modifier = Modifier.heightIn(max = 380.dp),
                ) {
                    items(items = folders, key = { it.key }) { folder ->
                        val isOn = folder.key in selected

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = isOn,
                                    onValueChange = { on ->
                                        selected = if (on) {
                                            selected + folder.key
                                        } else {
                                            selected - folder.key
                                        }
                                    },
                                )
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isOn, onCheckedChange = null)

                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = folder.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = pluralStringResource(
                                        R.plurals.settings_folders_count,
                                        folder.imageCount,
                                        folder.imageCount,
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (folder.imageCount >= LARGE_FOLDER_THRESHOLD) {
                                    Text(
                                        text = stringResource(
                                            R.string.settings_folders_large_warning,
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) {
                Text(stringResource(R.string.settings_folders_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(com.shelfie.core.designsystem.R.string.folder_cancel))
            }
        },
    )
}

/**
 * Above this, a folder is almost certainly a camera roll rather than somewhere
 * screenshots accumulate, and reading it in full is a real time cost.
 */
private const val LARGE_FOLDER_THRESHOLD = 1_000
