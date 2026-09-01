import Foundation
import CryptoKit
import CommonCrypto
import coreKmp

// MARK: - Local Types (Previously in coreKmp, now standalone)

/// AES-GCM encryption result containing nonce, ciphertext, and authentication tag
public struct AesGcmEncryptionResult {
    public let nonce: Data
    public let ciphertext: Data
    public let tag: Data

    public init(nonce: Data, ciphertext: Data, tag: Data) {
        self.nonce = nonce
        self.ciphertext = ciphertext
        self.tag = tag
    }

    /// Combined data format: nonce (12) + ciphertext + tag (16)
    public var combined: Data {
        return nonce + ciphertext + tag
    }

    /// Parse from combined format
    public static func fromCombined(_ data: Data) -> AesGcmEncryptionResult? {
        guard data.count > 28 else { return nil } // 12 + at least 1 + 16
        let nonce = data.prefix(12)
        let tag = data.suffix(16)
        let ciphertext = data.dropFirst(12).dropLast(16)
        return AesGcmEncryptionResult(nonce: Data(nonce), ciphertext: Data(ciphertext), tag: Data(tag))
    }
}

/// Protocol for AES-GCM implementation
public protocol AesGcmProvider {
    func encrypt(plaintext: Data, password: String, salt: Data, iterations: Int) throws -> AesGcmEncryptionResult
    func decrypt(encrypted: AesGcmEncryptionResult, password: String, salt: Data, iterations: Int) throws -> Data
}

// MARK: - CryptoSwiftBridge

/**
 * Swift implementation of AES-GCM for watchOS
 * Uses CryptoKit for encryption/decryption
 * Uses CommonCrypto for PBKDF2 key derivation
 */
@objc public class CryptoSwiftBridge: NSObject, AesGcmProvider {

    public static let shared = CryptoSwiftBridge()

    private override init() {
        super.init()
    }

    // MARK: - Integration

    public func register() {
        print("CryptoSwiftBridge: Registered for watchOS crypto operations")
        // Note: coreKmp AesGcmBridge was removed, this is now standalone
    }

    // MARK: - AesGcmProvider Implementation

    public func encrypt(plaintext: Data, password: String, salt: Data, iterations: Int) throws -> AesGcmEncryptionResult {
        // 1. Derive Key (PBKDF2)
        guard let keyData = deriveKey(password: password, salt: salt, iterations: iterations) else {
            throw CryptoError.keyDerivationFailed
        }
        let symmetricKey = SymmetricKey(data: keyData)

        // 2. Encrypt (AES-GCM)
        do {
            let sealedBox = try AES.GCM.seal(plaintext, using: symmetricKey)

            // 3. Return Result
            // Nonce is 12 bytes, Tag is 16 bytes
            let nonce = sealedBox.nonce.withUnsafeBytes { Data($0) }
            let ciphertext = sealedBox.ciphertext
            let tag = sealedBox.tag

            return AesGcmEncryptionResult(
                nonce: nonce,
                ciphertext: ciphertext,
                tag: tag
            )
        } catch {
            throw CryptoError.encryptionFailed(error.localizedDescription)
        }
    }

    public func decrypt(encrypted: AesGcmEncryptionResult, password: String, salt: Data, iterations: Int) throws -> Data {
        // 1. Derive Key
        guard let keyData = deriveKey(password: password, salt: salt, iterations: iterations) else {
            throw CryptoError.keyDerivationFailed
        }
        let symmetricKey = SymmetricKey(data: keyData)

        // 2. Reconstruct SealedBox
        do {
            let sealedBox = try AES.GCM.SealedBox(
                nonce: AES.GCM.Nonce(data: encrypted.nonce),
                ciphertext: encrypted.ciphertext,
                tag: encrypted.tag
            )

            // 3. Decrypt
            let decryptedData = try AES.GCM.open(sealedBox, using: symmetricKey)

            return decryptedData
        } catch {
            throw CryptoError.decryptionFailed(error.localizedDescription)
        }
    }

    // MARK: - Convenience Methods with KotlinByteArray

    public func encryptWithKotlinTypes(plaintext: KotlinByteArray, password: String, salt: KotlinByteArray, iterations: Int32) throws -> (nonce: KotlinByteArray, ciphertext: KotlinByteArray, tag: KotlinByteArray) {
        let result = try encrypt(
            plaintext: plaintext.toData(),
            password: password,
            salt: salt.toData(),
            iterations: Int(iterations)
        )
        return (
            nonce: result.nonce.toKotlinByteArray(),
            ciphertext: result.ciphertext.toKotlinByteArray(),
            tag: result.tag.toKotlinByteArray()
        )
    }

    public func decryptWithKotlinTypes(nonce: KotlinByteArray, ciphertext: KotlinByteArray, tag: KotlinByteArray, password: String, salt: KotlinByteArray, iterations: Int32) throws -> KotlinByteArray {
        let encrypted = AesGcmEncryptionResult(
            nonce: nonce.toData(),
            ciphertext: ciphertext.toData(),
            tag: tag.toData()
        )
        let result = try decrypt(
            encrypted: encrypted,
            password: password,
            salt: salt.toData(),
            iterations: Int(iterations)
        )
        return result.toKotlinByteArray()
    }

    // MARK: - Key Derivation

    private func deriveKey(password: String, salt: Data, iterations: Int) -> Data? {
        guard let passwordData = password.data(using: .utf8) else { return nil }

        var derivedBytes = [UInt8](repeating: 0, count: 32) // 256 bits
        let algorithm = CCPBKDFAlgorithm(kCCPBKDF2)
        let prf = CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256)

        let status = passwordData.withUnsafeBytes { passwordBytes in
            salt.withUnsafeBytes { saltBytes in
                CCKeyDerivationPBKDF(
                    algorithm,
                    passwordBytes.baseAddress, passwordBytes.count,
                    saltBytes.baseAddress, saltBytes.count,
                    prf,
                    UInt32(iterations),
                    &derivedBytes,
                    32
                )
            }
        }

        guard status == kCCSuccess else { return nil }
        return Data(derivedBytes)
    }

    // MARK: - Utility Methods

    /// Generate random salt
    public func generateSalt(length: Int = 16) -> Data {
        var bytes = [UInt8](repeating: 0, count: length)
        _ = SecRandomCopyBytes(kSecRandomDefault, length, &bytes)
        return Data(bytes)
    }

    /// Generate random IV/Nonce
    public func generateNonce() -> Data {
        var bytes = [UInt8](repeating: 0, count: 12)
        _ = SecRandomCopyBytes(kSecRandomDefault, 12, &bytes)
        return Data(bytes)
    }
}

// MARK: - Error Types

public enum CryptoError: LocalizedError {
    case keyDerivationFailed
    case encryptionFailed(String)
    case decryptionFailed(String)
    case invalidData

    public var errorDescription: String? {
        switch self {
        case .keyDerivationFailed:
            return "Key derivation failed"
        case .encryptionFailed(let reason):
            return "Encryption failed: \(reason)"
        case .decryptionFailed(let reason):
            return "Decryption failed: \(reason)"
        case .invalidData:
            return "Invalid data format"
        }
    }
}

// MARK: - KotlinByteArray <-> Data Helpers

extension KotlinByteArray {
    func toData() -> Data {
        let size = Int(self.size)
        var data = Data(count: size)
        for i in 0..<size {
            data[i] = UInt8(bitPattern: self.get(index: Int32(i)))
        }
        return data
    }
}

extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let size = Int32(self.count)
        let byteArray = KotlinByteArray(size: size)
        for i in 0..<Int(size) {
            byteArray.set(index: Int32(i), value: Int8(bitPattern: self[i]))
        }
        return byteArray
    }
}
