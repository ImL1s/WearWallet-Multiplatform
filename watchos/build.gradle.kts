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
        println("The KMP core framework is available at:")
        println("  :coreKmp:build/bin/watchosSimulatorArm64/debugFramework/coreKmp.framework")
        println("  :coreKmp:build/bin/watchosArm64/debugFramework/coreKmp.framework")
    }
}