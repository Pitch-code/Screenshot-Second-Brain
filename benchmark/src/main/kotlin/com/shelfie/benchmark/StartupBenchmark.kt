package com.shelfie.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start measurement.
 *
 * The budget is **under 500ms at P90 on a 4GB device**. This is the number that
 * decides whether the app feels instant or sluggish, and it is the one metric
 * that cannot be inferred from reading code.
 *
 * Run with:
 * ```
 * ./gradlew :benchmark:connectedBenchmarkAndroidTest
 * ```
 *
 * Requires a physical device or emulator. Prefer a low-end physical device —
 * benchmarking on a fast emulator will report numbers the target audience will
 * never see.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Baseline: no Baseline Profile applied. */
    @Test
    fun startupNoCompilation() = measureStartup(
        CompilationMode.None(),
    )

    /** With the Baseline Profile, which is what ships. */
    @Test
    fun startupWithBaselineProfile() = measureStartup(
        CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require),
    )

    private fun measureStartup(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        iterations = 10,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        },
    ) {
        startActivityAndWait()
    }
}

internal const val TARGET_PACKAGE = "com.pitchcode.shelfie"
