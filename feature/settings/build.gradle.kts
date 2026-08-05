plugins {
    alias(libs.plugins.shelfie.android.feature)
}

android {
    namespace = "com.shelfie.feature.settings"
}

dependencies {
    implementation(projects.core.media)
    implementation(projects.core.billing)
    implementation(projects.core.datastore)

    // Only to ask whether the device has a screen lock, so the app-lock toggle is not
    // offered on a phone that could never satisfy it. The prompt itself lives in :app.
    implementation(libs.androidx.biometric)
}
