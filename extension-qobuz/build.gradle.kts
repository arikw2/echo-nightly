plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlinx.serialization)
}

android {
    namespace = "dev.brahmkshatriya.echo.extension.qobuz"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.brahmkshatriya.echo.extension.qobuz"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // Extensions must not be minified — the app loads them via reflection
            isMinifyEnabled = false
        }
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    // All compileOnly: these are already present in Echo's parent classloader at runtime.
    // The extension APK only needs them for compilation.
    compileOnly(project(":common"))
    compileOnly(libs.okhttp)
    compileOnly(libs.kotlinx.serialization.json)
    compileOnly(libs.kotlinx.coroutines.core)
}
