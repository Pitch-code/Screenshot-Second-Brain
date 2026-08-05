package com.shelfie.core.ocr

/**
 * Version of the text extraction pipeline.
 *
 * Bumped whenever the stored text for an image would come out differently. Already
 * indexed screenshots keep whatever text the old pipeline produced until they are
 * re-read, so a change here is worthless without a re-index — the app compares this
 * against the version recorded in preferences and requeues everything when it moves.
 *
 * 1 — ML Kit's own `Text.getText()`, in model output order.
 * 2 — geometric reading order, see [ReadingOrder].
 */
const val TEXT_PIPELINE_VERSION = 2

/**
 * One recognised run of text, with where it sits on the image.
 *
 * Coordinates are in the pixel space of whatever bitmap was recognised, which is
 * downsampled and therefore varies in size. Every threshold below is expressed as a
 * ratio of measured text height rather than an absolute pixel count, so the result
 * does not change with the sample size.
 */
data class TextFragment(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    /** Coerced so a degenerate zero-height box can never produce a zero threshold. */
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/**
 * Puts recognised text back into the order a person would read it.
 *
 * ML Kit returns blocks in its own detection order, which is roughly the order the
 * model emitted them and bears no reliable relation to layout. Concatenating that
 * directly — which is all `Text.getText()` does — produces genuinely shuffled output
 * on ordinary screenshots: a payment confirmation whose amount arrives before the
 * merchant, a chat where replies precede the messages they answer.
 *
 * That is not only ugly to read. It breaks classification, because the category
 * rules match multi-word phrases like "payment successful": when the recogniser
 * splits a phrase across two blocks and those blocks come back separated by
 * unrelated text, the phrase is no longer there to match and the screenshot falls
 * through to "Not sorted yet".
 *
 * ## Approach
 *
 * Rows first, then columns within a row — the same thing an eye does.
 *
 * 1. Work at line granularity. Block boxes are loose and often swallow unrelated
 *    neighbours; line boxes are tight.
 * 2. Group lines into horizontal bands, where two lines belong to the same band if
 *    their vertical extents overlap by at least half the shorter one's height.
 *    Overlap is used rather than centre distance because it stays correct when a
 *    large heading sits beside small body text.
 * 3. Sort within each band left to right, and bands top to bottom.
 * 4. Separate bands with a blank line when the vertical gap between them is large,
 *    which preserves the visual grouping people rely on when skimming.
 *
 * This deliberately reads a multi-column layout across the row rather than down each
 * column. For screenshots that is the right call: the common multi-column cases are
 * status bars, list rows with a trailing value, and product grids, and in all three
 * the horizontal pairing is the meaningful one. True side-by-side prose columns
 * essentially do not occur in screenshots.
 */
object ReadingOrder {

    /**
     * Fraction of the shorter fragment's height that two fragments must overlap
     * vertically to count as the same row.
     *
     * Half is forgiving enough for the baseline jitter in real recognition output,
     * and strict enough that consecutive lines of a paragraph stay separate.
     */
    private const val ROW_OVERLAP_RATIO = 0.5

    /**
     * Vertical gap, as a multiple of median text height, above which two bands are
     * treated as separate paragraphs.
     */
    private const val PARAGRAPH_GAP_RATIO = 0.8

    /**
     * Fraction of image height within which a band may be the status bar.
     *
     * Android's status bar is around 24dp of a screen at least 640dp tall, so well
     * under 6% even on a short screen. Kept as a fraction because the bitmap is
     * downsampled to a size that varies.
     */
    private const val STATUS_BAR_MAX_FRACTION = 0.06

    /**
     * Longest a band may be and still be dismissed as the status bar.
     *
     * A status bar holds a clock, a couple of indicators and a battery figure. A real
     * line of content that happens to start near the top will normally be longer, and
     * this stops a legitimate heading being discarded because it contains a time.
     */
    private const val STATUS_BAR_MAX_CHARS = 44

    /**
     * What a status bar looks like: a clock, a battery percentage, or a network speed.
     *
     * A clock is effectively always present. The others are here because some skins —
     * including the one this was reported on — put a live network speed up there,
     * which is the source of values like `0.62` and `2.00`.
     */
    private val STATUS_BAR_SIGNATURE = Regex(
        """\d{1,2}:\d{2}|\d{1,3}\s?%|\b[KMG]B/s\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * @param imageHeight height in the same pixel space as the fragments' coordinates.
     *   Pass 0 when unknown, which disables status bar removal rather than guessing at
     *   a position — dropping a real line is worse than keeping a clock.
     */
    fun arrange(fragments: List<TextFragment>, imageHeight: Int = 0): String {
        val usable = fragments.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return ""

        val medianHeight = usable.map { it.height }.sorted()[usable.size / 2]

        // Sorted by top, then left, so band assembly only ever has to consider the
        // band it is currently building, and so the result is deterministic when
        // two fragments start at the same height.
        val ordered = usable.sortedWith(compareBy({ it.top }, { it.left }))

        val bands = mutableListOf<MutableList<TextFragment>>()
        for (fragment in ordered) {
            val current = bands.lastOrNull()
            // Compared against the band's first fragment rather than its grown
            // envelope: an envelope widens with every addition, so one tall
            // fragment would let the band go on absorbing everything below it.
            if (current != null && sharesRow(current.first(), fragment)) {
                current += fragment
            } else {
                bands += mutableListOf(fragment)
            }
        }

        /*
         * Drop the status bar.
         *
         * Every screenshot has one, and its contents — the clock, the battery, the
         * network speed on some skins — are never what the screenshot is about. Left
         * in, they are actively harmful now that reading order is correct, because
         * being at the top means being *first*: the shelf labels a screenshot with the
         * first useful-looking line it finds, and that line became the clock. Hence
         * tiles reading "2:03 2.00" instead of a place name.
         *
         * It also pollutes search — every screenshot matching a time — and adds noise
         * to the text the classifier scores.
         *
         * Only the first band is ever considered, and only when it is high enough,
         * short enough, and looks like a status bar. All three must hold: text lost
         * here can never be searched for again.
         */
        val body = bands.firstOrNull()
            ?.takeIf { first -> isStatusBar(first, imageHeight) }
            ?.let { bands.drop(1) }
            ?: bands

        return buildString {
            var previousBottom: Int? = null
            for (band in body) {
                band.sortBy { it.left }

                previousBottom?.let { bottom ->
                    val gap = band.minOf { it.top } - bottom
                    append(if (gap > PARAGRAPH_GAP_RATIO * medianHeight) "\n\n" else "\n")
                }

                append(band.joinToString(separator = " ") { it.text.trim() })
                previousBottom = band.maxOf { it.bottom }
            }
        }
    }

    private fun isStatusBar(band: List<TextFragment>, imageHeight: Int): Boolean {
        if (imageHeight <= 0) return false
        if (band.maxOf { it.bottom } > imageHeight * STATUS_BAR_MAX_FRACTION) return false

        val text = band.joinToString(" ") { it.text.trim() }
        if (text.length > STATUS_BAR_MAX_CHARS) return false

        return STATUS_BAR_SIGNATURE.containsMatchIn(text)
    }

    private fun sharesRow(a: TextFragment, b: TextFragment): Boolean {
        val overlap = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)
        return overlap >= ROW_OVERLAP_RATIO * minOf(a.height, b.height)
    }
}
