package com.shelfie.core.model

/**
 * What the shelf is currently showing.
 *
 * A sealed type rather than a nullable [ScreenshotCategory], because there are now
 * three cases and "null means everything" stops being expressive once folders
 * exist.
 */
sealed interface ShelfFilter {
    data object All : ShelfFilter

    /** An automatic category. Excludes screenshots filed into a folder. */
    data class Category(val category: ScreenshotCategory) : ShelfFilter

    data class InFolder(val folderId: Long) : ShelfFilter
}

/**
 * Grid ordering.
 *
 * Persisted by name, so adding or reordering entries cannot silently re-map a
 * saved preference.
 */
enum class ShelfSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST,
    LARGEST_FIRST,
    SMALLEST_FIRST,
    ;

    /**
     * Date headers only make sense when the list is in date order. Sorting by size
     * walks across days arbitrarily, which would emit a header above nearly every
     * tile — and repeat the same day's header, producing duplicate list keys.
     */
    val groupsByDate: Boolean get() = this == NEWEST_FIRST || this == OLDEST_FIRST

    companion object {
        val Default = NEWEST_FIRST

        fun fromNameOrDefault(value: String?): ShelfSortOrder =
            entries.firstOrNull { it.name == value } ?: Default
    }
}
