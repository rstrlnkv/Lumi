plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "ru.digitalpunk.lumi"
    compileSdk {
        // GeckoView 160 requires Android API 37.2 to compile against.
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "ru.digitalpunk.lumi"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        debug {
            // x86_64 for the emulator.
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        release {
            // GeckoView adds tens of MB per ABI: ship arm64 only.
            ndk { abiFilters += listOf("arm64-v8a") }
            isMinifyEnabled = false
        }
    }
}

kotlin {
    jvmToolchain(21)
}

// Some unit tests read the manifest and resources from the source tree, so they are inputs of the unit tests.
tasks.withType<Test>().configureEach {
    inputs.file("src/main/AndroidManifest.xml").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/res").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(project(":engine"))
    implementation(project(":data"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.material.icons.core)
    implementation(libs.biometric)

    testImplementation(libs.kotlin.test.junit)
}
