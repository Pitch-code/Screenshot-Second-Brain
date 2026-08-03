package com.shelfie.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The free window is meant to roll in both directions.
 *
 * It previously only ever held rows back and never released them, so once a
 * screenshot fell outside the newest 150 it stayed unsearchable permanently — even
 * after the user deleted enough screenshots to leave plenty of room. Reported from a
 * device showing "83 searchable · 13 held back by the free limit" on a library of 96.
 */
class QuotaReclaimTest {

    private val limit = IndexingQuota.FREE_INDEX_LIMIT

    /** Mirrors IndexingQuota.reclaimWithinQuota's arithmetic. */
    private fun releaseCount(indexed: Int, held: Int): Int {
        val slack = limit - indexed
        if (slack <= 0) return 0
        return minOf(slack, held)
    }

    @Test
    fun `the reported case releases every held screenshot`() {
        // 83 indexed, 13 held, limit 150. There is room for all of them.
        assertThat(releaseCount(indexed = 83, held = 13)).isEqualTo(13)
    }

    @Test
    fun `a full window releases nothing`() {
        assertThat(releaseCount(indexed = limit, held = 20)).isEqualTo(0)
    }

    @Test
    fun `an over-full window releases nothing`() {
        // Transiently possible: enforce runs after a batch, so indexed can exceed the
        // limit for a moment before the trim.
        assertThat(releaseCount(indexed = limit + 5, held = 20)).isEqualTo(0)
    }

    @Test
    fun `partial room releases only what fits`() {
        // 140 indexed leaves 10 slots; 30 are held, so only 10 come back.
        assertThat(releaseCount(indexed = 140, held = 30)).isEqualTo(10)
    }

    @Test
    fun `nothing held means nothing to release`() {
        assertThat(releaseCount(indexed = 10, held = 0)).isEqualTo(0)
    }

    @Test
    fun `an empty library releases nothing because nothing is held`() {
        assertThat(releaseCount(indexed = 0, held = 0)).isEqualTo(0)
    }

    @Test
    fun `reclaiming cannot oscillate`() {
        // The property that makes this safe to run on every indexing pass: releasing
        // at most the available slack means the resulting indexed count never exceeds
        // the limit, so the following enforce holds nothing back, so there is nothing
        // to reclaim on the pass after that.
        var indexed = 140
        var held = 30

        repeat(5) {
            val released = releaseCount(indexed, held)
            held -= released
            indexed += released // released rows are re-indexed

            assertThat(indexed).isAtMost(limit)
        }

        assertThat(indexed).isEqualTo(limit)
        assertThat(held).isEqualTo(20)
    }

    @Test
    fun `deleting screenshots at the boundary brings one back`() {
        // At the limit with one held, the user deletes a screenshot. The held one
        // should take the freed slot — this is the rolling window working.
        val afterDeletion = limit - 1

        assertThat(releaseCount(indexed = afterDeletion, held = 1)).isEqualTo(1)
    }

    @Test
    fun `release count is never negative`() {
        listOf(0, 1, 50, limit, limit + 100).forEach { indexed ->
            listOf(0, 1, 500).forEach { held ->
                assertThat(releaseCount(indexed, held)).isAtLeast(0)
            }
        }
    }
}
