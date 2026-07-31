plugins {
    alias(libs.plugins.shelfie.jvm.library)
}

dependencies {
    // Intentionally depends only on :core:model. No Android, no coroutines.
    // This is the highest-risk logic in the app, so it must be testable on the
    // JVM in milliseconds rather than needing a device.
    implementation(projects.core.model)
}
