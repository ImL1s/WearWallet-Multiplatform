//
//  KMPIntegrationTests.swift
//  WatchWallet Watch AppTests
//
//  Minimal KMP integration tests that compile with the current coreKmp API.
//

import XCTest
import coreKmp
@testable import WatchWallet_Watch_App

class KMPIntegrationTests: XCTestCase {
    
    // MARK: - Test coreKmp Types Available
    
    func testCoreKmpTypesAvailable() {
        // Verify that coreKmp types can be instantiated
        // If this compiles, coreKmp is correctly linked
        
        // MultiChainType enum should be accessible
        let chainType = MultiChainType.ethereum
        XCTAssertEqual(chainType.symbol, "ETH")
        
        print("✅ coreKmp module is correctly linked and types are accessible")
    }
    
    /*
    func testTransactionPriorityEnum() {
        // Test TransactionPriority enum from coreKmp
        let priority = TransactionPriority.medium
        XCTAssertNotNil(priority)
        print("✅ TransactionPriority enum works: \(priority)")
    }
    */
    
    func testChainTypeProperties() {
        // Test MultiChainType properties
        let ethereum = MultiChainType.ethereum
        XCTAssertEqual(ethereum.symbol, "ETH")
        XCTAssertEqual(ethereum.fullName, "Ethereum")
        XCTAssertEqual(ethereum.decimals, 18)
        
        let bitcoin = MultiChainType.bitcoin
        XCTAssertEqual(bitcoin.symbol, "BTC")
        XCTAssertEqual(bitcoin.decimals, 8)
        
        print("✅ MultiChainType properties are correct")
    }
    
    // MARK: - WalletRepository Integration
    
    func testWalletRepositoryManagerExists() {
        // Check that the shared instance exists
        let manager = WalletRepositoryManager.shared
        XCTAssertNotNil(manager)
        print("✅ WalletRepositoryManager.shared is accessible")
    }
}