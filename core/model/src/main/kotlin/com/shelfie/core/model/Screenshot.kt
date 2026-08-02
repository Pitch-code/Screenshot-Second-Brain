package com.shelfie.core.model

/**
 * A screenshot known to Shelfie.
 *
 * Deliberately free of Android types so it can be unit-tested on the JVM and
 * shared by every layer without dragging in a framework dependency.
 */
data class Screenshot(
    val id: Long,
    val mediaStoreId: Long,
    val uri: String,
    val displayName: String,
    /** Epoch seconds, as reported by MediaStore. */
    val dateAdded: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val indexState: IndexState,
    val category: ScreenshotCategory,
    /**
     * The single most useful value pulled out of the image — an amount, an OTP,
     * a booking reference. This is what the shelf tile shows instead of a
     * filename, and it is what makes the index feel alive.
     */
    val primaryValue: String? = null,
    val primaryAction: ScreenshotAction? = null,
    val indexedAt: Long? = null,
    val isDeleted: Boolean = false,
    val source: ScreenshotSource = ScreenshotSource.MEDIA_STORE,
    /**
     * Absolute path to a downscaled copy in the app's private storage.
     *
     * Only set for screenshots imported through the system photo picker in
     * Limited Mode. The picker grants read access that expires when the process
     * dies and cannot be made persistable, so without a local copy those tiles
     * would render as blank boxes on the next launch.
     */
    val localThumbnailPath: String? = null,

    /** The user-created folder this was filed into, if any. */
    val folderId: Long? = null,
) {
    /** True when the user has filed this deliberately, overriding the classifier. */
    val isFiled: Boolean get() = folderId != null

    val aspectRatio: Float
        get() = if (height == 0) 1f else width.toFloat() / height.toFloat()

    /**
     * What the UI should actually load. Prefers the durable local copy when one
     * exists, and falls back to the MediaStore URI.
     */
    val displayUri: String
        get() = localThumbnailPath ?: uri
}

/** Where a screenshot came from, which determines whether we can re-read it. */
enum class ScreenshotSource {
    /** Discovered via MediaStore with broad or partial media access. */
    MEDIA_STORE,

    /** Hand-picked through the system photo picker in Limited Mode. */
    PICKER,
}

/** Where a screenshot is in the indexing pipeline. */
enum class IndexState {
    /** Discovered but not yet read. */
    PENDING,

    /** Currently being processed. */
    IN_PROGRESS,

    /** Text extracted and classified. */
    INDEXED,

    /** Reading failed; retryable. */
    FAILED,

    /** Deliberately excluded (e.g. unreadable, or user-hidden). */
    SKIPPED,

    /**
     * Rolled out of the free tier's newest-N window.
     *
     * A distinct state rather than reusing PENDING: pending rows get retried, so
     * a held row would be re-indexed and immediately evicted again in a loop.
     * Unlocking the full version flips these back to PENDING.
     */
    QUOTA_HELD,
}

/**
 * Categories are surfaced to the user only once they have enough matches to be
 * useful — see the product spec. [NOT_SORTED] is the fallback bucket and is
 * never labelled "Uncategorised".
 */
enum class ScreenshotCategory {
    PAYMENTS,
    OTP_CODES,
    TICKETS,
    WIFI_PASSWORDS,
    PRODUCTS,
    CHATS,
    DOCUMENTS,
    RECIPES,
    PLACES,
    STUDY,
    CONTACTS,
    NOT_SORTED,
}

/** The next step a screenshot offers, shown directly on its tile. */
enum class ScreenshotAction {
    OPEN_LINK,
    COPY_CODE,
    ADD_TO_CALENDAR,
    DIAL_NUMBER,
    COPY_TEXT,
    SHARE,
}
