package com.shelfie.core.model

/**
 * Drives the non-blocking status strip on the shelf.
 *
 * The whole competitive thesis is that indexing never blocks the user, so this
 * is informational only — the UI must remain fully usable at every value.
 */
data class IndexProgress(
    val indexed: Int,
    val total: Int,
    val tier: IndexTier,
) {
    val isComplete: Boolean get() = indexed >= total
    val fraction: Float get() = if (total == 0) 1f else indexed.toFloat() / total.toFloat()

    companion object {
        val Complete = IndexProgress(0, 0, IndexTier.IDLE)
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
