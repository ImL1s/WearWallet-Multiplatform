//
//  KeystoneConnectionViewModelTests.swift
//  WatchWallet Watch AppTests
//
//  Created by IML1S
//

import XCTest
import Combine
@testable import WatchWallet_Watch_App

@MainActor
class KeystoneConnectionViewModelTests: XCTestCase {
    
    var viewModel: KeystoneConnectionViewModel!
    var mockClient: KeystoneClient!
    var syncedWalletString: String?
    
    override func setUp() {
        super.setUp()
        
        // Setup mock client
        mockClient = KeystoneClient(
            initialize: {},
            syncWalletFromiPhone: { [weak self] data in
                self?.syncedWalletString = data
            },
            isValidKeystoneQR: { $0.hasPrefix("UR:") },
            isInitialized: { true },
            getError: { nil }
        )
        
        // Initialize VM with mock client
        viewModel = KeystoneConnectionViewModel(client: mockClient)
    }
    
    override func tearDown() {
        viewModel = nil
        mockClient = nil
        syncedWalletString = nil
        super.tearDown()
    }
    
    func testCompleteConnectionWithJSON() async {
        // Arrange
        let jsonString = """
        {
            "xpub": "xpub6C...",
            "xfp": "12345678",
            "path": "m/44'/60'/0'/0/0"
        }
        """
        
        // Act
        viewModel.completeKeystoneConnection(scannedData: jsonString)
        
        // Wait for async task (since completeKeystoneConnection launches a Task)
        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5s
        
        // Assert
        XCTAssertEqual(viewModel.connectionState, .success)
        XCTAssertNotNil(syncedWalletString)
        
        // Verify synced data contains the parsed values
        if let data = syncedWalletString?.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            XCTAssertEqual(json["extendedPublicKey"] as? String, "xpub6C...")
            XCTAssertEqual(json["masterFingerprint"] as? String, "12345678")
            XCTAssertEqual(json["type"] as? String, "keystone")
        } else {
            XCTFail("Synced data is not valid JSON")
        }
    }
    
    func testCompleteConnectionWithLegacyUR() async {
        // Arrange
        let urString = "UR:CRYPTO-HDKEY/..."
        
        // Act
        viewModel.completeKeystoneConnection(scannedData: urString)
        
        // Wait for async task
        try? await Task.sleep(nanoseconds: 500_000_000)
        
        // Assert
        XCTAssertEqual(viewModel.connectionState, .success)
        XCTAssertNotNil(syncedWalletString)
        
        // Verify synced data (Legacy mock parsing logic)
        if let data = syncedWalletString?.data(using: .utf8),
           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            // Mock logic generates dummy values based on hash, just check type
            XCTAssertEqual(json["type"] as? String, "keystone")
        } else {
            XCTFail("Synced data is not valid JSON")
        }
    }
    
    func testCompleteConnectionWithInvalidData() async {
        // Arrange - Use data without spaces to avoid triggering mnemonic detection
        let invalidData = "InvalidRandomDataWithoutSpaces123"
        
        // Act
        viewModel.completeKeystoneConnection(scannedData: invalidData)
        
        // Wait for async task
        try? await Task.sleep(nanoseconds: 500_000_000)
        
        // Assert
        XCTAssertEqual(viewModel.connectionState, .error)
        XCTAssertEqual(viewModel.errorMessage, "無法識別的 QR 碼格式")
        XCTAssertNil(syncedWalletString) // Should not sync
    }

}
