import Foundation
import KeystoneSDK
import URKit

/**
 * Keystone SDK iOS 實現範例
 * 
 * 安裝步驟：
 * 1. 在 Xcode 中：File > Add Packages
 * 2. 輸入 URL：https://github.com/KeystoneHQ/keystone-sdk-ios.git
 * 3. 選擇最新版本並添加到專案
 * 
 * 這個檔案提供了與 Kotlin/Native 互操作的 Swift 實現
 */
@objc public class KeystoneSwiftBridge: NSObject {
    
    private static let shared = KeystoneSwiftBridge()
    private var currentWallet: KeystoneWalletData?
    
    // MARK: - Initialization
    
    @objc public static func initialize() -> Bool {
        print("KeystoneSwiftBridge: Initializing Keystone SDK for iOS")
        
        // 初始化 SDK 組件
        // 實際 SDK 不需要特別初始化，直接使用即可
        
        return true
    }
    
    // MARK: - ETH Sign Request Generation
    
    @objc public static func generateEthSignRequest(
        unsignedTxHex: String,
        derivationPath: String,
        masterFingerprint: String,
        chainId: Int64,
        requestId: String,
        fromAddress: String?
    ) -> KeystoneSignRequestData? {
        
        print("KeystoneSwiftBridge: Generating ETH sign request")
        
        do {
            // 創建 ETH 簽名請求
            let signData = Data(hex: unsignedTxHex)
            
            let ethSignRequest = EthSignRequest(
                requestId: requestId,
                signData: signData,
                dataType: .transaction,
                chainId: Int(chainId),
                path: derivationPath,
                xfp: masterFingerprint,
                address: fromAddress,
                origin: "WearWallet"
            )
            
            // 編碼為 UR
            let encoder = UREncoder(ethSignRequest, maxFragmentLen: 500)
            
            // 生成 QR Code 數據
            var qrCodes: [String] = []
            
            if encoder.isMultiPart {
                // 多片段動畫 QR Code
                while !encoder.isComplete {
                    if let part = encoder.nextPart() {
                        qrCodes.append(part)
                    }
                }
            } else {
                // 單一 QR Code
                if let singleQR = encoder.encode() {
                    qrCodes.append(singleQR)
                }
            }
            
            return KeystoneSignRequestData(
                requestId: requestId,
                qrCodeData: qrCodes,
                urString: encoder.encode() ?? ""
            )
            
        } catch {
            print("KeystoneSwiftBridge: Failed to generate ETH sign request: \(error)")
            return nil
        }
    }
    
    // MARK: - Signature Parsing
    
    @objc public static func parseSignature(_ urString: String) -> KeystoneSignatureData? {
        print("KeystoneSwiftBridge: Parsing signature from UR")
        
        do {
            // 解析 UR 字符串
            let decoder = URDecoder()
            decoder.receive(urString)
            
            if decoder.isComplete {
                let ur = try decoder.result()
                
                // 解析 ETH 簽名
                if let ethSignature = try? EthSignature(ur: ur) {
                    return KeystoneSignatureData(
                        signature: ethSignature.signature.hexEncodedString(),
                        requestId: ethSignature.requestId
                    )
                }
            }
            
            return nil
            
        } catch {
            print("KeystoneSwiftBridge: Failed to parse signature: \(error)")
            return nil
        }
    }
    
    // MARK: - HD Key Parsing
    
    @objc public static func parseHDKey(_ urString: String) -> KeystoneHDKeyData? {
        print("KeystoneSwiftBridge: Parsing HD Key from UR")
        
        do {
            let decoder = URDecoder()
            decoder.receive(urString)
            
            if decoder.isComplete {
                let ur = try decoder.result()
                
                // 解析 Crypto HD Key
                if let cryptoHDKey = try? CryptoHDKey(ur: ur) {
                    
                    // 從 HD Key 導出帳戶
                    var accounts: [KeystoneAccountData] = []
                    
                    // 預設導出前 5 個地址
                    for i in 0..<5 {
                        let path = "m/44'/60'/0'/0/\(i)"
                        let account = KeystoneAccountData(
                            path: path,
                            xpub: cryptoHDKey.extendedPublicKey ?? "",
                            address: deriveAddress(from: cryptoHDKey, at: path),
                            chainId: "1" // Ethereum mainnet
                        )
                        accounts.append(account)
                    }
                    
                    return KeystoneHDKeyData(
                        name: cryptoHDKey.name ?? "Keystone Wallet",
                        masterFingerprint: cryptoHDKey.origin?.fingerprint ?? "",
                        xpub: cryptoHDKey.extendedPublicKey ?? "",
                        accounts: accounts
                    )
                }
                
                // 解析 Crypto Multi-Accounts
                if let multiAccounts = try? CryptoMultiAccounts(ur: ur) {
                    
                    var accounts: [KeystoneAccountData] = []
                    
                    for key in multiAccounts.keys {
                        let account = KeystoneAccountData(
                            path: key.derivationPath ?? "",
                            xpub: key.extendedPublicKey ?? "",
                            address: deriveAddress(from: key),
                            chainId: "1"
                        )
                        accounts.append(account)
                    }
                    
                    return KeystoneHDKeyData(
                        name: multiAccounts.device ?? "Keystone Wallet",
                        masterFingerprint: multiAccounts.masterFingerprint,
                        xpub: "", // Multi-accounts 沒有單一 xpub
                        accounts: accounts
                    )
                }
            }
            
            return nil
            
        } catch {
            print("KeystoneSwiftBridge: Failed to parse HD Key: \(error)")
            return nil
        }
    }
    
    // MARK: - Wallet Import
    
    @objc public static func importWallet(_ qrData: String) -> KeystoneWalletData? {
        print("KeystoneSwiftBridge: Importing wallet from QR")
        
        guard let hdKey = parseHDKey(qrData) else {
            return nil
        }
        
        // 創建錢包數據
        var addresses: [KeystoneAddressData] = []
        
        for account in hdKey.accounts {
            let addressData = KeystoneAddressData(
                address: account.address,
                chainId: account.chainId ?? "1",
                derivationPath: account.path,
                publicKey: "", // 需要從 xpub 導出
                addressType: "LEGACY"
            )
            addresses.append(addressData)
        }
        
        let wallet = KeystoneWalletData(
            id: UUID().uuidString,
            name: hdKey.name,
            masterFingerprint: hdKey.masterFingerprint,
            addresses: addresses,
            supportedChains: ["1", "56", "137", "43114", "42161", "10"] // 主要 EVM 鏈
        )
        
        // 儲存當前錢包
        shared.currentWallet = wallet
        
        return wallet
    }
    
    // MARK: - Sync Request Generation
    
    @objc public static func generateSyncRequest() -> String? {
        print("KeystoneSwiftBridge: Generating sync request")
        
        do {
            // 創建同步請求
            let syncRequest = CryptoMultiAccounts(
                masterFingerprint: shared.currentWallet?.masterFingerprint ?? "",
                keys: [],
                device: "WearWallet",
                deviceId: UUID().uuidString
            )
            
            // 編碼為 UR
            let encoder = UREncoder(syncRequest, maxFragmentLen: 500)
            return encoder.encode()
            
        } catch {
            print("KeystoneSwiftBridge: Failed to generate sync request: \(error)")
            return nil
        }
    }
    
    // MARK: - Helper Methods
    
    private static func deriveAddress(from hdKey: CryptoHDKey, at path: String) -> String {
        // 實際實現需要使用加密庫來從公鑰導出地址
        // 這裡返回模擬地址
        return "0x" + UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(40).lowercased()
    }
    
    private static func deriveAddress(from account: CryptoMultiAccountsKey) -> String {
        // 從帳戶信息導出地址
        if let address = account.address {
            return address
        }
        return "0x" + UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(40).lowercased()
    }
}

// MARK: - Data Models for Kotlin Interop

@objc public class KeystoneSignRequestData: NSObject {
    @objc public let requestId: String
    @objc public let qrCodeData: [String]
    @objc public let urString: String
    
    @objc public init(requestId: String, qrCodeData: [String], urString: String) {
        self.requestId = requestId
        self.qrCodeData = qrCodeData
        self.urString = urString
    }
}

@objc public class KeystoneSignatureData: NSObject {
    @objc public let signature: String
    @objc public let requestId: String
    
    @objc public init(signature: String, requestId: String) {
        self.signature = signature
        self.requestId = requestId
    }
}

@objc public class KeystoneHDKeyData: NSObject {
    @objc public let name: String
    @objc public let masterFingerprint: String
    @objc public let xpub: String
    @objc public let accounts: [KeystoneAccountData]
    
    @objc public init(name: String, masterFingerprint: String, xpub: String, accounts: [KeystoneAccountData]) {
        self.name = name
        self.masterFingerprint = masterFingerprint
        self.xpub = xpub
        self.accounts = accounts
    }
}

@objc public class KeystoneAccountData: NSObject {
    @objc public let path: String
    @objc public let xpub: String
    @objc public let address: String
    @objc public let chainId: String?
    
    @objc public init(path: String, xpub: String, address: String, chainId: String?) {
        self.path = path
        self.xpub = xpub
        self.address = address
        self.chainId = chainId
    }
}

@objc public class KeystoneWalletData: NSObject {
    @objc public let id: String
    @objc public let name: String
    @objc public let masterFingerprint: String
    @objc public let addresses: [KeystoneAddressData]
    @objc public let supportedChains: [String]
    
    @objc public init(id: String, name: String, masterFingerprint: String, addresses: [KeystoneAddressData], supportedChains: [String]) {
        self.id = id
        self.name = name
        self.masterFingerprint = masterFingerprint
        self.addresses = addresses
        self.supportedChains = supportedChains
    }
}

@objc public class KeystoneAddressData: NSObject {
    @objc public let address: String
    @objc public let chainId: String
    @objc public let derivationPath: String
    @objc public let publicKey: String
    @objc public let addressType: String
    
    @objc public init(address: String, chainId: String, derivationPath: String, publicKey: String, addressType: String) {
        self.address = address
        self.chainId = chainId
        self.derivationPath = derivationPath
        self.publicKey = publicKey
        self.addressType = addressType
    }
}

// MARK: - Extensions

extension Data {
    init(hex: String) {
        var hex = hex
        if hex.hasPrefix("0x") {
            hex = String(hex.dropFirst(2))
        }
        
        var data = Data()
        var byte: UInt8 = 0
        var index = 0
        
        for char in hex {
            if let nibble = char.hexDigitValue {
                if index % 2 == 0 {
                    byte = UInt8(nibble) << 4
                } else {
                    byte |= UInt8(nibble)
                    data.append(byte)
                }
                index += 1
            }
        }
        
        self = data
    }
    
    func hexEncodedString() -> String {
        return "0x" + map { String(format: "%02x", $0) }.joined()
    }
}