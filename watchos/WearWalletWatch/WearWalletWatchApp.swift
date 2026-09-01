//
//  WearWalletWatchApp.swift
//  WearWalletWatch
//

import SwiftUI
import WatchConnectivity

@main
struct WearWalletWatchApp: App {
    
    init() {
        // 初始化 Kotlin/Native 橋接
        setupBridge()
    }
    
    @SceneBuilder var body: some Scene {
        WindowGroup {
            NavigationView {
                ContentView()
            }
        }
    }
    
    private func setupBridge() {
        // 初始化 Kotlin/Native 橋接
        KotlinNativeBridge.shared.initialize()
        
        #if os(watchOS)
        // 設置 WatchConnectivity
        KotlinNativeBridge.shared.setupWatchConnectivity()
        
        // 初始化加密代理
        let delegate = WatchNativeCryptoDelegate.shared
        CoreKmpNativeCrypto.shared.setDelegateDelegate(delegate)
        print("WearWalletWatchApp: NativeCryptoDelegate initialized")
        #endif
        
        print("WearWalletWatchApp: Bridge setup complete")
    }
}