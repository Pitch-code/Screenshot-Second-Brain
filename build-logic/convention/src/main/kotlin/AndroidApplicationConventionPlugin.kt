import com.android.build.api.dsl.ApplicationExtension
import com.shelfie.buildlogic.JAVA_VERSION
import com.shelfie.buildlogic.catalogVersionInt
import com.shelfie.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            compileSdk = catalogVersionInt("compileSdk")

            defaultConfig {
                minSdk = catalogVersionInt("minSdk")
                targetSdk = catalogVersionInt("targetSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }

            /*
             * Upload signing, from the environment.
             *
             * Read from environment variables rather than a checked-in
             * `keystore.properties`, so the key and its passwords live only in CI
             * secrets and never in the repository — which is public.
             *
             * Absent locally, and that is deliberate: `bundleRelease` still runs and
             * still exercises R8, producing an unsigned bundle. Being able to verify
             * that minification has not broken anything, without holding the signing
             * key, is worth more than refusing to build.
             *
             * `providers.environmentVariable` rather than `System.getenv` so the
             * configuration cache tracks the value instead of baking in whatever was
             * set the first time.
             */
            val keystorePath = providers.environmentVariable("SHELFIE_KEYSTORE_PATH").orNull
                ?.takeIf { it.isNotBlank() }

            if (keystorePath != null) {
                signingConfigs.create("upload") {
                    storeFile = file(keystorePath)
                    storePassword =
                        providers.environmentVariable("SHELFIE_KEYSTORE_PASSWORD").orNull
                    keyAlias = providers.environmentVariable("SHELFIE_KEY_ALIAS").orNull
                    keyPassword = providers.environmentVariable("SHELFIE_KEY_PASSWORD").orNull
                }
            }

            buildTypes {
                debug {
                    applicationIdSuffix = ".debug"
                    isMinifyEnabled = false
                }
                release {
                    // Null when the environment supplied no key, leaving the bundle
                    // unsigned rather than failing the build.
                    signingConfig = signingConfigs.findByName("upload")
                    // R8 full mode is enabled by default in AGP 8 with
                    // android.enableR8.fullMode; kept explicit for clarity.
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                }
            }

            // Release-equivalent but profileable, for macrobenchmark runs.
            buildTypes {
                create("benchmark") {
                    initWith(getByName("release"))
                    signingConfig = signingConfigs.getByName("debug")
                    matchingFallbacks += listOf("release")
                    isMinifyEnabled = true
                    isDebuggable = false
                }
            }

            lint {
                // Quality gates that matter for this app specifically.
                warningsAsErrors = false
                abortOnError = true
                checkDependencies = true
                // Accessibility and correctness issues must not ship.
                error += listOf(
                    "ContentDescription",
                    "ClickableViewAccessibility",
                    "TextFields",
                    "HardcodedText",
                    "SetTextI18n",
                    "UnusedResources",
                )
                // Translations are added by native speakers, not machine output,
                // so an untranslated string is a tracked gap rather than a build
                // failure.
                disable += listOf("MissingTranslation", "ExtraTranslation")
            }

            packaging {
                resources.excludes += setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/DEPENDENCIES",
                    "META-INF/LICENSE*",
                )
            }

            // Required for API 37 adaptive compliance: never lock orientation.
            // Enforced in the manifest; noted here as the owning decision point.
        }

        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("truth").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}
