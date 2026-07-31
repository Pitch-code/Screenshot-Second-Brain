package com.shelfie.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette.
 *
 * Amber is a deliberate choice: this app category is saturated with default
 * Material blue and purple, amber is the highest-attention hue at small sizes,
 * and it survives Play Store thumbnail compression.
 */
internal object BrandColors {
    val Deep = Color(0xFF12122A)
    val Signal = Color(0xFFFFD24A)
    val Shelf = Color(0xFFF5F5FA)

    val SignalDark = Color(0xFF7A5A00)
    val SignalContainer = Color(0xFFFFE9A8)
    val DeepElevated = Color(0xFF1D1D38)
    val DeepSurface = Color(0xFF0C0C1E)

    val Error = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFFFB4AB)
}
