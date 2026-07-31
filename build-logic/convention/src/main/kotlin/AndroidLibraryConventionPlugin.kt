import com.android.build.api.dsl.LibraryExtension
import com.shelfie.buildlogic.JAVA_VERSION
import com.shelfie.buildlogic.catalogVersionInt
import com.shelfie.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Baseline configuration for every Android library module.
 * Owning SDK levels, Java level and default test wiring here means no `:core:*`
 * or `:feature:*` module ever repeats them.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            compileSdk = catalogVersionInt("compileSdk")

            defaultConfig {
                minSdk = catalogVersionInt("minSdk")
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }

            compileOptions {
                sourceCompatibility = JAVA_VERSION
                targetCompatibility = JAVA_VERSION
            }

            testOptions {
                unitTests.isReturnDefaultValues = true
            }

            // Library modules must never carry their own minification config;
            // R8 runs once, at the :app level.
            buildTypes {
                release {
                    isMinifyEnabled = false
                }
            }
        }

        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("truth").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}
