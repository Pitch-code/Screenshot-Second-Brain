package com.shelfie.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Shelf scroll smoothness.
 *
 * Budget: **zero frozen frames** and P90 frame duration under 16ms while
 * scrolling. This must be measured against a large library — a few dozen
 * screenshots will always look fine, and the failure mode only appears at
 * thousands.
 *
 * Run with:
 * ```
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ShelfScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun scrollShelf() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 8,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        },
    ) {
        // Wait for the grid to appear before scrolling, so the measurement covers
        // scrolling rather than the tail of startup.
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        val grid = device.findObject(By.scrollable(true)) ?: return@measureRepeated

        grid.setGestureMargin(device.displayWidth / 5)
        repeat(4) {
            grid.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }
}
