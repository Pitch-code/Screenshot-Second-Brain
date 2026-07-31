package com.shelfie.feature.shelf

import com.shelfie.core.model.Screenshot
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A row in the shelf grid: either a full-width date header or a screenshot tile.
 *
 * Headers are produced by Paging's `insertSeparators`, so grouping works on a
 * paged stream without ever loading the whole library.
 */
sealed interface ShelfListItem {

    val key: String

    data class DateHeader(val label: DateLabel) : ShelfListItem {
        override val key: String get() = "header-${label.key}"
    }

    data class Item(val screenshot: Screenshot) : ShelfListItem {
        override val key: String get() = "item-${screenshot.id}"
    }
}

/**
 * A date group heading.
 *
 * Modelled as a type rather than a pre-built string so "Today" and "Yesterday"
 * can be localised by the UI layer. Formatting a date is locale-aware and safe to
 * do here; translating a word is not.
 */
sealed interface DateLabel {

    /** Stable identity for Paging keys. */
    val key: String

    data object Today : DateLabel {
        override val key: String get() = "today"
    }

    data object Yesterday : DateLabel {
        override val key: String get() = "yesterday"
    }

    data class Formatted(val text: String) : DateLabel {
        override val key: String get() = text
    }
}

/**
 * Human date grouping.
 *
 * "Today" and "Yesterday" carry more meaning than a date, and recent screenshots
 * are exactly the ones people are looking for.
 */
object ShelfDateFormatter {

    // Resolved per call rather than cached: caching Locale.getDefault() breaks
    // date formatting if the user changes language while the app is running.
    private val sameYear: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())

    private val otherYear: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.getDefault())

    fun label(
        epochSeconds: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): DateLabel {
        val date = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()

        return when {
            date == today -> DateLabel.Today
            date == today.minusDays(1) -> DateLabel.Yesterday
            date.year == today.year -> DateLabel.Formatted(date.format(sameYear))
            else -> DateLabel.Formatted(date.format(otherYear))
        }
    }

    /** True when [a] and [b] belong to different date groups. */
    fun needsHeaderBetween(
        a: Long?,
        b: Long?,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): Boolean {
        if (b == null) return false
        if (a == null) return true
        return label(a, zone, today) != label(b, zone, today)
    }
}
