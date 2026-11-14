plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.aboutlibraries)
}

// Signing keys come from four Gradle properties kept outside the repo. Presence is checked; values are never printed.
val signingValues = listOf("LUMI_RELEASE_STORE_FILE", "LUMI_RELEASE_STORE_PASSWORD", "LUMI_RELEASE_KEY_ALIAS", "LUMI_RELEASE_KEY_PASSWORD")
    .associateWith { providers.gradleProperty(it).orNull?.takeIf(String::isNotBlank) }
val canSign = signingValues.values.all { it != null }

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
        // From git and gradle.properties, checked in the root build script.
        versionCode = rootProject.extra["lumiVersionCode"] as Int
        versionName = rootProject.extra["lumiVersionName"] as String
        buildConfigField("String", "CHANNEL", "\"${rootProject.extra["lumiChannel"]}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    signingConfigs {
        if (canSign) create("release") {
            storeFile = file(signingValues.getValue("LUMI_RELEASE_STORE_FILE")!!)
            storePassword = signingValues.getValue("LUMI_RELEASE_STORE_PASSWORD")
            keyAlias = signingValues.getValue("LUMI_RELEASE_KEY_ALIAS")
            keyPassword = signingValues.getValue("LUMI_RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            // x86_64 for the emulator.
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
        release {
            // GeckoView adds tens of MB per ABI: ship arm64 only.
            ndk { abiFilters += listOf("arm64-v8a") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (canSign) signingConfigs.getByName("release") else null
        }
    }
}

// One clear line when the keys are missing; the unsigned APK is still built. A task with no outputs runs every time,
// so the line also shows when packaging is up to date, and on every release path (assemble, install, bundle).
val warnUnsignedRelease by tasks.registering {
    val unsigned = !canSign
    onlyIf { unsigned }
    doLast { logger.warn("WARNING: LUMI_RELEASE_* signing properties are missing; building an UNSIGNED release APK.") }
}
tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }.configureEach { dependsOn(warnUnsignedRelease) }

// The root CHANGELOG.md ships as assets/CHANGELOG.md, the source of the About screen's "What's new".
abstract class CopyChangelog : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val source: RegularFileProperty
    @get:OutputDirectory abstract val outDir: DirectoryProperty
    @TaskAction fun run() { source.get().asFile.copyTo(outDir.file("CHANGELOG.md").get().asFile, overwrite = true) }
}

androidComponents {
    onVariants { variant ->
        val copy = tasks.register<CopyChangelog>("copyChangelog${variant.name.replaceFirstChar(Char::uppercase)}") {
            source.set(rootProject.layout.projectDirectory.file("CHANGELOG.md"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(copy, CopyChangelog::outDir)
    }
}

kotlin {
    jvmToolchain(21)
}

aboutLibraries {
    // Material Symbols are copied into res/drawable, not a dependency, so they are listed by hand.
    collect.configPath = file("config")
}

// Some unit tests read the manifest, resources and Kotlin sources from the source tree, so they are inputs of the unit tests.
tasks.withType<Test>().configureEach {
    inputs.file("src/main/AndroidManifest.xml").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/res").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/java").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("CHANGELOG.md")).withPathSensitivity(PathSensitivity.RELATIVE)
    // ReleaseBuildPinsTest reads both build scripts.
    inputs.file("build.gradle.kts").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.file("build.gradle.kts")).withPathSensitivity(PathSensitivity.RELATIVE)
    // LicenseCompletenessTest reads the debug license export: generate it first and rerun the tests when it changes.
    inputs.files(tasks.named("prepareLibraryDefinitionsDebug")).withPathSensitivity(PathSensitivity.RELATIVE)
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
    testImplementation(libs.json)
}
