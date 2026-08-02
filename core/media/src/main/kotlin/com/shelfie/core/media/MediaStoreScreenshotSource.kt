package com.shelfie.core.media

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import com.shelfie.core.model.MediaAccess
import com.shelfie.core.model.MediaFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads screenshots out of MediaStore.
 *
 * All queries run on [Dispatchers.IO] — a MediaStore query over thousands of
 * rows on the main thread is a guaranteed ANR. Every cursor is closed via
 * `use {}` without exception.
 */
@Singleton
class MediaStoreScreenshotSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentResolver: ContentResolver,
    private val accessChecker: MediaAccessChecker,
) {

    private val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private val projection = buildList {
        add(MediaStore.Images.Media._ID)
        add(MediaStore.Images.Media.DISPLAY_NAME)
        add(MediaStore.Images.Media.DATE_ADDED)
        add(MediaStore.Images.Media.SIZE)
        add(MediaStore.Images.Media.WIDTH)
        add(MediaStore.Images.Media.HEIGHT)
        // Folder name, used for the opt-in folder picker. Chosen over RELATIVE_PATH
        // as the key because it exists on every supported API level and reads the
        // same way to a person as it does in a stored preference.
        add(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Images.Media.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            add(MediaStore.Images.Media.DATA)
        }
    }.toTypedArray()

    /**
     * Screenshots added strictly after [sinceDateAddedSeconds], newest first.
     *
     * The watermark is the reconciliation mechanism: rather than trusting that
     * every ContentObserver callback arrived, the app asks "what is newer than
     * what I already know about". Missed events therefore self-heal.
     *
     * [limit] of 0 means unbounded.
     */
    suspend fun queryScreenshotsSince(
        sinceDateAddedSeconds: Long,
        limit: Int = 0,
        includeFolders: Set<String> = emptySet(),
    ): List<MediaStoreScreenshot> = withContext(Dispatchers.IO) {
        if (accessChecker.current() == MediaAccess.DENIED) return@withContext emptyList()

        // >= rather than >: DATE_ADDED has one-second resolution, so a screenshot
        // captured in the same second as the newest one already known would be
        // invisible to every future watermark scan. Re-finding the boundary row is
        // harmless — upsert of an already-known media id is a no-op.
        val selection = "${MediaStore.Images.Media.DATE_ADDED} >= ?"
        val selectionArgs = arrayOf(sinceDateAddedSeconds.toString())
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        runCatching {
            contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
                ?.use { cursor -> cursor.readScreenshots(limit, includeFolders) }
                ?: emptyList()
        }.getOrElse { error ->
            // Permission revoked mid-query, or an OEM provider misbehaving.
            if (error is SecurityException) emptyList() else throw error
        }
    }

    /** Newest [limit] screenshots regardless of watermark. Drives Tier 1. */
    suspend fun queryNewest(
        limit: Int,
        includeFolders: Set<String> = emptySet(),
    ): List<MediaStoreScreenshot> = queryScreenshotsSince(
        sinceDateAddedSeconds = 0,
        limit = limit,
        includeFolders = includeFolders,
    )

    /**
     * Every image folder on the device, with how many images each holds.
     *
     * Backs the folder picker. Counts every image rather than only likely
     * screenshots, because the number that matters when deciding whether to tick
     * "Camera" is how much work it implies — and that is the total.
     *
     * Projection is deliberately just the bucket name: this walks the whole image
     * table, so pulling any other column would be wasted allocation per row.
     */
    suspend fun queryFolders(): List<MediaFolder> = withContext(Dispatchers.IO) {
        if (accessChecker.current() == MediaAccess.DENIED) return@withContext emptyList()

        runCatching {
            contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val bucketColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

                val counts = mutableMapOf<String, Int>()
                while (cursor.moveToNext()) {
                    val name = cursor.getStringOrEmpty(bucketColumn).ifBlank { continue }
                    counts[name] = (counts[name] ?: 0) + 1
                }

                counts.map { (name, count) -> MediaFolder(name = name, imageCount = count) }
                    // Largest first: the folders worth a decision are the big ones.
                    .sortedByDescending { it.imageCount }
            } ?: emptyList()
        }.getOrElse { error ->
            if (error is SecurityException) emptyList() else throw error
        }
    }

    /**
     * Every image id currently present, used to prune rows whose file was
     * deleted outside the app.
     */
    suspend fun queryAllImageIds(): Set<Long> = withContext(Dispatchers.IO) {
        if (accessChecker.current() == MediaAccess.DENIED) return@withContext emptySet()

        runCatching {
            contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID),
                null,
                null,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                buildSet {
                    while (cursor.moveToNext()) add(cursor.getLong(idColumn))
                }
            } ?: emptySet()
        }.getOrElse { error ->
            if (error is SecurityException) emptySet() else throw error
        }
    }

    private fun Cursor.readScreenshots(
        limit: Int,
        includeFolders: Set<String>,
    ): List<MediaStoreScreenshot> {
        val bucketColumn = getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
        val idColumn = getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameColumn = getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val dateColumn = getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
        val sizeColumn = getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
        val widthColumn = getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val heightColumn = getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        }

        val (displayWidth, displayHeight) = displaySize()
        val results = mutableListOf<MediaStoreScreenshot>()

        while (moveToNext()) {
            if (limit > 0 && results.size >= limit) break

            val name = getStringOrEmpty(nameColumn)
            val rawPath = getStringOrEmpty(pathColumn)
            val width = getInt(widthColumn)
            val height = getInt(heightColumn)

            // Opt-in folders are purely additive to the heuristics, never a
            // replacement. That keeps default behaviour byte-identical when nothing
            // is chosen, and means ticking a folder can only ever add screenshots,
            // never silently stop finding ones that were already being found.
            val folder = getStringOrEmpty(bucketColumn)
            val isChosenFolder = includeFolders.isNotEmpty() &&
                MediaFolder.normaliseKey(folder) in includeFolders

            val include = isChosenFolder || ScreenshotHeuristics.isLikelyScreenshot(
                relativePath = rawPath,
                displayName = name,
                imageWidth = width,
                imageHeight = height,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
            )
            if (!include) continue

            val id = getLong(idColumn)
            results += MediaStoreScreenshot(
                mediaStoreId = id,
                uri = ContentUris.withAppendedId(collection, id).toString(),
                displayName = name,
                relativePath = rawPath,
                dateAdded = getLong(dateColumn),
                sizeBytes = getLong(sizeColumn),
                width = width,
                height = height,
            )
        }
        return results
    }

    private fun Cursor.getStringOrEmpty(column: Int): String =
        if (isNull(column)) "" else getString(column) ?: ""

    /** Real display size in pixels, used by the dimension heuristic. */
    private fun displaySize(): Pair<Int, Int> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = context.resources.displayMetrics
            metrics.widthPixels to metrics.heightPixels
        } else {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }.getOrDefault(0 to 0)
}
