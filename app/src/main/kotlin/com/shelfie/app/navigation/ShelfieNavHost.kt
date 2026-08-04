package com.shelfie.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.shelfie.feature.cleanup.CleanupScreen
import com.shelfie.feature.search.SearchScreen
import com.shelfie.feature.settings.SettingsScreen
import com.shelfie.feature.shelf.ShelfScreen

/**
 * The only place that knows about every feature module.
 *
 * Feature modules never reference one another, so all cross-feature routing is
 * resolved here in `:app`.
 *
 * Opening a screenshot is reported upwards rather than handled here, because the
 * viewer has to be drawn above the whole shell including the navigation bar, and
 * anything composed inside this NavHost is by definition inside the scaffold.
 */
@Composable
fun ShelfieNavHost(
    navController: NavHostController,
    onScreenshotClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = ShelfieDestination.SHELF.route,
        modifier = modifier,
    ) {
        composable(ShelfieDestination.SHELF.route) {
            ShelfScreen(onScreenshotClick = onScreenshotClick)
        }
        composable(ShelfieDestination.SEARCH.route) {
            SearchScreen(onScreenshotClick = onScreenshotClick)
        }
        composable(ShelfieDestination.CLEANUP.route) {
            CleanupScreen()
        }
        composable(ShelfieDestination.SETTINGS.route) {
            SettingsScreen()
        }
    }
}
