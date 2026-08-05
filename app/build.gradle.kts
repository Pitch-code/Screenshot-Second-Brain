plugins {
    alias(libs.plugins.shelfie.android.application)
    alias(libs.plugins.shelfie.android.compose)
    alias(libs.plugins.shelfie.android.hilt)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.shelfie.app"

    defaultConfig {
        /*
         * Permanent and unchangeable once published to Play.
         *
         * Not `com.shelfie.app`: that identifier is already taken on Play by an
         * unrelated app, and identifiers are global and first-come. Prefixed with the
         * developer name instead, which is the convention precisely because it is far
         * less likely to collide than a bare product name.
         *
         * Deliberately different from `namespace` above, which stays `com.shelfie.app`.
         * The namespace only determines the Kotlin package and the generated `R` class,
         * so leaving it alone avoids renaming every source directory for no benefit.
         * The two being different is normal and supported.
         */
        applicationId = "com.pitchcode.shelfie"
        /*
         * Both overridable from the command line, e.g. `-PversionCode=2`.
         *
         * Play refuses an upload whose version code is not higher than every code
         * already uploaded, and that includes uploads that were later discarded. So
         * bumping it is a routine, frequent act — and requiring a commit and a merge
         * for each one turns a one-word change into a pull request.
         *
         * The checked-in values stay the source of truth for what a plain build
         * produces; the properties only override them for a release run.
         */
        versionCode = providers.gradleProperty("versionCode").orNull?.toIntOrNull() ?: 1

        // What users see on the store page. 1.0.0 rather than 0.1.0: a leading zero
        // reads as unfinished, and this is the version going to the public.
        versionName = providers.gradleProperty("versionName").orNull ?: "1.0.0"
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
