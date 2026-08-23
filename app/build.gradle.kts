import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing: keystore.properties + tabgreater.jks live in the repo root, both git-ignored.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.neatcode.tabgreater"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.neatcode.tabgreater"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    // Two distribution tracks from one applicationId: the APKs differ only in the manifest and in
    // BuildConfig.DISTRIBUTION, so one can replace the other on a device without losing any data.
    flavorDimensions += "distribution"

    productFlavors {
        create("foss") {
            dimension = "distribution"
            isDefault = true
            buildConfigField("String", "DISTRIBUTION", "\"foss\"")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"play\"")
        }
    }

    // F-Droid rejects the Play dependency blob (a signed, unreproducible payload); Play does not
    // need it either. Off for every variant.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }

    testOptions {
        // Pure JVM unit tests may touch android.util.Log through view models; return defaults instead of throwing.
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // USE_EXACT_ALARM is allowed for a sideloaded app; Play policy lint does not apply.
        // The toolchain is locked on purpose; "newer version available" notices are noise.
        disable += setOf("ExactAlarm", "AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
        abortOnError = false
    }
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:exchange"))
    implementation(project(":core:data"))
    implementation(project(":core:live"))
    implementation(project(":feature:chart"))
    implementation(project(":widget"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

/**
 * Vendored third-party assets: the exact file, from the exact upstream release, or the build stops.
 *
 * `app/src/main/assets/chart/vendor/klinecharts.js` is copied byte-for-byte out of the KLineChart
 * npm release (see `UPSTREAM.md` beside it for the tarball URL and its registry integrity hash).
 * Checking the digest on every build turns "we vendored upstream's file" from a claim in a README
 * into something the build itself enforces: nobody can quietly patch a 674 KB dependency, and a
 * reviewer — F-Droid's included — can verify the shipped bytes against the published release.
 *
 * Update both this map and `UPSTREAM.md` when the library is upgraded.
 */
val vendoredAssets = mapOf(
    "src/main/assets/chart/vendor/klinecharts.js" to
        "44dd99a21a637abc8bd398146e23581e862ede18702890f54ce200fab5d02ca6",
)

val verifyVendoredAssets by tasks.registering {
    description = "Fails the build if a vendored third-party asset is not the upstream file it claims to be."
    group = "verification"
    // Copied into plain locals: the configuration cache cannot serialise a reference back into the
    // build script, so the action below must close over data, not over the script.
    val digests = vendoredAssets.toMap()
    val files = digests.mapValues { (path, _) -> project.file(path) }
    inputs.files(files.values)
    inputs.property("digests", digests)
    // Nothing is produced; the marker keeps the task up-to-date instead of re-hashing every build.
    val marker = layout.buildDirectory.file("tmp/verifyVendoredAssets/ok")
    outputs.file(marker)
    doLast {
        digests.forEach { (path, expected) ->
            val file = files.getValue(path)
            check(file.isFile) { "Vendored asset missing: $path" }
            val actual = MessageDigest.getInstance("SHA-256")
                .digest(file.readBytes())
                .joinToString("") { byte -> "%02x".format(byte) }
            check(actual == expected) {
                buildString {
                    appendLine("Vendored asset $path does not match its recorded upstream checksum.")
                    appendLine("  expected $expected")
                    appendLine("  actual   $actual")
                    append(
                        "If the library was upgraded on purpose, update app/build.gradle.kts and " +
                            "app/src/main/assets/chart/vendor/UPSTREAM.md together.",
                    )
                }
            }
        }
        marker.get().asFile.apply { parentFile.mkdirs() }.writeText("ok")
    }
}

tasks.named("preBuild") { dependsOn(verifyVendoredAssets) }
