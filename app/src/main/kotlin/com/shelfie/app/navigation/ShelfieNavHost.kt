package com.shelfie.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.shelfie.feature.cleanup.CleanupScreen
import com.shelfie.feature.detail.DetailSheet
import com.shelfie.feature.search.SearchScreen
import com.shelfie.feature.settings.SettingsScreen
import com.shelfie.feature.shelf.ShelfScreen

/**
 * The only place that knows about every feature module.
 *
 * Feature modules never reference one another, so all cross-feature routing —
 * including opening the detail sheet from either the shelf or search — is
 * resolved here in `:app`.
 */
@Composable
fun ShelfieNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    // Detail is an overlay rather than a destination, so the shelf stays visible
    // behind the sheet's scrim. Saveable so it survives configuration changes.
    var detailScreenshotId: Long? by rememberSaveable { mutableStateOf(null) }

    NavHost(
        navController = navController,
        startDestination = ShelfieDestination.SHELF.route,
        modifier = modifier,
    ) {
        composable(ShelfieDestination.SHELF.route) {
            ShelfScreen(onScreenshotClick = { id -> detailScreenshotId = id })
        }
        composable(ShelfieDestination.SEARCH.route) {
            SearchScreen(onScreenshotClick = { id -> detailScreenshotId = id })
        }
        composable(ShelfieDestination.CLEANUP.route) {
            CleanupScreen()
        }
        composable(ShelfieDestination.SETTINGS.route) {
            SettingsScreen()
        }
    }

    detailScreenshotId?.let { id ->
        DetailSheet(
            screenshotId = id,
            onDismiss = { detailScreenshotId = null },
        )
    }
}
