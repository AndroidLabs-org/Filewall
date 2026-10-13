import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// keystore.properties wins; the RELEASE_* environment variables are what CI provides, where
// no properties file exists.
fun signingProperty(name: String): String? =
    keystoreProperties.getProperty(name) ?: System.getenv("RELEASE_${name.uppercase()}")

val hasReleaseKeystore = signingProperty("storeFile")?.let { file(it).exists() } == true

android {
    namespace = "org.androidlabs.filewall"
    compileSdk = 37

    defaultConfig {
        applicationId = "org.androidlabs.filewall"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Signing material is kept out of the repository. Locally, copy keystore.properties.example
    // to keystore.properties (git-ignored) and point it at your keystore; in CI the same four
    // values come from RELEASE_STOREFILE, RELEASE_STOREPASSWORD, RELEASE_KEYALIAS and
    // RELEASE_KEYPASSWORD. Without any of it the build still works: release is left unsigned
    // and debug falls back to the default debug keystore.
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(signingProperty("storeFile")!!)
                storePassword = signingProperty("storePassword")
                keyAlias = signingProperty("keyAlias")
                keyPassword = signingProperty("keyPassword")
            }
        }
        // Debug builds are signed with the release key only when a local keystore.properties
        // says so, which keeps a developer's debug installs upgradable over each other.
        if (keystorePropertiesFile.exists() && hasReleaseKeystore) {
            getByName("debug") {
                storeFile = file(signingProperty("storeFile")!!)
                storePassword = signingProperty("storePassword")
                keyAlias = signingProperty("keyAlias")
                keyPassword = signingProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // The dependency-metadata signing block is opaque to anyone reading the APK and is flagged
    // by F-Droid's scanner, so it is left out.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        // Never strip native libraries. Whether AGP strips them depends on an NDK being
        // installed, so the CI build, F-Droid and a laptop produced different bytes for
        // libdatastore_shared_counter.so. Keeping them as-is makes the APK reproducible.
        jniLibs {
            keepDebugSymbols += "**/*.so"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        disable += "NullSafeMutableLiveData"
        // Lint is a gate in CI. The baseline records the errors that were already
        // present when that gate was added — nine LocalContextGetResourceValueCall and one
        // NonObservableLocale, all from Compose lint checks newer than the code they flag — so a
        // build fails on an error a change actually introduces, not on a backlog it inherited.
        // Regenerate after fixing any of them: ./gradlew updateLintBaseline
        baseline = file("lint-baseline.xml")
    }

    androidResources {
        // The app has no values-* directories at all: every one of the 86 locales the APK
        // used to advertise came from AndroidX and Compose, so a store listing claimed
        // translations that do not exist and a non-English user got an English app. Trimmed
        // to what is real. Add a locale here when the strings for it are actually written.
        localeFilters += "en"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.runtime.livedata)
    implementation(libs.datastore.preferences)
    implementation(libs.biometric)
    implementation(libs.security.crypto)
    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.fragment.ktx)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.reorderable)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}