// `Properties` must be IMPORTED: inside a build script `java` resolves to Gradle's JavaPluginExtension accessor,
// so a fully-qualified `java.util.Properties` fails with "Unresolved reference 'util'".
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// ─── Release signing ─────────────────────────────────────────────────────────
// Credentials live in local.properties (gitignored): KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
// They are read at BUILD time only; what ships in the APK is the signature, never the secret — so no
// buildConfigField may ever expose any of them.
val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Null on a fresh clone (or in CI) with no local.properties or no keystore: no signing config is registered
// and the release build stays unsigned instead of failing.
val keystoreFile = keystoreProps.getProperty("KEYSTORE_PATH")
    ?.let { file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.crsmthw.sheliak"
    // Compose 1.13 alphas (pulled in by the alpha BOM) refuse to build against platform 37.0; 37.1 or later is
    // required, and AGP installs platform 37.2 on its own when it is missing.
    compileSdk      = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.crsmthw.sheliak"
        minSdk        = 35
        targetSdk     = 37
        versionCode   = 1
        versionName   = "0.1.0"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile     = keystoreFile
                storePassword = keystoreProps.getProperty("KEYSTORE_PASSWORD")
                keyAlias      = keystoreProps.getProperty("KEY_ALIAS")
                keyPassword   = keystoreProps.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // findByName, not getByName: null when unconfigured, which leaves the APK unsigned instead of failing
            // the build with "SigningConfig 'release' not found".
            signingConfig     = signingConfigs.findByName("release")
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures { compose = true; buildConfig = true }
}

kotlin { jvmToolchain(21) }

// Exported schemas are committed so every migration can be checked against the schema it starts from.
room { schemaDirectory("$projectDir/schemas") }

// JVM unit tests only. Device tests stay disabled: their component wiring trips Gradle's "Project object as a
// dependency notation" deprecation from inside AGP, and the project has no instrumented tests by policy.
androidComponents {
    beforeVariants(selector().all()) { variant ->
        variant.deviceTests.forEach { (_, deviceTest) -> deviceTest.enable = false }
    }
}

// Only what the current milestone uses. Glance (the widget) waits in the catalog until its milestone.
// WorkManager (M1, the index sync) initialises itself through androidx.startup; its own consumer R8 rules keep
// the workers' constructors, and Glance's extra keep rules land with the widget (docs/BUILD.md).
dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)

    // Compose BOM + UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Adaptive layout + navigation suite
    implementation(libs.androidx.material3.navigation.suite)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.nav3)
    implementation(libs.androidx.window)

    // Navigation 3
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.nav3)
    // Declared explicitly so the predictive-back transition state is on the compile classpath.
    implementation(libs.androidx.navigationevent.compose)

    // Persistence: the merged index (Room) and settings (DataStore)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Background work: the per-source index sync
    implementation(libs.androidx.work.runtime.ktx)

    // Playback: the one ExoPlayer, its session (notification, Android Auto) and its OkHttp data source
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.media3.datasource.okhttp)

    // HTTP: the app's one base client, shared by providers and the player
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx)

    // Images and shapes
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.androidx.graphics.shapes)

    // Theme: seed colour → full tonal scheme
    implementation(libs.material.color.utilities)

    // Kotlin
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Unit tests (JVM only)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
