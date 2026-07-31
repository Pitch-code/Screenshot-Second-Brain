plugins {
    alias(libs.plugins.shelfie.android.library)
    alias(libs.plugins.shelfie.android.hilt)
    alias(libs.plugins.shelfie.android.room)
}

android {
    namespace = "com.shelfie.core.database"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.paging.runtime)
}
