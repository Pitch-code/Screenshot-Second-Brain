package com.shelfie.core.media

import com.shelfie.core.database.dao.MAX_INDEX_ATTEMPTS

/**
 * Batch sizes for the three-tier indexing model.
 *
 * This is the single most important set of numbers in the app. Every competitor
 * in this category tries to OCR the user's entire backlog before showing
 * anything, which on a mid-range phone means tens of minutes of full-tilt CPU,
 * a hot device, and an uninstall before the user ever sees the payoff.
 *
 * Screenshot value decays fast: what you captured today matters, what you
 * captured eight months ago is mostly deletable. So indexing newest-first
 * delivers almost all the perceived value for a tiny fraction of the compute.
 */
object IndexTierPolicy {

    /**
     * Tier 1 — foreground, immediate. Sized so a searchable shelf appears in
     * under ten seconds even on a budget device.
     */
    const val IMMEDIATE_BATCH = 60

    /** Tier 2 — expedited background work covering the recent past. */
    const val RECENT_BATCH = 500

    /**
     * Tier 3 — the backlog. Chunked so each worker run finishes well inside its
     * execution window and checkpoints progress to the database.
     */
    const val BACKLOG_CHUNK = 50

    /**
     * Small batch read when the shelf resumes, so a screenshot taken while the
     * app was backgrounded shows up promptly without making resume feel heavy.
     */
    const val CATCH_UP_BATCH = 12

    /**
     * Give up on an item after this many failed attempts.
     *
     * Aliases the database-layer constant rather than restating it: the work
     * queue and the progress banner must agree on what "still retryable" means,
     * or the banner can hang open on rows the queue has already abandoned.
     */
    const val MAX_ATTEMPTS = MAX_INDEX_ATTEMPTS

    /** Periodic safety-net reconcile interval. */
    const val RECONCILE_INTERVAL_HOURS = 6L
}
