package com.shelfie.core.ocr

/**
 * Merges the output of the Latin and Devanagari recognisers into one text.
 *
 * Both bundled models run over the same image. The Latin pass is authoritative for
 * Latin-script text. The Devanagari model, run over a mostly-English screenshot,
 * tends to echo the Latin text back — often slightly garbled — which would fill the
 * index with near-duplicate noise. So only lines that actually contain a Devanagari
 * character are taken from the Devanagari pass; everything else is already covered,
 * and read more reliably, by the Latin pass.
 *
 * The result is the Latin text followed by the Devanagari-only lines. For Shelfie's
 * two uses — full-text search and keyword classification — what matters is that both
 * scripts are present and each is internally in reading order; interleaving the two
 * by exact on-screen position is not worth re-sorting across models for.
 */
object ScriptMerge {

    /** The basic Devanagari Unicode block, which covers Hindi and Marathi. */
    private val DEVANAGARI = '\u0900'..'\u097F'

    fun merge(latin: String, devanagari: String): String {
        val devanagariLines = devanagari.lineSequence()
            .map { it.trim() }
            .filter { line -> line.any { it in DEVANAGARI } }
            .toList()

        return when {
            devanagariLines.isEmpty() -> latin
            latin.isBlank() -> devanagariLines.joinToString("\n")
            else -> latin + "\n" + devanagariLines.joinToString("\n")
        }
    }
}
