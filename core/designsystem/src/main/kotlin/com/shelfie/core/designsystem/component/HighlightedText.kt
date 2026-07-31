package com.shelfie.core.designsystem.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.shelfie.core.model.SearchQuery

/**
 * Renders [text] with the parts that matched [query] emphasised.
 *
 * This is a trust feature, not decoration: showing *why* a result matched is what
 * convinces someone the index actually works, rather than leaving them to guess
 * whether the app found the right screenshot by luck.
 */
@Composable
fun HighlightedText(
    text: String,
    query: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = 2,
) {
    val highlightStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )

    val annotated: AnnotatedString = remember(text, query) {
        val ranges = SearchQuery.highlightRanges(text, query)
        if (ranges.isEmpty()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                var cursor = 0
                for (range in ranges) {
                    // Guard against any range drifting outside the string.
                    val start = range.first.coerceIn(0, text.length)
                    val end = (range.last + 1).coerceIn(start, text.length)

                    if (start > cursor) append(text.substring(cursor, start))
                    withStyleSafely(highlightStyle) { append(text.substring(start, end)) }
                    cursor = end
                }
                if (cursor < text.length) append(text.substring(cursor))
            }
        }
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleSafely(
    style: SpanStyle,
    block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit,
) {
    pushStyle(style)
    block()
    pop()
}
