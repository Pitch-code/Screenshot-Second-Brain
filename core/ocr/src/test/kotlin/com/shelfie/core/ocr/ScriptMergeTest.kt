package com.shelfie.core.ocr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Covers [ScriptMerge.merge], which decides what the Devanagari pass contributes
 * on top of the Latin pass. The rule under test: take a line from the Devanagari
 * pass only when it actually contains Devanagari script, so an English screenshot
 * is never polluted with the Devanagari model's garbled echo of the Latin text.
 */
class ScriptMergeTest {

    @Test
    fun `english-only screenshot keeps just the latin text`() {
        val latin = "Payment Successful\n₹500 paid to Ramesh"
        // What the Devanagari model tends to return on English input: a garbled
        // echo with no Devanagari characters. It must be dropped entirely.
        val deva = "Payrnent Successtul\n500 paid"

        assertThat(ScriptMerge.merge(latin, deva)).isEqualTo(latin)
    }

    @Test
    fun `devanagari lines are appended after the latin text`() {
        val latin = "₹500"
        val deva = "₹500 भेज दिए गए\nरमेश को"

        assertThat(ScriptMerge.merge(latin, deva))
            .isEqualTo("₹500\n₹500 भेज दिए गए\nरमेश को")
    }

    @Test
    fun `a hindi-only screenshot returns the devanagari text when latin found nothing`() {
        // The pure-Latin line (the digits) is dropped from the Devanagari pass
        // because in the real pipeline the Latin pass already captured it.
        val deva = "ओटीपी कोड\n4 8 3 9 2 0"

        assertThat(ScriptMerge.merge("", deva)).isEqualTo("ओटीपी कोड")
    }

    @Test
    fun `lines without a devanagari character are not taken from the second pass`() {
        val latin = "Invoice 12345"
        val deva = "12345\nTotal"

        assertThat(ScriptMerge.merge(latin, deva)).isEqualTo(latin)
    }

    @Test
    fun `a blank devanagari pass leaves the latin text untouched`() {
        val latin = "hello world"

        assertThat(ScriptMerge.merge(latin, "")).isEqualTo(latin)
    }
}
