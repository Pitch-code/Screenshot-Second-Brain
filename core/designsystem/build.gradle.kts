plugins {
    alias(libs.plugins.shelfie.android.library)
    alias(libs.plugins.shelfie.android.compose)
}

android {
    namespace = "com.shelfie.core.designsystem"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    api(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)
}
