package com.shelfie.core.media.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shelfie.core.media.IndexOutcome
import com.shelfie.core.media.IndexTierPolicy
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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        repository.discoverNew()
        return indexBatch(
            repository = repository,
            indexer = indexer,
            batchSize = IndexTierPolicy.RECENT_BATCH,
            reportProgress = { done, total -> setProgress(indexProgressData(done, total)) },
        )
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
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        repository.discoverNew()

        val result = indexBatch(
            repository = repository,
            indexer = indexer,
            batchSize = IndexTierPolicy.BACKLOG_CHUNK,
            reportProgress = { done, total -> setProgress(indexProgressData(done, total)) },
        )

        // If items remain, ask WorkManager to run us again rather than looping
        // here — that way the idle/charging constraints are re-evaluated and we
        // never hold the device awake.
        val remaining = repository.nextPending(1).isNotEmpty()
        return when {
            result is Result.Failure -> result
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

/**
 * Shared batch loop.
 *
 * Stops immediately on [IndexOutcome.AccessLost] — once permission is gone there
 * is no point burning a retry attempt on every remaining row.
 */
private suspend fun indexBatch(
    repository: ScreenshotRepository,
    indexer: ScreenshotIndexer,
    batchSize: Int,
    reportProgress: suspend (done: Int, total: Int) -> Unit,
): androidx.work.ListenableWorker.Result {
    val pending = repository.nextPending(batchSize)
    if (pending.isEmpty()) return androidx.work.ListenableWorker.Result.success()

    var processed = 0
    for (entity in pending) {
        val outcome = runCatching { indexer.index(entity) }
            .getOrElse { return androidx.work.ListenableWorker.Result.retry() }

        if (outcome == IndexOutcome.AccessLost) {
            // Not a failure: the user revoked access, which is their right.
            return androidx.work.ListenableWorker.Result.success()
        }

        processed++
        reportProgress(processed, pending.size)
    }
    return androidx.work.ListenableWorker.Result.success()
}

internal fun indexProgressData(done: Int, total: Int) = androidx.work.workDataOf(
    KEY_PROGRESS_DONE to done,
    KEY_PROGRESS_TOTAL to total,
)

const val KEY_PROGRESS_DONE = "progress_done"
const val KEY_PROGRESS_TOTAL = "progress_total"
