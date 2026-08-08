package com.shelfie.core.media

import androidx.core.net.toUri
import com.shelfie.core.classify.ScreenshotClassifier
import com.shelfie.core.classify.UserRule
import com.shelfie.core.database.dao.ScreenshotDao
import com.shelfie.core.database.entity.ScreenshotEntity
import com.shelfie.core.model.IndexState
import com.shelfie.core.ocr.ImageAnalyzer
import com.shelfie.core.ocr.OcrFailure
import com.shelfie.core.ocr.OcrResult
import com.shelfie.core.ocr.TextRecognitionEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Indexes one screenshot: read text, classify it, persist the result.
 *
 * Single-item granularity on purpose. Every tier of the scheduler reuses this, and
 * because each item commits independently, killing the process mid-batch loses at
 * most one screenshot's work rather than the whole batch.
 */
@Singleton
class ScreenshotIndexer @Inject constructor(
    private val dao: ScreenshotDao,
    private val recognitionEngine: TextRecognitionEngine,
    private val classifier: ScreenshotClassifier,
    private val imageAnalyzer: ImageAnalyzer,
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
        // Marks work in progress without consuming a retry attempt. Attempts are
        // counted only on real failures.
        dao.markStarted(entity.id)

        val uri = entity.uri.toUri()

        return when (val result = recognitionEngine.recognize(uri)) {
            is OcrResult.Success -> {
                val classification = classifier.classify(result.text, rules)

                // Duplicate and blur signals for Cleanup. Decoded at ~64px, so
                // this is cheap; a failure here must not fail the index.
                imageAnalyzer.analyze(uri)?.let { quality ->
                    dao.setQuality(
                        id = entity.id,
                        hash = quality.perceptualHash,
                        blurScore = quality.blurVariance.toFloat(),
                    )
                }

                dao.saveIndexResult(
                    screenshotId = entity.id,
                    text = result.text,
                    category = classification.category,
                    confidence = classification.confidence.toFloat(),
                    primaryValue = classification.primaryValue,
                    indexedAt = nowSeconds,
                )

                // Nothing is recorded when the text comes back empty, and that is
                // deliberate.
                //
                // This used to write "Recognised 0 text blocks" via setLastError.
                // The intent was diagnostic — separating "the model ran and found
                // nothing" from "the model never ran" — but it filed a *successful*
                // read into the error channel, which then surfaced on the shelf's
                // red problem card and in the diagnostics dump. A photo with no
                // writing in it is not a failure, and telling someone their
                // screenshot could not be read when it was read perfectly well is
                // worse than saying nothing.
                //
                // No information is actually lost: the row is INDEXED with empty
                // text, so "read but had no text" stays derivable from the database
                // whenever it is worth reporting. It just is not an error.

                IndexOutcome.Indexed
            }

            is OcrResult.Failure -> {
                val detail = result.detail ?: result.cause?.message ?: result.reason.name

                when (result.reason) {
                    OcrFailure.FILE_MISSING -> {
                        // The file is gone. Drop the row rather than retrying forever.
                        dao.softDelete(listOf(entity.id), nowSeconds)
                        IndexOutcome.Gone
                    }

                    OcrFailure.PERMISSION_DENIED -> {
                        // Not the screenshot's fault, so no attempt is consumed:
                        // it must be retried once access is restored.
                        dao.setLastError(entity.id, detail)
                        dao.requeue(entity.id)
                        IndexOutcome.AccessLost
                    }

                    OcrFailure.DECODE_FAILED, OcrFailure.UNSUPPORTED -> {
                        dao.markFailed(entity.id, IndexState.SKIPPED, detail)
                        IndexOutcome.Skipped
                    }

                    OcrFailure.RECOGNITION_FAILED -> {
                        dao.markFailed(entity.id, IndexState.FAILED, detail)
                        IndexOutcome.Retryable
                    }
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
     * Media permission was lost mid-batch. Callers should stop immediately rather
     * than burning attempts on every remaining row.
     */
    AccessLost,
}
