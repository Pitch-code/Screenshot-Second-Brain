plugins {
    alias(libs.plugins.shelfie.android.feature)
}

android {
    namespace = "com.shelfie.feature.onboarding"
}

dependencies {
    implementation(projects.core.media)
    implementation(projects.core.datastore)
}
