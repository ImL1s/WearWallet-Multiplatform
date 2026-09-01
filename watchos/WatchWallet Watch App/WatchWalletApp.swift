//
//  WatchWalletApp.swift
//  WatchWallet Watch App
//
//  Created by ImL1s on 2025/7/14.
//

import SwiftUI
import coreKmp

@main
struct WatchWallet_Watch_AppApp: App {
    
    init() {
        // Check for UI testing reset flag (Arguments OR Environment)
        let shouldReset = CommandLine.arguments.contains("-reset-data") || 
                          ProcessInfo.processInfo.environment["RESET_DATA"] == "true"
                          
        if shouldReset {
            print("[WatchWalletApp] 🧪 UI Testing Mode: Clearing all wallet data")
            clearAllDataForUITesting()
        }
        
        // Initialize DIContainer (which initializes KMP koin)
        _ = DIContainer.shared
        
        // Initialize Kotlin/Native bridge
        setupBridge()
        
        // Configure API keys on app startup
        configureApiKeysOnStartup()
        
        // Initialize Complication services
        initializeComplicationServices()
        
        // Initialize AI services (through iPhone relay)
        initializeAIServices()
    }
    
    /// Clear all wallet data for UI testing
    private func clearAllDataForUITesting() {
        // Clear UserDefaults
        if let bundleID = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: bundleID)
        }
        print("[WatchWalletApp] ✅ Cleared UserDefaults for UI testing")
        
        // Clear all wallets from KMP database
        let walletManager = WalletRepositoryManager.shared
        let result = walletManager.deleteAllWallets()
        switch result {
        case .success:
            print("[WatchWalletApp] ✅ Deleted all wallets for UI testing")
        case .failure(let error):
            print("[WatchWalletApp] ⚠️ Failed to delete wallets: \(error)")
        }
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
    
    // MARK: - Bridge Setup
    
    /// Setup Kotlin/Native and Keystone bridges
    private func setupBridge() {
        print("[WatchWalletApp] 🌉 Initializing Kotlin/Native bridge...")
        
        // Initialize Kotlin/Native bridge
        KotlinNativeBridge.shared.initialize()
        
        #if os(watchOS)
        // Setup WatchConnectivity via Manager
        _ = WatchConnectivityManager.shared
        #endif
        
        print("[WatchWalletApp] ✅ Bridge setup complete")
    }
    
    // MARK: - AI Services Configuration
    
    /// Initialize AI services (through iPhone relay)
    private func initializeAIServices() {
        print("[WatchWalletApp] 🤖 Initializing AI services (via iPhone relay)...")
        
        // Initialize Firebase AI service (through WatchConnectivity)
        // Note: Firebase SDK is not available on watchOS, using relay approach
        _ = FirebaseAIService()
        
        print("[WatchWalletApp] ✅ Firebase AI services initialized successfully (relay mode)")
    }
    
    // MARK: - API Key Configuration
    
    /// Configure API keys when the app starts
    private func configureApiKeysOnStartup() {
        print("[WatchWalletApp] Starting API key configuration...")
        
        // Initialize Koin for dependency injection
        // Note: Koin initialization should be done in coreKmp module
        print("[WatchWalletApp] ℹ️ Koin initialization handled by KMP shared module")
        
        // Note: API key configuration will be handled by the shared KMP module
        print("[WatchWalletApp] ℹ️ API key configuration handled by KMP shared module")
        
        // Test basic functionality
        Task {
            await testBasicFunctionality()
        }
    }
    
    
    /// Test basic Web3 functionality
    private func testBasicFunctionality() async {
        print("[WatchWalletApp] Testing basic functionality...")
        
        // Get repository from DIContainer
        if let repository = DIContainer.shared.getTokenRepository() {
            print("[WatchWalletApp] ✅ TokenRepository initialized successfully")
        } else {
            print("[WatchWalletApp] ⚠️ Failed to get TokenRepository")
            print("[WatchWalletApp] ℹ️ This is normal in development environment")
        }
        
        // Get repository from DIContainer
        if let repository = DIContainer.shared.getWalletRepository() {
            print("[WatchWalletApp] ✅ WalletRepository initialized successfully")
        } else {
            print("[WatchWalletApp] ⚠️ Failed to get WalletRepository")
            print("[WatchWalletApp] ℹ️ This is normal in development environment")
        }
    }
    
    // MARK: - Complication Services Initialization
    
    /// Initialize Complication services for Watch Face integration
    private func initializeComplicationServices() {
        print("[WatchWalletApp] 🔄 Initializing Complication services...")
        
        Task {
            do {
                // Initialize ComplicationUpdateService
                let updateService = ComplicationUpdateService.shared
                await updateService.initializeComplications()
                
                // Schedule background updates for complications
                updateService.scheduleBackgroundUpdates()
                
                print("[WatchWalletApp] ✅ Complication services initialized successfully")
                
                // Log active complications info
                let info = updateService.getActiveComplicationsInfo()
                print("[WatchWalletApp] 📊 Active complications: \(info)")
                
            } catch {
                print("[WatchWalletApp] ❌ Failed to initialize Complication services: \(error)")
            }
        }
    }
}

// MARK: - WatchKit Extensions

// Placeholder types for WatchKit background tasks
protocol WKRefreshBackgroundTask {
    func setTaskCompletedWithSnapshot(_ snapshot: Bool)
}

struct WKApplicationRefreshBackgroundTask: WKRefreshBackgroundTask {
    func setTaskCompletedWithSnapshot(_ snapshot: Bool) {}
}

struct WKSnapshotRefreshBackgroundTask: WKRefreshBackgroundTask {
    func setTaskCompleted(restoredDefaultState: Bool, estimatedSnapshotExpiration: Date, userInfo: [String: Any]?) {}
    func setTaskCompletedWithSnapshot(_ snapshot: Bool) {}
}

struct WKWatchConnectivityRefreshBackgroundTask: WKRefreshBackgroundTask {
    func setTaskCompletedWithSnapshot(_ snapshot: Bool) {}
}

// MARK: - App Extensions

extension WatchWallet_Watch_AppApp {
    // MARK: - Background Refresh Support
    
    /// Handle background refresh tasks
    func handleBackgroundRefresh(task: WKRefreshBackgroundTask) {
        print("[WatchWalletApp] 🔄 Handling background refresh task...")
        
        switch task {
        case let backgroundTask as WKApplicationRefreshBackgroundTask:
            // Handle complication data refresh
            Task {
                let updateService = ComplicationUpdateService.shared
                await updateService.handleBackgroundRefresh()
                backgroundTask.setTaskCompletedWithSnapshot(false)
            }
            
        case let snapshotTask as WKSnapshotRefreshBackgroundTask:
            // Handle snapshot refresh
            snapshotTask.setTaskCompleted(restoredDefaultState: true, estimatedSnapshotExpiration: Date.distantFuture, userInfo: nil)
            
        case let connectivityTask as WKWatchConnectivityRefreshBackgroundTask:
            // Handle connectivity refresh
            connectivityTask.setTaskCompletedWithSnapshot(false)
            
        default:
            // Complete any other tasks
            task.setTaskCompletedWithSnapshot(false)
        }
    }
}
