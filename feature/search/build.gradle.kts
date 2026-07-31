plugins {
    alias(libs.plugins.shelfie.android.feature)
}

android {
    namespace = "com.shelfie.feature.search"
}

dependencies {
    implementation(projects.core.media)
    implementation(libs.androidx.paging.compose)
}
