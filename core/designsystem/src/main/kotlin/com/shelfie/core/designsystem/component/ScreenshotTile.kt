package com.shelfie.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.shelfie.core.designsystem.category.icon
import com.shelfie.core.designsystem.category.label
import com.shelfie.core.model.Screenshot
import com.shelfie.core.model.ScreenshotAction
import com.shelfie.core.model.ScreenshotCategory

/**
 * One screenshot on the shelf.
 *
 * Two decisions here are what separate this from a gallery clone:
 *
 *  - The tile shows the **extracted value** (an amount, an OTP, a PNR) instead of
 *    a filename. That is what makes the index feel alive.
 *  - The tile carries **one action**, so the index has an obvious next step
 *    rather than being a dead end — the most common substantive complaint about
 *    competing apps.
 */
@Composable
fun ScreenshotTile(
    screenshot: Screenshot,
    onClick: () -> Unit,
    onAction: (ScreenshotAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = screenshot.accessibilityLabel() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Phone screenshots are tall; a fixed ratio keeps the grid even
                // and stops one odd image from stretching a row.
                .aspectRatio(0.62f),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(screenshot.uri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Scrim so overlaid text stays legible on any screenshot.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CategoryBadge(screenshot.category)

                screenshot.primaryValue?.let { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                screenshot.primaryAction?.let { action ->
                    TileActionChip(action = action, onClick = { onAction(action) })
                }
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: ScreenshotCategory) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(percent = 50),
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = category.label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1,
        )
    }
}

/** Screen-reader description: category, value, then date. */
private fun Screenshot.accessibilityLabel(): String = buildString {
    append(category.label)
    primaryValue?.let { append(", $it") }
    append(", $displayName")
}
