package com.shelfie.feature.shelf

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Shown after a scan finds more screenshots than the free window keeps searchable.
 *
 * Appears on first launch and on every manual refresh until the app is unlocked,
 * which is a deliberate product decision. It is defensible because it is always a
 * response to an action the user just took — a scan they asked for — rather than an
 * unprompted interstitial. It is dismissible, blocks nothing, and the shelf behind it
 * is fully usable either way.
 */
@Composable
fun UpsellDialog(
    foundCount: Int,
    freeLimit: Int,
    onContinueFree: () -> Unit,
    onUnlock: () -> Unit,
) {
    AlertDialog(
        // Tapping outside is the same as choosing to carry on, not a third outcome.
        onDismissRequest = onContinueFree,
        title = { Text(stringResource(R.string.upsell_title, foundCount)) },
        text = { Text(stringResource(R.string.upsell_body, freeLimit, foundCount)) },
        confirmButton = {
            TextButton(onClick = onUnlock) {
                Text(stringResource(R.string.upsell_unlock))
            }
        },
        dismissButton = {
            TextButton(onClick = onContinueFree) {
                Text(stringResource(R.string.upsell_continue_free))
            }
        },
    )
}
