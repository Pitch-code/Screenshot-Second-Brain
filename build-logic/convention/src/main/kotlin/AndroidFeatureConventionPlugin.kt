import com.shelfie.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * The single plugin every `:feature:*` module applies.
 *
 * It bundles library + compose + hilt config and wires the shared `:core:*`
 * dependencies exactly once. This is the mechanism that enforces the
 * architecture rule: no feature module may depend on another feature module —
 * shared code lives in `:core:*`.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("shelfie.android.library")
        pluginManager.apply("shelfie.android.compose")
        pluginManager.apply("shelfie.android.hilt")

        dependencies {
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:model"))

            add("implementation", libs.findLibrary("androidx-core-ktx").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
            add("implementation", libs.findLibrary("androidx-navigation-compose").get())
            add("implementation", libs.findLibrary("androidx-compose-material-icons-extended").get())
        }
    }
}
