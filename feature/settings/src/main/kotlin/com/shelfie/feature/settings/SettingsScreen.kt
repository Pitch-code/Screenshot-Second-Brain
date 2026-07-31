package com.shelfie.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shelfie.core.designsystem.component.PlaceholderScreen

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Settings",
        description = "Permissions, sorting rules, appearance, purchase and privacy policy. Phase 5.",
        icon = Icons.Outlined.Settings,
        modifier = modifier,
    )
}
