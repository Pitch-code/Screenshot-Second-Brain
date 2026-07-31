package com.shelfie.core.ocr

import android.graphics.Bitmap
import android.net.Uri
import com.shelfie.core.classify.ImageQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Perceptual hash and sharpness measure for one image. */
data class ImageQualityResult(
    val perceptualHash: String?,
    val blurVariance: Double,
) {
    val isBlurry: Boolean get() = ImageQuality.isBlurry(blurVariance)
}

/**
 * Computes duplicate and blur signals for an image.
 *
 * Decodes at roughly 64px, so this costs a fraction of what OCR does and can run
 * on every screenshot during indexing without affecting the time-to-first-value
 * budget. All the actual maths lives in `:core:classify` as pure functions.
 */
@Singleton
class ImageAnalyzer @Inject constructor(
    private val bitmapDecoder: BitmapDecoder,
) {

    suspend fun analyze(uri: Uri): ImageQualityResult? = withContext(Dispatchers.IO) {
        var bitmap: Bitmap? = null
        try {
            bitmap = bitmapDecoder.decodeForAnalysis(uri) ?: return@withContext null

            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return@withContext null

            val grey = bitmap.toGreyscale(width, height)

            ImageQualityResult(
                perceptualHash = ImageQuality.differenceHash(grey, width, height),
                blurVariance = ImageQuality.laplacianVariance(grey, width, height),
            )
        } catch (_: Exception) {
            // Quality signals are a bonus; failing to compute them must never
            // prevent a screenshot from being indexed and searchable.
            null
        } catch (_: OutOfMemoryError) {
            null
        } finally {
            bitmap?.recycle()
        }
    }

    /** Luminance conversion using the standard perceptual weights. */
    private fun Bitmap.toGreyscale(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)

        for (index in pixels.indices) {
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            pixels[index] = ((red * 299 + green * 587 + blue * 114) / 1000).coerceIn(0, 255)
        }
        return pixels
    }
}
