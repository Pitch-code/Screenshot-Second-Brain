package com.shelfie.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.shelfie.app.R

/**
 * The four top-level destinations. A fifth would be a mistake — cognitive
 * overload from feature bloat is a documented churn driver, and the product
 * spec fixes the information architecture at four.
 *
 * Labels are string resources, not literals. They were literals, which made them the
 * only user-facing text in the app that could never be translated — lint's
 * `HardcodedText` check reads XML and so had nothing to say about them.
 */
enum class ShelfieDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    SHELF(
        route = "shelf",
        labelRes = R.string.destination_shelf,
        selectedIcon = Icons.Filled.PhotoLibrary,
        unselectedIcon = Icons.Outlined.PhotoLibrary,
    ),
    /**
     * Labelled "Find" rather than "Search" because the tab now does both: browse
     * your folders and the automatic categories, or search inside the text. "Search"
     * described only half of it, and left folders looking like they belonged on the
     * shelf.
     *
     * The route string stays "search" so no saved navigation state breaks.
     */
    SEARCH(
        route = "search",
        labelRes = R.string.destination_find,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
    ),
    CLEANUP(
        route = "cleanup",
        labelRes = R.string.destination_cleanup,
        selectedIcon = Icons.Filled.CleaningServices,
        unselectedIcon = Icons.Outlined.CleaningServices,
    ),
    SETTINGS(
        route = "settings",
        labelRes = R.string.destination_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
    ;

    companion object {
        fun fromRoute(route: String?): ShelfieDestination =
            entries.firstOrNull { it.route == route } ?: SHELF
    }
}
