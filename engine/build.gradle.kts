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

// PageEndContentScriptTest runs the extension's scripts from the source tree, so they are inputs of the unit tests.
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/assets/lumi-page-end").withPathSensitivity(PathSensitivity.RELATIVE)
}

dependencies {
    implementation(libs.geckoview)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
}
