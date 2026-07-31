plugins {
    alias(libs.plugins.shelfie.android.library)
    alias(libs.plugins.shelfie.android.hilt)
}

android {
    namespace = "com.shelfie.core.billing"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.datastore)
    implementation(libs.androidx.core.ktx)

    // Communicates with the Play Store app over IPC, so it adds only
    // com.android.vending.BILLING and does not require INTERNET.
    implementation(libs.billing.ktx)
}
