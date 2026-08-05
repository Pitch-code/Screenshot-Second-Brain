plugins {
    alias(libs.plugins.shelfie.android.application)
    alias(libs.plugins.shelfie.android.compose)
    alias(libs.plugins.shelfie.android.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.shelfie.app"

    defaultConfig {
        // Permanent and unchangeable once published to Play.
        applicationId = "com.shelfie.app"
        versionCode = 1
        versionName = "0.1.0"
    }

    /**
     * Optional per-ABI APKs, enabled with `-PabiSplits`.
     *
     * A universal debug APK is ~64MB because it carries the ML Kit native library
     * for all four architectures. Splitting gives a ~17MB download for a real
     * phone, which matters when installing over mobile data. Off by default so
     * local builds keep producing a single predictable artifact.
     */
    if (providers.gradleProperty("abiSplits").isPresent) {
        splits {
            abi {
                isEnable = true
                reset()
                include("arm64-v8a", "armeabi-v7a")
                // Keep a universal APK as a fallback for unusual devices.
                isUniversalApk = true
            }
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.core.database)
    implementation(projects.core.datastore)
    implementation(projects.core.media)
    implementation(projects.core.billing)

    implementation(projects.feature.shelf)
    implementation(projects.feature.search)
    implementation(projects.feature.cleanup)
    implementation(projects.feature.settings)
    implementation(projects.feature.detail)
    implementation(projects.feature.onboarding)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.splashscreen)

    // App lock. Brings androidx.fragment with it, which BiometricPrompt requires.
    implementation(libs.androidx.biometric)

    // Applies the Baseline Profile at install time on API 28+.
    implementation(libs.androidx.profileinstaller)

    // Produces the profile consumed by the release build.
    baselineProfile(projects.benchmark)

    // Required so @HiltWorker index workers can be constructed by WorkManager.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
}
