package com.shelfie.core.classify

import com.shelfie.core.model.ScreenshotCategory

/**
 * A user-defined sorting rule: "anything containing Zerodha goes to Investments".
 *
 * This exists because the single most substantive complaint about every
 * competitor in this category is that the built-in categories don't match what
 * people actually screenshot. Rules always beat the built-in scorer, so the
 * user's judgement is final and one correction fixes every future screenshot.
 */
data class UserRule(
    val id: Long,
    /** Plain text the user typed. Matched case-insensitively as a whole word. */
    val keyword: String,
    val category: ScreenshotCategory,
    /** A user-supplied label, which may differ from the enum's default name. */
    val displayLabel: String? = null,
    val enabled: Boolean = true,
) {
    private val matcher: Regex by lazy {
        val escaped = keyword.trim().split(Regex("""\s+""")).joinToString("""\s+""") { Regex.escape(it) }
        Regex("""(?<![\p{L}\p{N}])$escaped(?![\p{L}\p{N}])""", RegexOption.IGNORE_CASE)
    }

    fun matches(text: String): Boolean =
        enabled && keyword.isNotBlank() && matcher.containsMatchIn(text)

    /**
     * Of the given (id, text) rows, the ids whose text this rule matches.
     *
     * Pulled out of the repository so the backlog sweep that runs when a rule is
     * created can be tested without a database: the result is exactly the set of
     * screenshots a future scan would also file under this rule.
     */
    fun selectMatchingIds(rows: List<Pair<Long, String?>>): List<Long> =
        rows.filter { matches(it.second.orEmpty()) }.map { it.first }
}
