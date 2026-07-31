package com.shelfie.feature.onboarding

import android.os.Build

/**
 * Which permissions to request, per Android version.
 *
 * On Android 14+ both `READ_MEDIA_IMAGES` and `READ_MEDIA_VISUAL_USER_SELECTED`
 * are requested together. That is what makes the system dialog offer
 * "Select photos…" alongside "Allow all", so partial access becomes a first-class
 * outcome rather than a denial.
 */
object MediaPermission {

    const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val READ_MEDIA_VISUAL_USER_SELECTED =
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

    @Suppress("DEPRECATION")
    const val READ_EXTERNAL_STORAGE = android.Manifest.permission.READ_EXTERNAL_STORAGE

    fun requestedPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> = when {
        sdkInt >= 34 -> arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VISUAL_USER_SELECTED)
        sdkInt >= 33 -> arrayOf(READ_MEDIA_IMAGES)
        else -> arrayOf(READ_EXTERNAL_STORAGE)
    }
}
