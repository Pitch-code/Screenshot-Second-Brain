plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

/**
 * Note: this module applies `com.android.test` directly rather than one of the
 * `shelfie.*` convention plugins, so the `com.shelfie.buildlogic` helpers are not
 * on its classpath. Values are read straight from the version catalog instead.
 */
android {
    namespace = "com.shelfie.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // Macrobenchmark needs API 24+; Baseline Profile generation needs 28+.
        minSdk = 28
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    // Benchmarks must run against a release-equivalent build. Measuring a debug
    // build gives numbers that are wrong in both directions and useless for
    // enforcing a budget.
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.junit)
}
