package com.shelfie.core.classify

import com.shelfie.core.model.ScreenshotAction
import com.shelfie.core.model.ScreenshotCategory

/**
 * Turns recognised text into a category, a headline value, and an action.
 *
 * Order of authority:
 *   1. User rules — always win, because the user is never wrong about their own
 *      screenshots.
 *   2. Weighted signal scoring.
 *   3. Entity-only fallback — e.g. a bare amount with no other clues still
 *      lands in Payments rather than nowhere.
 *   4. NOT_SORTED.
 *
 * Pure and deterministic: same text in, same result out, on every device.
 */
class ScreenshotClassifier(
    private val extractor: EntityExtractor = EntityExtractor,
) {

    fun classify(text: String, rules: List<UserRule> = emptyList()): Classification {
        if (text.isBlank()) return Classification.Unsorted

        val entities = extractor.extract(text)

        // 1. User rules take precedence, in declaration order.
        rules.firstOrNull { it.matches(text) }?.let { rule ->
            return Classification(
                category = rule.category,
                confidence = 1.0,
                primaryValue = primaryValueFor(rule.category, entities, text),
                primaryAction = primaryActionFor(rule.category, entities),
                entities = entities,
                matchedRuleId = rule.id,
            )
        }

        // 2. Weighted scoring across all categories.
        val scores = CategorySignals.table.mapValues { (_, signals) ->
            signals.sumOf { it.score(text) }
        }
        val best = scores.maxByOrNull { it.value }

        if (best != null && best.value >= MIN_SCORE) {
            return Classification(
                category = best.key,
                confidence = confidenceOf(best.value),
                primaryValue = primaryValueFor(best.key, entities, text),
                primaryAction = primaryActionFor(best.key, entities),
                entities = entities,
            )
        }

        // 3. Entity-only fallback for screenshots with values but no vocabulary.
        entityOnlyCategory(entities)?.let { category ->
            return Classification(
                category = category,
                confidence = 0.3,
                primaryValue = primaryValueFor(category, entities, text),
                primaryAction = primaryActionFor(category, entities),
                entities = entities,
            )
        }

        // 4. Everything else. Still searchable, still gets a copy action.
        return Classification(
            category = ScreenshotCategory.NOT_SORTED,
            confidence = 0.0,
            primaryValue = null,
            primaryAction = if (entities.urls.isNotEmpty()) {
                ScreenshotAction.OPEN_LINK
            } else {
                ScreenshotAction.COPY_TEXT
            },
            entities = entities,
        )
    }

    /**
     * Saturating curve rather than a linear ratio: once several strong signals
     * agree, extra matches shouldn't keep inflating the number.
     */
    private fun confidenceOf(score: Double): Double =
        (score / (score + SATURATION)).coerceIn(0.0, 1.0)

    private fun entityOnlyCategory(entities: ExtractedEntities): ScreenshotCategory? = when {
        entities.otpCodes.isNotEmpty() -> ScreenshotCategory.OTP_CODES
        entities.pnrCodes.isNotEmpty() -> ScreenshotCategory.TICKETS
        entities.referenceIds.isNotEmpty() || entities.amounts.isNotEmpty() ->
            ScreenshotCategory.PAYMENTS
        entities.passwords.isNotEmpty() -> ScreenshotCategory.WIFI_PASSWORDS
        entities.phoneNumbers.isNotEmpty() -> ScreenshotCategory.CONTACTS
        else -> null
    }

    /** The single most useful value for this category, or null to fall back. */
    private fun primaryValueFor(
        category: ScreenshotCategory,
        entities: ExtractedEntities,
        text: String,
    ): String? = when (category) {
        ScreenshotCategory.PAYMENTS ->
            entities.amounts.maxByOrNull { numericValue(it) } ?: entities.referenceIds.firstOrNull()

        ScreenshotCategory.OTP_CODES -> entities.otpCodes.firstOrNull()

        ScreenshotCategory.TICKETS ->
            entities.pnrCodes.firstOrNull()
                ?: entities.dates.firstOrNull()
                ?: entities.referenceIds.firstOrNull()

        ScreenshotCategory.WIFI_PASSWORDS -> entities.passwords.firstOrNull()

        ScreenshotCategory.PRODUCTS -> entities.amounts.maxByOrNull { numericValue(it) }

        ScreenshotCategory.DOCUMENTS -> entities.dates.firstOrNull()

        ScreenshotCategory.CONTACTS -> entities.phoneNumbers.firstOrNull()

        ScreenshotCategory.PLACES, ScreenshotCategory.RECIPES,
        ScreenshotCategory.STUDY, ScreenshotCategory.CHATS,
        -> firstMeaningfulLine(text)

        ScreenshotCategory.NOT_SORTED -> null
    }

    private fun primaryActionFor(
        category: ScreenshotCategory,
        entities: ExtractedEntities,
    ): ScreenshotAction = when (category) {
        ScreenshotCategory.OTP_CODES -> ScreenshotAction.COPY_CODE
        ScreenshotCategory.WIFI_PASSWORDS -> ScreenshotAction.COPY_CODE

        ScreenshotCategory.TICKETS ->
            if (entities.dates.isNotEmpty()) {
                ScreenshotAction.ADD_TO_CALENDAR
            } else {
                ScreenshotAction.COPY_TEXT
            }

        ScreenshotCategory.CONTACTS -> ScreenshotAction.DIAL_NUMBER

        ScreenshotCategory.PRODUCTS, ScreenshotCategory.PLACES ->
            if (entities.urls.isNotEmpty()) {
                ScreenshotAction.OPEN_LINK
            } else {
                ScreenshotAction.SHARE
            }

        else ->
            if (entities.urls.isNotEmpty()) {
                ScreenshotAction.OPEN_LINK
            } else {
                ScreenshotAction.COPY_TEXT
            }
    }

    /** Strips commas so "1,240.50" sorts above "999". */
    private fun numericValue(amount: String): Double =
        amount.replace(",", "").toDoubleOrNull() ?: 0.0

    /** First line with real content, for categories with no obvious key value. */
    /**
     * The first line worth putting on a tile.
     *
     * "Meaningful" used to mean only "between 3 and 60 characters", which let through
     * whatever happened to be at the top of the text — and once reading order was
     * fixed, what is at the top of a screenshot is the status bar. Tiles were labelled
     * with the clock and the network speed: `2:03 2.00`, `2:03 0.62 Y…`.
     *
     * The status bar is now removed during extraction, which is the real fix. This is
     * the second line of defence, because that removal is deliberately conservative and
     * will sometimes leave one in — and a label is the most visible text in the app, so
     * it should not be the first thing that clears a length check.
     *
     * A line has to be mostly letters to qualify. That rejects clocks, speeds, phone
     * numbers and reference codes, none of which read as a title, while accepting
     * ordinary content like a place or a company name.
     */
    private fun firstMeaningfulLine(text: String): String? =
        text.lineSequence()
            .map { it.trim() }
            .firstOrNull { line ->
                val letters = line.count(Char::isLetter)
                line.length in 3..60 &&
                    letters >= MIN_LABEL_LETTERS &&
                    letters.toDouble() / line.length >= MIN_LABEL_LETTER_RATIO
            }
            ?.take(60)

    private companion object {
        /** Below this, scoring is treated as noise. */
        const val MIN_SCORE = 3.0

        /** Controls how fast confidence approaches 1.0. */
        const val SATURATION = 6.0

        /** Fewest letters a line needs before it can be used as a tile label. */
        const val MIN_LABEL_LETTERS = 4

        /**
         * Proportion of a candidate label that must be letters.
         *
         * Half rejects `2:03 YeD RI` — five letters in eleven characters — while
         * accepting real content with a number in it, such as `Block 2, DLF Cyber City`.
         */
        const val MIN_LABEL_LETTER_RATIO = 0.5
    }
}
