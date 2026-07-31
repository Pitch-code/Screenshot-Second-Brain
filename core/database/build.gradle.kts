plugins {
    alias(libs.plugins.shelfie.android.library)
    alias(libs.plugins.shelfie.android.hilt)
    alias(libs.plugins.shelfie.android.room)
}

android {
    namespace = "com.shelfie.core.database"
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.paging.runtime)
}
