package com.shelfie.core.ocr

import android.net.Uri
import androidx.core.net.toUri
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
    private lateinit var pngFile: File
    private lateinit var pngUri: Uri

    @Before
    fun setUp() {
        decoder = BitmapDecoder(RuntimeEnvironment.getApplication().contentResolver)

        // A real 8x4 PNG. ContentResolver.openInputStream handles file:// URIs,
        // which keeps this test free of a stub ContentProvider.
        pngFile = File(temporaryFolder.root, "shot.png")
        pngFile.writeBytes(Base64.getDecoder().decode(PNG_8X4_BASE64))
        pngUri = Uri.fromFile(pngFile)
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

    // ------------------------------------------------- schemeless file paths
    //
    // The second shipped decode bug, and the reason these tests did not catch
    // it: every case above builds its Uri with Uri.fromFile, which always
    // produces a file:// scheme. Nothing exercised the *other* form the app
    // actually stores.
    //
    // PickerImporter writes ThumbnailStore's absolute path straight into the
    // row, so Limited Mode screenshots parse back into a Uri with no scheme at
    // all. ContentResolver.openInputStream refuses those, so every
    // picker-imported screenshot failed with
    // "Could not decode /data/user/0/...", while Coil — which treats a
    // schemeless Uri as a file path — rendered the tile perfectly. Visible on
    // the shelf, permanently unreadable.

    @Test
    fun `a bare filesystem path really does parse without a scheme`() {
        // Documents the precondition the two tests below depend on. If Uri ever
        // started inferring a file scheme here, they would pass for the wrong
        // reason and this would fail loudly instead.
        assertThat(pngFile.absolutePath.toUri().scheme).isNull()
    }

    @Test
    fun `readBounds returns dimensions for a bare filesystem path`() {
        val schemeless = pngFile.absolutePath.toUri()

        assertThat(decoder.readBounds(schemeless)).isEqualTo(PNG_WIDTH to PNG_HEIGHT)
    }

    @Test
    fun `decodeDownsampled produces a bitmap for a bare filesystem path`() {
        // The exact call that failed for every Limited Mode import.
        val bitmap = decoder.decodeDownsampled(pngFile.absolutePath.toUri())

        assertThat(bitmap).isNotNull()
        assertThat(bitmap!!.width).isEqualTo(PNG_WIDTH)
        assertThat(bitmap.height).isEqualTo(PNG_HEIGHT)
        bitmap.recycle()
    }

    @Test
    fun `decodeForAnalysis produces a bitmap for a bare filesystem path`() {
        // Feeds the Cleanup screen's duplicate and blur signals, which were
        // silently empty for every picker-imported screenshot.
        val bitmap = decoder.decodeForAnalysis(pngFile.absolutePath.toUri())

        assertThat(bitmap).isNotNull()
        bitmap!!.recycle()
    }

    @Test
    fun `readBounds returns null for a missing bare filesystem path`() {
        val missing = File(temporaryFolder.root, "gone.png").absolutePath.toUri()

        // Still null rather than an exception, on the path that no longer goes
        // through ContentResolver.
        assertThat(decoder.readBounds(missing)).isNull()
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
