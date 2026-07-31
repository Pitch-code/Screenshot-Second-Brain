package com.shelfie.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shelfie.core.designsystem.R
import com.shelfie.core.model.MediaAccess

/**
 * Limited Mode banner.
 *
 * Persistent but not nagging: it states the situation, offers both ways forward,
 * and never blocks content. Google Play's Photo and Video Permissions policy
 * requires a usable experience when broad access is withheld, so this must read
 * as a working mode rather than an error.
 */
@Composable
fun LimitedModeBanner(
    access: MediaAccess,
    visibleCount: Int,
    onAddMore: () -> Unit,
    onGrantFullAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!access.isLimited) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.limited_mode_title),
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Text(
                text = when {
                    visibleCount == 0 -> stringResource(R.string.limited_mode_empty)

                    // "selected" for the system's partial grant, "picked" for
                    // images handed over through the photo picker: different
                    // mechanisms, so different wording.
                    access == MediaAccess.PARTIAL -> pluralStringResource(
                        R.plurals.limited_mode_selected,
                        visibleCount,
                        visibleCount,
                    )

                    else -> pluralStringResource(
                        R.plurals.limited_mode_picked,
                        visibleCount,
                        visibleCount,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onAddMore) { Text(stringResource(R.string.limited_mode_add_more)) }
                TextButton(onClick = onGrantFullAccess) {
                    Text(stringResource(R.string.limited_mode_allow_all))
                }
            }
        }
    }
}
