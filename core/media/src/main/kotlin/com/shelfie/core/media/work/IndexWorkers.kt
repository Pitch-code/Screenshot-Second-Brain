package com.shelfie.core.media.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shelfie.core.media.IndexOutcome
import com.shelfie.core.media.IndexTierPolicy
import com.shelfie.core.media.IndexingQuota
import com.shelfie.core.media.ScreenshotIndexer
import com.shelfie.core.media.ScreenshotRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Tier 2 — indexes the recent past shortly after launch.
 *
 * Expedited so it starts promptly, but still cancellable and still bounded.
 */
@HiltWorker
class RecentIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ScreenshotRepository,
    private val indexer: ScreenshotIndexer,
    private val quota: IndexingQuota,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        repository.discoverAll()
        return when (
            indexBatch(
                repository = repository,
                indexer = indexer,
                quota = quota,
                batchSize = IndexTierPolicy.RECENT_BATCH,
                reportProgress = { done, total -> setProgress(indexProgressData(done, total)) },
            )
        ) {
            BatchOutcome.Completed -> Result.success()
            BatchOutcome.Retry -> Result.retry()
        }
    }
}

/**
 * Tier 3 — the backlog.
 *
 * Constrained to idle **and** charging. Those two constraints are the difference
 * between "this app organised my screenshots" and "this app melted my phone",
 * and they are the reason we can index thousands of images without the user ever
 * noticing.
 *
 * Chunked and checkpointed: each run handles [IndexTierPolicy.BACKLOG_CHUNK]
 * items, persists them, then reschedules itself if work remains. Progress lives
 * in the database, never in memory, so process death costs at most one chunk.
 */
@HiltWorker
class BacklogIndexWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ScreenshotRepository,
    private val indexer: ScreenshotIndexer,
    private val quota: IndexingQuota,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        repository.discoverAll()

        val outcome = indexBatch(
            repository = repository,
            indexer = indexer,
            quota = quota,
            batchSize = IndexTierPolicy.BACKLOG_CHUNK,
            reportProgress = { done, total -> setProgress(indexProgressData(done, total)) },
        )

        // If items remain, ask WorkManager to run us again rather than looping
        // here — that way the idle/charging constraints are re-evaluated and we
        // never hold the device awake.
        val remaining = repository.nextPending(1).isNotEmpty()
        return when {
            outcome == BatchOutcome.Retry -> Result.retry()
            remaining -> Result.retry()
            else -> Result.success()
        }
    }
}

/** Periodic safety net: reconciles MediaStore against the index. */
@HiltWorker
class ReconcileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ScreenshotRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return runCatching { repository.reconcile() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
    }
}

/** Result of a batch, kept separate from androidx.work's restricted Result API. */
private enum class BatchOutcome { Completed, Retry }

/**
 * Shared batch loop.
 *
 * Returns a plain enum rather than a `ListenableWorker.Result`: those factory
 * methods are library-group restricted and must only be constructed inside a
 * Worker. Each worker maps the outcome itself.
 *
 * Stops immediately on [IndexOutcome.AccessLost] — once permission is gone there
 * is no point burning a retry attempt on every remaining row.
 */
private suspend fun indexBatch(
    repository: ScreenshotRepository,
    indexer: ScreenshotIndexer,
    quota: IndexingQuota,
    batchSize: Int,
    reportProgress: suspend (done: Int, total: Int) -> Unit,
): BatchOutcome {
    val pending = repository.nextPending(batchSize)
    if (pending.isEmpty()) return BatchOutcome.Completed

    // Read user rules once for the whole batch.
    val rules = runCatching { repository.currentRules() }.getOrDefault(emptyList())

    var processed = 0
    for (entity in pending) {
        val outcome = runCatching { indexer.index(entity, rules) }
            .getOrElse { return BatchOutcome.Retry }

        if (outcome == IndexOutcome.AccessLost) {
            // Not a failure: the user revoked access, which is their right.
            return BatchOutcome.Completed
        }

        processed++
        reportProgress(processed, pending.size)
    }

    // Keep the free tier to its newest-N window.
    runCatching { quota.enforce() }

    return BatchOutcome.Completed
}

internal fun indexProgressData(done: Int, total: Int) = androidx.work.workDataOf(
    KEY_PROGRESS_DONE to done,
    KEY_PROGRESS_TOTAL to total,
)

const val KEY_PROGRESS_DONE = "progress_done"
const val KEY_PROGRESS_TOTAL = "progress_total"
