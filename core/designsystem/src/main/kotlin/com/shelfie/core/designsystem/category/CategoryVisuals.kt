package com.shelfie.core.designsystem.category

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.shelfie.core.model.ScreenshotCategory

/**
 * Display label and icon for each category.
 *
 * Labels are user-facing wording, not enum names. Note that [ScreenshotCategory.NOT_SORTED]
 * reads "Not sorted yet" rather than "Uncategorised" — it describes a pending
 * state rather than blaming the user's library for being messy.
 *
 * Every category carries an icon as well as a colour so meaning is never encoded
 * in colour alone.
 */
val ScreenshotCategory.label: String
    get() = when (this) {
        ScreenshotCategory.PAYMENTS -> "Payments"
        ScreenshotCategory.OTP_CODES -> "OTP codes"
        ScreenshotCategory.TICKETS -> "Tickets"
        ScreenshotCategory.WIFI_PASSWORDS -> "Wi-Fi"
        ScreenshotCategory.PRODUCTS -> "Products"
        ScreenshotCategory.CHATS -> "Chats"
        ScreenshotCategory.DOCUMENTS -> "Documents"
        ScreenshotCategory.RECIPES -> "Recipes"
        ScreenshotCategory.PLACES -> "Places"
        ScreenshotCategory.STUDY -> "Study"
        ScreenshotCategory.CONTACTS -> "Contacts"
        ScreenshotCategory.NOT_SORTED -> "Not sorted yet"
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
