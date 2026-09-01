import Foundation
import WalletCore  // ✅ 正確的模塊名稱（之前是 TrustWalletCore，但 pod 的 moduleName 是 WalletCore）

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

        guard let wallet = HDWallet(strength: strength, passphrase: "") else {
            return ""
        }
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

        // ✅ TrustWallet Core 4.x API: getKey() 需要 coin 參數
        let privateKey = wallet.getKey(coin: .ethereum, derivationPath: derivationPath)
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

        // ✅ TrustWallet Core 4.x API: deriveAddress() 需要 privateKey 參數
        // 但我們只有 publicKey，所以使用 AnyAddress
        // 注意：這個初始化器不是 Optional，總是會返回一個 AnyAddress 實例
        let address = AnyAddress(publicKey: publicKey, coin: coin)
        return address.description
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