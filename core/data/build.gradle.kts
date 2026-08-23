plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.neatcode.tabgreater.core.data"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // CacheMaintenance and the sparkline repository log with android.util.Log; JVM unit tests
    // must not blow up on the unmocked stubs.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    api(project(":core:exchange"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // mockwebserver3 is OkHttp 5's supported MockWebServer (okhttp3.mockwebserver is a deprecated
    // bridge); it backs the CoinGecko popular-pairs source test.
    testImplementation(libs.okhttp.mockwebserver3)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
}
