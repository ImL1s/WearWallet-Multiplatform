plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.kotlinx.kover")
    // Firebase plugins
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

android {
    namespace = "com.cbstudio.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cbstudio.wearwallet"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    // 🔧 ULTRATHINK 修復: 模擬 Android Log 以支持單元測試
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/NOTICE.md"
        }
    }
    
    // Lint 配置 - 修復 kapt 生成文件問題
    lint {
        checkGeneratedSources = false
        abortOnError = false
        checkReleaseBuilds = false
        quiet = true
        ignoreWarnings = true
    }
}

dependencies {
    // 純 KMP 架構 - 使用 coreKmp 模組
    // implementation(project(":shared"))  // shared 模組尚未設置
    // implementation(project(":sharedKmp")) // 已移除
    implementation(project(":coreKmp"))

    // Hilt 依賴注入
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp("com.google.dagger:hilt-compiler:2.55")
    
    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Paging 3 for NFT content
    implementation("androidx.paging:paging-runtime:3.2.1")
    implementation("androidx.paging:paging-compose:3.2.1")

    // Wear OS 通信
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // CameraX 核心庫
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit 條碼掃描
    implementation(libs.barcode.scanning)

    // Timber 日誌庫
    implementation(libs.timber)
    
    // Coil 圖片載入庫
    implementation("io.coil-kt:coil-compose:2.5.0")

    // Firebase AI & Services
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.vertexai)
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-perf-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    
    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Security Crypto library for encrypted preferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation(libs.androidx.webkit)

    // Keystone 3 Pro 硬體錢包支持 (和 shared 模組一致)
    implementation("com.github.KeystoneHQ:keystone-sdk-android:0.7.10")
    implementation("com.sparrowwallet:hummingbird:1.7.4")
    
    // Guava for CameraX (needed because BitcoinJ excludes it)
    implementation("com.google.guava:guava:32.1.3-android")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.mockito:mockito-android:5.8.0")
    
    // JSON library for unit tests (standalone, no Android dependency)
    testImplementation("org.json:json:20231013")
    
    // Robolectric for Android unit tests
    testImplementation("org.robolectric:robolectric:4.11.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    
    // Core library desugaring for Java 8+ API support on older Android versions
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    
    // JetBrains annotations to fix NoClassDefFoundError in Kotlin 2.1.0
    implementation("org.jetbrains:annotations:24.1.0")
    
    // Koin for KMP integration
    implementation("io.insert-koin:koin-core:4.0.0")
    implementation("io.insert-koin:koin-android:4.0.0")
    implementation("io.insert-koin:koin-androidx-compose:4.0.0")
    
    // Kotlinx datetime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
}