// Copyright 2026, mochi-compose contributors
// SPDX-License-Identifier: Apache-2.0
//
// 接入适配：改用主工程 version catalog；去掉只服务于库自身发布流程的 maven-publish 插件。

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ui.feice.mochi.ios"
    compileSdk = 36
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    api(project(":mochi-compose"))
    implementation(platform(libs.compose.bom))
    testImplementation(libs.junit)
}
