package com.shelfie.core.media

import android.net.Uri
import com.shelfie.core.classify.ScreenshotClassifier
import com.shelfie.core.classify.UserRule
import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.database.entity.ScreenshotEntity
import com.shelfie.core.model.IndexState
import com.shelfie.core.ocr.OcrFailure
import com.shelfie.core.ocr.OcrResult
import com.shelfie.core.ocr.TextRecognitionEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indexes one screenshot: read text, classify it, persist the result.
 *
 * Single-item granularity on purpose. Every tier of the scheduler reuses this,
 * and because each item commits independently, killing the process mid-batch
 * loses at most one screenshot's work rather than the whole batch.
 */
@Singleton
class ScreenshotIndexer @Inject constructor(
    private val dao: ScreenshotDao,
    private val recognitionEngine: TextRecognitionEngine,
    private val classifier: ScreenshotClassifier,
) {

    /**
     * Processes [entity]. Returns the outcome so callers can decide whether to
     * retry, prune, or stop the batch entirely.
     */
    suspend fun index(
        entity: ScreenshotEntity,
        rules: List<UserRule> = emptyList(),
        nowSeconds: Long = System.currentTimeMillis() / 1000,
    ): IndexOutcome {
        dao.markAttempt(entity.id, IndexState.IN_PROGRESS)

        return when (val result = recognitionEngine.recognize(Uri.parse(entity.uri))) {
            is OcrResult.Success -> {
                val classification = classifier.classify(result.text, rules)

                dao.saveIndexResult(
                    screenshotId = entity.id,
                    text = result.text,
                    category = classification.category,
                    confidence = classification.confidence.toFloat(),
                    primaryValue = classification.primaryValue,
                    indexedAt = nowSeconds,
                )
                IndexOutcome.Indexed
            }

            is OcrResult.Failure -> when (result.reason) {
                OcrFailure.FILE_MISSING -> {
                    // The file is gone. Drop the row rather than retrying forever.
                    dao.softDelete(listOf(entity.id), nowSeconds)
                    IndexOutcome.Gone
                }

                OcrFailure.PERMISSION_DENIED -> {
                    dao.markAttempt(entity.id, IndexState.PENDING)
                    IndexOutcome.AccessLost
                }

                OcrFailure.DECODE_FAILED, OcrFailure.UNSUPPORTED -> {
                    dao.markAttempt(entity.id, IndexState.SKIPPED)
                    IndexOutcome.Skipped
                }

                OcrFailure.RECOGNITION_FAILED -> {
                    dao.markAttempt(entity.id, IndexState.FAILED)
                    IndexOutcome.Retryable
                }
            }
        }
    }
}

/** What happened to one screenshot. */
enum class IndexOutcome {
    Indexed,

    /** Unreadable image; will not be retried. */
    Skipped,

    /** Transient failure; eligible for another attempt later. */
    Retryable,

    /** Underlying file no longer exists. */
    Gone,

    /**
     * Media permission was lost mid-batch. Callers should stop immediately
     * rather than burning attempts on every remaining row.
     */
    AccessLost,
}
