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

rootProject.name = "kotlin-blockchain-client"

val cryptoPure = file("../kotlin-crypto-pure")
if (cryptoPure.exists()) {
    includeBuild(cryptoPure)
}

val txBuilder = file("../kotlin-tx-builder")
if (txBuilder.exists()) {
    includeBuild(txBuilder)
}

val utxo = file("../kotlin-utxo")
if (utxo.exists()) {
    includeBuild(utxo)
}
