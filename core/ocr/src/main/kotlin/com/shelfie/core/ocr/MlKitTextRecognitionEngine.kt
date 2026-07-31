package com.shelfie.core.ocr

import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * On-device text recognition via ML Kit's bundled Latin recogniser.
 *
 * Two deliberate constraints:
 *
 *  - **One image in flight at a time.** Parallel recognition looks faster in a
 *    benchmark and then OOMs on a 3GB device, because each in-flight bitmap
 *    holds several megabytes. The permit makes memory use bounded and
 *    predictable regardless of how many callers pile in.
 *  - **Bitmaps are always recycled**, including on the failure path.
 *
 * Uses the bundled model, so nothing is ever downloaded and no network
 * permission is required.
 */
@Singleton
class MlKitTextRecognitionEngine @Inject constructor(
    private val bitmapDecoder: BitmapDecoder,
) : TextRecognitionEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** Bounds concurrent recognition to a single image. */
    private val permit = Semaphore(permits = 1)

    override suspend fun recognize(uri: Uri): OcrResult = permit.withPermit {
        var bitmap: Bitmap? = null
        try {
            bitmap = withContext(Dispatchers.IO) {
                bitmapDecoder.decodeDownsampled(uri)
            } ?: return@withPermit OcrResult.Failure(OcrFailure.DECODE_FAILED)

            if (bitmap.width <= 0 || bitmap.height <= 0) {
                return@withPermit OcrResult.Failure(OcrFailure.UNSUPPORTED)
            }

            recognizeBitmap(bitmap)
        } catch (e: FileNotFoundException) {
            // The user deleted the file elsewhere; the MediaStore row is stale.
            OcrResult.Failure(OcrFailure.FILE_MISSING, e)
        } catch (e: SecurityException) {
            // Permission revoked mid-session. Never cache the granted state.
            OcrResult.Failure(OcrFailure.PERMISSION_DENIED, e)
        } catch (e: OutOfMemoryError) {
            // Should be unreachable given downsampling, but a batch job must
            // never die because one pathological image slipped through.
            OcrResult.Failure(OcrFailure.DECODE_FAILED, e)
        } catch (e: Exception) {
            OcrResult.Failure(OcrFailure.RECOGNITION_FAILED, e)
        } finally {
            bitmap?.recycle()
        }
    }

    private suspend fun recognizeBitmap(bitmap: Bitmap): OcrResult =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    continuation.resumeIfActive(
                        OcrResult.Success(
                            text = visionText.text,
                            blockCount = visionText.textBlocks.size,
                        ),
                    )
                }
                .addOnFailureListener { error ->
                    continuation.resumeIfActive(
                        OcrResult.Failure(OcrFailure.RECOGNITION_FAILED, error),
                    )
                }
                .addOnCanceledListener {
                    continuation.resumeIfActive(
                        OcrResult.Failure(OcrFailure.RECOGNITION_FAILED),
                    )
                }
        }

    private fun CancellableContinuation<OcrResult>.resumeIfActive(result: OcrResult) {
        if (isActive) resume(result)
    }
}
