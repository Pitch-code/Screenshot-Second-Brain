package com.shelfie.core.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Subsampling maths is pure arithmetic, so it is verified here on the JVM
 * without needing a device. This is the calculation that keeps peak memory
 * bounded, so it is worth locking down.
 */
class BitmapDecoderTest {

    @Test
    fun `typical phone screenshot is not downsampled`() {
        // 1080x2400 — longest edge 2400. Halving gives 1200, which is below the
        // 1440 target, so it must stay at full resolution.
        assertThat(BitmapDecoder.calculateInSampleSize(1080, 2400)).isEqualTo(1)
    }

    @Test
    fun `very large screenshot is halved`() {
        // 1440x3200 -> halving gives 1600, still >= 1440, so sample by 2.
        assertThat(BitmapDecoder.calculateInSampleSize(1440, 3200)).isEqualTo(2)
    }

    @Test
    fun `tablet screenshot is downsampled aggressively`() {
        // 4000x6000 -> 3000 -> 1500 (both >= 1440), so sample by 4.
        assertThat(BitmapDecoder.calculateInSampleSize(4000, 6000)).isEqualTo(4)
    }

    @Test
    fun `small image is never upscaled`() {
        assertThat(BitmapDecoder.calculateInSampleSize(320, 480)).isEqualTo(1)
    }

    @Test
    fun `sample size is always a power of two`() {
        val sizes = listOf(
            800 to 600, 1080 to 1920, 1440 to 2960, 2160 to 3840, 5000 to 8000,
        )
        sizes.forEach { (w, h) ->
            val sample = BitmapDecoder.calculateInSampleSize(w, h)
            assertThat(Integer.bitCount(sample)).isEqualTo(1)
        }
    }

    @Test
    fun `landscape and portrait of the same size behave identically`() {
        assertThat(BitmapDecoder.calculateInSampleSize(3200, 1440))
            .isEqualTo(BitmapDecoder.calculateInSampleSize(1440, 3200))
    }

    @Test
    fun `resulting longest edge stays at or above the recognition floor`() {
        // Guards the accuracy side of the trade-off: downsampling must never
        // push an image below roughly 1000px, where OCR starts failing on
        // small UI text.
        val cases = listOf(1080 to 2400, 1440 to 3200, 2160 to 4800, 4000 to 6000)
        cases.forEach { (w, h) ->
            val sample = BitmapDecoder.calculateInSampleSize(w, h)
            val resultingLongest = maxOf(w, h) / sample
            assertThat(resultingLongest).isAtLeast(1000)
        }
    }
}
