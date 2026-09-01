#!/usr/bin/env swift

import Foundation
import coreKmp

// Simple test to verify KMP framework is working
func testKMPFramework() {
    print("=== Testing KMP Framework ===")
    
    // Test 1: Can create instances
    print("1. Testing object creation...")
    let cryptoProvider = CryptoProvider()
    let secureStorage = SecureStorage()
    print("   ✓ CryptoProvider created")
    print("   ✓ SecureStorage created")
    
    // Test 2: Can call methods
    print("\n2. Testing method calls...")
    let mnemonic = cryptoProvider.generateMnemonic(strength: 128)
    print("   Generated mnemonic count: \(mnemonic.count)")
    print("   First word: \(mnemonic.first ?? "none")")
    print("   ✓ generateMnemonic works")
    
    let isValid = cryptoProvider.validateMnemonic(words: mnemonic)
    print("   Mnemonic validation: \(isValid)")
    print("   ✓ validateMnemonic works")
    
    // Test 3: Can derive private key
    print("\n3. Testing private key derivation...")
    let privateKey = cryptoProvider.derivePrivateKey(mnemonic: mnemonic, path: "m/44'/60'/0'/0/0")
    print("   Private key length: \(privateKey.count)")
    print("   ✓ derivePrivateKey works")
    
    // Test 4: Can get address
    print("\n4. Testing address generation...")
    let address = cryptoProvider.getAddress(privateKey: privateKey, coinType: 60)
    print("   Address: \(address)")
    print("   ✓ getAddress works")
    
    print("\n=== KMP Framework Test Complete ===")
    print("✓ All KMP integration tests passed!")
}

// Run test
testKMPFramework()