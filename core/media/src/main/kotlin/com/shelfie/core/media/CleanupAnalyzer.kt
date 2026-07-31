package com.shelfie.core.media

import com.shelfie.core.classify.ImageQuality
import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.database.entity.ScreenshotEntity
import com.shelfie.core.database.entity.toDomain
import com.shelfie.core.model.Screenshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds screenshots worth deleting.
 *
 * Cleanup is the second retention hook after search: people install to find
 * things and stay because the app keeps giving them storage back. So the figures
 * shown must be honest and the selection conservative — one wrongly deleted photo
 * produces a one-star review that never goes away.
 */
@Singleton
class CleanupAnalyzer @Inject constructor(
    private val dao: ScreenshotDao,
) {

    suspend fun analyze(
        nowSeconds: Long = System.currentTimeMillis() / 1000,
    ): CleanupReport = withContext(Dispatchers.Default) {
        val duplicates = findDuplicateGroups()
        val blurry = dao.blurryScreenshots(ImageQuality.BLUR_VARIANCE_THRESHOLD.toFloat())
            .map(ScreenshotEntity::toDomain)
        val old = dao.olderThan(nowSeconds - OLD_THRESHOLD_SECONDS)
            .map(ScreenshotEntity::toDomain)

        CleanupReport(
            duplicateGroups = duplicates,
            blurry = blurry,
            oldAndUnopened = old,
        )
    }

    /**
     * Groups screenshots by perceptual hash, then merges groups whose hashes are
     * within the near-duplicate distance.
     *
     * The **oldest** member of each group is marked as the one to keep, on the
     * assumption that later copies are re-shares and re-compressions of an
     * original the user already had.
     */
    private suspend fun findDuplicateGroups(): List<DuplicateGroup> {
        val candidates = dao.duplicateCandidates()
        if (candidates.isEmpty()) return emptyList()

        val byHash = candidates.groupBy { it.perceptualHash }
            .filterKeys { it != null }
            .mapKeys { it.key!! }

        val merged = mutableListOf<MutableList<ScreenshotEntity>>()
        val consumedHashes = mutableSetOf<String>()

        for ((hash, rows) in byHash) {
            if (hash in consumedHashes) continue

            val group = rows.toMutableList()
            consumedHashes += hash

            // Fold in visually-identical hashes that differ by a few bits.
            for ((otherHash, otherRows) in byHash) {
                if (otherHash == hash || otherHash in consumedHashes) continue
                if (ImageQuality.areNearDuplicates(hash, otherHash)) {
                    group += otherRows
                    consumedHashes += otherHash
                }
            }
            if (group.size > 1) merged += group
        }

        return merged.map { group ->
            val sorted = group.sortedBy { it.dateAdded }
            DuplicateGroup(
                keep = sorted.first().toDomain(),
                removable = sorted.drop(1).map(ScreenshotEntity::toDomain),
            )
        }
    }

    private companion object {
        /** Six months, matching the wording in the product spec. */
        const val OLD_THRESHOLD_SECONDS = 182L * 24 * 60 * 60
    }
}

/**
 * One set of visually identical screenshots.
 *
 * [keep] is never offered for deletion — the user always ends up with a copy.
 */
data class DuplicateGroup(
    val keep: Screenshot,
    val removable: List<Screenshot>,
) {
    val reclaimableBytes: Long get() = removable.sumOf { it.sizeBytes }
}

data class CleanupReport(
    val duplicateGroups: List<DuplicateGroup> = emptyList(),
    val blurry: List<Screenshot> = emptyList(),
    val oldAndUnopened: List<Screenshot> = emptyList(),
) {
    val duplicateCount: Int get() = duplicateGroups.sumOf { it.removable.size }
    val duplicateBytes: Long get() = duplicateGroups.sumOf { it.reclaimableBytes }

    val blurryBytes: Long get() = blurry.sumOf { it.sizeBytes }
    val oldBytes: Long get() = oldAndUnopened.sumOf { it.sizeBytes }

    val totalReclaimableBytes: Long get() = duplicateBytes + blurryBytes + oldBytes
    val isEmpty: Boolean
        get() = duplicateGroups.isEmpty() && blurry.isEmpty() && oldAndUnopened.isEmpty()
}
