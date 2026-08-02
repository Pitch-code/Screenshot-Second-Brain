package com.shelfie.core.model

/**
 * A folder the user made themselves.
 *
 * ### Why folders are not just extra categories
 *
 * [ScreenshotCategory] is a closed enum on purpose: the classifier scores against
 * it, Room stores it by name, and the rule store decodes it by `valueOf`. Letting
 * users add members would mean a free-form category column, a lookup table for
 * labels and icons, and a silent-fallback path in three separate decoders — a lot
 * of risk for a feature that is really a different idea anyway.
 *
 * Categories answer "what did the app work out this is?". Folders answer "where do
 * I want this?". Keeping them as separate axes means a wrong guess by the
 * classifier can never destroy a filing decision the user made deliberately.
 *
 * A screenshot in a folder is shown under that folder rather than its category,
 * because the user's explicit choice outranks the app's guess.
 */
data class Folder(
    val id: Long,
    val name: String,
    val icon: FolderIcon,
) {
    companion object {
        /** Long enough to be descriptive, short enough to fit a filter chip. */
        const val MAX_NAME_LENGTH = 24

        /**
         * Folder names are compared case-insensitively and trimmed, so "Work" and
         * " work " are the same folder. Prevents a near-duplicate that looks like
         * a bug to the user.
         */
        fun normaliseName(raw: String): String = raw.trim().take(MAX_NAME_LENGTH)

        fun isValidName(raw: String): Boolean = normaliseName(raw).isNotEmpty()
    }
}

/**
 * Icon choices for a user-made folder.
 *
 * Stored by name, and a stable, closed set rather than free-form: the icons are
 * Compose vector objects, not drawable resources, so they cannot be looked up
 * dynamically. An unknown stored value decodes to [FOLDER].
 */
enum class FolderIcon {
    FOLDER,
    STAR,
    HEART,
    WORK,
    TRAVEL,
    MONEY,
    HOME,
    SHOPPING,
    ;

    companion object {
        fun fromNameOrDefault(value: String?): FolderIcon =
            entries.firstOrNull { it.name == value } ?: FOLDER
    }
}


/** A folder plus how many screenshots it currently holds. */
data class FolderWithCount(
    val folder: Folder,
    val count: Int,
)
