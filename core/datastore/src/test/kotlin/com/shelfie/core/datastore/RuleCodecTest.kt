package com.shelfie.core.datastore

import com.google.common.truth.Truth.assertThat
import com.shelfie.core.model.ScreenshotCategory
import org.junit.Test

class RuleCodecTest {

    @Test
    fun `round trips a list of rules`() {
        val rules = listOf(
            StoredRule(1, "Zerodha", ScreenshotCategory.DOCUMENTS),
            StoredRule(2, "swiggy order", ScreenshotCategory.PLACES, enabled = false),
        )
        assertThat(RuleCodec.decodeAll(RuleCodec.encodeAll(rules))).isEqualTo(rules)
    }

    @Test
    fun `empty input decodes to an empty list`() {
        assertThat(RuleCodec.decodeAll("")).isEmpty()
        assertThat(RuleCodec.decodeAll("   ")).isEmpty()
    }

    @Test
    fun `empty list encodes to an empty string`() {
        assertThat(RuleCodec.encodeAll(emptyList())).isEmpty()
    }

    @Test
    fun `drops malformed records instead of throwing`() {
        // A corrupt preference value must never stop the app from starting.
        val encoded = "not-a-rule\n1\u001FZerodha\u001FDOCUMENTS\u001Ftrue"
        val decoded = RuleCodec.decodeAll(encoded)

        assertThat(decoded).hasSize(1)
        assertThat(decoded.first().keyword).isEqualTo("Zerodha")
    }

    @Test
    fun `drops records with an unknown category`() {
        val encoded = "1\u001FThing\u001FNOT_A_REAL_CATEGORY\u001Ftrue"
        assertThat(RuleCodec.decodeAll(encoded)).isEmpty()
    }

    @Test
    fun `strips separator characters from keywords`() {
        val rules = listOf(StoredRule(1, "a\u001Fb\nc", ScreenshotCategory.PAYMENTS))
        val decoded = RuleCodec.decodeAll(RuleCodec.encodeAll(rules))

        assertThat(decoded).hasSize(1)
        assertThat(decoded.first().keyword).doesNotContain("\u001F")
        assertThat(decoded.first().keyword).doesNotContain("\n")
    }

    @Test
    fun `preserves keywords containing spaces and punctuation`() {
        val rules = listOf(StoredRule(9, "HDFC Bank - Credit", ScreenshotCategory.PAYMENTS))
        assertThat(RuleCodec.decodeAll(RuleCodec.encodeAll(rules)).first().keyword)
            .isEqualTo("HDFC Bank - Credit")
    }

    @Test
    fun `defaults enabled to true when the flag is unparseable`() {
        val encoded = "1\u001FThing\u001FPAYMENTS\u001Fnonsense"
        assertThat(RuleCodec.decodeAll(encoded).first().enabled).isTrue()
    }
}
