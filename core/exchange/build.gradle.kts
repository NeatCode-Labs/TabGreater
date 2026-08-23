plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

dependencies {
    api(project(":core:model"))
    api(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // mockwebserver3 is OkHttp 5's supported MockWebServer (okhttp3.mockwebserver is a deprecated bridge).
    testImplementation(libs.okhttp.mockwebserver3)
}
