package com.shelfie.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the Baseline Profile that ships with the app.
 *
 * A Baseline Profile tells ART which code paths to compile ahead of time, and
 * typically buys 20–30% faster cold start for no code changes — the cheapest
 * performance win available, and it matters most on exactly the low-end hardware
 * this app targets.
 *
 * Generate with a device connected:
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile
 * ```
 *
 * The journey below should cover what a real first session does, because only
 * the traversed paths get optimised.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        // Startup and the shelf: the critical path for time-to-first-value.
        device.wait(Until.hasObject(By.scrollable(true)), 5_000)
        device.findObject(By.scrollable(true))?.let { grid ->
            grid.setGestureMargin(device.displayWidth / 5)
            repeat(2) {
                grid.fling(Direction.DOWN)
                device.waitForIdle()
            }
            grid.fling(Direction.UP)
            device.waitForIdle()
        }

        /*
         * Visit the other destinations so their composition is also profiled.
         *
         * These must match the navigation bar labels in `ShelfieDestination`, which
         * are now string resources — `destination_find`, `destination_cleanup` and
         * `destination_settings` in `:app`.
         *
         * A missing tab fails the run rather than being skipped. This list said
         * "Search" long after the tab was renamed to "Find", and because the lookup
         * was a null-safe `?.click()` the step silently did nothing: the Find tab —
         * search, folders, categories, the whole second half of the app — was left
         * out of the profile for however long that went unnoticed. A generator that
         * quietly profiles less than it claims is worse than one that stops.
         */
        listOf("Find", "Cleanup", "Settings").forEach { label ->
            val tab = device.findObject(By.text(label))
                ?: error(
                    "Navigation tab \"$label\" was not found. If a destination was " +
                        "renamed, update this list — otherwise its code never makes " +
                        "it into the Baseline Profile.",
                )
            tab.click()
            device.waitForIdle()
        }
    }
}
