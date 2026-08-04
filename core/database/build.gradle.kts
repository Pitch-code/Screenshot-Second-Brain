plugins {
    alias(libs.plugins.shelfie.android.library)
    alias(libs.plugins.shelfie.android.hilt)
    alias(libs.plugins.shelfie.android.room)
}

android {
    namespace = "com.shelfie.core.database"
}

android {
    testOptions {
        // Robolectric needs the merged manifest and resources on the test classpath.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
    api(libs.androidx.paging.runtime)

    // Queries are tested against a real in-memory SQLite database, not a fake. The
    // defects that have actually shipped from this module were wrong predicates —
    // an index_state filter on one query and not on the one beside it — and no
    // amount of pure-Kotlin testing can see those.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
}
