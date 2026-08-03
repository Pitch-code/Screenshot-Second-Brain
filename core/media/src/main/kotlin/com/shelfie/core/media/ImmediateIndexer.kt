package com.shelfie.core.media

import com.shelfie.core.model.MediaAccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1 — the fast path that decides whether users keep the app.
 *
 * Discovers the newest [IndexTierPolicy.IMMEDIATE_BATCH] screenshots and indexes
 * them right away, emitting each to the database as it completes so the shelf
 * fills in progressively rather than appearing all at once at the end.
 *
 * Then it hands the rest to the background tiers and gets out of the way.
 */
@Singleton
class ImmediateIndexer @Inject constructor(
    private val repository: ScreenshotRepository,
    private val indexer: ScreenshotIndexer,
    private val scheduler: IndexScheduler,
    private val quota: IndexingQuota,
) {

    /** Guards against two concurrent warm-ups racing on the same rows. */
    private val mutex = Mutex()

    @Volatile
    private var hasRunThisProcess = false

    /**
     * Runs the immediate tier on first entry, and a cheap catch-up pass on every
     * entry after that.
     *
     * The catch-up matters more than it looks. This used to do nothing at all
     * after its first run in the process, and the MediaStore observer is only
     * registered while the activity is visible. So a screenshot taken with
     * Shelfie backgrounded — but its process still alive — was picked up by
     * nothing: the observer was unregistered, the immediate tier was fused off,
     * and Tier 2 had already completed. Returning to the shelf appeared to do
     * nothing because it genuinely did nothing.
     */
    fun warmUp(scope: CoroutineScope): Job = scope.launch {
        if (repository.currentAccess() == MediaAccess.DENIED) return@launch

        mutex.withLock {
            if (!hasRunThisProcess) {
                hasRunThisProcess = true
                runImmediateTier()
            } else {
                runCatchUp()
            }
        }

        // Hand off the long tail. Unique work, so calling this repeatedly is free.
        scheduler.scheduleAll()
    }

    /** Explicit user-triggered retry, ignoring the once-per-process guard. */
    fun retry(scope: CoroutineScope): Job = scope.launch {
        if (repository.currentAccess() == MediaAccess.DENIED) return@launch
        mutex.withLock { runImmediateTier() }
        scheduler.scheduleAll()
    }

    /**
     * Cheap pass for returning to the shelf: pick up anything new and read a
     * small batch of it.
     *
     * Bounded by [IndexTierPolicy.CATCH_UP_BATCH] rather than the full immediate
     * batch, because this runs every time the screen resumes and must never feel
     * like work. Anything it does not reach is left to the background tiers.
     */
    /**
     * User-pressed refresh.
     *
     * Bypasses [hasRunThisProcess] and the watermark, and reports back what
     * happened. Exists because there was previously no way at all to force a
     * rescan: the retry card is gated behind "nothing has ever indexed", so a
     * working library with one missing screenshot had no affordance whatsoever.
     */
    suspend fun refreshNow(): RescanResult {
        val result = repository.forceRescan()

        if (result is RescanResult.Completed) {
            mutex.withLock { drainPending(IndexTierPolicy.CATCH_UP_BATCH) }
        }
        scheduler.scheduleAll()
        return result
    }

    private suspend fun runCatchUp() {
        withContext(NonCancellable + Dispatchers.Default) {
            // A previous run may have been killed mid-index.
            runCatching { repository.requeueStaleWork() }

            val found = runCatching { repository.discoverNew() }
                .onFailure { error ->
                    // Previously swallowed outright, which made a broken watermark
                    // indistinguishable from "nothing new" — no log, no state, no
                    // way to tell. Surfaced on the diagnostics card instead.
                    // Cancellation excluded: it means the user left, not that
                    // anything went wrong.
                    if (error !is CancellationException) {
                        runCatching {
                            repository.recordGlobalError("Discovery failed: ${error.message}")
                        }
                    }
                }
                .getOrDefault(0)

            // Watermark scans can only ever look forward, so anything they miss
            // they miss forever. A full scan costs one cursor walk with no image
            // work, which is worth paying to avoid a permanently stuck library.
            if (found == 0) {
                runCatching { repository.discoverAll() }
            }

            drainPending(IndexTierPolicy.CATCH_UP_BATCH)
        }
    }

    private suspend fun drainPending(limit: Int) {
        val rules = runCatching { repository.currentRules() }.getOrDefault(emptyList())

        val pending = runCatching { repository.nextPending(limit) }.getOrDefault(emptyList())

        for (entity in pending) {
            // Skip anything already outside the free window rather than recognising
            // it and discarding the result a moment later.
            if (runCatching { quota.shouldSkipUnrecognised(entity.dateAdded) }.getOrDefault(false)) {
                runCatching { quota.holdWithoutIndexing(entity.id) }
                continue
            }

            val outcome = runCatching { indexer.index(entity, rules) }.getOrNull()
            if (outcome == IndexOutcome.AccessLost) break
        }

        runCatching { quota.enforce() }
    }

    private suspend fun runImmediateTier() {
        // NonCancellable so a fast navigation away doesn't leave rows stuck in
        // IN_PROGRESS with no worker owning them.
        // Dispatchers.Default because NonCancellable is a Job, not a dispatcher:
        // without this the loop would inherit the caller's Main dispatcher.
        withContext(NonCancellable + Dispatchers.Default) {
            // Recover anything a previous run abandoned mid-index.
            runCatching { repository.requeueStaleWork() }

            // Newest first so the shelf fills immediately...
            repository.discoverNewest(IndexTierPolicy.IMMEDIATE_BATCH)

            // Rules are read once per batch rather than per item.
            val rules = runCatching { repository.currentRules() }.getOrDefault(emptyList())

            val pending = repository.nextPending(IndexTierPolicy.IMMEDIATE_BATCH)
            for (entity in pending) {
                val outcome = runCatching { indexer.index(entity, rules) }
                    .onFailure { error ->
                        // Previously swallowed. An exception thrown *after*
                        // recognition — while saving, for instance — left no
                        // trace at all, which made the failure undiagnosable.
                        // Cancellation is excluded: it means the user left, not
                        // that anything went wrong.
                        if (error !is CancellationException) {
                            runCatching {
                                repository.recordError(
                                    entity.id,
                                    "index() threw ${error.javaClass.simpleName}: ${error.message}",
                                )
                            }
                        }
                    }
                    .getOrNull()
                if (outcome == IndexOutcome.AccessLost) break
            }

            // ...then discover the rest of the library so the background tiers
            // have something to work through.
            runCatching { repository.discoverAll() }

            // Roll anything beyond the free window out of the search index.
            runCatching { quota.enforce() }
        }
    }
}
