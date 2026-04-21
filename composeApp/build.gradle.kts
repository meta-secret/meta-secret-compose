import io.gitlab.arturbosch.detekt.Detekt
import io.github.ttypic.swiftklib.gradle.task.CompileSwiftTask
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.AbstractExecutable
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "2.1.21-RC"
    alias(libs.plugins.detekt)
    id("io.github.ttypic.swiftklib")
}

kotlin {
    // BACKLOG(AGP 9+): migrate KMP + Android app to recommended multi-module layout —
    // https://kotl.in/kmp-project-structure-migration (see also ARCHITECTURE.md "KMP layout vs AGP 9+")
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Rust static libraries for iOS (from core build-ios.sh)
    val metaSecretRustIosLibsDir = rootProject.file("iosApp/Libs")
    val metaSecretRustIosDeviceLib = metaSecretRustIosLibsDir.resolve("libmetasecret_mobile-ios-arm64.a")
    val metaSecretRustIosSimulatorLib = metaSecretRustIosLibsDir.resolve("libmetasecret_mobile-ios-simulator-arm64.a")

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        val rustStaticLib = when (iosTarget.name) {
            "iosArm64" -> metaSecretRustIosDeviceLib
            "iosSimulatorArm64" -> metaSecretRustIosSimulatorLib
            else -> error("Unexpected iOS target: ${iosTarget.name}")
        }

        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "metasecret.project.com")

            // Link Rust static library (UniFFI); required for SwiftBridge / mobile_uniffi
            if (rustStaticLib.exists()) {
                linkerOpts("-force_load", rustStaticLib.absolutePath)
            } else {
                logger.warn(
                    "Rust iOS library not found at: {}. Run: ./meta-secret/mobile/scripts/build-mobile.sh ios",
                    rustStaticLib.absolutePath,
                )
            }
        }

        // Test executable (linkDebugTest*) is linked separately and does not inherit framework linkerOpts.
        iosTarget.binaries.all {
            if (name == "debugTest") {
                val testExe = this as AbstractExecutable
                if (rustStaticLib.exists()) {
                    testExe.linkerOpts("-force_load", rustStaticLib.absolutePath)
                } else {
                    logger.warn(
                        "Rust iOS library not found at: {}. iOS unit test link will fail without it. Run: ./meta-secret/mobile/scripts/build-mobile.sh ios",
                        rustStaticLib.absolutePath,
                    )
                }
            }
        }

        iosTarget.compilations {
            val main by getting {
                cinterops {
                    create("SwiftBridge")
                }
            }
        }
    }

    targets.withType<KotlinNativeTarget>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-Xoverride-konan-properties=platform.ios_simulator_arm64.version_min=14.0")
            freeCompilerArgs.add("-Xoverride-konan-properties=platform.ios_arm64.version_min=14.0")
        }
    }

    sourceSets {
        androidMain.dependencies {
            // Android requires the AAR variant so libjnidispatch.so is packaged into APK.
            // JNA 5.17+: correct libjnidispatch.so for Android 15+ 16 KB pages (arm64 JNI_OnLoad SIGSEGV with older JNA; see java-native-access/jna#1647).
            implementation("net.java.dev.jna:jna:${libs.versions.jna.get()}@aar")
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.ui.android)
            implementation(libs.accompanist.pager)
            implementation(libs.accompanist.pager.indicators)
            implementation(libs.androidx.core)
            implementation(libs.androidx.biometric)
            implementation(libs.koin.android)
            implementation(libs.koin.android.compat)
            runtimeOnly(libs.androidx.ui)
        }

        iosMain.dependencies {
            // iOS-specific dependencies
        }

        commonMain.dependencies {
            implementation(kotlin("stdlib"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            
            implementation(platform("io.insert-koin:koin-bom:${libs.versions.koin.get()}"))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.composeVM)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.tabNavigator)
            implementation(libs.voyager.transitions)

            api(libs.koin.core)
            implementation(libs.multiplatform.settings)
            implementation(libs.serialization.json)
            implementation(libs.multiplatform.settings.serialization)
            implementation(libs.coroutines.core)
            implementation(libs.settings.coroutine)
            implementation(libs.qr.kit)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
    }
}

compose.resources {
    packageOfResClass = "kotlinproject.composeapp.generated.resources"
}

android {
    namespace = "metasecret.project.com"
    compileSdk = 36

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].resources.srcDirs("src/commonMain/composeResources")
    sourceSets["main"].res.srcDirs("src/androidMain/res")
    // Native libraries are in source folder (populated by build-android.sh)
    // sourceSets["main"].jniLibs.srcDirs(project.file("src/androidMain/jniLibs")) // default location


    defaultConfig {
        applicationId = "metasecret.project.com"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "APP_VERSION", "\"${versionName}\"")

    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    dependencies {
        debugImplementation(compose.uiTooling)
    }
}

dependencies {
    implementation(libs.androidx.annotation.jvm)
    implementation(libs.coroutines.core)
    implementation(libs.androidx.foundation.layout.android)
    implementation(libs.androidx.lifecycle.livedata.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.glance)
    implementation(libs.androidx.ui.android)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.runtime.android)
    runtimeOnly(libs.androidx.runtime)
}

swiftklib {
    create("SwiftBridge") {
        path = file("../iosApp/iosApp/MetaSecretCoreService/")
        packageName("com.metaSecret.ios")
    }
}

detekt {
    buildUponDefaultConfig = true
    parallel = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
    setSource(
        files(
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/iosMain/kotlin",
        ),
    )
    jvmTarget = "11"
    exclude("**/uniffi/**")
}
