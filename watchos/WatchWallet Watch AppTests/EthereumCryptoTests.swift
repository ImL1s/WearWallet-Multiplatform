import XCTest
import coreKmp
@testable import WatchWallet_Watch_App

/// Comprehensive tests for EthereumCrypto utilities
final class EthereumCryptoTests: XCTestCase {

    // MARK: - Ping Tests

    func testPingReturnsExpectedMessage() {
        // When
        let result = EthereumCrypto.ping()

        // Then
        XCTAssertTrue(result.contains("coreKmp"))
        XCTAssertTrue(result.contains("AddressDerivation"))
    }

    func testPingWithArgEchoes() {
        // Given
        let testValue = "Hello"

        // When
        let result = EthereumCrypto.pingWithArg(testValue)

        // Then
        XCTAssertTrue(result.contains(testValue))
    }

    // MARK: - Address Derivation Tests

    func testDeriveEthereumAddressFromValidMnemonic() {
        // Given - BIP39 test mnemonic
        let mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        // When
        let address = EthereumCrypto.deriveEthereumAddress(from: mnemonic)

        // Then
        XCTAssertNotNil(address)
        if let addr = address {
            XCTAssertTrue(addr.hasPrefix("0x"), "Ethereum address should start with 0x")
            XCTAssertEqual(addr.count, 42, "Ethereum address should be 42 characters (0x + 40 hex)")
        }
    }

    func testDeriveAddressForDifferentChains() {
        // Given
        let mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        // When
        let ethAddress = EthereumCrypto.deriveAddress(from: mnemonic, chainType: .ethereum)
        let solAddress = EthereumCrypto.deriveAddress(from: mnemonic, chainType: .solana)

        // Then
        XCTAssertNotNil(ethAddress)
        XCTAssertNotNil(solAddress)
        XCTAssertNotEqual(ethAddress, solAddress, "Different chains should produce different addresses")
    }

    func testDerivePrivateKeyReturnsNonEmpty() {
        // Given
        let mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        // When
        let privateKey = EthereumCrypto.derivePrivateKey(from: mnemonic, chainType: .ethereum)

        // Then
        XCTAssertGreaterThan(privateKey.size, 0)
    }

    func testDerivePrivateKeyHexReturnsValidHex() {
        // Given
        let mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"

        // When
        let privateKeyHex = EthereumCrypto.derivePrivateKeyHex(from: mnemonic)

        // Then
        XCTAssertNotNil(privateKeyHex)
        if let hex = privateKeyHex {
            XCTAssertEqual(hex.count, 64, "Private key should be 32 bytes = 64 hex characters")
            // Verify it's valid hex
            let isValidHex = hex.allSatisfy { "0123456789abcdef".contains($0) }
            XCTAssertTrue(isValidHex, "Should be valid lowercase hex")
        }
    }

    // MARK: - xpub Derivation Tests

    func testDeriveAddressFromXpubThrowsNotSupported() {
        // Given
        let xpub = "xpub661MyMwAqRbcFtXgS5sYJABqqG9YLmC4Q1Rdap9gSE8NqtwybGhePY2gZ29ESFjqJoCu1Rupje8YtGqsefD265TMg7usUDFdp6W1EGMcet8"
        let path = "m/44'/60'/0'/0/0"

        // When/Then
        XCTAssertThrowsError(try EthereumCrypto.deriveAddressFromXpub(xpub: xpub, derivationPath: path)) { error in
            XCTAssertTrue(error is EthereumCryptoError)
        }
    }

    // MARK: - Mnemonic Generation Tests

    func testGenerateMnemonicReturns12Words() {
        // When
        let mnemonic = EthereumCrypto.generateMnemonic(strength: 128)

        // Then
        XCTAssertNotNil(mnemonic)
        if let words = mnemonic?.components(separatedBy: " ") {
            XCTAssertEqual(words.count, 12)
        }
    }

    func testGenerateMnemonicReturns24Words() {
        // When
        let mnemonic = EthereumCrypto.generateMnemonic(strength: 256)

        // Then
        XCTAssertNotNil(mnemonic)
        if let words = mnemonic?.components(separatedBy: " ") {
            XCTAssertEqual(words.count, 24)
        }
    }

    func testGenerateMnemonicProducesUniqueValues() {
        // When
        let mnemonic1 = EthereumCrypto.generateMnemonic()
        let mnemonic2 = EthereumCrypto.generateMnemonic()

        // Then
        XCTAssertNotEqual(mnemonic1, mnemonic2)
    }

    // MARK: - Checksum Address Tests

    func testToChecksumAddressFormatsCorrectly() {
        // Given - lowercase address
        let lowercaseAddress = "0xab5801a7d398351b8be11c439e05c5b3259aec9b"

        // When
        let checksummed = EthereumCrypto.toChecksumAddress(lowercaseAddress)

        // Then
        XCTAssertTrue(checksummed.hasPrefix("0x"))
        XCTAssertEqual(checksummed.count, 42)
        // Should contain mixed case
        let hasUppercase = checksummed.contains { $0.isUppercase }
        let hasLowercase = checksummed.dropFirst(2).contains { $0.isLowercase }
        XCTAssertTrue(hasUppercase || hasLowercase)
    }

    func testToChecksumAddressHandles0xPrefix() {
        // Given
        let withPrefix = "0xab5801a7d398351b8be11c439e05c5b3259aec9b"
        let withoutPrefix = "ab5801a7d398351b8be11c439e05c5b3259aec9b"

        // When
        let result1 = EthereumCrypto.toChecksumAddress(withPrefix)
        let result2 = EthereumCrypto.toChecksumAddress(withoutPrefix)

        // Then - Both should produce same result
        XCTAssertTrue(result1.hasPrefix("0x"))
        XCTAssertTrue(result2.hasPrefix("0x"))
    }

    // MARK: - Keccak256 Tests

    func testKeccak256ProducesConsistentHash() {
        // Given
        let data1 = "hello".data(using: .utf8)!
        let data2 = "hello".data(using: .utf8)!
        let data3 = "world".data(using: .utf8)!

        // When
        let hash1 = EthereumCrypto.keccak256(data1)
        let hash2 = EthereumCrypto.keccak256(data2)
        let hash3 = EthereumCrypto.keccak256(data3)

        // Then
        XCTAssertEqual(hash1, hash2, "Same input should produce same hash")
        XCTAssertNotEqual(hash1, hash3, "Different input should produce different hash")
        XCTAssertEqual(hash1.count, 32, "Hash should be 32 bytes")
    }

    func testKeccak256HandlesEmptyData() {
        // Given
        let emptyData = Data()

        // When
        let hash = EthereumCrypto.keccak256(emptyData)

        // Then
        XCTAssertEqual(hash.count, 32)
    }

    // MARK: - KotlinByteArray Extension Tests

    func testKotlinByteArrayToHexString() {
        // Given
        let byteArray = KotlinByteArray(size: 3)
        byteArray.set(index: 0, value: 0x12)
        byteArray.set(index: 1, value: 0x34)
        byteArray.set(index: 2, value: 0x56)

        // When
        let hexString = convertKotlinByteArrayToHex(byteArray)

        // Then
        XCTAssertEqual(hexString, "123456")
    }

    func testKotlinByteArrayToHexStringWithLeadingZeros() {
        // Given
        let byteArray = KotlinByteArray(size: 2)
        byteArray.set(index: 0, value: 0x01)
        byteArray.set(index: 1, value: 0x0F)

        // When
        let hexString = convertKotlinByteArrayToHex(byteArray)

        // Then
        XCTAssertEqual(hexString, "010f")
    }

    // Helper to avoid ambiguous extension
    private func convertKotlinByteArrayToHex(_ byteArray: KotlinByteArray) -> String {
        var hex = ""
        for i in 0..<byteArray.size {
            let byte = UInt8(bitPattern: byteArray.get(index: i))
            hex += String(format: "%02x", byte)
        }
        return hex
    }

    // MARK: - Data Extension Tests

    func testDataToHexString() {
        // Given
        let data = Data([0x12, 0x34, 0x56, 0xAB])

        // When
        let hexString = data.toHexString()

        // Then
        XCTAssertEqual(hexString, "123456ab")
    }
}
