package com.shelfie.feature.detail

import com.shelfie.core.model.ByteFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formatting for the details panel.
 *
 * Kept out of the composable so each rule can be read on its own, and so the awkward
 * cases — a missing value, a file exactly one kilobyte — are visible rather than
 * buried in a string template.
 */
internal object MetadataFormat {

    private val dateTime: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())

    /**
     * Formats an instant in the phone's own zone.
     *
     * The zone is resolved per call rather than held, because a cached
     * [ZoneId.systemDefault] survives the user travelling or changing the setting and
     * would then quietly report every timestamp in the old zone.
     */
    fun timestamp(millis: Long?): String? =
        millis?.let {
            runCatching {
                dateTime.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
            }.getOrNull()
        }

    fun dimensions(width: Int, height: Int): String? =
        if (width > 0 && height > 0) "$width × $height" else null

    /**
     * Human-readable file size, or null when there is no size to show.
     *
     * Delegates to [ByteFormat], which Cleanup already uses. Writing a second
     * formatter here would mean the same file could be reported as two different sizes
     * on two screens of the same app — and [ByteFormat] is the one with tests.
     */
    fun fileSize(bytes: Long): String? =
        if (bytes > 0L) ByteFormat.format(bytes) else null
}
