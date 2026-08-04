package com.shelfie.core.designsystem.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush

/**
 * The app's background.
 *
 * A very shallow vertical gradient rather than one flat fill. A single solid colour
 * behind a grid of screenshots reads as dead space, and the tiles all appear to float
 * on nothing; a slight lift towards the top gives the page a direction and makes the
 * cards look like they are sitting on something.
 *
 * Deliberately subtle — roughly one elevation step from end to end. A gradient you
 * can clearly see is a gradient you will be tired of by the third launch, and it
 * would compete with the screenshots, which are the actual content.
 *
 * Derived from the theme rather than hardcoded, so it follows dynamic colour for
 * anyone who turns that on.
 */
@Composable
fun ShelfieBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.surfaceContainerLow,
                        scheme.background,
                    ),
                ),
            ),
    ) {
        /*
         * Sets the ambient content colour, which a plain Box does not.
         *
         * Only Surface establishes a content colour, so drawing the background with a
         * Box left LocalContentColor at its root default of BLACK. Everything that
         * did not set its own colour — date headers, the selection count, toolbar
         * icons — rendered near-black on dark navy and was effectively unreadable.
         *
         * Fixed here rather than in each caller so no screen can be added later that
         * quietly reintroduces it.
         */
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground) {
            content()
        }
    }
}
