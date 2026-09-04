//
//  MainTabView.swift
//  WatchWallet Watch App
//
//  Main navigation tab view for the app
//

import SwiftUI
import coreKmp

struct MainTabView: View {
    @State private var selectedTab = 0
    @State private var hasWallets = false
    @State private var isLoading = true
    
    private let timer = Timer.publish(every: 1.0, on: .main, in: .common).autoconnect()

    var body: some View {
        Group {
            // ... (keep existing body structure)
            if isLoading {
                ProgressView(NSLocalizedString("loading", comment: ""))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if !hasWallets {
                NavigationStack {
                    WalletOnboardingView()
                }
                .accessibilityIdentifier("AppRoot_NoWallets")
            } else {
                TabView(selection: $selectedTab) {
                    NavigationStack {
                        WalletMainView()
                    }
                    .tag(0)
                    
                    NavigationStack {
                        WalletSettingsView()
                    }
                    .tag(1)
                }
                .tabViewStyle(.page)
                .accessibilityIdentifier("AppRoot_HasWallets")
            }
        }
        .onAppear {
            checkWallets()
        }
        .onReceive(NotificationCenter.default.publisher(for: .walletCreated)) { _ in
            print("[MainTabView] Received walletCreated notification")
            checkWallets()
        }
        .onReceive(NotificationCenter.default.publisher(for: .walletDeleted)) { _ in
            checkWallets()
        }
        .onReceive(timer) { _ in
            #if DEBUG
            if !hasWallets {
                print("[MainTabView] Polling for wallets (DEBUG)...")
                checkWallets(silent: true)
            }
            #endif
        }
    }
    
    private func checkWallets(silent: Bool = false) {
        logToDebugFile("Checking wallets... silent=\(silent)")
        if !silent {
            isLoading = true
        }
        Task {
            do {
                let walletRepository = WalletRepositoryManager.shared
                let wallets = try await walletRepository.getAllWalletsAsync()
                logToDebugFile("Found \(wallets.count) wallets")
                
                await MainActor.run {
                    hasWallets = !wallets.isEmpty
                    if !silent {
                        isLoading = false
                    }
                    logToDebugFile("hasWallets = \(hasWallets)")
                }
            } catch {
                logToDebugFile("Error checking wallets: \(error)")
                await MainActor.run {
                    hasWallets = false
                    if !silent {
                        isLoading = false
                    }
                    logToDebugFile("hasWallets = false (error)")
                }
            }
        }
    }
    
    private func logToDebugFile(_ message: String) {
        let logMessage = "\(Date()): [MainTabView] \(message)\n"
        // Try Documents directory
        if let docDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first,
           let data = logMessage.data(using: .utf8) {
            let url = docDir.appendingPathComponent("e2e_debug_log.txt")
            
            // Output path to console for debugging location
            print("LOG_PATH: \(url.path)")
            
            if FileManager.default.fileExists(atPath: url.path) {
                if let fileHandle = try? FileHandle(forWritingTo: url) {
                    fileHandle.seekToEndOfFile()
                    fileHandle.write(data)
                    try? fileHandle.close()
                }
            } else {
                try? data.write(to: url)
            }
        }
    }
}

#Preview {
    MainTabView()
}