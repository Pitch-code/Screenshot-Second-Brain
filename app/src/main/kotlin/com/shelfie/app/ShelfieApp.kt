package com.shelfie.app

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shelfie.app.navigation.ShelfieDestination
import com.shelfie.app.navigation.ShelfieNavHost

/**
 * Adaptive app shell.
 *
 * A NavigationBar at compact width and a NavigationRail from 600dp. Android 17
 * (API 37) removes the opt-out from resizability constraints on large screens,
 * so adapting is a requirement rather than a nicety.
 */
@Composable
fun ShelfieApp(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = ShelfieDestination.fromRoute(backStackEntry?.destination?.route)

    val onNavigate: (ShelfieDestination) -> Unit = { destination ->
        navController.navigate(destination.route) {
            // Single instance per tab, and preserve each tab's state.
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

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
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Scaffold(
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
            ) { innerPadding ->
                ShelfieNavHost(
                    navController = navController,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun DestinationIcon(destination: ShelfieDestination, selected: Boolean) {
    Icon(
        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
        // Label is always present alongside, so the icon is decorative.
        contentDescription = null,
    )
}
