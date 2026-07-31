plugins {
    alias(libs.plugins.shelfie.android.library)
    alias(libs.plugins.shelfie.android.hilt)
}

android {
    namespace = "com.shelfie.core.media"
}

dependencies {
    // api so feature modules consuming the repository also see the model and
    // DAO types it exposes, without each of them re-declaring the dependency.
    api(projects.core.model)
    api(projects.core.database)
    implementation(projects.core.datastore)
    api(projects.core.classify)
    implementation(projects.core.ocr)

    implementation(libs.androidx.core.ktx)
    api(libs.androidx.paging.runtime)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
