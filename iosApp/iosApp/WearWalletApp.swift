import SwiftUI

@main
struct WearWalletApp: App {
    // Initialize Connectivity Manager on launch
    @StateObject private var connectivityManager = WatchConnectivityManager.shared
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(connectivityManager)
        }
    }
}
