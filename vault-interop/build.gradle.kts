import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

/**
 * One static library per Apple target, built from the vendored C by `build-apple-native.sh`.
 * The script is the single implementation; Gradle only chooses the SDK and triple, so the same
 * build can be reproduced by hand on the Mac.
 */
val appleTargets = mapOf(
    "iosArm64" to Pair("iphoneos", "arm64-apple-ios15.0"),
    "iosSimulatorArm64" to Pair("iphonesimulator", "arm64-apple-ios15.0-simulator"),
)

fun nativeOutDir(targetName: String) =
    layout.buildDirectory.dir("native/$targetName").get().asFile

val appleNativeTasks = appleTargets.map { (targetName, sdkAndTriple) ->
    tasks.register<Exec>("buildAppleNative${targetName.replaceFirstChar { it.uppercase() }}") {
        group = "build"
        description = "Compiles the vendored sqlite3mc + argon2 into libvalijanative.a ($targetName)"
        onlyIf { org.gradle.internal.os.OperatingSystem.current().isMacOsX }
        inputs.files(
            rootProject.file("vendor/sqlite3mc/sqlite3.c"),
            rootProject.file("gradle/native-defines.txt"),
            rootProject.file("gradle/argon2-defines.txt"),
        )
        outputs.file(File(nativeOutDir(targetName), "libvalijanative.a"))
        commandLine(
            "bash", file("build-apple-native.sh").absolutePath,
            sdkAndTriple.first, sdkAndTriple.second,
            nativeOutDir(targetName).absolutePath, rootProject.projectDir.absolutePath,
        )
    }
}

kotlin {
    androidTarget()
    iosArm64()
    iosSimulatorArm64()

    targets.withType<KotlinNativeTarget>().configureEach {
        val nativeTaskName = "buildAppleNative${targetName.replaceFirstChar { it.uppercase() }}"
        compilations.getByName("main") {
            cinterops.create("sqlite3mc") {
                defFile(project.file("src/nativeInterop/cinterop/sqlite3mc.def"))
                includeDirs(rootProject.file("vendor/sqlite3mc"))
            }
            cinterops.create("argon2") {
                defFile(project.file("src/nativeInterop/cinterop/argon2.def"))
                includeDirs(rootProject.file("vendor/argon2/include"))
            }
        }
        // The archive is resolved at the final link (framework or test executable) rather than
        // embedded in each klib, so the same objects are never linked twice. If the symbols
        // ever fail to survive into the Compose framework link, plan.md D-6's named fallback is
        // to add the C to the Xcode target's own Compile Sources -- and to record the switch.
        binaries.all {
            linkerOpts("-L${nativeOutDir(targetName).absolutePath}", "-lvalijanative")
        }
        compilations.getByName("main").compileTaskProvider.configure {
            dependsOn(nativeTaskName)
        }
    }

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

// The simulator shares the host filesystem, so the Kotlin/Native test reads the committed
// fixture by absolute path. See IosVaultConformanceTest's own scope note: this is a
// simulator-only check, and the physical-device evidence comes from the app.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>()
    .configureEach {
        environment("VALIJA_FIXTURES", rootProject.file("vendor/golden-vault").absolutePath)
    }
