package com.shelfie.feature.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaPermissionTest {

    @Test
    fun `android 14 and above also requests user selected access`() {
        // Requesting both is what makes the system dialog offer "Select photos",
        // so partial access becomes a real outcome instead of a denial.
        val permissions = MediaPermission.requestedPermissions(sdkInt = 34)

        assertThat(permissions).asList().containsExactly(
            MediaPermission.READ_MEDIA_IMAGES,
            MediaPermission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
    }

    @Test
    fun `android 13 requests only the images permission`() {
        // READ_MEDIA_VISUAL_USER_SELECTED does not exist before API 34 and
        // requesting it there would fail the whole request.
        assertThat(MediaPermission.requestedPermissions(sdkInt = 33)).asList()
            .containsExactly(MediaPermission.READ_MEDIA_IMAGES)
    }

    @Test
    fun `android 12 and below falls back to external storage`() {
        listOf(26, 29, 32).forEach { sdk ->
            assertThat(MediaPermission.requestedPermissions(sdkInt = sdk)).asList()
                .containsExactly(MediaPermission.READ_EXTERNAL_STORAGE)
        }
    }

    @Test
    fun `never requests write or unrelated permissions`() {
        listOf(26, 33, 34, 36).forEach { sdk ->
            MediaPermission.requestedPermissions(sdk).forEach { permission ->
                assertThat(permission).doesNotContain("WRITE")
                assertThat(permission).doesNotContain("LOCATION")
                assertThat(permission).doesNotContain("CAMERA")
            }
        }
    }
}
