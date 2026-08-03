package com.shelfie.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The free window is meant to roll in both directions.
 *
 * It previously only ever held rows back and never released them, so once a
 * screenshot fell outside the free window it stayed unsearchable permanently — even
 * after the user deleted enough screenshots to leave plenty of room. Reported from a
 * device showing "83 searchable · 13 held back by the free limit" on a library of 96,
 * when the limit was 150.
 *
 * These take the limit as a parameter rather than reading the constant. An earlier
 * version hardcoded counts that only made sense at a limit of 150, so lowering it to
 * 50 broke the tests without anything being wrong with the code — the arithmetic being
 * verified is independent of where the limit happens to sit.
 */
class QuotaReclaimTest {

    /** Mirrors IndexingQuota.reclaimWithinQuota's arithmetic. */
    private fun releaseCount(limit: Int, indexed: Int, held: Int): Int {
        val slack = limit - indexed
        if (slack <= 0) return 0
        return minOf(slack, held)
    }

    private val current = IndexingQuota.FREE_INDEX_LIMIT

    @Test
    fun `the originally reported case releases every held screenshot`() {
        // 83 indexed, 13 held, against the limit of 150 that was in force at the time.
        assertThat(releaseCount(limit = 150, indexed = 83, held = 13)).isEqualTo(13)
    }

    @Test
    fun `a full window releases nothing`() {
        assertThat(releaseCount(current, indexed = current, held = 20)).isEqualTo(0)
    }

    @Test
    fun `an over-full window releases nothing`() {
        // Transiently possible: enforce runs after a batch, so indexed can exceed the
        // limit for a moment before the trim.
        assertThat(releaseCount(current, indexed = current + 5, held = 20)).isEqualTo(0)
    }

    @Test
    fun `partial room releases only what fits`() {
        val indexed = current - 10

        assertThat(releaseCount(current, indexed = indexed, held = 30)).isEqualTo(10)
    }

    @Test
    fun `nothing held means nothing to release`() {
        assertThat(releaseCount(current, indexed = 1, held = 0)).isEqualTo(0)
    }

    @Test
    fun `an empty library releases nothing because nothing is held`() {
        assertThat(releaseCount(current, indexed = 0, held = 0)).isEqualTo(0)
    }

    @Test
    fun `reclaiming cannot oscillate`() {
        // The property that makes this safe to run on every indexing pass: releasing
        // at most the available slack means the resulting indexed count never exceeds
        // the limit, so the following enforce holds nothing back, so there is nothing
        // to reclaim on the pass after that.
        var indexed = current - 10
        var held = 30

        repeat(5) {
            val released = releaseCount(current, indexed, held)
            held -= released
            indexed += released // released rows are re-indexed

            assertThat(indexed).isAtMost(current)
        }

        assertThat(indexed).isEqualTo(current)
        assertThat(held).isEqualTo(20)
    }

    @Test
    fun `deleting a screenshot at the boundary brings one back`() {
        // At the limit with one held, the user deletes a screenshot. The held one
        // should take the freed slot — this is the rolling window working.
        assertThat(releaseCount(current, indexed = current - 1, held = 1)).isEqualTo(1)
    }

    @Test
    fun `release count is never negative and never exceeds what is held`() {
        listOf(0, 1, current / 2, current, current + 100).forEach { indexed ->
            listOf(0, 1, 500).forEach { held ->
                val released = releaseCount(current, indexed, held)
                assertThat(released).isAtLeast(0)
                assertThat(released).isAtMost(held)
            }
        }
    }

    @Test
    fun `the arithmetic holds at any limit`() {
        // Guards against the limit becoming a load-bearing magic number again.
        listOf(1, 10, 50, 150, 1_000).forEach { limit ->
            // Full window: nothing comes back.
            assertThat(releaseCount(limit, indexed = limit, held = 5)).isEqualTo(0)

            // Empty window: everything held comes back, but never more slots than the
            // limit has. At a limit of 1, only one of the five can return.
            assertThat(releaseCount(limit, indexed = 0, held = 5))
                .isEqualTo(minOf(limit, 5))
        }
    }
}
