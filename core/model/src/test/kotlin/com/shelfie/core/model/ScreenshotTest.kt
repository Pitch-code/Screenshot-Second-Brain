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
        val progress = IndexProgress(indexed = 120, total = 120, tier = IndexTier.IDLE)
        assertThat(progress.isComplete).isTrue()
        assertThat(progress.fraction).isEqualTo(1f)
    }

    @Test
    fun `index progress fraction is safe on an empty library`() {
        val progress = IndexProgress(indexed = 0, total = 0, tier = IndexTier.IDLE)
        assertThat(progress.fraction).isEqualTo(1f)
        assertThat(progress.isComplete).isTrue()
    }

    @Test
    fun `index progress reports partial state during backlog`() {
        val progress = IndexProgress(indexed = 60, total = 5300, tier = IndexTier.BACKLOG)
        assertThat(progress.isComplete).isFalse()
        assertThat(progress.fraction).isLessThan(0.02f)
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
