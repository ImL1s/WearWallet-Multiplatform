// This is a placeholder build file for watchOS module
// The actual watchOS app will be built using Xcode
// This file is here to satisfy Gradle's module structure

tasks.register("buildWatchOS") {
    doLast {
        println("Building watchOS app with Xcode...")
        // Add xcodebuild commands here if needed
    }
}

tasks.register("info") {
    doLast {
        println("watchOS module - Use Xcode to build the actual watchOS app")
        println("The KMP shared framework is available at:")
        println("  :sharedKmp:build/bin/watchosSimulatorArm64/debugFramework/WearWalletShared.framework")
    }
}