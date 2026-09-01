// Top-level build file optimized for KMP project build performance (2025 best practices)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.1"
    // Firebase plugins
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
    id("com.google.firebase.firebase-perf") version "1.4.2" apply false
    // Build optimization plugins
    id("org.gradle.android.cache-fix") version "3.0.1" apply false
}

// 全局配置優化
allprojects {
    // 配置所有項目的通用設置
    configurations.all {
        // 緩存動態版本 24 小時
        resolutionStrategy.cacheDynamicVersionsFor(24, "hours")
        // 緩存變更模組 24 小時
        resolutionStrategy.cacheChangingModulesFor(24, "hours")
        
        // 排除重複依賴，提高構建速度
        // 注意：不能排除 org.jetbrains:annotations，這會導致 Kotlin 2.1.0 編譯錯誤
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
}

// 子項目通用配置
subprojects {
    // 應用 Android 緩存修復插件到所有 Android 模組
    plugins.withType<com.android.build.gradle.BasePlugin> {
        apply(plugin = "org.gradle.android.cache-fix")
    }
    
    // Kotlin 編譯優化 - K2 編譯器在 Kotlin 2.0+ 中預設啟用
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            // 增量編譯配置
            freeCompilerArgs.add("-Xjvm-default=all")
            freeCompilerArgs.add("-Xsuppress-version-warnings")
            
            // 並行編譯
            freeCompilerArgs.add("-Xbackend-threads=8")
        }
    }
    
    // Android 特定優化
    plugins.withId("com.android.application") {
        configure<com.android.build.gradle.AppExtension> {
            compileOptions {
                isCoreLibraryDesugaringEnabled = true
            }
        }
        dependencies {
            "coreLibraryDesugaring"(libs.desugar.jdk.libs)
        }
    }
    
    plugins.withId("com.android.library") {
        configure<com.android.build.gradle.LibraryExtension> {
            compileOptions {
                isCoreLibraryDesugaringEnabled = true
            }
        }
        dependencies {
            "coreLibraryDesugaring"(libs.desugar.jdk.libs)
        }
    }
}

// Aggregate coverage from all project modules
dependencies {
    kover(project(":wear"))
    // 純 KMP 架構 - 只使用 sharedKmp 模組
    // kover(project(":shared"))
    kover(project(":mobile"))
    // kover(project(":sharedKmp")) // 已移除
}

// Kover configuration for aggregated code coverage
kover {
    reports {
        // Apply common filters to exclude generated code, test code, and framework code
        filters {
            excludes {
                // Android/Compose generated code
                classes("*.*BuildConfig*", "*.*R\$*", "*.*R", "*.*Test*")
                packages("dagger.hilt.*", "javax.inject.*", "androidx.*")
                // Firebase generated code
                packages("com.google.firebase.*")
                // Kotlin serialization generated code
                packages("kotlinx.serialization.*")
                // Hilt generated code
                annotatedBy("dagger.*", "*Generated*", "*Module*")
            }
        }
        
        total {
            // Configure XML report for CI integration
            xml {
                title = "WearWallet Code Coverage Report"
                onCheck = false // Don't fail build, just generate report
                xmlFile = layout.buildDirectory.file("reports/kover/xml/result.xml")
            }
            
            // Configure HTML report for local development
            html {
                title = "WearWallet Code Coverage Report"
                onCheck = false
                htmlDir = layout.buildDirectory.dir("reports/kover/html")
            }
            
            // Set up verification rules with 80% minimum coverage (2025 KMP best practice)
            verify {
                onCheck = false // Enable this in CI, disable for local development
                
                rule("Minimum Line Coverage") {
                    // Set 80% minimum line coverage threshold
                    minBound(80)
                }
                
                rule("Minimum Branch Coverage") {
                    // Set 70% minimum branch coverage threshold (typically lower than line coverage)  
                    bound {
                        minValue = 70
                        coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                        aggregationForGroup = kotlinx.kover.gradle.plugin.dsl.AggregationType.COVERED_PERCENTAGE
                    }
                }
            }
        }
    }
}

// Top-level task execution ordering optimization for KMP + AGP multi-module build
gradle.projectsEvaluated {
    val allCleanTasks = rootProject.allprojects.flatMap { p -> p.tasks.matching { it.name.lowercase().contains("clean") } }
    rootProject.allprojects.forEach { p ->
        p.tasks.configureEach {
            if (!name.lowercase().contains("clean")) {
                mustRunAfter(allCleanTasks)
            }
        }
        val compileJar = p.tasks.findByName("bundleLibCompileToJarDebug")
        val runtimeJar = p.tasks.findByName("bundleLibRuntimeToJarDebug")
        if (compileJar != null && runtimeJar != null) {
            runtimeJar.mustRunAfter(compileJar)
            runtimeJar.doLast {
                val cJar = p.layout.buildDirectory.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").get().asFile
                val rJar = p.layout.buildDirectory.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar").get().asFile
                if (cJar.exists()) {
                    cJar.copyTo(rJar, overwrite = true)
                }
            }
        }
    }
}