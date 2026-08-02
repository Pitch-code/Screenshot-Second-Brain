package com.shelfie.core.ocr

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.Base64
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression tests for the decode path against real Android framework code.
 *
 * These exist because of a shipped bug that made *every* screenshot fail to
 * index. [BitmapDecoder.readBounds] treated a null return from
 * `BitmapFactory.decodeStream` as failure, but with `inJustDecodeBounds = true`
 * a null return is the documented *success* case — the dimensions arrive on the
 * `Options` object. So `readBounds` returned null unconditionally, which made
 * `decodeDownsampled` return null unconditionally, which failed every image
 * with `DECODE_FAILED`.
 *
 * [BitmapDecoderTest] could not catch this: it only covers the pure subsampling
 * arithmetic, and the module sets `unitTests.isReturnDefaultValues = true`, so
 * in a plain JVM test `decodeStream` returns null and `outWidth` stays 0 —
 * indistinguishable from the bug. Catching it requires real framework code,
 * hence Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// NATIVE runs real Skia decoding rather than shadow stubs. Without it,
// BitmapFactory "decodes" arbitrary bytes into a placeholder 100x100 bitmap,
// which would let a broken decoder look healthy — the exact failure mode these
// tests exist to prevent.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapDecoderBoundsTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var decoder: BitmapDecoder
    private lateinit var pngUri: Uri

    @Before
    fun setUp() {
        decoder = BitmapDecoder(RuntimeEnvironment.getApplication().contentResolver)

        // A real 8x4 PNG. ContentResolver.openInputStream handles file:// URIs,
        // which keeps this test free of a stub ContentProvider.
        val png = File(temporaryFolder.root, "shot.png")
        png.writeBytes(Base64.getDecoder().decode(PNG_8X4_BASE64))
        pngUri = Uri.fromFile(png)
    }

    @Test
    fun `readBounds returns dimensions for a real image`() {
        // The core regression: this returned null for every image.
        val bounds = decoder.readBounds(pngUri)

        assertThat(bounds).isEqualTo(PNG_WIDTH to PNG_HEIGHT)
    }

    @Test
    fun `decodeDownsampled produces a bitmap for a real image`() {
        // This is what MlKitTextRecognitionEngine calls. Null here meant
        // OcrFailure.DECODE_FAILED and a SKIPPED screenshot.
        val bitmap = decoder.decodeDownsampled(pngUri)

        assertThat(bitmap).isNotNull()
        // Well below the downsample target, so it must come back at full size.
        assertThat(bitmap!!.width).isEqualTo(PNG_WIDTH)
        assertThat(bitmap.height).isEqualTo(PNG_HEIGHT)
        bitmap.recycle()
    }

    @Test
    fun `decodeForAnalysis produces a bitmap for a real image`() {
        val bitmap = decoder.decodeForAnalysis(pngUri)

        assertThat(bitmap).isNotNull()
        bitmap!!.recycle()
    }

    @Test
    fun `readBounds returns null when the file is missing`() {
        val missing = Uri.fromFile(File(temporaryFolder.root, "gone.png"))

        // Must be null, not an exception: media rows outlive deleted files and
        // one stale row must not abort an indexing run.
        assertThat(decoder.readBounds(missing)).isNull()
    }

    @Test
    fun `decodeDownsampled returns null when the file is missing`() {
        val missing = Uri.fromFile(File(temporaryFolder.root, "gone.png"))

        assertThat(decoder.decodeDownsampled(missing)).isNull()
    }

    @Test
    fun `readBounds returns null for data that is not an image`() {
        val junk = File(temporaryFolder.root, "notanimage.png")
        junk.writeText("this is definitely not a PNG")

        assertThat(decoder.readBounds(Uri.fromFile(junk))).isNull()
    }

    private companion object {
        const val PNG_WIDTH = 8
        const val PNG_HEIGHT = 4

        /** 8x4 truecolour PNG, generated once and inlined to keep the test hermetic. */
        const val PNG_8X4_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAgAAAAECAIAAAA8r+mnAAAAJUlEQVR4nGNgkLOJqpi2" +
                "5dIHPh2vjLYlhx78k7GKKJuyiYF6EgCojC1hmM63+wAAAABJRU5ErkJggg=="
    }
}
