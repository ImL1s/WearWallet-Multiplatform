include(":wear", ":mobile", ":watchos", ":coreKmp")
include(":modules:kotlin-address", ":modules:kotlin-tx-builder", ":modules:kotlin-blockchain-client", ":modules:kotlin-secure-storage", ":modules:kotlin-utxo", ":modules:kotlin-crypto-pure", ":modules:kotlin-caip-standards")
// 純 KMP 架構 - 徹底移除 shared 模組依賴
// include(":shared")


pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // maven { url = uri("https://maven.reown.com/releases") }
        maven {
            url = uri("https://maven.pkg.github.com/trustwallet/wallet-core")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: settings.providers.gradleProperty("github.actor").orNull ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: settings.providers.gradleProperty("github.token").orNull ?: ""
            }
        }
    }
}

rootProject.name = "WearWallet"