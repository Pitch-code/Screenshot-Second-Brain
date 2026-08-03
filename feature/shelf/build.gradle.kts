plugins {
    alias(libs.plugins.shelfie.android.feature)
}

android {
    namespace = "com.shelfie.feature.shelf"
}

dependencies {
    // Feature modules may depend on :core:* but never on another :feature:*.
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.media)
    // The unlock prompt lives on the shelf, so the purchase flow is needed here too.
    implementation(projects.core.billing)
    implementation(libs.androidx.paging.compose)
}
