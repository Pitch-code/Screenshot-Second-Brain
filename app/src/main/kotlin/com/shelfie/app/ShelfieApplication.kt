package com.shelfie.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Does no eager work beyond supplying WorkManager's configuration. The
 * cold-start budget is under 500ms on a 4GB device and the fastest way to blow
 * it is to initialise things here, so everything else stays lazy: Hilt builds
 * the graph on first injection, Room opens the database on first query, and
 * indexing is kicked off from the shelf rather than from startup.
 */
@HiltAndroidApp
class ShelfieApplication : Application(), Configuration.Provider {

    /**
     * Injected lazily by Hilt. Required because the index workers use
     * `@HiltWorker` constructor injection, which needs a custom factory.
     */
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()
}
