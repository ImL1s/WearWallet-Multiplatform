pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }

    versionCatalogs {
        create("libs") {
            // Kotlin
            version("kotlin", "2.1.0")

            // Android
            version("androidGradlePlugin", "8.5.2")
            version("compileSdk", "35")
            version("minSdk", "26")
            version("targetSdk", "35")

            // Plugins
            plugin("kotlin-multiplatform", "org.jetbrains.kotlin.multiplatform").versionRef("kotlin")
            plugin("android-library", "com.android.library").versionRef("androidGradlePlugin")
        }
    }
}

rootProject.name = "kotlin-utxo"
