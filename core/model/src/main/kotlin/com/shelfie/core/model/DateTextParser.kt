package com.shelfie.core.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Turns extracted date and time strings into epoch millis, for the
 * "add to calendar" action.
 *
 * Best-effort by design. If parsing fails we still open the calendar with a
 * title filled in and let the user pick the date, which is far better than
 * silently guessing wrong and creating an event on the wrong day.
 *
 * Pure Kotlin, so all of the fiddly format handling is unit-tested.
 */
object DateTextParser {

    private val DATE_PATTERNS = listOf(
        "d/M/uuuu", "d-M-uuuu", "d.M.uuuu",
        "d/M/uu", "d-M-uu",
        "d MMM uuuu", "d MMMM uuuu",
        "MMM d uuuu", "MMMM d uuuu",
    )

    private val TIME_PATTERNS = listOf("H:mm", "h:mm a", "h:mma")

    /**
     * Parses [dateText], optionally combined with [timeText].
     *
     * Returns null when the date cannot be understood. When the date parses but
     * the time does not, the result is midnight local time.
     */
    fun parseEpochMillis(
        dateText: String?,
        timeText: String? = null,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        val date = parseDate(dateText) ?: return null
        val time = parseTime(timeText) ?: LocalTime.MIDNIGHT

        return LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
    }

    internal fun parseDate(raw: String?): LocalDate? {
        val cleaned = raw?.trim()?.replace(",", "")?.replace(Regex("""\s+"""), " ")
            ?: return null
        if (cleaned.isEmpty()) return null

        for (pattern in DATE_PATTERNS) {
            val formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
            try {
                return LocalDate.parse(cleaned, formatter)
            } catch (_: DateTimeParseException) {
                // Try the next pattern.
            }
        }
        return null
    }

    internal fun parseTime(raw: String?): LocalTime? {
        val cleaned = raw?.trim()?.uppercase(Locale.ENGLISH)?.replace(".", "")
            ?: return null
        if (cleaned.isEmpty()) return null

        for (pattern in TIME_PATTERNS) {
            val formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)
            try {
                return LocalTime.parse(cleaned, formatter)
            } catch (_: DateTimeParseException) {
                // Try the next pattern.
            }
        }
        return null
    }
}
