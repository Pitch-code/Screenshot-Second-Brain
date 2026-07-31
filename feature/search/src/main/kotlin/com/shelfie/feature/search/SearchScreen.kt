package com.shelfie.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.shelfie.feature.search.R
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.shelfie.core.designsystem.category.icon
import com.shelfie.core.designsystem.category.labelRes
import com.shelfie.core.designsystem.component.EmptyState
import com.shelfie.core.designsystem.component.HighlightedText
import com.shelfie.core.model.Screenshot
import com.shelfie.core.model.SearchQuery

@Composable
fun SearchScreen(
    onScreenshotClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results = viewModel.results.collectAsLazyPagingItems()

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::onClear) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Search,
            ),
        )

        when {
            query.isBlank() -> EmptyState(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.search_idle_title),
                description = stringResource(R.string.search_idle_body),
            )

            results.itemCount == 0 -> EmptyState(
                icon = Icons.Outlined.Search,
                title = stringResource(R.string.search_no_results_title, query),
                description = stringResource(R.string.search_no_results_body),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 12.dp,
                    vertical = 4.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    count = results.itemCount,
                    key = results.itemKey { it.id },
                ) { index ->
                    results[index]?.let { screenshot ->
                        SearchResultRow(
                            screenshot = screenshot,
                            query = query,
                            loadText = { viewModel.textFor(screenshot.id) },
                            onClick = { onScreenshotClick(screenshot.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    screenshot: Screenshot,
    query: String,
    loadText: suspend () -> String?,
    onClick: () -> Unit,
) {
    // Text is fetched per visible row rather than joined into the paged query, so
    // scrolling never drags kilobytes of OCR text per item into memory.
    val text by produceState<String?>(initialValue = null, screenshot.id) {
        value = loadText()
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = screenshot.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 56.dp, height = 84.dp)
                    .then(Modifier),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = screenshot.category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(screenshot.category.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                screenshot.primaryValue?.let { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                }

                // Highlighting the matched words is what proves the index works,
                // rather than leaving the user to guess why this row appeared.
                val snippet = text?.let { SearchQuery.snippet(it, query) }
                if (snippet != null) {
                    HighlightedText(
                        text = snippet,
                        query = query,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
