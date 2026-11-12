// The only module that depends on GeckoView. See docs: architecture.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "ru.digitalpunk.lumi.engine"
    compileSdk {
        // GeckoView 160 requires Android API 37.2 to compile against.
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.geckoview)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
}
