package com.shelfie.core.model

import java.util.Locale

/**
 * Human-readable byte sizes.
 *
 * Cleanup's entire promise is "get this much storage back", so these figures are
 * the most trust-sensitive numbers in the app. Uses binary units, matching what
 * Android's own storage settings report, so Shelfie's figure doesn't contradict
 * the system's.
 */
object ByteFormat {

    private const val KB = 1024.0
    private const val MB = KB * 1024
    private const val GB = MB * 1024

    fun format(bytes: Long, locale: Locale = Locale.getDefault()): String = when {
        bytes < 0 -> "0 KB"
        bytes < KB -> "${bytes} B"
        bytes < MB -> String.format(locale, "%.0f KB", bytes / KB)
        bytes < GB -> {
            val mb = bytes / MB
            // Below 10MB a whole number reads as suspiciously rounded, so show a
            // decimal; above it, the decimal is noise.
            if (mb < 10) String.format(locale, "%.1f MB", mb)
            else String.format(locale, "%.0f MB", mb)
        }

        else -> String.format(locale, "%.2f GB", bytes / GB)
    }
}
