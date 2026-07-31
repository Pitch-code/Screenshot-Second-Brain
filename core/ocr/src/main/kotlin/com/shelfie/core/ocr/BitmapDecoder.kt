package com.shelfie.core.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Memory-safe image decoding.
 *
 * This class is the difference between an app that works on a 3GB phone and one
 * that crashes on it. A 1440p screenshot decoded at ARGB_8888 is roughly 11MB;
 * a handful in flight will OOM a budget device. So:
 *
 *  1. Read bounds only first — never decode full size to find out how big it is.
 *  2. Downsample during decode via inSampleSize.
 *  3. Use RGB_565, halving bytes per pixel. OCR does not need alpha.
 *  4. Recycle immediately after use (the caller's responsibility).
 */
@Singleton
class BitmapDecoder @Inject constructor(
    private val contentResolver: ContentResolver,
) {

    /**
     * Decodes [uri] downsampled so its longest edge is near [TARGET_LONGEST_EDGE].
     *
     * Returns null when the image cannot be decoded. The caller must
     * [Bitmap.recycle] the result as soon as it is finished with it.
     */
    fun decodeDownsampled(uri: Uri): Bitmap? {
        val (width, height) = readBounds(uri) ?: return null
        if (width <= 0 || height <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = Companion.calculateInSampleSize(width, height)
            // OCR is greyscale-insensitive and needs no alpha channel.
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        return contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    /** Reads dimensions without allocating pixel memory. */
    fun readBounds(uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }

    internal companion object {
        /**
         * Below roughly 1000px, recognition accuracy on small UI text drops off.
         * Above roughly 2000px, the extra memory buys nothing. 1440 sits in the
         * middle and matches the native height of most phone screenshots.
         */
        const val TARGET_LONGEST_EDGE = 1440

        /**
         * Largest power-of-two subsample that keeps the longest edge at or above
         * [TARGET_LONGEST_EDGE].
         *
         * Powers of two because BitmapFactory rounds down to one anyway, so
         * being explicit keeps the result predictable.
         *
         * Kept as a pure companion function with no Android dependencies so the
         * memory-safety maths is unit-testable on the JVM.
         */
        internal fun calculateInSampleSize(width: Int, height: Int): Int {
            var sampleSize = 1
            var longest = maxOf(width, height)

            // Halve while the next halving would still leave enough resolution
            // for reliable recognition.
            while (longest / 2 >= TARGET_LONGEST_EDGE) {
                longest /= 2
                sampleSize *= 2
            }
            return sampleSize
        }
    }
}
