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


}

rootProject.name = "kotlin-tx-builder"

includeBuild("../kotlin-crypto-pure")
includeBuild("../kotlin-address")
