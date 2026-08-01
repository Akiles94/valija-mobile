plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget()

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ComposeApp"
            // Static: the Xcode app links one framework and the vendored C comes with it.
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":vault-core"))
            implementation(project(":vault-interop"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
        }
    }
}

// androidx.activity/Compose pull in androidx.profileinstaller transitively -- a pure startup-time
// perf optimization (ahead-of-time bytecode profile installation), entirely unrelated to the app's
// own behaviour. Its manifest fragment declares a broadcast receiver and, on API 33+, AGP's
// manifest merger auto-injects a self-scoped `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_
// PERMISSION` to guard it. That permission can never be granted TO this app by anything else and
// grants no capability -- but this PoC's whole claim is "zero permissions, enforced by the
// platform" (README.md, poc.md), and a real reader should not have to know that story to trust
// the claim. Excluding the dependency keeps the promise literally true instead of documenting an
// exception to it.
configurations.configureEach {
    exclude(group = "androidx.profileinstaller", module = "profileinstaller")
}

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.valija.poc.resources"
    generateResClass = always
}

android {
    namespace = "dev.valija.poc"
    compileSdk = libs.versions.android.compile.sdk.get().toInt()

    defaultConfig {
        applicationId = "dev.valija.poc"
        minSdk = libs.versions.android.min.sdk.get().toInt()
        targetSdk = libs.versions.android.target.sdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0-poc"
    }

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
        }
    }
}
