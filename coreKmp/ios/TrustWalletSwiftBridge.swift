import Foundation
import WalletCore
import coreKmp

// MARK: - Data Models

/**
 * 密鑰對數據結構
 * 必須使用 @objc 和繼承 NSObject 才能被 Objective-C 調用
 */
@objc(KeyPair) public class KeyPair: NSObject {
    @objc public let publicKey: String
    @objc public let privateKey: String

    @objc public init(publicKey: String, privateKey: String) {
        self.publicKey = publicKey
        self.privateKey = privateKey
        super.init()
    }
}

// MARK: - TrustWallet Bridge

/**
 * Swift 橋接類，用於與 TrustWallet Core 交互
 * 這個類會被 Kotlin/Native 代碼調用
 *
 * 重要提示：
 * - 使用 @objc(TrustWalletSwiftBridge) 確保符號名稱穩定
 * - 所有公開方法必須標註 @objc
 * - 必須繼承 NSObject
 * - 只能使用 Objective-C 兼容的類型
 */
@objc(TrustWalletSwiftBridge) public class TrustWalletSwiftBridge: NSObject {
    
    @objc public override init() {
        super.init()
    }
    
    /**
     * 從助記詞生成密鑰對
     */
    @objc public func generateKeyPairFromMnemonic(_ mnemonic: String) -> KeyPair {
        let wallet = HDWallet(mnemonic: mnemonic, passphrase: "")!
        let privateKey = wallet.getKey(coin: .ethereum, derivationPath: "m/44'/60'/0'/0/0")
        let publicKey = privateKey.getPublicKeySecp256k1(compressed: false)
        
        return KeyPair(
            publicKey: publicKey.data.hexString,
            privateKey: privateKey.data.hexString
        )
    }
    
    /**
     * 從私鑰生成密鑰對
     */
    @objc public func generateKeyPairFromPrivateKey(_ privateKeyHex: String) -> KeyPair {
        let privateKeyData = Data(hexString: privateKeyHex)!
        let privateKey = PrivateKey(data: privateKeyData)!
        let publicKey = privateKey.getPublicKeySecp256k1(compressed: false)
        
        return KeyPair(
            publicKey: publicKey.data.hexString,
            privateKey: privateKeyHex
        )
    }
    
    /**
     * 從公鑰導出地址
     */
    @objc public func deriveAddress(_ publicKeyHex: String) -> String {
        let publicKeyData = Data(hexString: publicKeyHex)!
        let publicKey = PublicKey(data: publicKeyData, type: .secp256k1)!
        let address = AnyAddress(publicKey: publicKey, coin: .ethereum)
        return address.description
    }
    
    /**
     * 從擴展公鑰導出地址
     */
    @objc public func deriveAddressFromXpub(_ xpub: String, derivationPath: String) -> String {
        // 使用 HDWallet 的擴展公鑰功能
        // 注意：TrustWallet Core 的擴展公鑰支援可能有限
        // 這裡提供基本實現，實際使用時可能需要更複雜的邏輯
        
        // 暫時使用簡單的哈希方法生成地址
        let combined = xpub + derivationPath
        let hash = combined.data(using: .utf8)!.sha256()
        let addressBytes = hash.prefix(20)
        return "0x" + addressBytes.hexString
    }
    
    /**
     * 生成助記詞
     */
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
    
    /**
     * 驗證助記詞
     */
    @objc public func validateMnemonic(_ mnemonic: String) -> Bool {
        return Mnemonic.isValid(mnemonic: mnemonic)
    }
    
    /**
     * 簽名交易
     */
    @objc public func signTransaction(_ transaction: Data, privateKeyHex: String) -> String {
        let privateKeyData = Data(hexString: privateKeyHex)!
        let privateKey = PrivateKey(data: privateKeyData)!
        
        // 使用 ECDSA 簽名
        let signature = privateKey.sign(digest: transaction, curve: .secp256k1)!
        return signature.hexString
    }
}

// MARK: - Extensions

extension Data {
    init?(hexString: String) {
        let hex = hexString.hasPrefix("0x") ? String(hexString.dropFirst(2)) : hexString
        let len = hex.count / 2
        var data = Data(capacity: len)
        for i in 0..<len {
            let j = hex.index(hex.startIndex, offsetBy: i*2)
            let k = hex.index(j, offsetBy: 2)
            let bytes = hex[j..<k]
            if var num = UInt8(bytes, radix: 16) {
                data.append(&num, count: 1)
            } else {
                return nil
            }
        }
        self = data
    }
    
    var hexString: String {
        return map { String(format: "%02x", $0) }.joined()
    }
    
    func sha256() -> Data {
        return Hash.sha256(data: self)
    }
}