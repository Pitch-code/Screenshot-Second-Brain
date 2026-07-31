package com.shelfie.core.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.shelfie.core.model.MediaAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the current photo access level.
 *
 * Never cache the result. Permissions can be revoked while the app is running,
 * and a stale "granted" value turns into a SecurityException crash. Every access
 * point re-checks.
 *
 * PARTIAL is a first-class state, not an error: on Android 14+ the user can
 * grant access to selected images only, and Play's Photo and Video Permissions
 * policy requires the app to stay useful in that case.
 */
@Singleton
class MediaAccessChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun current(): MediaAccess = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            when {
                isGranted(Manifest.permission.READ_MEDIA_IMAGES) -> MediaAccess.FULL
                isGranted(VISUAL_USER_SELECTED) -> MediaAccess.PARTIAL
                else -> MediaAccess.DENIED
            }
        }

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            if (isGranted(Manifest.permission.READ_MEDIA_IMAGES)) {
                MediaAccess.FULL
            } else {
                MediaAccess.DENIED
            }

        else ->
            @Suppress("DEPRECATION")
            if (isGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                MediaAccess.FULL
            } else {
                MediaAccess.DENIED
            }
    }

    fun canReadAnyMedia(): Boolean = current() != MediaAccess.DENIED

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        // Inlined rather than referencing the constant, so the module still
        // compiles against older compile SDKs.
        const val VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    }
}
