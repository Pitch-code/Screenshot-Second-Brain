package com.shelfie.app.debug

import android.os.Build
import android.os.StrictMode

/**
 * StrictMode, debug builds only.
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
