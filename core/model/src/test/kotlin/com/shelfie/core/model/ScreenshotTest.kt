package com.shelfie.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Sanity tests for the pure-Kotlin model layer. These run on the JVM in
 * milliseconds, which is the reason `:core:model` has no Android dependency.
 */
class ScreenshotTest {

    @Test
    fun `aspect ratio is width over height`() {
        val screenshot = screenshot(width = 1080, height = 2400)
        assertThat(screenshot.aspectRatio).isWithin(0.0001f).of(0.45f)
    }

    @Test
    fun `aspect ratio does not divide by zero`() {
        val screenshot = screenshot(width = 1080, height = 0)
        assertThat(screenshot.aspectRatio).isEqualTo(1f)
    }

    @Test
    fun `full access is not limited mode`() {
        assertThat(MediaAccess.FULL.isLimited).isFalse()
    }

    @Test
    fun `partial and denied access both mean limited mode`() {
        // Limited Mode is required by Play policy whenever broad access is not
        // granted, so both states must route to the same degraded experience.
        assertThat(MediaAccess.PARTIAL.isLimited).isTrue()
        assertThat(MediaAccess.DENIED.isLimited).isTrue()
    }

    @Test
    fun `index progress reports completion when nothing is pending`() {
        val progress = IndexProgress(indexed = 120, total = 120, outstanding = 0, tier = IndexTier.IDLE)
        assertThat(progress.isComplete).isTrue()
        assertThat(progress.fraction).isEqualTo(1f)
    }

    @Test
    fun `index progress fraction is safe on an empty library`() {
        val progress = IndexProgress(indexed = 0, total = 0, outstanding = 0, tier = IndexTier.IDLE)
        assertThat(progress.fraction).isEqualTo(1f)
        assertThat(progress.isComplete).isTrue()
    }

    @Test
    fun `index progress reports partial state during backlog`() {
        val progress =
            IndexProgress(indexed = 60, total = 5300, outstanding = 5240, tier = IndexTier.BACKLOG)
        assertThat(progress.isComplete).isFalse()
        assertThat(progress.fraction).isLessThan(0.02f)
    }

    @Test
    fun `free tier holding screenshots back still counts as complete`() {
        // The exact shipped bug: a free user with 152 screenshots reached 150
        // indexed and 2 held back, and the old rule (indexed >= total) left the
        // banner up permanently claiming "150 of 152 done".
        val progress =
            IndexProgress(indexed = 150, total = 152, outstanding = 0, tier = IndexTier.IDLE)

        assertThat(progress.isComplete).isTrue()
        assertThat(progress.settled).isEqualTo(152)
        assertThat(progress.fraction).isEqualTo(1f)
    }

    @Test
    fun `unreadable screenshots do not hold the banner open`() {
        // Same failure shape as the quota case: rows that will never progress
        // must not keep the strip on screen.
        val progress =
            IndexProgress(indexed = 90, total = 100, outstanding = 0, tier = IndexTier.IDLE)

        assertThat(progress.isComplete).isTrue()
    }

    @Test
    fun `progress is incomplete while any work remains`() {
        val progress =
            IndexProgress(indexed = 150, total = 152, outstanding = 2, tier = IndexTier.BACKLOG)

        assertThat(progress.isComplete).isFalse()
        assertThat(progress.settled).isEqualTo(150)
    }

    @Test
    fun `settled never exceeds total and fraction never exceeds one`() {
        // Guards against a transient race between the three count queries, which
        // are separate flows and can briefly disagree.
        val progress =
            IndexProgress(indexed = 10, total = 10, outstanding = -3, tier = IndexTier.IDLE)

        assertThat(progress.settled).isAtMost(progress.total)
        assertThat(progress.fraction).isAtMost(1f)
    }

    @Test
    fun `outstanding work larger than the total never yields a negative bar`() {
        val progress =
            IndexProgress(indexed = 0, total = 5, outstanding = 99, tier = IndexTier.BACKLOG)

        assertThat(progress.settled).isEqualTo(0)
        assertThat(progress.fraction).isAtLeast(0f)
    }

    private fun screenshot(width: Int, height: Int) = Screenshot(
        id = 1,
        mediaStoreId = 42,
        uri = "content://media/external/images/media/42",
        displayName = "Screenshot_20260731.png",
        dateAdded = 1_785_000_000,
        sizeBytes = 512_000,
        width = width,
        height = height,
        indexState = IndexState.INDEXED,
        category = ScreenshotCategory.PAYMENTS,
    )
}
