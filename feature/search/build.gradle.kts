plugins {
    alias(libs.plugins.shelfie.android.feature)
}

android {
    namespace = "com.shelfie.feature.search"
}

dependencies {
    implementation(projects.core.media)
    // Folder counts come back as domain types from :core:media, but the chip model
    // and tile live in the design system.
    implementation(projects.core.database)
    implementation(libs.androidx.paging.compose)
}
