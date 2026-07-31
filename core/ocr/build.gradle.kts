plugins {
    alias(libs.plugins.shelfie.android.library)
    alias(libs.plugins.shelfie.android.hilt)
}

android {
    namespace = "com.shelfie.core.ocr"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.classify)
    implementation(libs.androidx.core.ktx)

    // Bundled model: the recogniser ships inside the APK, so text recognition
    // works on first launch with no network and no Play Services model download.
    implementation(libs.mlkit.text.recognition)
}
