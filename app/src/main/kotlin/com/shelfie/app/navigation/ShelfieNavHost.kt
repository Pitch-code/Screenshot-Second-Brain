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
 * The only place that knows about every feature module. Features never
 * reference one another, so all cross-feature routing lives here in `:app`.
 */
@Composable
fun ShelfieNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = ShelfieDestination.SHELF.route,
        modifier = modifier,
    ) {
        composable(ShelfieDestination.SHELF.route) {
            ShelfScreen()
        }
        composable(ShelfieDestination.SEARCH.route) {
            SearchScreen()
        }
        composable(ShelfieDestination.CLEANUP.route) {
            CleanupScreen()
        }
        composable(ShelfieDestination.SETTINGS.route) {
            SettingsScreen()
        }
    }
}
