package com.shelfie.core.classify

import com.google.common.truth.Truth.assertThat
import com.shelfie.core.model.ScreenshotCategory
import org.junit.Test

/**
 * Covers [UserRule.selectMatchingIds], the pure core of the backlog sweep that
 * runs when a user creates a correction rule. It decides which already-indexed
 * screenshots a new rule should pull out of NOT_SORTED, and must use exactly the
 * same whole-word, case-insensitive matching the classifier uses for future
 * scans — otherwise a row moved today could fail to match tomorrow.
 */
class UserRuleBacklogTest {

    private fun rule(keyword: String, enabled: Boolean = true) = UserRule(
        id = 1,
        keyword = keyword,
        category = ScreenshotCategory.STUDY,
        enabled = enabled,
    )

    @Test
    fun `picks only rows whose text contains the keyword as a whole word`() {
        val rows = listOf(
            1L to "Zerodha order report for the quarter",
            2L to "nothing relevant here",
            3L to "notes on ZERODHA account",
        )

        val matched = rule("zerodha").selectMatchingIds(rows)

        assertThat(matched).containsExactly(1L, 3L).inOrder()
    }

    @Test
    fun `does not match the keyword inside a larger word`() {
        val rows = listOf(
            1L to "this is a payment, not a paymentgateway",
            2L to "pay the bill",
        )

        // "pay" must not match "payment"; it should match the standalone "pay".
        val matched = rule("pay").selectMatchingIds(rows)

        assertThat(matched).containsExactly(2L)
    }

    @Test
    fun `tolerates rows with no recognised text`() {
        val rows = listOf(
            1L to null,
            2L to "",
            3L to "syllabus for semester one",
        )

        val matched = rule("syllabus").selectMatchingIds(rows)

        assertThat(matched).containsExactly(3L)
    }

    @Test
    fun `a disabled rule matches nothing`() {
        val rows = listOf(1L to "chapter one revision")

        val matched = rule("chapter", enabled = false).selectMatchingIds(rows)

        assertThat(matched).isEmpty()
    }

    @Test
    fun `a blank keyword matches nothing, so the whole library is never swept`() {
        val rows = listOf(1L to "anything at all", 2L to "literally anything")

        val matched = rule("   ").selectMatchingIds(rows)

        assertThat(matched).isEmpty()
    }

    @Test
    fun `matches a multi-word keyword across flexible whitespace`() {
        val rows = listOf(
            1L to "your ANSWER   KEY is ready",
            2L to "answerkey attached",
        )

        val matched = rule("answer key").selectMatchingIds(rows)

        assertThat(matched).containsExactly(1L)
    }
}
