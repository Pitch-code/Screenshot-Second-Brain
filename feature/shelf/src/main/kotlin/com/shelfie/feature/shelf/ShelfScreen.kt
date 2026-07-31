package com.shelfie.feature.shelf

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shelfie.core.designsystem.component.PlaceholderScreen
import com.shelfie.core.model.MediaAccess

@Composable
fun ShelfScreen(
    modifier: Modifier = Modifier,
    viewModel: ShelfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The real paged grid arrives in Phase 2. This surfaces live engine state so
    // the indexing pipeline is observable end to end in the meantime.
    PlaceholderScreen(
        title = "Shelf",
        description = state.describe(),
        icon = Icons.Outlined.PhotoLibrary,
        modifier = modifier,
    )
}

private fun ShelfUiState.describe(): String = when {
    access == MediaAccess.DENIED ->
        "Shelfie needs access to your screenshots. Limited Mode arrives in Phase 3."

    progress.total == 0 ->
        "Nothing here yet. Take a screenshot and it'll land on the shelf."

    isIndexing ->
        "${progress.indexed} of ${progress.total} indexed. " +
            "Older ones finish while your phone is idle and charging."

    else -> buildString {
        append("${progress.total} screenshots indexed")
        if (categories.isNotEmpty()) {
            append("\n\n")
            append(categories.joinToString("\n") { "${it.category.name}  ${it.count}" })
        }
    }
}
