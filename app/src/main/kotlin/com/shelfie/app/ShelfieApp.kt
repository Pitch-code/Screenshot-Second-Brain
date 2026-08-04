package com.shelfie.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shelfie.app.navigation.ShelfieDestination
import com.shelfie.app.navigation.ShelfieNavHost
import com.shelfie.feature.detail.ScreenshotViewer
import com.shelfie.feature.onboarding.OnboardingScreen

/**
 * Adaptive app shell.
 *
 * A NavigationBar at compact width and a NavigationRail from 600dp. Android 17
 * (API 37) removes the opt-out from resizability constraints on large screens,
 * so adapting is a requirement rather than a nicety.
 */
@Composable
fun ShelfieApp(
    startOnSearch: Boolean = false,
    navController: NavHostController = rememberNavController(),
    viewModel: ShelfieAppViewModel = hiltViewModel(),
) {
    val startState by viewModel.startState.collectAsStateWithLifecycle()

    when (val state = startState) {
        ShelfieStartState.Loading -> Unit // Splash screen is still showing.

        ShelfieStartState.Onboarding -> OnboardingScreen(
            onFinished = viewModel::onOnboardingFinished,
        )

        is ShelfieStartState.Ready -> MainShell(
            navController = navController,
            startOnSearch = startOnSearch,
        )
    }
}

@Composable
private fun MainShell(navController: NavHostController, startOnSearch: Boolean = false) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = ShelfieDestination.fromRoute(backStackEntry?.destination?.route)

    // Jump to Search once, when launched from the widget or the tile.
    var handledDeepLink by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startOnSearch) {
        if (startOnSearch && !handledDeepLink) {
            handledDeepLink = true
            navController.navigate(ShelfieDestination.SEARCH.route) { launchSingleTop = true }
        }
    }

    val onNavigate: (ShelfieDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            // One instance per tab, and each tab keeps its own state.
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /*
     * Which screenshot is open in the full-screen viewer, if any.
     *
     * Held here, above the scaffold, rather than inside the NavHost. The viewer has
     * to cover the navigation bar to be full screen, and anything composed inside
     * the NavHost sits within the scaffold's content slot with the bar drawn on top
     * of it. Saveable so it survives rotation.
     */
    var viewingScreenshotId: Long? by rememberSaveable { mutableStateOf(null) }
    val onScreenshotClick: (Long) -> Unit = { id -> viewingScreenshotId = id }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        if (useRail) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    ShelfieDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = destination == current,
                            onClick = { onNavigate(destination) },
                            icon = { DestinationIcon(destination, destination == current) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                ShelfieNavHost(
                    navController = navController,
                    onScreenshotClick = onScreenshotClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Scaffold(
                // Transparent so the gradient behind it shows through. Scaffold
                // otherwise paints an opaque background over it.
                containerColor = Color.Transparent,
                // Both, always. Scaffold derives its content colour from its
                // container, and `contentColorFor(Transparent)` is unspecified, so it
                // falls back to LocalContentColor — which at the root is BLACK. That
                // rendered every text and icon that does not set its own colour in
                // near-black on a dark navy background: date headers, the selection
                // count, the toolbar icons.
                contentColor = MaterialTheme.colorScheme.onBackground,
                bottomBar = {
                    NavigationBar {
                        ShelfieDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = destination == current,
                                onClick = { onNavigate(destination) },
                                icon = { DestinationIcon(destination, destination == current) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) { innerPadding ->
                ShelfieNavHost(
                    navController = navController,
                    onScreenshotClick = onScreenshotClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }

        // Last in the box, so it is drawn over the navigation bar and the rail.
        viewingScreenshotId?.let { id ->
            ScreenshotViewer(
                screenshotId = id,
                onDismiss = { viewingScreenshotId = null },
            )
        }
    }
}

@Composable
private fun DestinationIcon(destination: ShelfieDestination, selected: Boolean) {
    Icon(
        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
        // The label is always shown alongside, so the icon is decorative.
        contentDescription = null,
    )
}
