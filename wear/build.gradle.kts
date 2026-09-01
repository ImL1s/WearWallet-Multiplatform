import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.kotlinx.kover")
    // Firebase plugins
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}

// ULTRATHINK 清理：移除平台特定依賴配置
// KMP 架構統一管理所有網絡依賴

android {
    namespace = "com.cbstudio.wearwallet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cbstudio.wearwallet"
        minSdk = 30
        targetSdk = 34
        versionCode = 4
        versionName = "1.0.3"
        multiDexEnabled = true
        
        testInstrumentationRunner = "com.cbstudio.wearwallet.WearTestRunner"
        
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // API Keys - These will be replaced from local.properties or environment variables
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        
        buildConfigField("String", "INFURA_PROJECT_ID", 
            "\"${localProperties.getProperty("infura.project.id", System.getenv("INFURA_PROJECT_ID") ?: "YOUR_INFURA_PROJECT_ID")}\"")
        buildConfigField("String", "ETHERSCAN_API_KEY", 
            "\"${localProperties.getProperty("etherscan.api.key", System.getenv("ETHERSCAN_API_KEY") ?: "YOUR_ETHERSCAN_API_KEY")}\"")
        buildConfigField("String", "MORALIS_API_KEY", 
            "\"${localProperties.getProperty("moralis.api.key", System.getenv("MORALIS_API_KEY") ?: "YOUR_MORALIS_API_KEY")}\"")
        buildConfigField("String", "GOOGLE_AI_API_KEY", 
            "\"${project.findProperty("GOOGLE_AI_API_KEY") ?: System.getenv("GOOGLE_AI_API_KEY") ?: "YOUR_GEMINI_API_KEY"}\"")
    }

    signingConfigs {
        if (project.hasProperty("WEARWALLET_STORE_FILE")) {
            create("release") {
                storeFile = file(project.findProperty("WEARWALLET_STORE_FILE") as String)
                storePassword = project.findProperty("WEARWALLET_STORE_PASSWORD") as String
                keyAlias = project.findProperty("WEARWALLET_KEY_ALIAS") as String
                keyPassword = project.findProperty("WEARWALLET_KEY_PASSWORD") as String
            }
        }
    }

    buildTypes {
        debug {
            // 啟用 Monero 實體機測試功能
            buildConfigField("Boolean", "ENABLE_MONERO_DEBUG", "true")
            // 保留 JNI 符號以便調試
            isJniDebuggable = true
            // 禁用混淆以便調試
            isMinifyEnabled = false
            // 啟用調試日誌
            buildConfigField("Boolean", "DEBUG_LOGGING", "true")
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // 2025-10-28: 啟用 ProGuard 混淆以提升安全性
            // Firebase Vertex AI 相容性問題已透過專用 ProGuard 規則解決
            // 安全評分目標: 從 1.1/10 提升至 6.5/10 (+490%)
            isMinifyEnabled = true             // ✅ 啟用混淆
            isShrinkResources = true           // ✅ 啟用資源收縮
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-firebase-ai.pro"     // ✅ Firebase AI 專用規則
            )
            // 禁用 Monero 測試功能
            buildConfigField("Boolean", "ENABLE_MONERO_DEBUG", "false")
            buildConfigField("Boolean", "DEBUG_LOGGING", "false")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    
    kotlin {
        jvmToolchain(17)
        
        // Kotlin 編譯優化 (2025 最佳實踐)
        compilerOptions {
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi", 
                "-opt-in=androidx.wear.compose.material3.ExperimentalWearMaterial3Api",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-Xjvm-default=all",
                "-Xcontext-receivers",
                "-Xbackend-threads=4" // 加速編譯
            )
        }
    }
    
    buildFeatures {
        compose = true
        buildConfig = true
        // 禁用不需要的功能以加速編譯
        aidl = false
        renderScript = false
        shaders = false
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
    
    // 資源優化 (已移至 buildTypes.release 中的 isMinifyEnabled 和 isShrinkResources)
    // DEX 優化 (現在由 AGP 自動處理，無需手動配置)
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            // 修復 Apache HttpComponents 依賴衝突
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
            excludes += "META-INF/license.txt"
            excludes += "META-INF/NOTICE"
            excludes += "META-INF/NOTICE.txt"
            excludes += "META-INF/notice.txt"
            excludes += "META-INF/ASL2.0"
            // 修復 Jakarta 依賴衝突
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
    
    // Test 配置 - Mock Android 框架類別
    testOptions {
        unitTests {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all {
                it.exclude("**/FullStackInfraTest.class")
            }
        }
    }
    
    
    // Lint 配置 - 修復 kapt 生成文件問題
    lint {
        checkGeneratedSources = false
        abortOnError = false
        checkReleaseBuilds = false
        quiet = true
        ignoreWarnings = true
        // 禁用不必要的檢查以加速構建
        disable += setOf(
            "Typos", "TypographyFractions", "TypographyDashes",
            "SmallSp", "UnusedResources", "IconDensities",
            "IconDuplicates", "IconLocation", "VectorDrawableCompat"
        )
    }
    
    // 增量編譯優化
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            // 增量編譯設置
            freeCompilerArgs.addAll(
                "-Xbackend-threads=8",
                "-Xinline-classes",
                "-Xopt-in=kotlin.contracts.ExperimentalContracts"
            )
        }
    }
}

dependencies {
    // 使用 coreKmp 作為主要業務邏輯模組
    implementation(project(":coreKmp"))
    // sharedKmp 依賴已移除 - 完全遷移到 coreKmp
    // implementation(project(":sharedKmp"))
    
    // TrustWallet Core for Android - 確保原生庫 libTrustWalletCore.so 正確打包
    implementation("com.trustwallet:wallet-core:4.1.17")
    
    // Kotlinx DateTime for cross-platform date/time handling
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.wear.remote.interactions)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.tiles)
    implementation(libs.tiles.material)
    implementation(libs.horologist.compose.tools)
    implementation(libs.horologist.tiles)
    implementation(libs.watchface.complications.data.source.ktx)
    implementation(libs.compose.material3)
    
    // Wear OS 6 Material 3 Expressive Design (2025 最新版本)
    implementation("androidx.wear.compose:compose-material3:1.5.0-beta03") // 最新 2025 版本 - EdgeButton、ButtonGroup、動態色彩主題
    implementation("androidx.wear.compose:compose-foundation:1.4.0-alpha10") // 2025 增強基礎組件 - TransformingLazyColumn 支援
    implementation("androidx.wear.compose:compose-navigation:1.4.0-alpha10") // 2025 導航支援
    implementation("androidx.wear.protolayout:protolayout-material3:1.3.0-alpha02") // ProtoLayout Material 3 for Tiles
    // Removed conflicting adaptive libraries - using Wear OS specific Material 3 instead
    // implementation("androidx.compose.material3:material3-adaptive:1.0.0-alpha05") // 適應性佈局  
    // implementation("androidx.compose.material3:material3-adaptive-navigation-suite:1.4.0-alpha02") // 適應性導航
    implementation("androidx.compose.animation:animation-graphics:1.7.5") // 圖形動畫
    implementation("androidx.profileinstaller:profileinstaller:1.4.1") // Baseline Profiles 支援
    implementation(libs.runtime)
    implementation(libs.material.icons.core)
    implementation(libs.material.icons.extended)
    
    // Logging
    implementation(libs.timber)
    
    // WorkManager - 移除 Hilt Work，統一使用 Koin
    implementation(libs.work.runtime.ktx)
    implementation(libs.guava)

    implementation(libs.androidx.security.crypto)
    implementation(libs.material)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3.android)
    implementation(libs.zxing.android.embedded)
    
    // Google Play Billing for subscription
    implementation("com.android.billingclient:billing-ktx:7.1.1")
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-perf-ktx")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")
    // Firebase Vertex AI (現有版本) - KMP 架構統一管理網絡依賴
    implementation(libs.firebase.vertexai)
    // Firebase App Check - 保護後端服務免受濫用
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    implementation("com.google.firebase:firebase-appcheck-debug")
    
    // AI 系統級整合依賴項
    // 生物識別驗證
    implementation("androidx.biometric:biometric:1.1.0")
    
    // OkHttp for WebSocket connections (Gemini Live API)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // JSON 處理
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // 音頻處理
    implementation("androidx.media:media:1.7.0")
    
    // ULTRATHINK Phase 11: Gemini AI SDK for advanced features
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
    
    // KMP BigDecimal 支援 - 統一數值處理
    implementation("com.ionspin.kotlin:bignum:0.3.10")
    
    // Ktor client - coreKmp 的 RPC/Explorer 服務需要這些依賴在 runtime 可用
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)

    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
    
    // Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.kotlin.test)
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("io.insert-koin:koin-test:3.5.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("androidx.paging:paging-testing:3.2.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    testImplementation("androidx.work:work-testing:2.9.0")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
    
    // Koin testing dependencies - 替代 Hilt
    testImplementation("io.insert-koin:koin-test:4.0.1")
    testImplementation("io.insert-koin:koin-test-junit4:4.0.1")
    
    // Android Test dependencies for integration tests
    androidTestImplementation(libs.junit)
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.0")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("io.mockk:mockk-android:1.13.8")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation("app.cash.sqldelight:android-driver:2.0.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    // KSP Hilt compiler 已移除，使用純 Koin DI
    
    // Core library desugaring for Java 8+ API support on older Android versions
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    
    // JetBrains annotations to fix NoClassDefFoundError in Kotlin 2.1.20
    implementation("org.jetbrains:annotations:24.1.0")
    
    // Koin dependencies for Phase 4 KMP Integration
    implementation("io.insert-koin:koin-core:4.0.1")
    implementation("io.insert-koin:koin-android:4.0.1")
    implementation("io.insert-koin:koin-androidx-compose:4.0.1")
    
    // DataStore for secure voice template storage
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    
    // Health Connect dependencies for WearFi Health Mining
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")
    
    // Coil for image loading in ENS profile components
    implementation("io.coil-kt:coil-compose:2.6.0")
}

// Test 配置 - 增加記憶體和優化測試執行
tasks.withType<Test> {
    binaryResultsDirectory.set(layout.buildDirectory.dir("test-results/binary-${name}-${System.nanoTime()}"))
    maxHeapSize = "2048m"
    jvmArgs = listOf(
        "-Xmx2048m",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=200",
        "-Dkotlinx.coroutines.debug=off"
    )
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
        showCauses = true
    }
}