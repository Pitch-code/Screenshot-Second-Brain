package com.shelfie.core.media

import com.shelfie.core.model.MediaAccess

/**
 * The two pure decisions behind MediaStore reconciliation.
 *
 * Both were previously inlined into [ScreenshotRepository], where they could only be
 * reached through a `ContentResolver` and a database. The tests responded by declaring
 * private copies of the arithmetic and asserting against those — which meant the most
 * consequential logic in the app, the part that decides whether a user's index gets
 * deleted, was covered by tests that would keep passing no matter what the real
 * implementation did.
 *
 * Extracted here so there is exactly one copy, and the tests call it.
 */
internal object ReconcileDecisions {

    /**
     * The lower bound for the next discovery scan.
     *
     * `date_added` is copied verbatim from the media provider, and some OEM providers —
     * along with restored or cloud-synced media — report it in milliseconds or with a
     * timestamp in the future. A single such row makes `MAX(date_added)` astronomically
     * large, and every later `DATE_ADDED >= watermark` scan then matches nothing.
     * Permanently, and silently: the first launch works and no screenshot is ever found
     * again.
     *
     * Clamping into `0..nowSeconds` costs at worst one redundant re-scan of the
     * boundary second, which `upsert` makes a no-op anyway.
     *
     * @param newestDateAdded newest `date_added` this app knows about, or null when the
     *   index is empty.
     */
    fun clampWatermark(newestDateAdded: Long?, nowSeconds: Long): Long =
        (newestDateAdded ?: 0L).coerceIn(0L, nowSeconds)

    /**
     * Which of the app's rows refer to files that no longer exist.
     *
     * Compares the app's own ids against what MediaStore still reports, rather than the
     * other way round: this app knows about far fewer images than the device holds, so
     * this direction stays bounded instead of binding tens of thousands of parameters.
     *
     * Two of the three rules exist purely to stop the app destroying a working index,
     * and neither is obvious from the happy path:
     *
     *  - **Full access only.** Under Android 14's partial grant MediaStore reports only
     *    the handful of images the user hand-picked, so every other row would look
     *    deleted. A permission downgrade must never lose data.
     *  - **Never act on an empty result.** A `SecurityException` mid-query comes back as
     *    an empty set, which is indistinguishable from "the gallery is empty". Treating
     *    that as mass deletion would clear the whole library on a transient failure.
     *
     * @param liveIds every image id MediaStore currently reports.
     * @param known every MediaStore id this app has a row for.
     */
    fun idsToRemove(
        access: MediaAccess,
        liveIds: Set<Long>,
        known: List<Long>,
    ): List<Long> {
        if (access != MediaAccess.FULL) return emptyList()
        if (liveIds.isEmpty()) return emptyList()
        return known.filterNot { it in liveIds }
    }
}
