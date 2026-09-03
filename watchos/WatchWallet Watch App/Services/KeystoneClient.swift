//
//  KeystoneClient.swift
//  WatchWallet Watch App
//
//  Created by IML1S
//  Protocol Witness for Keystone Service
//

import Foundation

/**
 * KeystoneClient
 *
 * A Protocol Witness that abstracts the KeystoneService operations.
 * This allows us to use proper dependency injection and mocking in tests.
 */
struct KeystoneClient {
    var initialize: () async -> Void
    var syncWalletFromiPhone: (String) async -> Void
    var isValidKeystoneQR: (String) -> Bool
    
    // State accessors
    var isInitialized: () -> Bool
    var getError: () -> String?
}

extension KeystoneClient {
    /**
     * The live implementation that interacts with the real KeystoneService.
     */
    static var live: KeystoneClient {
        let service = KeystoneService.shared
        return KeystoneClient(
            initialize: { await service.initialize() },
            syncWalletFromiPhone: { await service.syncWalletFromiPhone($0) },
            isValidKeystoneQR: { service.isValidKeystoneQR($0) },
            isInitialized: { service.isInitialized },
            getError: { service.error }
        )
    }
    
    /**
     * A mock implementation for previews and testing.
     * By default, it simulates a successful initialization and valid QR codes starting with "ur:".
     */
    static var mock: KeystoneClient {
        return KeystoneClient(
            initialize: {
                // Simulate async work
                try? await Task.sleep(nanoseconds: 100_000_000)
            },
            syncWalletFromiPhone: { _ in
                // Simulate async work
                try? await Task.sleep(nanoseconds: 100_000_000)
            },
            isValidKeystoneQR: { $0.starts(with: "ur:") },
            isInitialized: { true },
            getError: { nil }
        )
    }
    
    /**
     * A failing mock implementation for testing error scenarios.
     */
    static var failing: KeystoneClient {
        return KeystoneClient(
            initialize: { },
            syncWalletFromiPhone: { _ in },
            isValidKeystoneQR: { _ in false },
            isInitialized: { false },
            getError: { "Mock initialization error" }
        )
    }
}
