package com.shelfie.feature.detail

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.shelfie.core.designsystem.action.ScreenshotActionLauncher
import com.shelfie.core.designsystem.category.icon
import com.shelfie.core.designsystem.category.label
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
                Icon(
                    imageVector = screenshot.category.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = screenshot.category.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // Full picker lands with Settings in Phase 5; the sheet already
                // owns the interaction point.
                TextButton(onClick = { /* category picker: Phase 5 */ }) { Text("Change") }
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
                Text("Detected", style = MaterialTheme.typography.labelLarge)
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
            Text("Text in this screenshot", style = MaterialTheme.typography.labelLarge)
            SelectionContainer {
                Text(
                    text = state.text?.takeIf { it.isNotBlank() }
                        ?: "No text was recognised in this screenshot.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            // Destructive action, kept well away from the primary ones.
            TextButton(onClick = { /* delete flow: Phase 4 */ }) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Text("  Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
