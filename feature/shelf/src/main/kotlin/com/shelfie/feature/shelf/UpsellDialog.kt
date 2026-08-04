package com.shelfie.feature.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Shown after a scan finds more screenshots than the free window keeps searchable.
 *
 * Appears on first launch and after each manual refresh until the app is unlocked.
 * Defensible because it always follows an action the user just took — a scan they
 * asked for — rather than arriving unprompted. Dismissible, blocks nothing, and the
 * shelf behind it stays fully usable either way.
 *
 * @param formattedPrice localised price from Play, or null when Play has not answered
 *   yet. Never hardcoded, and never guessed: quoting a price that turns out to be
 *   wrong is worse than not quoting one.
 */
@Composable
fun UpsellDialog(
    foundCount: Int,
    freeLimit: Int,
    formattedPrice: String?,
    onContinueFree: () -> Unit,
    onUnlock: () -> Unit,
) {
    AlertDialog(
        // Tapping outside is the same as choosing to carry on, not a third outcome.
        onDismissRequest = onContinueFree,
        title = { Text(stringResource(R.string.upsell_title, foundCount)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(
                        R.string.upsell_body,
                        freeLimit,
                        (foundCount - freeLimit).coerceAtLeast(0),
                    ),
                )
                // Its own line, and quieter than the offer above it. This is
                // reassurance, not the pitch.
                Text(
                    text = formattedPrice
                        ?.let { stringResource(R.string.upsell_price, it) }
                        ?: stringResource(R.string.upsell_price_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUnlock) {
                Text(
                    text = formattedPrice
                        ?.let { stringResource(R.string.upsell_unlock_priced, it) }
                        ?: stringResource(R.string.upsell_unlock),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onContinueFree) {
                Text(stringResource(R.string.upsell_continue_free))
            }
        },
    )
}
