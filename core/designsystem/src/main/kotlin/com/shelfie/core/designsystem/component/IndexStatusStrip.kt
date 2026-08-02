package com.shelfie.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.shelfie.core.designsystem.R
import com.shelfie.core.model.IndexProgress

/**
 * Indexing status.
 *
 * Deliberately **informational and dismissible, never blocking**. The whole
 * competitive thesis is that the shelf stays usable while older screenshots are
 * still being read, so this must never become a modal progress dialog. It also
 * explains *when* the rest will finish, which is what stops a half-done count
 * from feeling broken.
 */
@Composable
fun IndexStatusStrip(
    progress: IndexProgress,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    AnimatedVisibility(visible = visible && !progress.isComplete) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.index_status_title),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = pluralStringResource(
                                R.plurals.index_status_detail,
                                progress.total,
                                // settled, not indexed: held-back and unreadable
                                // rows are finished with, so counting only indexed
                                // rows made the number stall short of the total.
                                progress.settled,
                                progress.total,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.index_status_hide),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, end = 12.dp),
                )
            }
        }
    }
}
