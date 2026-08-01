package com.shelfie.core.ocr

import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * On-device text recognition via ML Kit's bundled Latin recogniser.
 *
 * Three deliberate constraints:
 *
 *  - **One image in flight at a time.** Parallel recognition looks faster in a
 *    benchmark and then OOMs a 3GB device, because each in-flight bitmap holds
 *    several megabytes.
 *  - **A hard timeout per image.** Combined with the single permit above, a task
 *    whose callback never fires would otherwise hold the permit forever and
 *    silently freeze the entire indexing pipeline. The timeout guarantees the
 *    permit is always released.
 *  - **Bitmaps are always recycled**, including on the failure path.
 *
 * Uses the bundled model, so nothing is downloaded and no network permission is
 * required.
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
            } ?: return@withPermit OcrResult.Failure(
                reason = OcrFailure.DECODE_FAILED,
                detail = "Could not decode $uri",
            )

            if (bitmap.width <= 0 || bitmap.height <= 0) {
                return@withPermit OcrResult.Failure(
                    reason = OcrFailure.UNSUPPORTED,
                    detail = "Zero-sized bitmap",
                )
            }

            // The first call also loads the model, so allow extra headroom.
            withTimeout(RECOGNITION_TIMEOUT_MILLIS) {
                recognizeBitmap(bitmap)
            }
        } catch (e: TimeoutCancellationException) {
            OcrResult.Failure(
                reason = OcrFailure.RECOGNITION_FAILED,
                cause = e,
                detail = "Timed out after ${RECOGNITION_TIMEOUT_MILLIS}ms",
            )
        } catch (e: FileNotFoundException) {
            // The user deleted the file elsewhere; the MediaStore row is stale.
            OcrResult.Failure(OcrFailure.FILE_MISSING, e, e.message)
        } catch (e: SecurityException) {
            // Permission revoked mid-session. Never cache the granted state.
            OcrResult.Failure(OcrFailure.PERMISSION_DENIED, e, e.message)
        } catch (e: OutOfMemoryError) {
            OcrResult.Failure(OcrFailure.DECODE_FAILED, e, "Out of memory")
        } catch (e: Exception) {
            OcrResult.Failure(
                reason = OcrFailure.RECOGNITION_FAILED,
                cause = e,
                // Class name included because ML Kit's own messages are often
                // empty, and without this the failure is undiagnosable.
                detail = "${e.javaClass.simpleName}: ${e.message ?: "no message"}",
            )
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
                        OcrResult.Failure(
                            reason = OcrFailure.RECOGNITION_FAILED,
                            cause = error,
                            detail = "ML Kit: ${error.javaClass.simpleName}: ${error.message}",
                        ),
                    )
                }
                .addOnCanceledListener {
                    continuation.resumeIfActive(
                        OcrResult.Failure(
                            reason = OcrFailure.RECOGNITION_FAILED,
                            detail = "ML Kit cancelled the task",
                        ),
                    )
                }
        }

    private fun CancellableContinuation<OcrResult>.resumeIfActive(result: OcrResult) {
        if (isActive) resume(result)
    }

    private companion object {
        /**
         * Generous, because the very first call also initialises the model and
         * can legitimately take several seconds on a slow device. The point is
         * only to guarantee the permit is eventually released.
         */
        const val RECOGNITION_TIMEOUT_MILLIS = 30_000L
    }
}
