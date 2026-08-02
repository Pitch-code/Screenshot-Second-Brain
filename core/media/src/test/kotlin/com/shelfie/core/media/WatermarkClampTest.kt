package com.shelfie.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The watermark clamp, extracted as pure arithmetic so it is testable without a
 * database or a media provider.
 *
 * This guards a failure mode that is invisible in normal use and permanent once it
 * happens: `date_added` is copied verbatim from the media provider, and some OEM
 * providers (and restored or cloud-synced media) report it in milliseconds or with a
 * future timestamp. One such row makes `MAX(date_added)` enormous, and every
 * subsequent `DATE_ADDED >= watermark` scan then matches nothing — for good. The
 * user sees the first launch work and no screenshot ever discovered again.
 */
class WatermarkClampTest {

    /** Mirrors ScreenshotRepository.currentWatermark's clamping. */
    private fun clamp(newest: Long?, nowSeconds: Long): Long =
        (newest ?: 0L).coerceIn(0L, nowSeconds)

    private val now = 1_800_000_000L

    @Test
    fun `a sane timestamp passes through untouched`() {
        assertThat(clamp(now - 60, now)).isEqualTo(now - 60)
    }

    @Test
    fun `a millisecond timestamp cannot freeze discovery`() {
        // The real-world case: a provider reporting milliseconds is ~1000x too
        // large, which would put the watermark tens of thousands of years ahead.
        val milliseconds = now * 1000

        assertThat(clamp(milliseconds, now)).isEqualTo(now)
    }

    @Test
    fun `a future timestamp is pulled back to now`() {
        assertThat(clamp(now + 86_400, now)).isEqualTo(now)
    }

    @Test
    fun `an empty database starts from the beginning of time`() {
        assertThat(clamp(null, now)).isEqualTo(0L)
    }

    @Test
    fun `a negative timestamp cannot produce a negative watermark`() {
        assertThat(clamp(-5L, now)).isEqualTo(0L)
    }

    @Test
    fun `the clamped watermark is always usable as a query bound`() {
        val candidates = listOf(null, -1L, 0L, now - 1, now, now + 1, now * 1000, Long.MAX_VALUE)

        candidates.forEach { candidate ->
            val result = clamp(candidate, now)
            assertThat(result).isAtLeast(0L)
            assertThat(result).isAtMost(now)
        }
    }
}
