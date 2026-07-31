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
 * paged stream without ever loading the whole library into memory.
 */
sealed interface ShelfListItem {

    val key: String

    data class DateHeader(val label: String) : ShelfListItem {
        override val key: String get() = "header-$label"
    }

    data class Item(val screenshot: Screenshot) : ShelfListItem {
        override val key: String get() = "item-${screenshot.id}"
    }
}

/**
 * Human date grouping.
 *
 * "Today" and "Yesterday" carry more meaning than a date, and recent screenshots
 * are exactly the ones users are looking for.
 */
object ShelfDateFormatter {

    private val sameYear = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
    private val otherYear = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.getDefault())

    fun label(
        epochSeconds: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): String {
        val date = Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate()

        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> date.format(sameYear)
            else -> date.format(otherYear)
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
