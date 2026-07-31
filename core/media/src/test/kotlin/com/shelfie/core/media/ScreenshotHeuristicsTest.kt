package com.shelfie.core.media

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * OEM path fragmentation is the most likely cause of "the app missed my
 * screenshots" reviews, so every known vendor layout is pinned here.
 */
class ScreenshotHeuristicsTest {

    // ---------------------------------------------------------------- paths

    @Test
    fun `recognises known oem screenshot folders`() {
        val paths = listOf(
            "Pictures/Screenshots/",
            "DCIM/Screenshots/",
            "Pictures/Screenshot/",
            "DCIM/ScreenCapture/",
            "Pictures/ScreenCapture/",
            "Pictures/screenshots/",
        )
        paths.forEach { path ->
            assertThat(ScreenshotHeuristics.isScreenshotPath(path)).isTrue()
        }
    }

    @Test
    fun `rejects ordinary photo folders`() {
        val paths = listOf("DCIM/Camera/", "Pictures/WhatsApp/", "Download/", "Movies/")
        paths.forEach { path ->
            assertThat(ScreenshotHeuristics.isScreenshotPath(path)).isFalse()
        }
    }

    @Test
    fun `null path is not a screenshot path`() {
        assertThat(ScreenshotHeuristics.isScreenshotPath(null)).isFalse()
    }

    @Test
    fun `handles windows style separators`() {
        assertThat(ScreenshotHeuristics.isScreenshotPath("Pictures\\Screenshots\\")).isTrue()
    }

    // ------------------------------------------------------------- filenames

    @Test
    fun `recognises typical screenshot filenames`() {
        val names = listOf(
            "Screenshot_20260731-142233.png",
            "screenshot_2026-07-31.jpg",
            "Screen_20260731_142233.png",
            "ScreenCap_001.png",
        )
        names.forEach { name ->
            assertThat(ScreenshotHeuristics.isScreenshotFilename(name)).isTrue()
        }
    }

    @Test
    fun `rejects camera filenames`() {
        val names = listOf("IMG_20260731_142233.jpg", "PXL_20260731.jpg", "photo.png")
        names.forEach { name ->
            assertThat(ScreenshotHeuristics.isScreenshotFilename(name)).isFalse()
        }
    }

    // ------------------------------------------------------------ dimensions

    @Test
    fun `matches exact display size`() {
        assertThat(
            ScreenshotHeuristics.matchesDisplaySize(1080, 2400, 1080, 2400),
        ).isTrue()
    }

    @Test
    fun `matches when status or nav bar is excluded`() {
        // Capture 120px shorter than the display, inside the default tolerance.
        assertThat(
            ScreenshotHeuristics.matchesDisplaySize(1080, 2280, 1080, 2400),
        ).isTrue()
    }

    @Test
    fun `matches a landscape capture on a portrait display`() {
        assertThat(
            ScreenshotHeuristics.matchesDisplaySize(2400, 1080, 1080, 2400),
        ).isTrue()
    }

    @Test
    fun `rejects a camera photo of a different aspect`() {
        assertThat(
            ScreenshotHeuristics.matchesDisplaySize(4000, 3000, 1080, 2400),
        ).isFalse()
    }

    @Test
    fun `rejects zero or negative dimensions`() {
        assertThat(ScreenshotHeuristics.matchesDisplaySize(0, 2400, 1080, 2400)).isFalse()
        assertThat(ScreenshotHeuristics.matchesDisplaySize(1080, 2400, 0, 0)).isFalse()
    }

    // -------------------------------------------------------------- combined

    @Test
    fun `path evidence alone is enough`() {
        assertThat(
            ScreenshotHeuristics.isLikelyScreenshot(
                relativePath = "Pictures/Screenshots/",
                displayName = "weird-name.png",
                imageWidth = 500,
                imageHeight = 500,
                displayWidth = 1080,
                displayHeight = 2400,
            ),
        ).isTrue()
    }

    @Test
    fun `dimension evidence rescues an oddly stored screenshot`() {
        assertThat(
            ScreenshotHeuristics.isLikelyScreenshot(
                relativePath = "Pictures/MIUI/Gallery/cloud/",
                displayName = "1753968000.png",
                imageWidth = 1080,
                imageHeight = 2400,
                displayWidth = 1080,
                displayHeight = 2400,
            ),
        ).isTrue()
    }

    @Test
    fun `an ordinary camera photo is excluded on all three signals`() {
        assertThat(
            ScreenshotHeuristics.isLikelyScreenshot(
                relativePath = "DCIM/Camera/",
                displayName = "IMG_20260731.jpg",
                imageWidth = 4000,
                imageHeight = 3000,
                displayWidth = 1080,
                displayHeight = 2400,
            ),
        ).isFalse()
    }
}
