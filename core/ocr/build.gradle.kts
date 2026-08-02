plugins {
    alias(libs.plugins.shelfie.android.library)
    alias(libs.plugins.shelfie.android.hilt)
}

android {
    namespace = "com.shelfie.core.ocr"

    testOptions {
        // Robolectric needs the merged manifest and resources on the test classpath.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.classify)
    implementation(libs.androidx.core.ktx)

    // Bundled model: the recogniser ships inside the APK, so text recognition
    // works on first launch with no network and no Play Services model download.
    implementation(libs.mlkit.text.recognition)

    // BitmapDecoder's correctness depends on real BitmapFactory semantics
    // (inJustDecodeBounds makes decodeStream return null on success). Stubbed
    // unit tests cannot express that, so these tests need a real framework.
    testImplementation(libs.robolectric)
}
