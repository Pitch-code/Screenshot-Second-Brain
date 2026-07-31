package com.shelfie.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class ByteFormatTest {

    private val locale = Locale.UK

    @Test
    fun `formats bytes below a kilobyte`() {
        assertThat(ByteFormat.format(0, locale)).isEqualTo("0 B")
        assertThat(ByteFormat.format(512, locale)).isEqualTo("512 B")
    }

    @Test
    fun `formats kilobytes without decimals`() {
        assertThat(ByteFormat.format(1024, locale)).isEqualTo("1 KB")
        assertThat(ByteFormat.format(20 * 1024, locale)).isEqualTo("20 KB")
    }

    @Test
    fun `shows a decimal for small megabyte values`() {
        // 2.5 MB reading as "2 MB" would understate what the user gets back.
        assertThat(ByteFormat.format((2.5 * 1024 * 1024).toLong(), locale)).isEqualTo("2.5 MB")
    }

    @Test
    fun `drops the decimal for larger megabyte values`() {
        assertThat(ByteFormat.format(780L * 1024 * 1024, locale)).isEqualTo("780 MB")
    }

    @Test
    fun `formats gigabytes with two decimals`() {
        assertThat(ByteFormat.format(2L * 1024 * 1024 * 1024, locale)).isEqualTo("2.00 GB")
    }

    @Test
    fun `negative input is clamped rather than shown as negative`() {
        assertThat(ByteFormat.format(-500, locale)).isEqualTo("0 KB")
    }

    @Test
    fun `uses binary units so figures agree with Android storage settings`() {
        // 1 MB is 1024 KB here, not 1000, matching what the system reports.
        assertThat(ByteFormat.format(1024L * 1024, locale)).isEqualTo("1.0 MB")
    }
}
