package com.shelfie.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shelfie.core.designsystem.category.icon
import com.shelfie.core.designsystem.category.label
import com.shelfie.core.model.ScreenshotCategory

/**
 * Category filter chips.
 *
 * The caller only passes categories that already have enough matches to be
 * worth showing, so the row reflects the user's actual library rather than a
 * fixed list of mostly-empty buckets. That is the fix for the most common
 * substantive complaint about competing apps.
 */
@Composable
fun CategoryFilterRow(
    counts: List<Pair<ScreenshotCategory, Int>>,
    selected: ScreenshotCategory?,
    onSelect: (ScreenshotCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
            )
        }

        items(items = counts, key = { it.first.name }) { (category, count) ->
            val isSelected = selected == category
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else category) },
                label = { Text("${category.label} $count") },
                leadingIcon = {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}
