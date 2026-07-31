package com.shelfie.core.ocr

/**
 * Outcome of reading one image.
 *
 * Failures are modelled explicitly rather than thrown, because indexing runs
 * over thousands of files in a background worker and a single unreadable image
 * must never take down the batch.
 */
sealed interface OcrResult {

    data class Success(
        val text: String,
        val blockCount: Int,
    ) : OcrResult

    data class Failure(
        val reason: OcrFailure,
        val cause: Throwable? = null,
    ) : OcrResult
}

enum class OcrFailure {
    /** The bitmap could not be decoded — truncated or unsupported file. */
    DECODE_FAILED,

    /** The file is gone; the MediaStore row is stale and should be pruned. */
    FILE_MISSING,

    /** Media permission was revoked while indexing was in flight. */
    PERMISSION_DENIED,

    /** ML Kit itself failed. Retryable. */
    RECOGNITION_FAILED,

    /** Image is unusable, e.g. zero-sized. */
    UNSUPPORTED,
    ;

    /** Whether re-running later could plausibly succeed. */
    val isRetryable: Boolean
        get() = this == RECOGNITION_FAILED || this == PERMISSION_DENIED
}
