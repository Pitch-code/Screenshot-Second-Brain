package com.shelfie.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale.
 *
 * Body text never drops below 16sp — this app targets budget devices and older
 * users, and small type is the fastest way to make a utility feel unusable.
 * Expressive favours heavier display weights, hence Bold on the large styles.
 */
internal val ShelfieTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(
            fontFamily = FontFamily.SansSerif,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.Bold,
        ),
        headlineMedium = headlineMedium.copy(
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
        ),
        titleLarge = titleLarge.copy(
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
        ),
        bodyLarge = bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyMedium = bodyMedium.copy(
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        labelLarge = labelLarge.copy(
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        ),
    )
}

// ExtractedValueStyle lived here, unused: ScreenshotTile builds its own monospace
// style from bodyMedium so the extracted value scales with the type scale rather than
// being pinned at 16sp.
