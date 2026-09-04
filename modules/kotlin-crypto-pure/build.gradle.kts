plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    `maven-publish`
}

// Library version and metadata
group = "io.github.iml1s"
version = "1.0.0"

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-opt-in=kotlin.ExperimentalStdlibApi")
        }
        // Enable publishing for Android target
        publishLibraryVariants("release")
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    watchosArm64()
    watchosSimulatorArm64()
    watchosX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib")
                api("org.kotlincrypto.hash:sha3:0.5.3")
                api("org.kotlincrypto.hash:sha2:0.5.3")
                api("com.ionspin.kotlin:bignum:0.3.9")
                api("io.github.andreypfau:curve25519-kotlin:0.0.8")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.core:core-ktx:1.13.1")
                api("fr.acinq.secp256k1:secp256k1-kmp:0.19.0")
                api("fr.acinq.secp256k1:secp256k1-kmp-jni-android:0.19.0")
                api("org.bouncycastle:bcprov-jdk18on:1.78.1")
                api("com.trustwallet:wallet-core:4.0.27")
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.13.2")
            }
        }

        // iOS source set - includes secp256k1-kmp (has native iOS bindings)
        val iosMain by creating {
            dependsOn(commonMain)
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                api("fr.acinq.secp256k1:secp256k1-kmp:0.19.0")
            }
        }

        // Link iOS targets to iosMain
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }
        val iosX64Main by getting { dependsOn(iosMain) }

        // watchOS source set - no secp256k1-kmp (no watchOS bindings available)
        // Uses pure Kotlin Secp256k1Pure instead
        val watchosMain by creating {
            dependsOn(commonMain)
             dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
            }
        }

        // Link watchOS targets to watchosMain
        val watchosArm64Main by getting { dependsOn(watchosMain) }
        val watchosSimulatorArm64Main by getting { dependsOn(watchosMain) }
        val watchosX64Main by getting { dependsOn(watchosMain) }
    }
}

android {
    namespace = "io.github.iml1s.crypto"
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
                val baseArtifactId = "kotlin-crypto-pure"
                artifactId = when (name) {
                    "androidRelease" -> "$baseArtifactId-android"
                    "iosArm64" -> "$baseArtifactId-iosarm64"
                    "iosSimulatorArm64" -> "$baseArtifactId-iossimulatorarm64"
                    "iosX64" -> "$baseArtifactId-iosx64"
                    "watchosArm64" -> "$baseArtifactId-watchosarm64"
                    "watchosSimulatorArm64" -> "$baseArtifactId-watchossimulatorarm64"
                    "watchosX64" -> "$baseArtifactId-watchosx64"
                    "kotlinMultiplatform" -> baseArtifactId
                    else -> "$baseArtifactId-$name"
                }

                pom {
                    name.set("Kotlin Crypto Pure")
                    description.set("Pure Kotlin cryptographic library for multiplatform (Android, iOS, watchOS)")
                    url.set("https://github.com/iml1s/kotlin-crypto-pure")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("iml1s")
                            name.set("iml1s")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/iml1s/kotlin-crypto-pure.git")
                        developerConnection.set("scm:git:ssh://github.com:iml1s/kotlin-crypto-pure.git")
                        url.set("https://github.com/iml1s/kotlin-crypto-pure/tree/main")
                    }
                }
            }
        }
    }
}
