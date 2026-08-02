package com.shelfie.core.model

/**
 * Drives the non-blocking status strip on the shelf.
 *
 * The whole competitive thesis is that indexing never blocks the user, so this
 * is informational only — the UI must remain fully usable at every value.
 *
 * ### Why completion is defined by [outstanding] and not by `indexed >= total`
 *
 * It used to be the latter, which could never become true for a free user with
 * more screenshots than the free-tier window. `total` counts every row, while
 * `indexed` counts only fully-indexed ones — and the free tier deliberately caps
 * that at the newest 150, rolling the remainder into a held state. So a user with
 * 152 screenshots sat at "150 of 152 done" permanently, with a progress bar
 * frozen at 98% and a banner that could not be dismissed for good.
 *
 * Completion therefore has to be about **work that can still happen**, not about
 * reaching a total that includes rows the app has deliberately decided not to
 * process.
 */
data class IndexProgress(
    /** Rows fully indexed and searchable. */
    val indexed: Int,
    /** Every row Shelfie knows about, excluding deleted ones. */
    val total: Int,
    /**
     * Rows that can still change state: queued, in flight, or retryable.
     *
     * Excludes held-back and unreadable rows, which will never progress and so
     * must not hold the banner open.
     */
    val outstanding: Int,
    val tier: IndexTier,
) {
    /**
     * Rows that have reached a final state, by any route — indexed, held back by
     * the free tier, or genuinely unreadable.
     *
     * This is the honest numerator for "X of Y": it always converges on [total].
     */
    val settled: Int
        get() {
            // Clamped because the three counts come from separate database flows
            // and can briefly disagree mid-update; a nonsensical intermediate
            // value must not render as a >100% progress bar.
            val ceiling = total.coerceAtLeast(0)
            return (total - outstanding).coerceIn(0, ceiling)
        }

    val isComplete: Boolean get() = outstanding <= 0

    val fraction: Float
        get() = if (total <= 0) 1f else (settled.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    companion object {
        val Complete = IndexProgress(indexed = 0, total = 0, outstanding = 0, tier = IndexTier.IDLE)
    }
}

/**
 * The three-tier scheduling model. Tier 3 is constrained to idle + charging,
 * which is the single most important scheduling decision in the app.
 */
enum class IndexTier {
    /** Nothing pending. */
    IDLE,

    /** Newest ~60, processed in the foreground for time-to-first-value. */
    IMMEDIATE,

    /** Next ~500, expedited background work. */
    RECENT,

    /** Everything older; runs only while idle and charging. */
    BACKLOG,
}
