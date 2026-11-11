// Vendored Compose Multiplatform "Liquid Glass (Backdrop)" — Android-only adaptation.
// Source: https://github.com/Kyant0/AndroidLiquidGlass (io.github.kyant0:backdrop)
// The upstream artifact is built with Kotlin 2.4.10 / AGP 9.3.2, which the app's
// Kotlin 2.0.21 compiler cannot read; vendoring the source lets it compile here.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kyant.backdrop"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation("androidx.annotation:annotation:1.8.2")
    implementation("org.jetbrains:annotations:24.1.0")
}
