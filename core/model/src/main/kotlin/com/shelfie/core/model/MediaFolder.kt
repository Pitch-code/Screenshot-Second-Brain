package com.shelfie.core.model

/**
 * An image folder on the device that the user can choose to have read.
 *
 * ### Why this is not a permission problem
 *
 * Shelfie already queries every image on the device — the MediaStore query carries
 * no folder restriction. Chat app media lives under `Android/media/...`, which
 * Android's media scanner does index, so WhatsApp, Telegram and Messenger images are
 * already *visible*. They are discarded afterwards by the screenshot heuristics.
 *
 * So letting someone search their WhatsApp images is a matter of not throwing them
 * away, not of asking for `MANAGE_EXTERNAL_STORAGE`. That permission is restricted
 * by Google to file managers, antivirus and backup apps; a screenshot search app
 * does not qualify, would likely be rejected, and would trade away the app's
 * verifiable no-network position for access it already has.
 *
 * @param name the folder's display name, e.g. "Screenshots", "WhatsApp Images".
 *   Doubles as the stored key, so a saved preference is self-describing.
 * @param imageCount how many images it holds. Shown in the picker because the
 *   difference between ticking a 150-image folder and a 5,000-image one is hours of
 *   background work, and the user deserves to know that before choosing.
 */
data class MediaFolder(
    val name: String,
    val imageCount: Int,
) {
    /** Case-insensitive key used for matching and storage. */
    val key: String get() = normaliseKey(name)

    companion object {
        fun normaliseKey(name: String): String = name.trim().lowercase()
    }
}
