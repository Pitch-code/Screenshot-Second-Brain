package com.shelfie.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchQueryTest {

    // ------------------------------------------------------------ tokenizing

    @Test
    fun `splits on whitespace and punctuation`() {
        assertThat(SearchQuery.tokenize("swiggy order, 340!"))
            .containsExactly("swiggy", "order", "340")
    }

    @Test
    fun `lowercases tokens`() {
        assertThat(SearchQuery.tokenize("IRCTC PNR")).containsExactly("irctc", "pnr")
    }

    @Test
    fun `blank query yields no tokens`() {
        assertThat(SearchQuery.tokenize("   ")).isEmpty()
        assertThat(SearchQuery.tokenize("!!! ???")).isEmpty()
    }

    // ------------------------------------------------------------- FTS match

    @Test
    fun `last token gets a prefix wildcard for as-you-type search`() {
        assertThat(SearchQuery.toFtsMatch("rece")).isEqualTo("rece*")
    }

    @Test
    fun `multiple tokens are anded with only the last one wildcarded`() {
        assertThat(SearchQuery.toFtsMatch("swiggy rece")).isEqualTo("swiggy rece*")
    }

    @Test
    fun `returns null when there is nothing to search for`() {
        assertThat(SearchQuery.toFtsMatch("")).isNull()
        assertThat(SearchQuery.toFtsMatch("  -- ")).isNull()
    }

    @Test
    fun `strips fts operators so a query cannot break the match expression`() {
        // Quotes, stars, carets and NEAR are all meaningful in FTS syntax. If any
        // survived tokenisation, a user typing them would crash the query.
        val match = SearchQuery.toFtsMatch("""pay" OR ^x NEAR/2 *""")
        assertThat(match).isNotNull()
        assertThat(match!!).doesNotContain("\"")
        assertThat(match).doesNotContain("^")
        assertThat(match).doesNotContain("/")
        // Only the trailing wildcard we added ourselves may remain.
        assertThat(match.count { it == '*' }).isEqualTo(1)
    }

    @Test
    fun `quoted phrase becomes separate anded tokens`() {
        assertThat(SearchQuery.toFtsMatch("\"payment successful\""))
            .isEqualTo("payment successful*")
    }

    // ------------------------------------------------------------ highlights

    @Test
    fun `highlights a single match`() {
        val ranges = SearchQuery.highlightRanges("Payment successful", "payment")
        assertThat(ranges).containsExactly(0 until 7)
    }

    @Test
    fun `highlighting is case insensitive`() {
        val ranges = SearchQuery.highlightRanges("PAYMENT successful", "payment")
        assertThat(ranges).containsExactly(0 until 7)
    }

    @Test
    fun `highlights prefix matches at word starts`() {
        val ranges = SearchQuery.highlightRanges("receipt received", "rece")
        assertThat(ranges).hasSize(2)
    }

    @Test
    fun `does not highlight mid word occurrences`() {
        // FTS prefix search matches word starts, so highlighting must agree.
        // "pay" must not light up inside "repay".
        val ranges = SearchQuery.highlightRanges("repay", "pay")
        assertThat(ranges).isEmpty()
    }

    @Test
    fun `highlights each token of a multi token query`() {
        val ranges = SearchQuery.highlightRanges("Paid to Swiggy today", "paid swiggy")
        assertThat(ranges).hasSize(2)
    }

    @Test
    fun `merges overlapping ranges`() {
        val ranges = SearchQuery.highlightRanges("payment", "pay payment")
        assertThat(ranges).hasSize(1)
        assertThat(ranges.first()).isEqualTo(0 until 7)
    }

    @Test
    fun `no tokens means no highlights`() {
        assertThat(SearchQuery.highlightRanges("anything", "")).isEmpty()
    }

    @Test
    fun `empty text means no highlights`() {
        assertThat(SearchQuery.highlightRanges("", "pay")).isEmpty()
    }

    @Test
    fun `highlight ranges are within the text bounds`() {
        val text = "UPI Ref No 412345678901 payment successful"
        SearchQuery.highlightRanges(text, "payment upi 4123").forEach { range ->
            assertThat(range.first).isAtLeast(0)
            assertThat(range.last).isLessThan(text.length)
        }
    }

    // -------------------------------------------------------------- snippets

    @Test
    fun `short text is returned whole`() {
        assertThat(SearchQuery.snippet("Payment successful", "payment"))
            .isEqualTo("Payment successful")
    }

    @Test
    fun `collapses runs of whitespace`() {
        assertThat(SearchQuery.snippet("Payment\n\n  successful", "payment"))
            .isEqualTo("Payment successful")
    }

    @Test
    fun `long text is trimmed around the first match`() {
        val text = "filler ".repeat(40) + "TARGETWORD " + "tail ".repeat(40)
        val snippet = SearchQuery.snippet(text, "targetword", maxLength = 60)

        assertThat(snippet.length).isAtMost(64) // 60 plus ellipses
        assertThat(snippet.lowercase()).contains("targetword")
    }

    @Test
    fun `long text with no match is truncated from the start`() {
        val text = "filler ".repeat(50)
        val snippet = SearchQuery.snippet(text, "absent", maxLength = 40)
        assertThat(snippet).endsWith("…")
    }
}
