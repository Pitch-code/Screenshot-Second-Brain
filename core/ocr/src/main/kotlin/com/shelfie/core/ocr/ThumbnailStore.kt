package com.shelfie.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable local copies of picker-imported screenshots.
 *
 * The system photo picker grants read access that expires when the process dies,
 * and those grants cannot be made persistable. So in Limited Mode, an image the
 * user hand-picked would render as a blank tile on the next launch unless we keep
 * our own copy.
 *
 * Copies are downscaled and JPEG-compressed, so a few hundred of them cost single
 * -digit megabytes rather than gigabytes. They live in the app's private storage,
 * which means they are removed automatically on uninstall.
 */
@Singleton
class ThumbnailStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bitmapDecoder: BitmapDecoder,
) {

    private val directory: File
        get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /**
     * Saves a downscaled copy of [uri], returning its absolute path, or null if
     * the image could not be read.
     */
    suspend fun save(key: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            bitmap = bitmapDecoder.decodeDownsampled(uri) ?: return@withContext null

            val target = File(directory, "$key.jpg")
            target.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY, stream)
            }
            target.absolutePath
        } catch (_: Exception) {
            // A single unreadable image must never break a bulk import.
            null
        } finally {
            bitmap?.recycle()
        }
    }

    fun pathFor(key: String): String? =
        File(directory, "$key.jpg").takeIf { it.exists() }?.absolutePath

    fun delete(key: String): Boolean = File(directory, "$key.jpg").let { it.exists() && it.delete() }

    /** Total bytes used, for the Cleanup screen's storage figures. */
    fun totalBytes(): Long =
        directory.listFiles()?.sumOf { it.length() } ?: 0L

    /** Removes copies whose screenshot rows no longer exist. */
    fun pruneExcept(keys: Set<String>): Int {
        val files = directory.listFiles() ?: return 0
        var removed = 0
        for (file in files) {
            val key = file.nameWithoutExtension
            if (key !in keys && file.delete()) removed++
        }
        return removed
    }

    private companion object {
        const val DIRECTORY = "picked_thumbnails"

        /**
         * 80 is visually indistinguishable at tile size while roughly halving the
         * file compared with 95.
         */
        const val QUALITY = 80
    }
}
