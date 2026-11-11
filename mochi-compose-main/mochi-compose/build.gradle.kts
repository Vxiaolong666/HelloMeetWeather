// Copyright 2026, mochi-compose contributors
// SPDX-License-Identifier: Apache-2.0
//
// 接入适配：改用主工程 version catalog；
// 去掉只服务于库自身贡献流程的 roborazzi / maven-publish 插件与截图测试依赖。

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ui.feice.mochi"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
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
    explicitApi()
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    api(libs.compose.ui)
    api("androidx.compose.ui:ui-graphics")
    api(libs.compose.foundation)
    // 形状：iOS 式连续曲率圆角 https://github.com/Kyant0/Capsule
    api("io.github.kyant0:capsule:2.1.3")
    // 玻璃材质抓背后的内容 https://github.com/Kyant0/AndroidLiquidGlass
    // 全工程统一使用这一份官方构件（app 也依赖同版本），避免 R8 重复类。
    api("io.github.kyant0:backdrop:1.0.6")
    // 公开 API 里用到 ImmutableList
    api("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.5.2")
    // Monet：从种子色生成 MD3 tonal 配色
    implementation("com.materialkolor:material-color-utilities:4.1.1")
    implementation(libs.activity.compose)

    testImplementation(libs.junit)
}
