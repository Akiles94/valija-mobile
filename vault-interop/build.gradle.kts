plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // `api`: this module's public surface exposes vault-core types (VaultReader,
            // ContextItem), so every consumer needs them on its compile classpath.
            api(project(":vault-core"))
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "dev.valija.poc.vaultinterop"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.min.sdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // arm64-v8a is the real device (G2); x86_64 is the CI emulator, which is native
            // speed on GitHub's runners and is NOT arm64 evidence.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/androidMain/cpp/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // The instrumented test needs the fixture on the device; ship it as a test-only asset so it
    // never enters the app's own APK.
    sourceSets["androidTest"].assets.srcDir(rootProject.file("vendor/golden-vault"))
}

dependencies {
    androidTestImplementation(libs.androidx.test.runner)
}
