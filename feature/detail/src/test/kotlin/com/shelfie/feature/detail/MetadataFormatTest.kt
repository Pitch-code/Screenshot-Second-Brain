package com.shelfie.feature.detail

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for the details panel's formatting.
 *
 * These exist because the panel's only job is to be *correct* — it is there so the
 * figures can be checked against the gallery. A details panel that is merely
 * plausible is worse than none, and the failure mode is silent: a wrong timestamp
 * still looks like a timestamp.
 */
class MetadataFormatTest {

    @Test
    fun `a missing timestamp formats to null rather than the epoch`() {
        // The bug this guards: treating null as 0 prints "1 Jan 1970", which looks
        // like real data and is the single most common date-handling mistake here.
        assertThat(MetadataFormat.timestamp(null)).isNull()
    }

    @Test
    fun `a timestamp in milliseconds formats to its own year`() {
        // 2024-03-15T10:30:00Z in milliseconds.
        val formatted = MetadataFormat.timestamp(1_710_498_600_000L)

        assertThat(formatted).isNotNull()
        assertThat(formatted).contains("2024")
    }

    @Test
    fun `seconds mistaken for milliseconds would land in 1970 and this proves the unit`() {
        // The same instant expressed in seconds. If anything ever passes seconds to
        // this function, the year is 1970 — so asserting the two differ pins down
        // which unit the function expects.
        val asMillis = MetadataFormat.timestamp(1_710_498_600_000L)
        val asSecondsByMistake = MetadataFormat.timestamp(1_710_498_600L)

        assertThat(asMillis).contains("2024")
        assertThat(asSecondsByMistake).contains("1970")
        assertThat(asMillis).isNotEqualTo(asSecondsByMistake)
    }

    @Test
    fun `dimensions read width by height`() {
        assertThat(MetadataFormat.dimensions(1080, 2400)).isEqualTo("1080 × 2400")
    }

    @Test
    fun `dimensions are omitted when either side is unknown`() {
        // MediaStore reports 0 for images it has not measured. "0 × 2400" is worse
        // than showing nothing, because it reads as a real measurement.
        assertThat(MetadataFormat.dimensions(0, 2400)).isNull()
        assertThat(MetadataFormat.dimensions(1080, 0)).isNull()
        assertThat(MetadataFormat.dimensions(0, 0)).isNull()
    }

    @Test
    fun `file size is omitted when unknown rather than shown as zero`() {
        assertThat(MetadataFormat.fileSize(0L)).isNull()
        assertThat(MetadataFormat.fileSize(-1L)).isNull()
    }

    @Test
    fun `a real file size is formatted in binary units`() {
        // 1 MiB. Reported as MB, matching Android's own storage figures, so the panel
        // cannot contradict the gallery it exists to be checked against.
        assertThat(MetadataFormat.fileSize(1024L * 1024L)).contains("MB")
        assertThat(MetadataFormat.fileSize(2048L)).contains("KB")
    }
}
