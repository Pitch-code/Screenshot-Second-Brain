package com.shelfie.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shelfie.core.model.ScreenshotAction

val ScreenshotAction.label: String
    get() = when (this) {
        ScreenshotAction.OPEN_LINK -> "Open link"
        ScreenshotAction.COPY_CODE -> "Copy code"
        ScreenshotAction.ADD_TO_CALENDAR -> "Add to calendar"
        ScreenshotAction.DIAL_NUMBER -> "Call"
        ScreenshotAction.COPY_TEXT -> "Copy text"
        ScreenshotAction.SHARE -> "Share"
    }

val ScreenshotAction.icon: ImageVector
    get() = when (this) {
        ScreenshotAction.OPEN_LINK -> Icons.AutoMirrored.Outlined.OpenInNew
        ScreenshotAction.COPY_CODE -> Icons.Outlined.Pin
        ScreenshotAction.ADD_TO_CALENDAR -> Icons.Outlined.CalendarMonth
        ScreenshotAction.DIAL_NUMBER -> Icons.Outlined.Call
        ScreenshotAction.COPY_TEXT -> Icons.Outlined.ContentCopy
        ScreenshotAction.SHARE -> Icons.Outlined.Share
    }

/** Compact action chip shown on a shelf tile, over the image scrim. */
@Composable
fun TileActionChip(
    action: ScreenshotAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SuggestionChip(
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(text = action.label, maxLines = 1)
        },
        icon = {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = Color.White.copy(alpha = 0.16f),
            labelColor = Color.White,
            iconContentColor = Color.White,
        ),
        border = null,
    )
}

/** Full-size action chip for the detail sheet's action row. */
@Composable
fun DetailActionChip(
    action: ScreenshotAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = { Text(action.label) },
        leadingIcon = {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}

/** A chip that copies a detected entity, e.g. an amount or a booking reference. */
@Composable
fun EntityChip(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = { Text(value, maxLines = 1) },
        leadingIcon = {
            Row {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            }
        },
    )
}
