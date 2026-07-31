pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Shelfie"

// Enables the `projects.core.model` style accessors used in module build files.
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":benchmark")

include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:classify")
include(":core:ocr")
include(":core:media")
include(":core:billing")

include(":feature:shelf")
include(":feature:search")
include(":feature:cleanup")
include(":feature:settings")
include(":feature:detail")
include(":feature:onboarding")
