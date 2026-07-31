plugins {
    alias(libs.plugins.shelfie.android.feature)
}

android {
    namespace = "com.shelfie.feature.cleanup"
}

dependencies {
    implementation(projects.core.media)
}
