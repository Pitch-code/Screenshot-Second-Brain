import com.shelfie.buildlogic.JAVA_VERSION
import com.shelfie.buildlogic.configureKotlinJvmCompiler
import com.shelfie.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * For pure-Kotlin modules with zero Android dependencies (`:core:model`, and
 * later `:core:classify`). Keeping the highest-risk logic Android-free means it
 * unit-tests on the JVM in milliseconds instead of needing a device.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
        }

        configureKotlinJvmCompiler()

        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("truth").get())
            add("testImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}
