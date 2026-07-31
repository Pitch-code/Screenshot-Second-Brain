package com.shelfie.core.classify

/**
 * Image quality analysis: perceptual hashing for duplicate detection, and
 * Laplacian variance for blur detection.
 *
 * Pure functions over a greyscale pixel array, with no Android types, so the
 * maths is unit-tested on the JVM. The caller supplies pixels; extracting them
 * from a bitmap lives in `:core:ocr`.
 */
object ImageQuality {

    /** dHash grid: 9 columns so 8 horizontal comparisons produce 64 bits. */
    const val HASH_WIDTH = 9
    const val HASH_HEIGHT = 8

    /**
     * Below this Laplacian variance an image is treated as too blurry to be
     * worth keeping.
     *
     * Screenshots are synthetic images of crisp text, so a genuinely blurry one
     * is almost always a failed capture or a photo of a screen. The threshold is
     * scale-dependent, so it assumes the analysis size used by the caller and
     * needs confirming against real screenshots on a device.
     */
    const val BLUR_VARIANCE_THRESHOLD = 60.0

    /**
     * Hamming distance at or below which two hashes are treated as the same
     * image. 0 is a byte-identical downscale; up to 5 catches re-compressions
     * and minor crops without merging genuinely different screenshots.
     */
    const val NEAR_DUPLICATE_DISTANCE = 5

    /**
     * Difference hash of a greyscale image, as 16 hex characters (64 bits).
     *
     * Compares each pixel with its right-hand neighbour on a 9x8 sample grid.
     * Robust to scaling and mild compression, which is exactly what duplicate
     * screenshots differ by.
     */
    fun differenceHash(grey: IntArray, width: Int, height: Int): String? {
        if (width <= 1 || height <= 0) return null
        if (grey.size < width * height) return null

        var bits = 0L
        var bitIndex = 0

        for (row in 0 until HASH_HEIGHT) {
            for (column in 0 until HASH_WIDTH - 1) {
                val left = sample(grey, width, height, column, row)
                val right = sample(grey, width, height, column + 1, row)

                if (left > right) bits = bits or (1L shl bitIndex)
                bitIndex++
            }
        }
        return bits.toULong().toString(16).padStart(16, '0')
    }

    /**
     * Number of differing bits between two hashes, or null if either is
     * malformed. 64 means maximally different.
     */
    fun hammingDistance(a: String, b: String): Int? {
        val left = a.toULongOrNull(16) ?: return null
        val right = b.toULongOrNull(16) ?: return null
        return (left xor right).countOneBits()
    }

    fun areNearDuplicates(a: String, b: String): Boolean {
        val distance = hammingDistance(a, b) ?: return false
        return distance <= NEAR_DUPLICATE_DISTANCE
    }

    /**
     * Variance of the Laplacian, the standard sharpness measure. A sharp image
     * has strong second derivatives at edges and therefore high variance; a
     * blurred one has little edge energy and low variance.
     */
    fun laplacianVariance(grey: IntArray, width: Int, height: Int): Double {
        if (width < 3 || height < 3) return 0.0
        if (grey.size < width * height) return 0.0

        val responses = ArrayList<Double>((width - 2) * (height - 2))

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val centre = grey[y * width + x]
                val up = grey[(y - 1) * width + x]
                val down = grey[(y + 1) * width + x]
                val left = grey[y * width + (x - 1)]
                val right = grey[y * width + (x + 1)]

                // 4-neighbour Laplacian kernel.
                responses += (4 * centre - up - down - left - right).toDouble()
            }
        }
        if (responses.isEmpty()) return 0.0

        val mean = responses.average()
        return responses.sumOf { value -> (value - mean) * (value - mean) } / responses.size
    }

    fun isBlurry(variance: Double): Boolean = variance < BLUR_VARIANCE_THRESHOLD

    /** Nearest-neighbour sample from the source grid onto the hash grid. */
    private fun sample(
        grey: IntArray,
        width: Int,
        height: Int,
        column: Int,
        row: Int,
    ): Int {
        val x = (column * width / HASH_WIDTH).coerceIn(0, width - 1)
        val y = (row * height / HASH_HEIGHT).coerceIn(0, height - 1)
        return grey[y * width + x]
    }
}
