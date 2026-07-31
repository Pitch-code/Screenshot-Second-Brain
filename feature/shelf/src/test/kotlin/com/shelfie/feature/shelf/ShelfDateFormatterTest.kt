package com.shelfie.feature.shelf

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ShelfDateFormatterTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 31)

    private fun epochOf(date: LocalDate): Long =
        date.atStartOfDay(zone).toEpochSecond() + 3600

    @Test
    fun `today is labelled as the Today type rather than English text`() {
        assertThat(ShelfDateFormatter.label(epochOf(today), zone, today)).isEqualTo(DateLabel.Today)
    }

    @Test
    fun `yesterday is labelled Yesterday`() {
        val yesterday = today.minusDays(1)
        assertThat(ShelfDateFormatter.label(epochOf(yesterday), zone, today))
            .isEqualTo(DateLabel.Yesterday)
    }

    @Test
    fun `earlier this year omits the year`() {
        val label = ShelfDateFormatter.label(epochOf(LocalDate.of(2026, 3, 4)), zone, today)
        assertThat(label).isInstanceOf(DateLabel.Formatted::class.java)
        val text = (label as DateLabel.Formatted).text
        assertThat(text).contains("4")
        assertThat(text).doesNotContain("2026")
    }

    @Test
    fun `a previous year includes the year`() {
        val label = ShelfDateFormatter.label(epochOf(LocalDate.of(2025, 3, 4)), zone, today)
        assertThat((label as DateLabel.Formatted).text).contains("2025")
    }

    @Test
    fun `two screenshots on the same day need no header between them`() {
        val morning = today.atStartOfDay(zone).toEpochSecond() + 60
        val evening = today.atStartOfDay(zone).toEpochSecond() + 80_000
        assertThat(ShelfDateFormatter.needsHeaderBetween(morning, evening, zone, today)).isFalse()
    }

    @Test
    fun `a day boundary needs a header`() {
        assertThat(
            ShelfDateFormatter.needsHeaderBetween(
                epochOf(today),
                epochOf(today.minusDays(1)),
                zone,
                today,
            ),
        ).isTrue()
    }

    @Test
    fun `the first item always gets a header`() {
        assertThat(
            ShelfDateFormatter.needsHeaderBetween(null, epochOf(today), zone, today),
        ).isTrue()
    }

    @Test
    fun `no header is added after the last item`() {
        assertThat(
            ShelfDateFormatter.needsHeaderBetween(epochOf(today), null, zone, today),
        ).isFalse()
    }
}
