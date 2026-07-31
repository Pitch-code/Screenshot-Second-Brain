package com.shelfie.core.classify

import com.shelfie.core.model.ScreenshotAction
import com.shelfie.core.model.ScreenshotCategory

/** The complete result of classifying one screenshot's text. */
data class Classification(
    val category: ScreenshotCategory,
    /** 0.0..1.0. Low confidence still files the screenshot, just less loudly. */
    val confidence: Double,
    /**
     * The one value worth showing on the shelf tile. Null means the tile falls
     * back to the first line of recognised text.
     */
    val primaryValue: String?,
    /** The action offered directly on the tile. */
    val primaryAction: ScreenshotAction?,
    val entities: ExtractedEntities,
    /** Set when a [UserRule] decided this, so the UI can say so. */
    val matchedRuleId: Long? = null,
) {
    companion object {
        val Unsorted = Classification(
            category = ScreenshotCategory.NOT_SORTED,
            confidence = 0.0,
            primaryValue = null,
            primaryAction = null,
            entities = ExtractedEntities.Empty,
        )
    }
}
