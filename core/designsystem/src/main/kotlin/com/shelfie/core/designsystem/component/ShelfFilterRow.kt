package com.shelfie.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shelfie.core.designsystem.R
import com.shelfie.core.designsystem.category.icon
import com.shelfie.core.designsystem.category.labelRes
import com.shelfie.core.model.Folder
import com.shelfie.core.model.ScreenshotCategory
import com.shelfie.core.model.ShelfFilter
import com.shelfie.core.model.ShelfSortOrder

/**
 * One chip in the shelf's filter row.
 *
 * A category chip carries a [ScreenshotCategory] and resolves its label from
 * resources; a folder chip carries a [Folder] and uses the user's own text. Two
 * optional fields rather than a sealed hierarchy keeps the call sites in the
 * ViewModel flat and the rendering a single branch.
 */
data class ShelfChip(
    val filter: ShelfFilter,
    val count: Int,
    val category: ScreenshotCategory? = null,
    val folder: Folder? = null,
) {
    /** Stable identity for the lazy row; folders and categories cannot collide. */
    val key: String
        get() = when {
            folder != null -> "folder-${folder.id}"
            category != null -> "category-${category.name}"
            else -> "all"
        }
}

/**
 * Filter chips plus the sort control.
 *
 * The two live together because they answer adjacent questions — *which*
 * screenshots, and in *what order* — and separating them into different screens
 * makes ordering feel like a buried setting rather than a property of the view
 * you are looking at.
 *
 * Folder chips come first: they are the user's own filing, so they outrank the
 * app's guesses.
 */
@Composable
fun ShelfFilterRow(
    chips: List<ShelfChip>,
    selected: ShelfFilter,
    sort: ShelfSortOrder,
    onSelect: (ShelfFilter) -> Unit,
    onSortChange: (ShelfSortOrder) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all") {
                FilterChip(
                    selected = selected == ShelfFilter.All,
                    onClick = { onSelect(ShelfFilter.All) },
                    label = { Text(stringResource(R.string.filter_all)) },
                )
            }

            items(items = chips, key = { it.key }) { chip ->
                val isSelected = selected == chip.filter
                val label = chip.folder?.name
                    ?: chip.category?.let { stringResource(it.labelRes) }
                    ?: return@items
                val leading = chip.folder?.icon?.icon ?: chip.category?.icon

                FilterChip(
                    selected = isSelected,
                    // Tapping the selected chip clears the filter, which is the
                    // only way back to "All" without hunting for that chip.
                    onClick = { onSelect(if (isSelected) ShelfFilter.All else chip.filter) },
                    label = { Text("$label  ${chip.count}") },
                    leadingIcon = leading?.let {
                        {
                            Icon(
                                imageVector = it,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    },
                )
            }
        }

        SortMenuButton(sort = sort, onSortChange = onSortChange)
    }
}

@Composable
private fun SortMenuButton(
    sort: ShelfSortOrder,
    onSortChange: (ShelfSortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Sort,
            contentDescription = stringResource(R.string.sort_content_description),
        )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        ShelfSortOrder.entries.forEach { order ->
            DropdownMenuItem(
                text = { Text(stringResource(order.labelRes)) },
                // A radio button rather than a check mark: these are mutually
                // exclusive, and a tick reads as "this is on" rather than
                // "this one, not the others".
                leadingIcon = {
                    RadioButton(
                        selected = order == sort,
                        onClick = null,
                    )
                },
                onClick = {
                    onSortChange(order)
                    expanded = false
                },
            )
        }
    }
}

@get:androidx.annotation.StringRes
private val ShelfSortOrder.labelRes: Int
    get() = when (this) {
        ShelfSortOrder.NEWEST_FIRST -> R.string.sort_newest_first
        ShelfSortOrder.OLDEST_FIRST -> R.string.sort_oldest_first
        ShelfSortOrder.LARGEST_FIRST -> R.string.sort_largest_first
        ShelfSortOrder.SMALLEST_FIRST -> R.string.sort_smallest_first
    }
