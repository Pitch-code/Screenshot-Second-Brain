package com.shelfie.core.classify

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class ImageQualityTest {

    // --------------------------------------------------------------- hashing

    @Test
    fun `hash is 16 hex characters`() {
        val hash = ImageQuality.differenceHash(gradient(32, 32), 32, 32)
        assertThat(hash).isNotNull()
        assertThat(hash!!).hasLength(16)
        assertThat(hash.matches(Regex("[0-9a-f]{16}"))).isTrue()
    }

    @Test
    fun `identical images hash identically`() {
        val a = gradient(64, 64)
        val b = gradient(64, 64)
        assertThat(ImageQuality.differenceHash(a, 64, 64))
            .isEqualTo(ImageQuality.differenceHash(b, 64, 64))
    }

    @Test
    fun `hash is stable across scale`() {
        // The same visual content at two resolutions must land within the
        // near-duplicate threshold, otherwise re-compressed copies would not be
        // detected as duplicates.
        val small = gradient(32, 32)
        val large = gradient(256, 256)

        val hashSmall = ImageQuality.differenceHash(small, 32, 32)!!
        val hashLarge = ImageQuality.differenceHash(large, 256, 256)!!

        assertThat(ImageQuality.areNearDuplicates(hashSmall, hashLarge)).isTrue()
    }

    @Test
    fun `visually different images produce distant hashes`() {
        val gradientHash = ImageQuality.differenceHash(gradient(64, 64), 64, 64)!!
        val invertedHash = ImageQuality.differenceHash(inverseGradient(64, 64), 64, 64)!!

        val distance = ImageQuality.hammingDistance(gradientHash, invertedHash)!!
        assertThat(distance).isGreaterThan(ImageQuality.NEAR_DUPLICATE_DISTANCE)
    }

    @Test
    fun `rejects degenerate dimensions`() {
        assertThat(ImageQuality.differenceHash(IntArray(0), 0, 0)).isNull()
        assertThat(ImageQuality.differenceHash(IntArray(4), 1, 4)).isNull()
        // Array smaller than the stated dimensions must not crash.
        assertThat(ImageQuality.differenceHash(IntArray(4), 64, 64)).isNull()
    }

    // -------------------------------------------------------------- distance

    @Test
    fun `hamming distance of identical hashes is zero`() {
        assertThat(ImageQuality.hammingDistance("ffffffffffffffff", "ffffffffffffffff"))
            .isEqualTo(0)
    }

    @Test
    fun `hamming distance of opposite hashes is 64`() {
        assertThat(ImageQuality.hammingDistance("0000000000000000", "ffffffffffffffff"))
            .isEqualTo(64)
    }

    @Test
    fun `hamming distance counts single bit differences`() {
        assertThat(ImageQuality.hammingDistance("0000000000000000", "0000000000000001"))
            .isEqualTo(1)
        assertThat(ImageQuality.hammingDistance("0000000000000000", "0000000000000003"))
            .isEqualTo(2)
    }

    @Test
    fun `malformed hashes return null rather than throwing`() {
        // A corrupt stored hash must never crash the Cleanup screen.
        assertThat(ImageQuality.hammingDistance("not-hex", "0000000000000000")).isNull()
        assertThat(ImageQuality.hammingDistance("", "0")).isNull()
        assertThat(ImageQuality.areNearDuplicates("garbage", "0000000000000000")).isFalse()
    }

    @Test
    fun `near duplicate threshold is respected at the boundary`() {
        val base = "0000000000000000"
        // Exactly 5 bits set: at the threshold, so still a near duplicate.
        assertThat(ImageQuality.areNearDuplicates(base, "000000000000001f")).isTrue()
        // 6 bits set: over the threshold.
        assertThat(ImageQuality.areNearDuplicates(base, "000000000000003f")).isFalse()
    }

    // ------------------------------------------------------------------ blur

    @Test
    fun `a flat image has essentially no edge energy`() {
        val flat = IntArray(64 * 64) { 128 }
        val variance = ImageQuality.laplacianVariance(flat, 64, 64)

        assertThat(variance).isWithin(0.001).of(0.0)
        assertThat(ImageQuality.isBlurry(variance)).isTrue()
    }

    @Test
    fun `a sharp checkerboard has high variance`() {
        val sharp = IntArray(64 * 64) { index ->
            val x = index % 64
            val y = index / 64
            if ((x + y) % 2 == 0) 0 else 255
        }
        val variance = ImageQuality.laplacianVariance(sharp, 64, 64)

        assertThat(variance).isGreaterThan(ImageQuality.BLUR_VARIANCE_THRESHOLD)
        assertThat(ImageQuality.isBlurry(variance)).isFalse()
    }

    @Test
    fun `a smooth gradient scores lower than a sharp checkerboard`() {
        val gradientVariance = ImageQuality.laplacianVariance(gradient(64, 64), 64, 64)
        val sharp = IntArray(64 * 64) { index ->
            if ((index % 64 + index / 64) % 2 == 0) 0 else 255
        }
        val sharpVariance = ImageQuality.laplacianVariance(sharp, 64, 64)

        assertThat(gradientVariance).isLessThan(sharpVariance)
    }

    @Test
    fun `variance handles degenerate dimensions safely`() {
        assertThat(ImageQuality.laplacianVariance(IntArray(0), 0, 0)).isEqualTo(0.0)
        assertThat(ImageQuality.laplacianVariance(IntArray(4), 2, 2)).isEqualTo(0.0)
        assertThat(ImageQuality.laplacianVariance(IntArray(4), 64, 64)).isEqualTo(0.0)
    }

    @Test
    fun `noise is not classified as blurry`() {
        val random = Random(seed = 42)
        val noise = IntArray(64 * 64) { random.nextInt(256) }
        assertThat(ImageQuality.isBlurry(ImageQuality.laplacianVariance(noise, 64, 64)))
            .isFalse()
    }

    // --------------------------------------------------------------- helpers

    private fun gradient(width: Int, height: Int) = IntArray(width * height) { index ->
        val x = index % width
        val y = index / width
        ((x.toDouble() / width + y.toDouble() / height) / 2 * 255).toInt()
    }

    private fun inverseGradient(width: Int, height: Int) = IntArray(width * height) { index ->
        255 - gradient(width, height)[index]
    }
}
