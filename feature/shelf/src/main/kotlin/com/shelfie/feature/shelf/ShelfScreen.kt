package com.shelfie.feature.shelf

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shelfie.core.designsystem.component.PlaceholderScreen

@Composable
fun ShelfScreen(
    modifier: Modifier = Modifier,
    viewModel: ShelfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PlaceholderScreen(
        title = "Shelf",
        description = if (state.progress.total == 0) {
            "Nothing here yet. Take a screenshot and it'll land on the shelf."
        } else {
            "${state.progress.indexed} of ${state.progress.total} indexed"
        },
        icon = Icons.Outlined.PhotoLibrary,
        modifier = modifier,
    )
}
