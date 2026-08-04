package com.shelfie.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ShelfieDarkColors: ColorScheme = darkColorScheme(
    primary = BrandColors.Signal,
    onPrimary = BrandColors.SignalDark,
    primaryContainer = BrandColors.SignalDark,
    onPrimaryContainer = BrandColors.SignalContainer,

    secondary = BrandColors.Aqua,
    onSecondary = BrandColors.AquaDark,
    secondaryContainer = BrandColors.AquaDark,
    onSecondaryContainer = BrandColors.Aqua,

    tertiary = BrandColors.Violet,
    onTertiary = BrandColors.VioletDark,
    tertiaryContainer = BrandColors.VioletDark,
    onTertiaryContainer = BrandColors.Violet,

    background = BrandColors.DeepSurface,
    onBackground = BrandColors.Shelf,
    surface = BrandColors.Deep,
    onSurface = BrandColors.Shelf,
    // Muted rather than full-strength white for supporting text: with everything at
    // the same brightness there is no visual hierarchy and the screen reads as noise.
    onSurfaceVariant = BrandColors.ShelfMuted,

    // Four distinguishable steps. Cards previously sat on a surface almost the same
    // colour as themselves, so nothing had edges.
    surfaceContainerLowest = BrandColors.DeepSurface,
    surfaceContainerLow = BrandColors.Deep,
    surfaceContainer = BrandColors.DeepElevated,
    surfaceContainerHigh = BrandColors.DeepHigh,
    surfaceContainerHighest = BrandColors.DeepHigh,

    outline = BrandColors.DeepHigh,
    outlineVariant = BrandColors.DeepElevated,

    error = BrandColors.ErrorDark,
)

private val ShelfieLightColors: ColorScheme = lightColorScheme(
    primary = BrandColors.SignalDark,
    onPrimary = BrandColors.Shelf,
    primaryContainer = BrandColors.SignalContainer,
    onPrimaryContainer = BrandColors.SignalDark,

    secondary = BrandColors.AquaDark,
    onSecondary = BrandColors.Shelf,
    tertiary = BrandColors.VioletDark,
    onTertiary = BrandColors.Shelf,

    background = BrandColors.Shelf,
    onBackground = BrandColors.Deep,
    surface = BrandColors.Shelf,
    onSurface = BrandColors.Deep,
    error = BrandColors.Error,
)

/**
 * The app theme.
 *
 * Material 3 Expressive is the current Android design language. In the Compose
 * Material 3 version this project targets, Expressive styling — including the
 * spring-based motion physics scheme — is the default behaviour of
 * [MaterialTheme], and the separate `MaterialExpressiveTheme` entry point is no
 * longer public API. So the expressive look comes from [MaterialTheme] plus the
 * larger corner radii in [ShelfieShapes] and the heavier display weights in
 * [ShelfieTypography].
 *
 * Dark-first by default, with dynamic colour honoured on Android 12+.
 */
@Composable
fun ShelfieTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> ShelfieDarkColors
        else -> ShelfieLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = ShelfieShapes,
        typography = ShelfieTypography,
        content = content,
    )
}
