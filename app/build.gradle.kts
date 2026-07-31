plugins {
    alias(libs.plugins.shelfie.android.application)
    alias(libs.plugins.shelfie.android.compose)
    alias(libs.plugins.shelfie.android.hilt)
}

android {
    namespace = "com.shelfie.app"

    defaultConfig {
        // Permanent and unchangeable once published to Play.
        applicationId = "com.shelfie.app"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.datastore)

    implementation(projects.feature.shelf)
    implementation(projects.feature.search)
    implementation(projects.feature.cleanup)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.splashscreen)
}
