import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
    `maven-publish`
}

group = "io.github.iml1s"
version = "1.0.0"

kotlin {
    // JVM target
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // Android target
    androidTarget {
        publishLibraryVariants("release", "debug")
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    // iOS targets
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // watchOS targets
    watchosX64()
    watchosArm64()
    watchosSimulatorArm64()

    // Source set configuration
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }

        val androidMain by getting {
            dependencies {
                // Android-specific dependencies if needed
            }
        }

        val jvmMain by getting {
            dependsOn(commonMain)
        }

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test-junit5"))
                implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
                runtimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
            }
        }

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
        }

        val watchosX64Main by getting
        val watchosArm64Main by getting
        val watchosSimulatorArm64Main by getting
        val watchosMain by creating {
            dependsOn(commonMain)
            watchosX64Main.dependsOn(this)
            watchosArm64Main.dependsOn(this)
            watchosSimulatorArm64Main.dependsOn(this)
        }
    }
}

android {
    namespace = "io.github.iml1s.caip"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = false
    }
}

// Maven publish configuration
publishing {
    publications {
        withType<MavenPublication> {
            groupId = "io.github.iml1s"
            artifactId = "kotlin-caip-standards"
            version = project.version.toString()

            pom {
                name.set("Kotlin CAIP Standards")
                description.set("Kotlin Multiplatform implementation of Chain Agnostic Improvement Proposals (CAIP) standards")
                url.set("https://github.com/iml1s/kotlin-caip-standards")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("iml1s")
                        name.set("iml1s")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/iml1s/kotlin-caip-standards.git")
                    developerConnection.set("scm:git:ssh://github.com/iml1s/kotlin-caip-standards.git")
                    url.set("https://github.com/iml1s/kotlin-caip-standards")
                }
            }
        }
    }
}
