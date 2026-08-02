package com.shelfie.feature.detail

import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import com.shelfie.core.model.ScreenshotCategory
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.shelfie.feature.detail.R
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.shelfie.core.designsystem.action.ScreenshotActionLauncher
import com.shelfie.core.designsystem.category.icon
import com.shelfie.core.designsystem.category.labelRes
import com.shelfie.core.designsystem.component.FolderCreateDialog
import com.shelfie.core.model.Folder
import com.shelfie.core.designsystem.component.DetailActionChip
import com.shelfie.core.designsystem.component.EntityChip
import com.shelfie.core.model.ScreenshotAction

/**
 * Detail as a bottom sheet rather than a full screen, so the shelf stays visible
 * behind it and dismissing feels instant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    screenshotId: Long,
    onDismiss: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(key = "detail-$screenshotId"),
) {
    LaunchedEffect(screenshotId) { viewModel.load(screenshotId) }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showFolderCreate by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val context = LocalContext.current
    val launcher = remember(context) { ScreenshotActionLauncher(context) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        if (state.isLoading || state.screenshot == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
            return@ModalBottomSheet
        }

        val screenshot = state.screenshot!!
        val entities = state.entities

        if (showCategoryPicker) {
            CategoryPickerDialog(
                current = screenshot.category,
                currentFolderId = screenshot.folderId,
                folders = state.folders,
                // Offered because the moment someone notices a wrong category is
                // the only moment they are motivated to fix it for good.
                ruleKeyword = screenshot.primaryValue?.takeIf { it.length in 3..30 },
                onDismiss = { showCategoryPicker = false },
                onPick = { category, alsoCreateRule ->
                    val keyword = screenshot.primaryValue
                    if (alsoCreateRule && keyword != null) {
                        viewModel.onCreateRule(keyword, category)
                    } else {
                        viewModel.onCategoryChanged(category)
                    }
                    showCategoryPicker = false
                },
                onPickFolder = { folder ->
                    viewModel.onFolderChanged(folder.id)
                    showCategoryPicker = false
                },
                onCreateFolder = {
                    // Swap dialogs rather than stacking them: two AlertDialogs at
                    // once leaves the lower one visible behind the scrim.
                    showCategoryPicker = false
                    showFolderCreate = true
                },
            )
        }

        if (showFolderCreate) {
            FolderCreateDialog(
                onDismiss = { showFolderCreate = false },
                onCreate = { name, icon ->
                    viewModel.onCreateFolderAndFile(name, icon)
                    showFolderCreate = false
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(
                model = screenshot.uri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
            )

            // Category, with the route to correcting it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Shows the folder when filed, so the move is visibly reflected
                // here and not only on the shelf.
                val filedIn = state.folder
                Icon(
                    imageVector = filedIn?.icon?.icon ?: screenshot.category.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = filedIn?.name ?: stringResource(screenshot.category.labelRes),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showCategoryPicker = true }) {
                    Text(stringResource(R.string.detail_change_category))
                }
            }

            // Primary actions.
            screenshot.primaryAction?.let { primary ->
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DetailActionChip(
                        action = primary,
                        onClick = {
                            launcher.launch(
                                action = primary,
                                primaryValue = screenshot.primaryValue,
                                fullText = state.text,
                                firstUrl = entities.urls.firstOrNull(),
                                firstPhone = entities.phoneNumbers.firstOrNull(),
                                firstDate = entities.dates.firstOrNull(),
                                firstTime = entities.times.firstOrNull(),
                            )
                        },
                    )
                    DetailActionChip(
                        action = ScreenshotAction.SHARE,
                        onClick = {
                            launcher.launch(
                                action = ScreenshotAction.SHARE,
                                primaryValue = screenshot.primaryValue,
                                fullText = state.text,
                            )
                        },
                    )
                }
            }

            // Detected values, each one tap to copy.
            val chips = buildList {
                entities.amounts.take(3).forEach { add(it) }
                entities.otpCodes.take(2).forEach { add(it) }
                entities.referenceIds.take(2).forEach { add(it) }
                entities.pnrCodes.take(2).forEach { add(it) }
                entities.phoneNumbers.take(2).forEach { add(it) }
                entities.dates.take(2).forEach { add(it) }
                entities.passwords.take(1).forEach { add(it) }
            }
            if (chips.isNotEmpty()) {
                Text(stringResource(R.string.detail_detected), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    chips.forEach { value ->
                        EntityChip(value = value, onClick = { launcher.copy(value) })
                    }
                }
            }

            HorizontalDivider()

            // Recognised text, selectable so any part can be copied.
            Text(
                text = stringResource(R.string.detail_text_heading),
                style = MaterialTheme.typography.labelLarge,
            )
            SelectionContainer {
                Text(
                    text = state.text?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.detail_no_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // Destructive action, kept well away from the primary ones.
            TextButton(onClick = { /* delete flow: Phase 4 */ }) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text(
                    text = "  " + stringResource(R.string.detail_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Category picker.
 *
 * Also offers to turn the correction into a standing rule. The most substantive
 * complaint about every competing app is that preset categories do not match what
 * people actually screenshot, so one correction here should fix every future
 * screenshot containing the same term.
 */
@Composable
private fun CategoryPickerDialog(
    current: ScreenshotCategory,
    currentFolderId: Long?,
    folders: List<Folder>,
    ruleKeyword: String?,
    onDismiss: () -> Unit,
    onPick: (ScreenshotCategory, Boolean) -> Unit,
    onPickFolder: (Folder) -> Unit,
    onCreateFolder: () -> Unit,
) {
    var createRule by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_pick_category)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Folders lead, because a user who has made one is telling us their
                // own filing beats the app's category guesses.
                folders.forEach { folder ->
                    val isCurrent = folder.id == currentFolderId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickFolder(folder) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = folder.icon.icon,
                            contentDescription = null,
                            tint = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCreateFolder)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CreateNewFolder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(
                            com.shelfie.core.designsystem.R.string.folder_new,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ScreenshotCategory.entries.forEach { category ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(category, createRule) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Only "current" when nothing has been filed: a screenshot
                        // in a folder still has a category underneath, and
                        // highlighting both would suggest it lives in two places.
                        val isCurrent = category == current && currentFolderId == null
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = stringResource(category.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }

                if (ruleKeyword != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { createRule = !createRule }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = createRule, onCheckedChange = { createRule = it })
                        Text(
                            text = stringResource(R.string.detail_rule_hint, ruleKeyword),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.detail_cancel)) }
        },
    )
}
