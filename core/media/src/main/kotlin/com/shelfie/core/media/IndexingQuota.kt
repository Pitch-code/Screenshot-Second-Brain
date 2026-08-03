package com.shelfie.core.media

import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.datastore.ShelfiePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The free tier.
 *
 * Deliberately a **rolling window of the newest screenshots** rather than a hard
 * stop at the first 150. A hard stop would mean the app silently stopped working
 * for anything taken after the cap was hit, which is the punitive design the
 * product spec rules out. With a rolling window, a free user's *recent*
 * screenshots are always searchable — which is where nearly all the value is,
 * since screenshot usefulness decays fast.
 *
 * Rolled-out rows keep their metadata and stay visible on the shelf. Only the
 * searchable text is dropped, and unlocking restores everything.
 */
@Singleton
class IndexingQuota @Inject constructor(
    private val dao: ScreenshotDao,
    private val preferences: ShelfiePreferences,
) {

    suspend fun isUnlimited(): Boolean = preferences.isFullVersion.first()

    /**
     * Rolls anything beyond the free window out of the search index.
     *
     * Called after each indexing batch. No-op for paid users.
     */
    suspend fun enforce(): Int {
        if (isUnlimited()) {
            // A paid user should never have holds left over from before the purchase.
            dao.releaseQuotaHolds()
            return 0
        }

        // Reclaim before trimming, not after. The window is meant to be rolling in
        // both directions: this used to only ever hold rows back and never give them
        // up, so once a screenshot fell out of the free window it stayed unsearchable
        // for good — even after the user deleted enough screenshots to leave plenty of
        // room. It showed up as "83 searchable · 13 held back by the free limit" on a
        // library of 96, which is nonsense to read and looks like the free tier
        // quietly shrinking.
        reclaimWithinQuota()

        return dao.holdBeyondQuota(FREE_INDEX_LIMIT)
    }

    /**
     * True when this row would be held back the moment it was recognised, so there is
     * no point recognising it.
     *
     * Without this the indexer works through the entire backlog newest-first,
     * extracting text from every screenshot and then discarding all but the newest N.
     * On a library of a few thousand that is hours of battery spent producing nothing.
     *
     * The rolling window is preserved: a row newer than the current boundary still
     * gets recognised, and displaces an older one. Only rows that are already outside
     * the window are skipped.
     */
    suspend fun shouldSkipUnrecognised(dateAdded: Long): Boolean {
        if (isUnlimited()) return false

        // Room left, so nothing is being displaced.
        if (dao.indexedCount() < FREE_INDEX_LIMIT) return false

        val boundary = dao.dateAddedAtRank(FREE_INDEX_LIMIT - 1) ?: return false
        return dateAdded < boundary
    }

    /** Marks a row as held without recognising it. */
    suspend fun holdWithoutIndexing(id: Long) = dao.holdWithoutIndexing(id)

    /**
     * Returns held-back screenshots to the queue while the window has room.
     *
     * Released rows go back to PENDING rather than straight to INDEXED, because their
     * recognised text was deleted when they were held — the text has to be extracted
     * again before they are searchable.
     *
     * Newest-first, matching the direction the window rolls: if only some can come
     * back, they should be the most recent of the held set.
     *
     * Cannot oscillate. Releasing at most the available slack means the next
     * [enforce] finds nothing beyond the limit, so it holds nothing, so there is
     * nothing to reclaim on the pass after that.
     */
    private suspend fun reclaimWithinQuota(): Int {
        val indexed = dao.indexedCount()
        val slack = FREE_INDEX_LIMIT - indexed
        if (slack <= 0) return 0

        return dao.releaseNewestQuotaHolds(slack)
    }

    /**
     * Releases every held row back to PENDING so the background tiers pick them
     * up. Called immediately after a successful purchase.
     */
    suspend fun releaseAll(): Int = dao.releaseQuotaHolds()

    val state: Flow<QuotaState> = combine(
        preferences.isFullVersion,
        dao.observeIndexedCount(),
        dao.observeQuotaHeldCount(),
    ) { isFull, indexed, held ->
        QuotaState(
            isUnlimited = isFull,
            indexed = indexed,
            heldBack = held,
            limit = FREE_INDEX_LIMIT,
        )
    }

    companion object {
        /**
         * Free-tier window size.
         *
         * Large enough to be genuinely useful — most people's recent screenshots
         * fit comfortably — while leaving a real reason to unlock for anyone with
         * a big backlog.
         */
        const val FREE_INDEX_LIMIT = 50
    }
}

data class QuotaState(
    val isUnlimited: Boolean = false,
    val indexed: Int = 0,
    val heldBack: Int = 0,
    val limit: Int = IndexingQuota.FREE_INDEX_LIMIT,
) {
    /** True when screenshots exist that a purchase would make searchable. */
    val hasHeldBackItems: Boolean get() = !isUnlimited && heldBack > 0

    /** Non-punitive prompt: only shown when there is something concrete to gain. */
    val shouldSuggestUpgrade: Boolean get() = hasHeldBackItems
}
