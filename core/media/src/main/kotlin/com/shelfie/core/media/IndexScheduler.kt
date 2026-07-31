package com.shelfie.core.media

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shelfie.core.media.work.BacklogIndexWorker
import com.shelfie.core.media.work.ReconcileWorker
import com.shelfie.core.media.work.RecentIndexWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues the background tiers.
 *
 * Tier 1 is not here — it runs in the foreground from the shelf, because its
 * whole purpose is to put content on screen before the user loses patience.
 */
@Singleton
class IndexScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    /**
     * Tier 2 — the recent past.
     *
     * Deliberately *not* expedited. Expedited work falls back to a foreground
     * service on API 30 and below, which would mean declaring and justifying a
     * foreground service type to Play for no real user benefit: Tier 1 already
     * delivers immediacy in the foreground, so Tier 2 only needs to be prompt,
     * not instant.
     */
    fun scheduleRecent() {
        val request = OneTimeWorkRequestBuilder<RecentIndexWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

        workManager.enqueueUniqueWork(WORK_RECENT, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Tier 3. The constraints here are the whole point: the backlog only runs
     * when the device is idle, charging, and not low on battery. The user should
     * never feel this work happening.
     */
    fun scheduleBacklog() {
        val request = OneTimeWorkRequestBuilder<BacklogIndexWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresDeviceIdle(true)
                    .setRequiresCharging(true)
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .build()

        workManager.enqueueUniqueWork(WORK_BACKLOG, ExistingWorkPolicy.KEEP, request)
    }

    /**
     * Periodic reconcile. The final safety net for ContentObserver events that
     * never arrived — for example because the process was dead when the user
     * took a screenshot.
     */
    fun schedulePeriodicReconcile() {
        val request = PeriodicWorkRequestBuilder<ReconcileWorker>(
            IndexTierPolicy.RECONCILE_INTERVAL_HOURS,
            TimeUnit.HOURS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_RECONCILE,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Called once media access is granted, and on every cold start. */
    fun scheduleAll() {
        scheduleRecent()
        scheduleBacklog()
        schedulePeriodicReconcile()
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(WORK_RECENT)
        workManager.cancelUniqueWork(WORK_BACKLOG)
        workManager.cancelUniqueWork(WORK_RECONCILE)
    }

    private companion object {
        const val WORK_RECENT = "shelfie_index_recent"
        const val WORK_BACKLOG = "shelfie_index_backlog"
        const val WORK_RECONCILE = "shelfie_reconcile"
    }
}
