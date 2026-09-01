import Foundation
import WalletCore  // ✅ 正確的模塊名稱（之前是 TrustWalletCore，但 pod 的 moduleName 是 WalletCore）
import CommonCrypto  // ✅ PBKDF2 支援
import CryptoKit  // ✅ AES-GCM 支援 (iOS 13+)

@objc public class TrustWalletSwiftBridge: NSObject {
    
    // MARK: - Wallet Management
    
    @objc public func generateMnemonic(_ wordCount: Int) -> String {
        let strength: Int32
        switch wordCount {
        case 12:
            strength = 128
        case 15:
            strength = 160
        case 18:
            strength = 192
        case 21:
            strength = 224
        case 24:
            strength = 256
        default:
            strength = 128
        }
        
        let wallet = HDWallet(strength: strength, passphrase: "")
        return wallet.mnemonic
    }
    
    @objc public func validateMnemonic(_ mnemonic: String) -> Bool {
        return Mnemonic.isValid(mnemonic: mnemonic)
    }
    
    @objc public func createWallet(mnemonic: String, passphrase: String) -> WalletData? {
        guard let wallet = HDWallet(mnemonic: mnemonic, passphrase: passphrase) else {
            return nil
        }
        
        return WalletData(
            mnemonic: wallet.mnemonic,
            seed: wallet.seed.hexString,
            entropy: wallet.entropy.hexString
        )
    }
    
    // MARK: - Key Generation
    
    @objc public func generateKeyPair(mnemonic: String, passphrase: String, derivationPath: String) -> KeyPairData? {
        guard let wallet = HDWallet(mnemonic: mnemonic, passphrase: passphrase) else {
            return nil
        }
        
        let privateKey = wallet.getKey(derivationPath: derivationPath)
        let publicKey = privateKey.getPublicKeySecp256k1(compressed: false)
        
        return KeyPairData(
            privateKey: privateKey.data.hexString,
            publicKey: publicKey.data.hexString
        )
    }
    
    @objc public func deriveAddress(publicKeyHex: String, coinType: String) -> String? {
        guard let publicKeyData = Data(hexString: publicKeyHex),
              let publicKey = PublicKey(data: publicKeyData, type: .secp256k1) else {
            return nil
        }
        
        // Map coin type string to CoinType enum
        let coin: CoinType
        switch coinType.lowercased() {
        case "ethereum":
            coin = .ethereum
        case "bitcoin":
            coin = .bitcoin
        case "binance":
            coin = .binance
        case "polygon":
            coin = .polygon
        case "avalanche":
            coin = .avalancheCChain
        default:
            coin = .ethereum
        }
        
        return coin.deriveAddress(publicKey: publicKey)
    }
    
    // MARK: - Transaction Signing
    
    @objc public func signEthereumTransaction(
        privateKeyHex: String,
        chainId: String,
        nonce: String,
        gasPrice: String,
        gasLimit: String,
        toAddress: String,
        value: String
    ) -> String? {
        guard let privateKeyData = Data(hexString: privateKeyHex),
              let privateKey = PrivateKey(data: privateKeyData) else {
            return nil
        }
        
        // Create Ethereum signing input
        let input = EthereumSigningInput.with {
            $0.chainID = Data(hexString: chainId) ?? Data()
            $0.nonce = Data(hexString: nonce) ?? Data()
            $0.gasPrice = Data(hexString: gasPrice) ?? Data()
            $0.gasLimit = Data(hexString: gasLimit) ?? Data()
            $0.toAddress = toAddress
            $0.transaction = EthereumTransaction.with {
                $0.transfer = EthereumTransaction.Transfer.with {
                    $0.amount = Data(hexString: value) ?? Data()
                }
            }
            $0.privateKey = privateKey.data
        }
        
        let output: EthereumSigningOutput = AnySigner.sign(input: input, coin: .ethereum)
        return output.encoded.hexString
    }
    
    @objc public func signMessage(privateKeyHex: String, message: String) -> String? {
        guard let privateKeyData = Data(hexString: privateKeyHex),
              let privateKey = PrivateKey(data: privateKeyData),
              let messageData = message.data(using: .utf8) else {
            return nil
        }

        let hash = Hash.keccak256(data: messageData)
        let signature = privateKey.sign(digest: hash, curve: .secp256k1)

        return signature?.hexString
    }

    // MARK: - TRON Specific

    @objc public func signTronTransaction(privateKeyHex: String, rawDataHex: String) -> String? {
        guard let privateKeyData = Data(hexString: privateKeyHex),
              let privateKey = PrivateKey(data: privateKeyData),
              let rawDataBytes = Data(hexString: rawDataHex) else {
            return nil
        }

        // 1. 計算 SHA-256 hash
        let txHash = Hash.sha256(data: rawDataBytes)

        // 2. 使用 secp256k1 簽名
        let signature = privateKey.sign(digest: txHash, curve: .secp256k1)

        // 3. 返回 65 字節簽名的十六進制字符串
        return signature?.hexString
    }

    // MARK: - Ed25519 Signing (for Solana)

    /// ✅ RFC 8032 標準 Ed25519 簽名
    /// 直接簽名原始消息（TrustWallet Core 內部使用 SHA-512）
    @objc public func signWithEd25519(messageHex: String, privateKeyHex: String) -> String? {
        // ✅ P0-2 安全增強：使用 var 使數據可清零
        var privateKeyData = Data(hexString: privateKeyHex)
        var messageData = Data(hexString: messageHex)

        // ✅ 函數結束前自動清零敏感數據（防止記憶體 dump 攻擊）
        defer {
            privateKeyData?.withUnsafeMutableBytes { bytes in
                memset(bytes.baseAddress, 0, bytes.count)
            }
            messageData?.withUnsafeMutableBytes { bytes in
                memset(bytes.baseAddress, 0, bytes.count)
            }
        }

        guard let pkData = privateKeyData,
              let msgData = messageData,
              let privateKey = PrivateKey(data: pkData) else {
            NSLog("❌ TrustWallet Ed25519: 無效的輸入數據格式")
            return nil
        }

        // ✅ RFC 8032: 直接簽名原始消息，不預哈希
        // TrustWallet Core 內部會正確使用 SHA-512
        guard let signature = privateKey.sign(digest: msgData, curve: .ed25519) else {
            NSLog("❌ TrustWallet Ed25519: 簽名失敗")
            return nil
        }

        // 返回十六進制簽名（64 字節）
        return signature.hexString
    }

    /// ✅ RFC 8032 標準 Ed25519 簽名驗證
    @objc public func verifyEd25519Signature(messageHex: String, signatureHex: String, publicKeyHex: String) -> Bool {
        guard let messageData = Data(hexString: messageHex),
              let publicKeyData = Data(hexString: publicKeyHex),
              let publicKey = PublicKey(data: publicKeyData, type: .ed25519),
              let signatureData = Data(hexString: signatureHex) else {
            return false
        }

        // ✅ RFC 8032: 驗證原始消息，不預哈希
        return publicKey.verify(signature: signatureData, message: messageData)
    }

    /// Solana 專用：返回 Base58 編碼的簽名
    @objc public func signWithEd25519Base58(messageHex: String, privateKeyHex: String) -> String? {
        guard let signatureHex = signWithEd25519(messageHex: messageHex, privateKeyHex: privateKeyHex),
              let signatureData = Data(hexString: signatureHex) else {
            return nil
        }
        return Base58.encode(data: signatureData)
    }

    // MARK: - Utility

    @objc public func hashKeccak256(_ data: String) -> String? {
        guard let inputData = Data(hexString: data) else {
            return nil
        }

        let hash = Hash.keccak256(data: inputData)
        return hash.hexString
    }

    @objc public func hashSHA256(_ data: String) -> String? {
        guard let inputData = Data(hexString: data) else {
            return nil
        }

        let hash = Hash.sha256(data: inputData)
        return hash.hexString
    }

    @objc public func base58Encode(_ dataHex: String) -> String? {
        guard let data = Data(hexString: dataHex) else {
            return nil
        }
        return Base58.encode(data: data)
    }

    @objc public func base58Decode(_ base58String: String) -> String? {
        guard let data = Base58.decode(string: base58String) else {
            return nil
        }
        return data.hexString
    }

    // MARK: - Cryptographic Functions (for BIP39/PBKDF2)

    /// PBKDF2-HMAC-SHA512 密鑰派生
    /// 用於 BIP39 助記詞轉種子
    @objc public func pbkdf2HmacSha512(
        password: Data,
        salt: Data,
        iterations: Int,
        keyLength: Int
    ) -> Data? {
        var derivedKey = Data(count: keyLength)

        let result = derivedKey.withUnsafeMutableBytes { derivedKeyBytes in
            password.withUnsafeBytes { passwordBytes in
                salt.withUnsafeBytes { saltBytes in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passwordBytes.baseAddress!.assumingMemoryBound(to: Int8.self),
                        password.count,
                        saltBytes.baseAddress!.assumingMemoryBound(to: UInt8.self),
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA512),
                        UInt32(iterations),
                        derivedKeyBytes.baseAddress!.assumingMemoryBound(to: UInt8.self),
                        keyLength
                    )
                }
            }
        }

        guard result == kCCSuccess else { return nil }
        return derivedKey
    }

    /// NFKD Unicode 正規化
    /// 用於 BIP39 助記詞標準化
    @objc public func normalizeNFKD(_ text: String) -> String {
        // NFKD = 相容性分解正規化
        // 先做相容性組合，再做標準分解
        let compatible = (text as NSString).precomposedStringWithCompatibilityMapping
        let nfkd = (compatible as NSString).decomposedStringWithCanonicalMapping
        return nfkd
    }

    // MARK: - AES-GCM Encryption (iOS 13+)

    /// AES-256-GCM 加密
    ///
    /// - Parameters:
    ///   - plaintext: 要加密的明文數據
    ///   - key: 256-bit (32 bytes) 加密密鑰
    ///   - nonce: 12-byte 隨機 nonce
    /// - Returns: 包含 nonce + ciphertext + tag 的組合數據，失敗返回 nil
    @available(iOS 13.0, watchOS 6.0, *)
    @objc public func aesGcmEncrypt(
        plaintext: Data,
        key: Data,
        nonce: Data
    ) -> Data? {
        guard key.count == 32 else {
            NSLog("❌ AES-GCM: Key must be 32 bytes, got \(key.count)")
            return nil
        }
        guard nonce.count == 12 else {
            NSLog("❌ AES-GCM: Nonce must be 12 bytes, got \(nonce.count)")
            return nil
        }

        do {
            let symmetricKey = SymmetricKey(data: key)
            let gcmNonce = try AES.GCM.Nonce(data: nonce)

            let sealedBox = try AES.GCM.seal(
                plaintext,
                using: symmetricKey,
                nonce: gcmNonce
            )

            // 返回組合格式: nonce + ciphertext + tag
            return sealedBox.combined
        } catch {
            NSLog("❌ AES-GCM encryption failed: \(error)")
            return nil
        }
    }

    /// AES-256-GCM 解密
    ///
    /// - Parameters:
    ///   - combined: 包含 nonce + ciphertext + tag 的組合數據
    ///   - key: 256-bit (32 bytes) 解密密鑰
    /// - Returns: 解密後的明文，失敗返回 nil
    @available(iOS 13.0, watchOS 6.0, *)
    @objc public func aesGcmDecrypt(
        combined: Data,
        key: Data
    ) -> Data? {
        guard key.count == 32 else {
            NSLog("❌ AES-GCM: Key must be 32 bytes, got \(key.count)")
            return nil
        }

        do {
            let symmetricKey = SymmetricKey(data: key)
            let sealedBox = try AES.GCM.SealedBox(combined: combined)

            let plaintext = try AES.GCM.open(
                sealedBox,
                using: symmetricKey
            )

            return plaintext
        } catch {
            NSLog("❌ AES-GCM decryption failed: \(error)")
            return nil
        }
    }

    /// PBKDF2-HMAC-SHA256 密鑰派生 (用於 AES-GCM)
    ///
    /// - Parameters:
    ///   - password: 密碼字符串
    ///   - salt: 鹽值數據
    ///   - iterations: 迭代次數
    ///   - keyLength: 派生密鑰長度（字節）
    /// - Returns: 派生的密鑰，失敗返回 nil
    @objc public func pbkdf2HmacSha256(
        password: String,
        salt: Data,
        iterations: Int,
        keyLength: Int
    ) -> Data? {
        guard let passwordData = password.data(using: .utf8) else {
            NSLog("❌ PBKDF2: Failed to encode password")
            return nil
        }

        var derivedKey = Data(count: keyLength)
        let result = derivedKey.withUnsafeMutableBytes { derivedKeyBytes in
            passwordData.withUnsafeBytes { passwordBytes in
                salt.withUnsafeBytes { saltBytes in
                    CCKeyDerivationPBKDF(
                        CCPBKDFAlgorithm(kCCPBKDF2),
                        passwordBytes.baseAddress!.assumingMemoryBound(to: Int8.self),
                        passwordData.count,
                        saltBytes.baseAddress!.assumingMemoryBound(to: UInt8.self),
                        salt.count,
                        CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),
                        UInt32(iterations),
                        derivedKeyBytes.baseAddress!.assumingMemoryBound(to: UInt8.self),
                        keyLength
                    )
                }
            }
        }

        guard result == kCCSuccess else {
            NSLog("❌ PBKDF2 derivation failed with status: \(result)")
            return nil
        }
        return derivedKey
    }
}

// MARK: - Data Models

@objc public class WalletData: NSObject {
    @objc public let mnemonic: String
    @objc public let seed: String
    @objc public let entropy: String
    
    @objc public init(mnemonic: String, seed: String, entropy: String) {
        self.mnemonic = mnemonic
        self.seed = seed
        self.entropy = entropy
    }
}

@objc public class KeyPairData: NSObject {
    @objc public let privateKey: String
    @objc public let publicKey: String
    
    @objc public init(privateKey: String, publicKey: String) {
        self.privateKey = privateKey
        self.publicKey = publicKey
    }
}

// MARK: - Extensions

extension Data {
    init?(hexString: String) {
        let hex = hexString.hasPrefix("0x") ? String(hexString.dropFirst(2)) : hexString
        let len = hex.count / 2
        var data = Data(capacity: len)
        var index = hex.startIndex
        for _ in 0..<len {
            let nextIndex = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<nextIndex], radix: 16) else { return nil }
            data.append(byte)
            index = nextIndex
        }
        self = data
    }
    
    var hexString: String {
        return map { String(format: "%02x", $0) }.joined()
    }
}