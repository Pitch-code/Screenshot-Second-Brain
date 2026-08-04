package com.shelfie.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette.
 *
 * Amber is a deliberate choice: this app category is saturated with default
 * Material blue and purple, amber is the highest-attention hue at small sizes,
 * and it survives Play Store thumbnail compression.
 *
 * ### Why there are more colours here than a two-colour brand needs
 *
 * The first version was navy plus amber and nothing else, which read as flat and
 * lifeless once the screen filled with screenshot thumbnails: every surface was the
 * same value, so nothing separated a card from the page behind it, and the only
 * accent was one yellow. Depth in a dark theme comes from having distinguishable
 * elevation steps, and interest comes from having more than one accent — so there are
 * now four surface tiers and two supporting accents.
 */
internal object BrandColors {
    // ---- surfaces, darkest to lightest. Each step is visibly distinct at low
    // brightness, which is where a dark theme usually collapses into one flat grey.
    val DeepSurface = Color(0xFF0A0A18)
    val Deep = Color(0xFF14142B)
    val DeepElevated = Color(0xFF1E1E3C)
    val DeepHigh = Color(0xFF28284C)

    // ---- primary accent
    val Signal = Color(0xFFFFD24A)
    val SignalDark = Color(0xFF6B4E00)
    val SignalContainer = Color(0xFFFFE9A8)

    /** Supporting accent. Used for links and secondary emphasis. */
    val Aqua = Color(0xFF5FE0E6)
    val AquaDark = Color(0xFF00494D)

    /** Third accent, so category chips and folders are not all one colour. */
    val Violet = Color(0xFFB9A5FF)
    val VioletDark = Color(0xFF33246B)

    val Shelf = Color(0xFFF6F6FB)
    val ShelfMuted = Color(0xFFC9C9DC)

    val Error = Color(0xFFB3261E)
    val ErrorDark = Color(0xFFFFB4AB)
}
