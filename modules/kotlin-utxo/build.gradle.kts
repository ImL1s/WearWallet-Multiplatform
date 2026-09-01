plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

// Library version and metadata
group = "io.github.iml1s"
version = "1.0.0"

kotlin {
    // JVM target
    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // Android target
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.ExperimentalStdlibApi")
        }
        // Enable publishing for Android target
        publishLibraryVariants("release")
    }

    // iOS targets
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    // watchOS targets
    watchosArm64()
    watchosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test")
                implementation("org.jetbrains.kotlin:kotlin-test-common")
                implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
            }
        }

        val jvmMain by getting {
            dependsOn(commonMain)
        }

        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-junit")
            }
        }

        val androidMain by getting {
            dependsOn(commonMain)
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.13.2")
            }
        }

        // iOS source set
        val iosMain by creating {
            dependsOn(commonMain)
        }

        // Link iOS targets to iosMain
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        val iosX64Main by getting { dependsOn(iosMain) }

        // watchOS source set
        val watchosMain by creating {
            dependsOn(commonMain)
        }

        // Link watchOS targets to watchosMain
        val watchosArm64Main by getting { dependsOn(watchosMain) }
        val watchosSimulatorArm64Main by getting { dependsOn(watchosMain) }
    }
}

android {
    namespace = "io.github.iml1s.utxo"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

// Maven publishing configuration for JitPack
afterEvaluate {
    publishing {
        publications {
            // Configure KMP publications with correct artifact ID
            withType<MavenPublication>().configureEach {
                // Override the artifact ID for all publications
                val baseArtifactId = "kotlin-utxo"
                artifactId = when (name) {
                    "jvm" -> "$baseArtifactId-jvm"
                    "androidRelease" -> "$baseArtifactId-android"
                    "iosArm64" -> "$baseArtifactId-iosarm64"
                    "iosSimulatorArm64" -> "$baseArtifactId-iossimulatorarm64"
                    "iosX64" -> "$baseArtifactId-iosx64"
                    "watchosArm64" -> "$baseArtifactId-watchosarm64"
                    "watchosSimulatorArm64" -> "$baseArtifactId-watchossimulatorarm64"
                    "kotlinMultiplatform" -> baseArtifactId
                    else -> "$baseArtifactId-$name"
                }

                pom {
                    name.set("Kotlin UTXO")
                    description.set("Pure Kotlin UTXO (Unspent Transaction Output) management library for Bitcoin and UTXO-based blockchains")
                    url.set("https://github.com/iml1s/kotlin-utxo")

                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }

                    developers {
                        developer {
                            id.set("iml1s")
                            name.set("iml1s")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/iml1s/kotlin-utxo.git")
                        developerConnection.set("scm:git:ssh://github.com:iml1s/kotlin-utxo.git")
                        url.set("https://github.com/iml1s/kotlin-utxo/tree/main")
                    }
                }
            }
        }
    }
}
