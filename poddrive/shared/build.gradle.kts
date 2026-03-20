import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    val xcf = XCFramework("shared")
    val iosTargets = listOf(iosX64(), iosArm64(), iosSimulatorArm64())
    iosTargets.forEach { target ->
        target.binaries.framework {
            baseName = "shared"
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Story 1.2+: SQLDelight, Ktor, etc. added here
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            // Story 1.2+
        }
        iosMain.dependencies {
            // Story 1.2+
        }
    }
}

android {
    namespace = "com.poddrive"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
