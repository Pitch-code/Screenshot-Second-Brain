package com.shelfie.core.media

import com.shelfie.core.model.MediaAccess
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
     * Runs the immediate tier, then schedules Tiers 2 and 3.
     *
     * Safe to call on every shelf entry — it no-ops after the first successful
     * run in this process, and the background tiers use unique work so
     * re-scheduling is idempotent.
     */
    fun warmUp(scope: CoroutineScope): Job = scope.launch {
        if (repository.currentAccess() == MediaAccess.DENIED) return@launch

        mutex.withLock {
            if (!hasRunThisProcess) {
                hasRunThisProcess = true
                runImmediateTier()
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
                        runCatching {
                            repository.recordError(
                                entity.id,
                                "index() threw ${error.javaClass.simpleName}: ${error.message}",
                            )
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
