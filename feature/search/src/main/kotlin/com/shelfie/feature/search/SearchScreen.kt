package com.shelfie.feature.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shelfie.core.designsystem.component.PlaceholderScreen

@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Search",
        description = "Full-text search over the FTS index, with match highlighting. Phase 2.",
        icon = Icons.Outlined.Search,
        modifier = modifier,
    )
}
