package com.shelfie.core.model

/**
 * Photo access state.
 *
 * [PARTIAL] and [DENIED] are first-class states, not errors. Google Play's
 * Photo and Video Permissions policy requires that the app remain useful when
 * broad access is withheld, so Limited Mode is a compliance requirement rather
 * than a nice-to-have.
 */
enum class MediaAccess {
    /** READ_MEDIA_IMAGES granted — full library indexing. */
    FULL,

    /** Android 14+ user-selected subset (READ_MEDIA_VISUAL_USER_SELECTED). */
    PARTIAL,

    /** No access; the user hands over images via the system photo picker. */
    DENIED,
    ;

    /** True when the app is running in the policy-mandated Limited Mode. */
    val isLimited: Boolean get() = this != FULL
}
