package com.shelfie.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class DateTextParserTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun `parses day first numeric dates`() {
        assertThat(DateTextParser.parseDate("14/08/2026")).isEqualTo(LocalDate.of(2026, 8, 14))
        assertThat(DateTextParser.parseDate("14-08-2026")).isEqualTo(LocalDate.of(2026, 8, 14))
        assertThat(DateTextParser.parseDate("14.8.2026")).isEqualTo(LocalDate.of(2026, 8, 14))
    }

    @Test
    fun `parses textual dates in both orders`() {
        assertThat(DateTextParser.parseDate("14 Aug 2026")).isEqualTo(LocalDate.of(2026, 8, 14))
        assertThat(DateTextParser.parseDate("Aug 14, 2026")).isEqualTo(LocalDate.of(2026, 8, 14))
        assertThat(DateTextParser.parseDate("14 August 2026")).isEqualTo(LocalDate.of(2026, 8, 14))
    }

    @Test
    fun `parses two digit years`() {
        assertThat(DateTextParser.parseDate("14/08/26")).isEqualTo(LocalDate.of(2026, 8, 14))
    }

    @Test
    fun `returns null for unparseable or blank dates`() {
        assertThat(DateTextParser.parseDate("not a date")).isNull()
        assertThat(DateTextParser.parseDate("")).isNull()
        assertThat(DateTextParser.parseDate(null)).isNull()
    }

    @Test
    fun `returns null for an impossible date rather than rolling over`() {
        // Silently turning 32 January into 1 February would put a calendar event
        // on the wrong day, which is worse than not creating one.
        assertThat(DateTextParser.parseDate("32/01/2026")).isNull()
        assertThat(DateTextParser.parseDate("14/13/2026")).isNull()
    }

    @Test
    fun `parses 24 hour and 12 hour times`() {
        assertThat(DateTextParser.parseTime("18:30")).isEqualTo(LocalTime.of(18, 30))
        assertThat(DateTextParser.parseTime("6:45 AM")).isEqualTo(LocalTime.of(6, 45))
        assertThat(DateTextParser.parseTime("9:05 p.m.")).isEqualTo(LocalTime.of(21, 5))
    }

    @Test
    fun `combines date and time into epoch millis`() {
        val millis = DateTextParser.parseEpochMillis("14 Aug 2026", "18:30", utc)
        val expected = LocalDate.of(2026, 8, 14).atTime(18, 30)
            .atZone(utc).toInstant().toEpochMilli()

        assertThat(millis).isEqualTo(expected)
    }

    @Test
    fun `falls back to midnight when the time is missing or unparseable`() {
        val expected = LocalDate.of(2026, 8, 14).atStartOfDay(utc).toInstant().toEpochMilli()

        assertThat(DateTextParser.parseEpochMillis("14 Aug 2026", null, utc)).isEqualTo(expected)
        assertThat(DateTextParser.parseEpochMillis("14 Aug 2026", "half past six", utc))
            .isEqualTo(expected)
    }

    @Test
    fun `returns null when the date cannot be parsed even with a valid time`() {
        assertThat(DateTextParser.parseEpochMillis("someday", "18:30", utc)).isNull()
    }
}
