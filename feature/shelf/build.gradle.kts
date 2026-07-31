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
}
