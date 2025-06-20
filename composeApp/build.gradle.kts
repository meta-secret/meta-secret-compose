import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "2.1.21-RC"
    alias(libs.plugins.swiftklib)
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Metasecret"
            isStatic = true
            binaryOption("bundleId", "metasecret.project.com")
        }

        iosTarget.compilations {
            val main by getting {
                cinterops {
                    create("SwiftBridge")
                }
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.ui.android)
            implementation(libs.androidx.ui.graphics.android)
            implementation(libs.accompanist.pager)
            implementation(libs.accompanist.pager.indicators)
            implementation(libs.androidx.core)
            implementation(libs.androidx.biometric)
            runtimeOnly(libs.androidx.ui)
        }

        iosMain.dependencies {
            // iOS-specific dependencies
        }

        commonMain.dependencies {
            implementation(kotlin("stdlib"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            
            implementation(project.dependencies.platform("io.insert-koin:koin-bom:3.6.0-wasm-alpha2"))
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
        }
    }
}

android {
    namespace = "metasecret.project.com"
    compileSdk = 35

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    sourceSets["main"].resources.srcDirs("src/commonMain/composeResources")
    sourceSets["main"].res.srcDirs("src/androidMain/res")


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
    implementation(libs.kotlinx.coroutines.core.v164)
    implementation(libs.androidx.foundation.layout.android)
    implementation(libs.androidx.lifecycle.livedata.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.glance)
    implementation(libs.ui.android)
    implementation(libs.androidx.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.runtime.android)
    implementation(libs.androidx.compose.material.core)
    runtimeOnly(libs.androidx.runtime)
}

swiftklib {
    create("SwiftBridge") {
        path = file("../iosApp/iosApp/MetaSecretCoreService/")
        packageName("com.metaSecret.ios")
    }
}