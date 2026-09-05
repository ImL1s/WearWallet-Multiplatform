// Placeholder so Gradle include(":watchos") has a project. The SwiftUI app
// is built with Xcode after linking coreKmp. Do not treat these tasks as a
// verified watchOS product build.

tasks.register("buildWatchOS") {
    doLast {
        println("watchOS app is built with Xcode, not this Gradle task.")
        println("Link the simulator framework first:")
        println("  ./gradlew :coreKmp:linkDebugFrameworkWatchosSimulatorArm64 -PpublicSnapshot=true")
        println("  or ./scripts/build-watchos.sh / watchos/build-kmp.sh")
        println("Then open watchos/WearWallet.xcworkspace after pod install.")
    }
}

tasks.register("info") {
    doLast {
        println("watchOS Gradle module is a placeholder.")
        println("KMP framework output:")
        println("  coreKmp/build/bin/watchosSimulatorArm64/debugFramework/coreKmp.framework")
        println("Do not use retired sharedKmp / WearWalletShared.framework paths.")
    }
}
