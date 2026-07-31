package com.shelfie.core.media

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Watches MediaStore for new screenshots.
 *
 * Important: this observer is treated as a **hint only**, never a source of
 * truth. It is not delivered while the process is dead, OEM skins drop events,
 * and it can fire several times for a single file write. So every callback just
 * triggers the same watermark-based reconcile that runs at startup, which makes
 * missed events self-healing and duplicate events harmless.
 */
@Singleton
class ScreenshotContentObserver @Inject constructor(
    private val contentResolver: ContentResolver,
    private val repository: ScreenshotRepository,
    private val indexer: ScreenshotIndexer,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var observer: ContentObserver? = null

    fun start() {
        if (observer != null) return

        val handler = Handler(Looper.getMainLooper())
        val newObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                onMediaStoreChanged()
            }

            override fun onChange(selfChange: Boolean) {
                onMediaStoreChanged()
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            /* notifyForDescendants = */ true,
            newObserver,
        )
        observer = newObserver
    }

    fun stop() {
        observer?.let(contentResolver::unregisterContentObserver)
        observer = null
    }

    private fun onMediaStoreChanged() {
        scope.launch {
            // Serialised so a burst of callbacks for one file does not start
            // several overlapping scans.
            mutex.withLock {
                val discovered = runCatching { repository.discoverNew() }.getOrDefault(0)
                if (discovered <= 0) return@withLock

                // Index the handful that just arrived straight away — this is
                // what makes a screenshot searchable within seconds of capture.
                val pending = runCatching {
                    repository.nextPending(MAX_IMMEDIATE_ON_CHANGE)
                }.getOrDefault(emptyList())

                for (entity in pending) {
                    val outcome = runCatching { indexer.index(entity) }.getOrNull()
                    if (outcome == IndexOutcome.AccessLost) break
                }
            }
        }
    }

    private companion object {
        /**
         * A change notification normally means one new screenshot. Cap the
         * inline work so an unexpected bulk import (a restore, a file manager
         * copy) falls through to the background tiers instead of running
         * unbounded work here.
         */
        const val MAX_IMMEDIATE_ON_CHANGE = 5
    }
}
