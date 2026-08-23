plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.neatcode.tabgreater.core.live"
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
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

dependencies {
    api(project(":core:model"))
    api(project(":core:exchange"))
    // api: LiveMarketDataRepository's constructor exposes TickerSnapshotDao / MarketRepository.
    api(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
    // api: TabGreaterApp installs Koin's WorkManager factory (workManagerFactory()), so both
    // artifacts have to be on `:app`'s compile classpath.
    api(libs.androidx.work.runtime.ktx)
    api(libs.koin.androidx.workmanager)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
