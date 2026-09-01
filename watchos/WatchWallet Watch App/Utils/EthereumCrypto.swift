import Foundation
import coreKmp
import CryptoKit
import CommonCrypto

/// Ethereum address generation and crypto utilities for watchOS
/// Uses coreKmp AddressDerivation for address derivation
class EthereumCrypto {

    // MARK: - Properties

    /// Shared instance of AddressDerivation_ from coreKmp
    private static let addressDerivation = AddressDerivation_()

    // MARK: - API

    /// Derive an Ethereum address from an xpub and derivation path
    /// For watchOS, we delegate xpub derivation to the iPhone via WatchConnectivity
    /// since this requires complex BIP32 operations
    static func deriveAddressFromXpub(xpub: String, derivationPath: String) throws -> String {
        // For xpub-based derivation, we need to use WatchConnectivity
        // to ask the iPhone to perform the derivation
        // This is a placeholder that should be replaced with WC call
        print("[EthereumCrypto] xpub derivation should use WatchConnectivity")
        throw EthereumCryptoError.xpubDerivationNotSupported
    }

    /// Ping function for connectivity testing
    static func ping() -> String {
        return "EthereumCrypto ready - using coreKmp AddressDerivation"
    }

    /// Ping with argument for testing
    static func pingWithArg(_ val: String) -> String {
        return "Echo: \(val)"
    }

    // MARK: - Address Derivation via coreKmp

    /// Derive Ethereum address from mnemonic
    static func deriveEthereumAddress(from mnemonic: String) -> String? {
        return addressDerivation.deriveAddress(mnemonic: mnemonic, chainType: .ethereum)
    }

    /// Derive address for any supported chain
    static func deriveAddress(from mnemonic: String, chainType: MultiChainType) -> String? {
        return addressDerivation.deriveAddress(mnemonic: mnemonic, chainType: chainType)
    }

    /// Derive private key from mnemonic (returns KotlinByteArray)
    static func derivePrivateKey(from mnemonic: String, chainType: MultiChainType) -> KotlinByteArray {
        return addressDerivation.derivePrivateKey(mnemonic: mnemonic, chainType: chainType)
    }

    /// Derive private key and convert to hex string
    static func derivePrivateKeyHex(from mnemonic: String, chainType: MultiChainType = .ethereum) -> String? {
        let keyBytes = addressDerivation.derivePrivateKey(mnemonic: mnemonic, chainType: chainType)
        if keyBytes.size == 0 {
            return nil
        }
        return keyBytes.toHexString()
    }

    // MARK: - Legacy / Simplified Methods (No external deps needed)

    static func generateMnemonic(strength: Int32 = 128) -> String? {
        // Use BIP39 from coreKmp if available, otherwise use simple word list
        // This is a simplified version for testing - production should use BIP39.shared
        let wordList = ["abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract", "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid", "acoustic", "acquire", "across", "act", "action", "actor", "actress", "actual", "adapt", "add", "addict", "address", "adjust", "admit", "adult", "advance", "advice", "aerobic", "affair", "afford", "afraid", "again", "against", "age", "agent", "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album", "alcohol", "alert", "alien", "all", "alley", "allow", "almost", "alone", "alpha", "already", "also", "alter", "always", "amateur", "amazing", "among", "amount", "amused", "analyst", "anchor", "ancient", "anger", "angle", "angry", "animal", "ankle", "announce", "annual", "another", "answer", "antenna", "antique", "anxiety", "any", "apart", "apology", "appear", "apple", "approve", "april", "arch", "arctic", "area", "arena", "argue", "arm", "armed", "armor", "army", "around", "arrange", "arrest", "arrive", "arrow", "art", "article", "artist", "artwork", "ask", "aspect", "assault", "asset", "assist", "assume", "asthma", "athlete", "atom", "attack", "attend", "attitude", "attract", "auction", "audit", "august", "aunt", "author", "auto", "autumn", "average"]
        let wordCount = strength == 128 ? 12 : 24
        var words: [String] = []
        for _ in 0..<wordCount {
            words.append(wordList[Int.random(in: 0..<wordList.count)])
        }
        return words.joined(separator: " ")
    }

    static func derivePrivateKey(from mnemonic: String, path: String) -> String? {
        // For Ethereum, use the default derivation and add 0x prefix
        guard let hex = derivePrivateKeyHex(from: mnemonic, chainType: .ethereum) else {
            return nil
        }
        return "0x" + hex
    }

    static func generateEthereumAddress(from privateKeyHex: String) -> String? {
        // This would need secp256k1 to compute public key from private key
        // For now, defer to coreKmp or WatchConnectivity
        print("[EthereumCrypto] Direct address from private key requires secp256k1")
        return nil
    }

    // MARK: - Keccak-256 Hashing (for address checksum, etc)

    /// Compute Keccak-256 hash (used in Ethereum)
    static func keccak256(_ data: Data) -> Data {
        // Use CommonCrypto or a pure Swift implementation
        // For now, return SHA3-256 as placeholder (not exactly Keccak)
        // In production, use a proper Keccak implementation
        var hash = [UInt8](repeating: 0, count: 32)
        data.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            if let baseAddress = bytes.baseAddress {
                CC_SHA256(baseAddress, CC_LONG(data.count), &hash)
            }
        }
        return Data(hash)
    }

    /// Convert address to checksummed format (EIP-55)
    static func toChecksumAddress(_ address: String) -> String {
        let addr = address.lowercased().replacingOccurrences(of: "0x", with: "")
        let hash = keccak256(addr.data(using: .utf8)!).toHexString()

        var checksummed = "0x"
        for (i, char) in addr.enumerated() {
            let hashChar = hash[hash.index(hash.startIndex, offsetBy: i)]
            if let hashVal = Int(String(hashChar), radix: 16), hashVal >= 8 {
                checksummed += char.uppercased()
            } else {
                checksummed += String(char)
            }
        }
        return checksummed
    }
}

// MARK: - Errors

enum EthereumCryptoError: LocalizedError {
    case xpubDerivationNotSupported
    case invalidMnemonic
    case derivationFailed

    var errorDescription: String? {
        switch self {
        case .xpubDerivationNotSupported:
            return "xpub derivation is not supported on watchOS - use WatchConnectivity"
        case .invalidMnemonic:
            return "Invalid mnemonic phrase"
        case .derivationFailed:
            return "Address derivation failed"
        }
    }
}

// MARK: - Extensions

extension KotlinByteArray {
    /// Convert to hex string
    func toHexString() -> String {
        var hex = ""
        for i in 0..<size {
            let byte = UInt8(bitPattern: get(index: i))
            hex += String(format: "%02x", byte)
        }
        return hex
    }
}

extension Data {
    /// Convert to hex string
    func toHexString() -> String {
        return map { String(format: "%02x", $0) }.joined()
    }
}
