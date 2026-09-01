import XCTest
@testable import WatchWallet_Watch_App

/// Comprehensive tests for CryptoSwiftBridge AES-GCM implementation
final class CryptoSwiftBridgeTests: XCTestCase {

    var cryptoBridge: CryptoSwiftBridge!

    override func setUp() {
        super.setUp()
        cryptoBridge = CryptoSwiftBridge.shared
    }

    // MARK: - Encryption Tests

    func testEncryptDecryptRoundtrip() throws {
        // Given
        let plaintext = "Hello, WearWallet!".data(using: .utf8)!
        let password = "testPassword123"
        let salt = cryptoBridge.generateSalt()

        // When
        let encrypted = try cryptoBridge.encrypt(
            plaintext: plaintext,
            password: password,
            salt: salt,
            iterations: 10000
        )

        let decrypted = try cryptoBridge.decrypt(
            encrypted: encrypted,
            password: password,
            salt: salt,
            iterations: 10000
        )

        // Then
        XCTAssertEqual(decrypted, plaintext)
    }

    func testEncryptProducesValidComponents() throws {
        // Given
        let plaintext = "Test data".data(using: .utf8)!
        let password = "password"
        let salt = cryptoBridge.generateSalt()

        // When
        let result = try cryptoBridge.encrypt(
            plaintext: plaintext,
            password: password,
            salt: salt,
            iterations: 10000
        )

        // Then
        XCTAssertEqual(result.nonce.count, 12, "Nonce should be 12 bytes")
        XCTAssertEqual(result.tag.count, 16, "Tag should be 16 bytes")
        XCTAssertGreaterThan(result.ciphertext.count, 0, "Ciphertext should not be empty")
    }

    func testDecryptWithWrongPasswordFails() throws {
        // Given
        let plaintext = "Secret message".data(using: .utf8)!
        let correctPassword = "correctPassword"
        let wrongPassword = "wrongPassword"
        let salt = cryptoBridge.generateSalt()

        let encrypted = try cryptoBridge.encrypt(
            plaintext: plaintext,
            password: correctPassword,
            salt: salt,
            iterations: 10000
        )

        // When/Then
        XCTAssertThrowsError(try cryptoBridge.decrypt(
            encrypted: encrypted,
            password: wrongPassword,
            salt: salt,
            iterations: 10000
        ))
    }

    func testDecryptWithWrongSaltFails() throws {
        // Given
        let plaintext = "Secret message".data(using: .utf8)!
        let password = "password"
        let salt1 = cryptoBridge.generateSalt()
        let salt2 = cryptoBridge.generateSalt()

        let encrypted = try cryptoBridge.encrypt(
            plaintext: plaintext,
            password: password,
            salt: salt1,
            iterations: 10000
        )

        // When/Then
        XCTAssertThrowsError(try cryptoBridge.decrypt(
            encrypted: encrypted,
            password: password,
            salt: salt2,
            iterations: 10000
        ))
    }

    func testEncryptWithEmptyPlaintext() throws {
        // Given
        let plaintext = Data()
        let password = "password"
        let salt = cryptoBridge.generateSalt()

        // When
        let encrypted = try cryptoBridge.encrypt(
            plaintext: plaintext,
            password: password,
            salt: salt,
            iterations: 10000
        )

        let decrypted = try cryptoBridge.decrypt(
            encrypted: encrypted,
            password: password,
            salt: salt,
            iterations: 10000
        )

        // Then
        XCTAssertEqual(decrypted, plaintext)
    }

    func testEncryptWithLargePlaintext() throws {
        // Given - 1MB of data
        let plaintext = Data(repeating: 0xAB, count: 1024 * 1024)
        let password = "password"
        let salt = cryptoBridge.generateSalt()

        // When
        let encrypted = try cryptoBridge.encrypt(
            plaintext: plaintext,
            password: password,
            salt: salt,
            iterations: 10000
        )

        let decrypted = try cryptoBridge.decrypt(
            encrypted: encrypted,
            password: password,
            salt: salt,
            iterations: 10000
        )

        // Then
        XCTAssertEqual(decrypted, plaintext)
    }

    // MARK: - Combined Format Tests

    func testCombinedFormat() throws {
        // Given
        let plaintext = "Test combined format".data(using: .utf8)!
        let password = "password"
        let salt = cryptoBridge.generateSalt()

        let encrypted = try cryptoBridge.encrypt(
            plaintext: plaintext,
            password: password,
            salt: salt,
            iterations: 10000
        )

        // When
        let combined = encrypted.combined
        let parsed = AesGcmEncryptionResult.fromCombined(combined)

        // Then
        XCTAssertNotNil(parsed)
        XCTAssertEqual(parsed?.nonce, encrypted.nonce)
        XCTAssertEqual(parsed?.ciphertext, encrypted.ciphertext)
        XCTAssertEqual(parsed?.tag, encrypted.tag)
    }

    func testFromCombinedWithInvalidDataReturnsNil() {
        // Given - data too short
        let invalidData = Data(repeating: 0, count: 10)

        // When
        let result = AesGcmEncryptionResult.fromCombined(invalidData)

        // Then
        XCTAssertNil(result)
    }

    // MARK: - Salt and Nonce Generation Tests

    func testGenerateSaltProducesUniqueValues() {
        // When
        let salt1 = cryptoBridge.generateSalt()
        let salt2 = cryptoBridge.generateSalt()

        // Then
        XCTAssertNotEqual(salt1, salt2)
        XCTAssertEqual(salt1.count, 16)
        XCTAssertEqual(salt2.count, 16)
    }

    func testGenerateSaltWithCustomLength() {
        // When
        let salt = cryptoBridge.generateSalt(length: 32)

        // Then
        XCTAssertEqual(salt.count, 32)
    }

    func testGenerateNonceProducesUniqueValues() {
        // When
        let nonce1 = cryptoBridge.generateNonce()
        let nonce2 = cryptoBridge.generateNonce()

        // Then
        XCTAssertNotEqual(nonce1, nonce2)
        XCTAssertEqual(nonce1.count, 12)
        XCTAssertEqual(nonce2.count, 12)
    }

    // MARK: - Iteration Count Tests

    func testDifferentIterationCountsProduceDifferentResults() throws {
        // Given
        let plaintext = "Test data".data(using: .utf8)!
        let password = "password"
        let salt = cryptoBridge.generateSalt()

        // When
        let encrypted1 = try cryptoBridge.encrypt(
            plaintext: plaintext,
            password: password,
            salt: salt,
            iterations: 1000
        )

        let encrypted2 = try cryptoBridge.encrypt(
            plaintext: plaintext,
            password: password,
            salt: salt,
            iterations: 2000
        )

        // Then - Different iterations should produce different ciphertexts
        // (because key derivation uses different iteration counts)
        XCTAssertNotEqual(encrypted1.ciphertext, encrypted2.ciphertext)
    }
}
