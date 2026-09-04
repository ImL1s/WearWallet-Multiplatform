import Foundation
// NOTE: You need to add TrustWalletCore and KeystoneSDK to your project dependencies
import TrustWalletCore
import KeystoneSDK
import coreKmp // Replace with your actual KMP module name (e.g. shared, coreKmp)

/**
 * Reference implementation of NativeCryptoDelegate for WearWallet
 * This class bridges Kotlin Multiplatform calls to native TrustWallet Core and Keystone SDKs.
 *
 * Usage in iOS/WatchOS App Delegate:
 * ```swift
 * let adapter = WearWalletCoreAdapter()
 * NativeCrypto.shared.setDelegate(delegate: adapter)
 * ```
 */
class WearWalletCoreAdapter: NativeCryptoDelegate {
    
    private let keystoneSDK = KeystoneSDK()
    
    // MARK: - Address Derivation
    
    func deriveAddressFromXpub(xpub: String, derivationPath: String) -> String {
        guard let coin = getCoinType(from: derivationPath) else {
            print("Error: Unsupported derivation path: \(derivationPath)")
            return ""
        }
        
        // Connect derivation path to coin type if needed, or just use the coin matching the path
        // TrustWallet Core HDWallet.getPublicKeyFromExtended behaves differently depending on the coin
        // Note: verify if TrustWallet Core exposes getPublicKeyFromExtended in Swift directly or slightly different API.
        
        // Simplified Logic:
        // 1. Create public key from xpub
        guard let publicKey = PublicKey(extended: xpub, coin: coin) else {
            print("Error: Invalid Xpub")
            return ""
        }
        
        // 2. Derive address
        return coin.deriveAddress(publicKey: publicKey)
    }
    
    func deriveAddress(publicKey hex: String) -> String {
        // Assume Ethereum by default for simple deriveAddress calls, or pass CoinType?
        // The interface `deriveAddress(publicKey: String)` doesn't pass chain.
        // It's likely used for Eth-like chains in context.
        guard let data = Data(hexString: hex),
              let pubKey = PublicKey(data: data, type: .secp256k1) else {
            return ""
        }
        return CoinType.ethereum.deriveAddress(publicKey: pubKey)
    }

    // MARK: - Key Generation
    
    func generateKeyPair(mnemonic: String, derivationPath: String, chainType: ChainType) -> KeyPair {
        guard let wallet = HDWallet(mnemonic: mnemonic, passphrase: "") else {
            return KeyPair(publicKey: "", privateKey: "")
        }
        
        // derive private key
        // Need to parse derivation path string to DerivationPath object if possible,
        // or loop through components.
        // Current TW Swift API helper:
        guard let privateKey = wallet.getKey(derivationPath: derivationPath) else {
             return KeyPair(publicKey: "", privateKey: "")
        }
        
        let publicKey = privateKey.getPublicKeySecp256k1(compressed: false)
        
        return KeyPair(
            publicKey: publicKey.data.hexString,
            privateKey: privateKey.data.hexString
        )
    }
    
    // MARK: - Transaction Signing
    
    func signTransaction(data: ByteArray, privateKey: String) -> ByteArray {
        // This depends on what 'data' is (Hash? RLP Encoded tx?).
        // If it's a hash to sign:
        guard let privateKeyObj = PrivateKey(data: Data(hexString: privateKey)) else {
            return KotlinByteArray(size: 0)
        }
        
        let dataObj = data.toData() // Convert KotlinByteArray to Data
        guard let signature = privateKeyObj.sign(digest: dataObj, curve: .secp256k1) else {
            return KotlinByteArray(size: 0)
        }
        
        return signature.toKotlinByteArray()
    }
    
    // MARK: - Keystone / UR Protocol
    
    func encodeUR(data: ByteArray, type: String, maxFragmentSize: Int32) -> [String] {
        let urData = data.toData()
        
        // Use KeystoneSDK or URKit to encode
        // Example using generic UR encoder if available in your KeystoneSDK version
        // Or mapping types to specific KeystoneSDK methods (e.g. parseCryptoAccount)
        
        // Conceptual implementation:
        guard let ur = try? UR(type: type, cbor: urData) else {
            return []
        }
        
        let encoder = UREncoder(ur, maxFragmentLen: Int(maxFragmentSize))
        var parts: [String] = []
        while true {
            let part = encoder.nextPart()
            parts.append(part)
            if encoder.isComplete { break }
        }
        return parts
    }
    
    func decodeUR(urString: String) -> ByteArray {
        // Stateless decode attempt, mainly for single part or if SDK handles it.
        // For multipart, the App typically maintains a URDecoder state in the View/Controller
        // and passes the FINAL result here? 
        // Or if this receives a single UR part, it tries to decode it.
        
        do {
            let decoder = URDecoder()
            decoder.receivePart(urString)
            
            if decoder.isComplete {
                if let result = try decoder.resolve() {
                    return result.cbor.toKotlinByteArray()
                }
            }
            return KotlinByteArray(size: 0) // Not complete or error
        } catch {
             print("UR Decode Error: \(error)")
             return KotlinByteArray(size: 0)
        }
    }
    
    func combineUR(parts: [String]) -> ByteArray {
        do {
            let decoder = URDecoder()
            for part in parts {
                decoder.receivePart(part)
            }
            
            if decoder.isComplete {
                if let result = try decoder.resolve() {
                    return result.cbor.toKotlinByteArray()
                }
            }
            return KotlinByteArray(size: 0)
        } catch {
             print("UR Combine Error: \(error)")
             return KotlinByteArray(size: 0)
        }
    }
    
    // MARK: - Helpers
    
    private func getCoinType(from path: String) -> CoinType? {
        if path.contains("/60'/") { return .ethereum }
        if path.contains("/0'/") { return .bitcoin }
        if path.contains("/2'/") { return .litecoin }
        // Add more mappings
        return .ethereum
    }
}

// MARK: - Extensions

extension Data {
    func toKotlinByteArray() -> KotlinByteArray {
        let byteArray = KotlinByteArray(size: Int32(count))
        self.withUnsafeBytes { buffer in
            for i in 0..<count {
                byteArray.set(index: Int32(i), value: Int8(bitPattern: buffer[i]))
            }
        }
        return byteArray
    }
}

extension KotlinByteArray {
    func toData() -> Data {
        var data = Data(count: Int(size))
        for i in 0..<Int(size) {
            data[i] = UInt8(bitPattern: get(index: Int32(i)))
        }
        return data
    }
}
