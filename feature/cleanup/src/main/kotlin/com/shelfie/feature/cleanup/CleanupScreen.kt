package com.shelfie.feature.cleanup

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shelfie.core.designsystem.component.PlaceholderScreen

@Composable
fun CleanupScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Cleanup",
        description = "Duplicates, blurry captures and old screenshots. Always previewed before delete. Phase 4.",
        icon = Icons.Outlined.CleaningServices,
        modifier = modifier,
    )
}
