package com.shelfie.core.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.annotation.StringRes
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.shelfie.core.designsystem.R
import com.shelfie.core.model.ScreenshotAction

@get:StringRes
val ScreenshotAction.labelRes: Int
    get() = when (this) {
        ScreenshotAction.OPEN_LINK -> R.string.action_open_link
        ScreenshotAction.COPY_CODE -> R.string.action_copy_code
        ScreenshotAction.ADD_TO_CALENDAR -> R.string.action_add_to_calendar
        ScreenshotAction.DIAL_NUMBER -> R.string.action_call
        ScreenshotAction.COPY_TEXT -> R.string.action_copy_text
        ScreenshotAction.SHARE -> R.string.action_share
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
            Text(text = stringResource(action.labelRes), maxLines = 1)
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
        label = { Text(stringResource(action.labelRes)) },
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
    // Screen readers otherwise announce only the raw value with no hint that
    // tapping copies it.
    val copyDescription = stringResource(R.string.a11y_copy_value, value)

    AssistChip(
        onClick = onClick,
        modifier = modifier.semantics {
            contentDescription = copyDescription
        },
        label = { Text(value, maxLines = 1) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}
