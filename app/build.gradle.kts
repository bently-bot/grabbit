import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

/** Single source of truth for the version; the release workflow checks the tag against it. */
val appVersionName = "0.2.0"

/**
 * Derived from the version name (0.2.0 -> 2000, 1.4.12 -> 1004012) so every build of a
 * given version has the same code and codes only ever go up between releases. The in-app
 * updater relies on that ordering.
 */
val appVersionCode = appVersionName.split(".").map { it.toInt() }
    .let { (major, minor, patch) -> major * 1_000_000 + minor * 1_000 + patch }

/**
 * GitHub "owner/repo" whose Releases the in-app updater reads. Override with
 * `-PupdateRepo=owner/repo` (or an empty string to switch OTA off) without touching code.
 */
val updateRepo = (project.findProperty("updateRepo") as String?) ?: "bently-bot/grabbit"

android {
    namespace = "com.bentlybro.grabbit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.bentlybro.grabbit"
        // 29 so MediaStore RELATIVE_PATH works and no legacy storage permission is needed.
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("String", "UPDATE_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        create("release") {
            // Only ever supplied by CI secrets; never committed.
            val keystore = System.getenv("KEYSTORE_FILE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds are signed with a throwaway key, so they can't take release
            // updates; skip the automatic check (the Settings button still works).
            buildConfigField("Boolean", "AUTO_UPDATE_CHECK", "false")
        }
        release {
            buildConfigField("Boolean", "AUTO_UPDATE_CHECK", "true")
            // yt-dlp is driven over a process boundary and the library maps its JSON with
            // Jackson reflection, so shrinking buys little and risks a release-only crash.
            isMinifyEnabled = false
            signingConfig = if (System.getenv("KEYSTORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }

    /**
     * youtubedl-android ships Python, yt-dlp, ffmpeg and QuickJS as native libs per ABI.
     * One fat APK would be ~4x the size, so build one APK per ABI. arm64-v8a is the one
     * for any modern phone; x86_64 is for the emulator.
     */
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // The bundled python/ffmpeg are exec'd from nativeLibraryDir, so they must be
            // extracted to disk rather than left compressed inside the APK.
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        // We deliberately track the latest stable targetSdk the box toolchain has tested.
        disable += setOf("OldTargetApi", "GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // The download engine: Python + yt-dlp (+ QuickJS) and ffmpeg for merging / MP3.
    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)

    // Plain JVM tests: pure logic only, no emulator or network.
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
