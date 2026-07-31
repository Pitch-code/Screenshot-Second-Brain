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
}
