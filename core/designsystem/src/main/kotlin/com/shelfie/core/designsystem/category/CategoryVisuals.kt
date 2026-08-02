package com.shelfie.core.designsystem.category

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HomeWork
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.shelfie.core.designsystem.R
import com.shelfie.core.model.FolderIcon
import com.shelfie.core.model.ScreenshotCategory

/**
 * Display label and icon for each category.
 *
 * Labels are string resources rather than literals so the app can be localised —
 * the launch market is multilingual, and hardcoded English would make that a
 * rewrite rather than a translation.
 *
 * Every category carries an icon as well as a colour, so meaning is never encoded
 * in colour alone.
 */
@get:StringRes
val ScreenshotCategory.labelRes: Int
    get() = when (this) {
        ScreenshotCategory.PAYMENTS -> R.string.category_payments
        ScreenshotCategory.OTP_CODES -> R.string.category_otp
        ScreenshotCategory.TICKETS -> R.string.category_tickets
        ScreenshotCategory.WIFI_PASSWORDS -> R.string.category_wifi
        ScreenshotCategory.PRODUCTS -> R.string.category_products
        ScreenshotCategory.CHATS -> R.string.category_chats
        ScreenshotCategory.DOCUMENTS -> R.string.category_documents
        ScreenshotCategory.RECIPES -> R.string.category_recipes
        ScreenshotCategory.PLACES -> R.string.category_places
        ScreenshotCategory.STUDY -> R.string.category_study
        ScreenshotCategory.CONTACTS -> R.string.category_contacts
        ScreenshotCategory.NOT_SORTED -> R.string.category_not_sorted
    }

val ScreenshotCategory.icon: ImageVector
    get() = when (this) {
        ScreenshotCategory.PAYMENTS -> Icons.Outlined.AccountBalanceWallet
        ScreenshotCategory.OTP_CODES -> Icons.Outlined.Pin
        ScreenshotCategory.TICKETS -> Icons.Outlined.ConfirmationNumber
        ScreenshotCategory.WIFI_PASSWORDS -> Icons.Outlined.Wifi
        ScreenshotCategory.PRODUCTS -> Icons.Outlined.Inventory2
        ScreenshotCategory.CHATS -> Icons.AutoMirrored.Outlined.Chat
        ScreenshotCategory.DOCUMENTS -> Icons.Outlined.Description
        ScreenshotCategory.RECIPES -> Icons.Outlined.Restaurant
        ScreenshotCategory.PLACES -> Icons.Outlined.Place
        ScreenshotCategory.STUDY -> Icons.Outlined.School
        ScreenshotCategory.CONTACTS -> Icons.Outlined.Contacts
        ScreenshotCategory.NOT_SORTED -> Icons.Outlined.Password
    }

/**
 * Icon for a user-made folder.
 *
 * Folder names are user text and so cannot be string resources; the icon is chosen
 * from a fixed set instead, which keeps it localisation-independent and means a
 * stored value can always be resolved to something drawable.
 */
val FolderIcon.icon: ImageVector
    get() = when (this) {
        FolderIcon.FOLDER -> Icons.Outlined.Folder
        FolderIcon.STAR -> Icons.Outlined.StarBorder
        FolderIcon.HEART -> Icons.Outlined.FavoriteBorder
        FolderIcon.WORK -> Icons.Outlined.Work
        FolderIcon.TRAVEL -> Icons.Outlined.Flight
        FolderIcon.MONEY -> Icons.Outlined.Payments
        FolderIcon.HOME -> Icons.Outlined.HomeWork
        FolderIcon.SHOPPING -> Icons.Outlined.ShoppingBag
    }
