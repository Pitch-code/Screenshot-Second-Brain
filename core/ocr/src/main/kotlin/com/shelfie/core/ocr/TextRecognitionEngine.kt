package com.shelfie.core.ocr

import android.net.Uri

/**
 * Reads the text in an image.
 *
 * An interface so the pipeline can be tested without ML Kit, and so a different
 * recogniser (Devanagari, or an on-device GenAI describer for image-only
 * screenshots) can be dropped in later without touching callers.
 */
interface TextRecognitionEngine {
    suspend fun recognize(uri: Uri): OcrResult
}
