import SwiftUI
import coreKmp

@main
struct WearWalletApp: App {
    init() {
        // Initialize KMP shared module
        SharedPlatformKt.platform()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}