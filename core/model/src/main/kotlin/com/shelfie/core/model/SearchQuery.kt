package com.shelfie.core.model

/**
 * Search query handling: tokenising, building the SQLite FTS MATCH expression,
 * and locating matches for highlighting.
 *
 * Lives in `:core:model` because both the database layer and the UI need the
 * exact same tokenisation. If they disagreed, results would highlight the wrong
 * substrings — or nothing at all — which quietly destroys trust in the index.
 *
 * Pure Kotlin and fully unit-tested.
 */
object SearchQuery {

    /** Anything that is not a letter or digit is a separator. */
    private val SEPARATORS = Regex("""[^\p{L}\p{N}]+""")

    /**
     * Splits a raw query into normalised search tokens.
     *
     * Because every non-alphanumeric character is discarded, the resulting
     * tokens are inherently safe to embed in an FTS expression — there is no
     * way to inject MATCH operators such as `*`, `"`, `^`, `OR` or `NEAR`.
     */
    fun tokenize(raw: String): List<String> =
        raw.lowercase()
            .split(SEPARATORS)
            .filter { it.isNotBlank() }

    /**
     * Builds an FTS4 MATCH expression, or null when the query has no usable
     * tokens (callers should then show the unfiltered shelf rather than running
     * a query that matches nothing).
     *
     * Tokens are ANDed implicitly. The final token gets a `*` so that search
     * behaves as-you-type: typing "rece" already matches "receipt".
     */
    fun toFtsMatch(raw: String): String? {
        val tokens = tokenize(raw)
        if (tokens.isEmpty()) return null

        return tokens.mapIndexed { index, token ->
            if (index == tokens.lastIndex) "$token*" else token
        }.joinToString(separator = " ")
    }

    /**
     * Ranges in [text] that matched [raw], for highlighting in results.
     *
     * Prefix semantics mirror [toFtsMatch]: every token matches at a word start,
     * which is what FTS actually did, so the highlight reflects the real reason
     * the row was returned.
     *
     * Returned ranges are sorted and non-overlapping.
     */
    fun highlightRanges(text: String, raw: String): List<IntRange> {
        val tokens = tokenize(raw)
        if (tokens.isEmpty() || text.isEmpty()) return emptyList()

        val lowerText = text.lowercase()
        val ranges = mutableListOf<IntRange>()

        for (token in tokens) {
            var searchFrom = 0
            while (searchFrom <= lowerText.length - token.length) {
                val start = lowerText.indexOf(token, searchFrom)
                if (start < 0) break

                // Only highlight at a word boundary, matching FTS prefix search.
                val precededByWordChar = start > 0 && lowerText[start - 1].isLetterOrDigit()
                if (!precededByWordChar) {
                    ranges += start until (start + token.length)
                }
                searchFrom = start + token.length
            }
        }
        return ranges.mergeOverlapping()
    }

    /**
     * A short excerpt of [text] centred on the first match, for the result
     * subtitle. Long OCR text is mostly irrelevant to why a row matched.
     */
    fun snippet(text: String, raw: String, maxLength: Int = 90): String {
        val flattened = text.replace(Regex("""\s+"""), " ").trim()
        if (flattened.length <= maxLength) return flattened

        val firstMatch = highlightRanges(flattened, raw).minByOrNull { it.first }
            ?: return flattened.take(maxLength).trimEnd() + "…"

        // Centre the window on the match, then clamp to the text bounds.
        val half = maxLength / 2
        var start = (firstMatch.first - half).coerceAtLeast(0)
        var end = (start + maxLength).coerceAtMost(flattened.length)
        start = (end - maxLength).coerceAtLeast(0)

        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < flattened.length) "…" else ""
        return prefix + flattened.substring(start, end).trim() + suffix
    }

    private fun List<IntRange>.mergeOverlapping(): List<IntRange> {
        if (size <= 1) return this
        val sorted = sortedBy { it.first }
        val merged = mutableListOf(sorted.first())

        for (range in sorted.drop(1)) {
            val last = merged.last()
            if (range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }
}
