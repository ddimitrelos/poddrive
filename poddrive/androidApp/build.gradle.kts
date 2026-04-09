plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.poddrive.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.poddrive.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Story 1.3: Spotify Client ID — set via -PSPOTIFY_CLIENT_ID=... or gradle.properties
        buildConfigField(
            "String",
            "SPOTIFY_CLIENT_ID",
            "\"${findProperty("SPOTIFY_CLIENT_ID") ?: ""}\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":shared"))

    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    // Story 1.3: Chrome Custom Tabs for OAuth + ViewModel for Compose
    implementation(libs.androidx.browser)
    implementation(libs.lifecycle.viewmodel.compose)
    // Story 1.3: expose HttpClient type used in PodDriveApplication
    implementation(libs.ktor.client.core)
    // Story 2.5: WorkManager for background library sync
    implementation(libs.androidx.work.runtime.ktx)
    // Story 4.10: Media3 session for background playback (foreground service / MediaSession)
    implementation(libs.androidx.media3.session)

    // Debug only: SLF4J Android backend so Ktor HTTP logs appear in logcat
    debugImplementation("org.slf4j:slf4j-android:1.7.36")

    debugImplementation(libs.compose.ui.tooling)

    // Story 4.6: JVM unit tests for DriveSessionViewModel (constant verification)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    // Story 4.9: kxml2 provides a real XmlPullParserFactory implementation for JVM unit tests
    // (avoids needing Robolectric to test RssParser which uses XmlPullParserFactory.newInstance())
    testImplementation("net.sf.kxml:kxml2:2.3.0")

    androidTestImplementation(libs.androidx.test.runner)
    // Story 4.11: Compose UI tests for DriveSessionScreenTest
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
