// Top-level build file. Plugin versions come from gradle/libs.versions.toml.
buildscript {
    repositories { google() }
    dependencies {
        // AGP 8.13 bundles an R8 that cannot parse Kotlin 2.4 metadata (warning in release builds);
        // pin a newer R8 that understands it. Keep in step with Kotlin bumps.
        classpath("com.android.tools:r8:9.3.19")
    }
}
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
