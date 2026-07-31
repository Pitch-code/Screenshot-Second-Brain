package com.shelfie.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Central accessor for the shared version catalog. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun Project.catalogVersion(alias: String): String =
    libs.findVersion(alias).get().requiredVersion

fun Project.catalogVersionInt(alias: String): Int = catalogVersion(alias).toInt()

/**
 * Single source of truth for the JVM level used across every module.
 * Kept at 17 so the project builds on any JDK 17+ without toolchain provisioning.
 */
val JAVA_VERSION: JavaVersion = JavaVersion.VERSION_17

/** Applies the shared Kotlin compiler configuration to a pure JVM module. */
fun Project.configureKotlinJvmCompiler() {
    extensions.getByType<KotlinJvmProjectExtension>().apply {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(providers.gradleProperty("shelfie.warningsAsErrors").isPresent)
        }
    }
}
