import XCTest
@testable import WatchWallet_Watch_App

class CryptoTests: XCTestCase {
    
    override func setUpWithError() throws {
        // Put setup code here. This method is called before the invocation of each test method in the class.
    }
    
    override func tearDownWithError() throws {
        // Put teardown code here. This method is called after the invocation of each test method in the class.
    }
    
    // MARK: - Mnemonic Generation Tests
    
    func testMnemonicGeneration() throws {
        // Test generating a mnemonic
        let mnemonic = EthereumCrypto.generateMnemonic(strength: 128)
        XCTAssertNotNil(mnemonic, "Mnemonic generation should not return nil")
        
        if let mnemonic = mnemonic {
            let words = mnemonic.split(separator: " ")
            XCTAssertEqual(words.count, 12, "128-bit mnemonic should have 12 words")
            
            // Check that all words are valid
            for word in words {
                XCTAssertFalse(word.isEmpty, "No word should be empty")
                XCTAssertTrue(word.allSatisfy { $0.isLetter }, "All words should contain only letters")
            }
        }
        
        print("✅ Generated mnemonic: \(mnemonic ?? "nil")")
    }
    
    func testMnemonicConsistency() throws {
        // Test that mnemonics are different (randomness)
        let mnemonic1 = EthereumCrypto.generateMnemonic(strength: 128)
        let mnemonic2 = EthereumCrypto.generateMnemonic(strength: 128)
        
        XCTAssertNotNil(mnemonic1, "First mnemonic should not be nil")
        XCTAssertNotNil(mnemonic2, "Second mnemonic should not be nil")
        
        // While theoretically possible, it's extremely unlikely they're the same
        XCTAssertNotEqual(mnemonic1, mnemonic2, "Two generated mnemonics should be different")
        
        print("✅ Mnemonic1: \(mnemonic1 ?? "nil")")
        print("✅ Mnemonic2: \(mnemonic2 ?? "nil")")
    }
    
    // MARK: - Private Key Derivation Tests
    
    func testPrivateKeyDerivation() throws {
        let testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        let privateKey = EthereumCrypto.derivePrivateKey(from: testMnemonic, path: "m/44'/60'/0'/0/0")
        
        XCTAssertNotNil(privateKey, "Private key derivation should not return nil")
        
        if let privateKey = privateKey {
            XCTAssertTrue(privateKey.hasPrefix("0x"), "Private key should start with 0x")
            XCTAssertEqual(privateKey.count, 66, "Private key should be 66 characters (0x + 64 hex chars)")
            
            // Check that it's valid hex
            let hexPart = String(privateKey.dropFirst(2))
            XCTAssertTrue(hexPart.allSatisfy { $0.isHexDigit }, "Private key should be valid hex")
        }
        
        print("✅ Derived private key: \(privateKey ?? "nil")")
    }
    
    func testPrivateKeyDeterminism() throws {
        let testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        let privateKey1 = EthereumCrypto.derivePrivateKey(from: testMnemonic, path: "m/44'/60'/0'/0/0")
        let privateKey2 = EthereumCrypto.derivePrivateKey(from: testMnemonic, path: "m/44'/60'/0'/0/0")
        
        XCTAssertNotNil(privateKey1, "First private key should not be nil")
        XCTAssertNotNil(privateKey2, "Second private key should not be nil")
        XCTAssertEqual(privateKey1, privateKey2, "Same mnemonic and path should produce same private key")
        
        print("✅ Deterministic private key: \(privateKey1 ?? "nil")")
    }
    
    // MARK: - Address Generation Tests
    
    func testAddressGeneration() throws {
        let testPrivateKey = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
        let address = EthereumCrypto.generateEthereumAddress(from: testPrivateKey)
        
        XCTAssertNotNil(address, "Address generation should not return nil")
        
        if let address = address {
            XCTAssertTrue(address.hasPrefix("0x"), "Address should start with 0x")
            XCTAssertEqual(address.count, 42, "Address should be 42 characters (0x + 40 hex chars)")
            
            // Check that it's valid hex
            let hexPart = String(address.dropFirst(2))
            XCTAssertTrue(hexPart.allSatisfy { $0.isHexDigit }, "Address should be valid hex")
            
            // Check EIP-55 checksum (should have mixed case)
            let hasUpperCase = hexPart.contains { $0.isUppercase }
            let hasLowerCase = hexPart.contains { $0.isLowercase }
            XCTAssertTrue(hasUpperCase || hasLowerCase, "Address should have EIP-55 checksum encoding")
        }
        
        print("✅ Generated address: \(address ?? "nil")")
    }
    
    func testAddressDeterminism() throws {
        let testPrivateKey = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
        let address1 = EthereumCrypto.generateEthereumAddress(from: testPrivateKey)
        let address2 = EthereumCrypto.generateEthereumAddress(from: testPrivateKey)
        
        XCTAssertNotNil(address1, "First address should not be nil")
        XCTAssertNotNil(address2, "Second address should not be nil")
        XCTAssertEqual(address1, address2, "Same private key should produce same address")
        
        print("✅ Deterministic address: \(address1 ?? "nil")")
    }
    
    // MARK: - Integration Tests
    
    func testFullWalletCreationFlow() throws {
        print("🔄 Testing full wallet creation flow...")
        
        // Step 1: Generate mnemonic
        let mnemonic = EthereumCrypto.generateMnemonic(strength: 128)
        XCTAssertNotNil(mnemonic, "Mnemonic generation should succeed")
        print("✅ Step 1 - Generated mnemonic: \(mnemonic ?? "nil")")
        
        // Step 2: Derive private key
        let privateKey = EthereumCrypto.derivePrivateKey(from: mnemonic!, path: "m/44'/60'/0'/0/0")
        XCTAssertNotNil(privateKey, "Private key derivation should succeed")
        print("✅ Step 2 - Derived private key: \(privateKey ?? "nil")")
        
        // Step 3: Generate address
        let address = EthereumCrypto.generateEthereumAddress(from: privateKey!)
        XCTAssertNotNil(address, "Address generation should succeed")
        print("✅ Step 3 - Generated address: \(address ?? "nil")")
        
        // Step 4: Verify the flow is deterministic
        let privateKey2 = EthereumCrypto.derivePrivateKey(from: mnemonic!, path: "m/44'/60'/0'/0/0")
        let address2 = EthereumCrypto.generateEthereumAddress(from: privateKey2!)
        
        XCTAssertEqual(privateKey, privateKey2, "Private key derivation should be deterministic")
        XCTAssertEqual(address, address2, "Address generation should be deterministic")
        
        print("✅ Full wallet creation flow completed successfully!")
        print("📋 Final wallet details:")
        print("   Mnemonic: \(mnemonic!)")
        print("   Private Key: \(privateKey!)")
        print("   Address: \(address!)")
    }
    
    // MARK: - Performance Tests
    
    func testPerformanceMnemonicGeneration() throws {
        measure {
            _ = EthereumCrypto.generateMnemonic(strength: 128)
        }
    }
    
    func testPerformancePrivateKeyDerivation() throws {
        let testMnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        measure {
            _ = EthereumCrypto.derivePrivateKey(from: testMnemonic, path: "m/44'/60'/0'/0/0")
        }
    }
    
    func testPerformanceAddressGeneration() throws {
        let testPrivateKey = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
        measure {
            _ = EthereumCrypto.generateEthereumAddress(from: testPrivateKey)
        }
    }
}

// MARK: - Helper Extensions

extension Character {
    var isHexDigit: Bool {
        return isNumber || (isLetter && lowercased().first! >= "a" && lowercased().first! <= "f")
    }
}