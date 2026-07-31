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
}
