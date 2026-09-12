package com.shelfie.app

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.shelfie.app.debug.StrictModeInitializer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Does no eager work beyond supplying WorkManager's configuration and, in debuggable
 * builds only, installing StrictMode. The cold-start budget is under 500ms on a 4GB
 * device and the fastest way to blow it is to initialise things here, so everything
 * else stays lazy: Hilt builds the graph on first injection, Room opens the database
 * on first query, and indexing is kicked off from the shelf rather than from startup.
 */
@HiltAndroidApp
class ShelfieApplication : Application(), Configuration.Provider {

    /**
     * Injected lazily by Hilt. Required because the index workers use
     * `@HiltWorker` constructor injection, which needs a custom factory.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * The one exception to doing no eager work: StrictMode, and only when the build is
     * debuggable.
     *
     * It has to be installed before anything else runs to be worth having, and it
     * costs a flag test plus two policy builders — microseconds, and none of them on
     * the path a released build takes. Release and `benchmark` builds are not
     * debuggable, so neither pays for this at all and benchmark timings are unaffected.
     */
    override fun onCreate() {
        super.onCreate()

        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            StrictModeInitializer.install()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()
}
