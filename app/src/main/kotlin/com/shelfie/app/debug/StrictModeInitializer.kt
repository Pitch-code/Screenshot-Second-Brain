package com.shelfie.app.debug

import android.os.Build
import android.os.StrictMode

/**
 * StrictMode, installed only in debuggable builds.
 *
 * ## Why this lives in `main` rather than in a `debug` source set
 *
 * It used to sit in `src/debug`, where nothing in `src/main` could reference it — so
 * [install] had no callers and StrictMode was never actually switched on, despite the
 * README and the architecture doc both claiming it was. A source set that only the
 * debug variant compiles cannot be called from shared code, and the obvious fix of
 * adding a no-op twin under `src/release` breaks the `benchmark` build type, which
 * gets its own source set and would have neither.
 *
 * So it is compiled into every variant and gated at runtime by the debuggable flag
 * instead. `BuildConfig.DEBUG` would be the usual gate, but this project does not
 * enable the `buildConfig` build feature, and turning it on to read one boolean is a
 * worse trade than a single flag test at startup.
 *
 * Catches the two failure modes this app is most exposed to:
 *
 *  - **Disk reads on the main thread.** Every MediaStore query and every Room
 *    read must be off the main thread; one that slips through is an ANR waiting
 *    for a user with 5,000 screenshots.
 *  - **Leaked closeables.** The indexing pipeline opens a lot of input streams
 *    and cursors, and a leak only shows up as a mysterious crash much later.
 *
 * Also asserts that no network is attempted — which should be structurally
 * impossible without the INTERNET permission, but is worth a runtime tripwire so
 * a future dependency cannot quietly change that.
 *
 * Logs rather than crashes: a penalty of death here would make debugging harder
 * than the bugs it catches, since some violations come from framework code.
 */
object StrictModeInitializer {

    fun install() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        detectUnsafeIntentLaunch()
                    }
                }
                .penaltyLog()
                .build(),
        )
    }
}
