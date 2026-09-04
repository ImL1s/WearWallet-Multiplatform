import Foundation
import KeystoneSDK
import URKit

/**
 * Keystone SDK iOS/watchOS 橋接實現
 * 
 * 這個類別提供 Kotlin/Native 與 Keystone SDK 之間的橋接
 * 用於處理硬體錢包的 QR Code 通信和交易簽名
 */
@objc public class KeystoneSwiftBridge: NSObject {
    
    private static let shared = KeystoneSwiftBridge()
    private var currentWallet: KeystoneWalletData?
    
    // MARK: - Initialization
    
    @objc public static func initialize() -> Bool {
        print("KeystoneSwiftBridge: Initializing Keystone SDK for watchOS")
        
        // Keystone SDK 不需要特別初始化
        // 但可以在這裡設置任何必要的配置
        
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
        print("  Chain ID: \(chainId)")
        print("  Path: \(derivationPath)")
        print("  Master FP: \(masterFingerprint)")
        
        do {
            // 清理交易數據
            let cleanTxHex = unsignedTxHex.hasPrefix("0x") 
                ? String(unsignedTxHex.dropFirst(2)) 
                : unsignedTxHex
            
            // 創建簽名請求數據
            let signData = Data(hex: cleanTxHex)
            
            // 創建 ETH 簽名請求
            let ethSignRequest = EthSignRequest(
                requestId: Data(requestId.utf8),
                signData: signData,
                dataType: .transaction,
                chainId: Int(chainId),
                path: derivationPath,
                xfp: Data(hex: masterFingerprint),
                address: fromAddress,
                origin: "WearWallet"
            )
            
            // 編碼為 UR
            let encoder = UREncoder(ethSignRequest, maxFragmentLen: 200) // watchOS 使用較小的片段
            
            // 生成 QR Code 數據
            var qrCodes: [String] = []
            
            if encoder.isMultiPart {
                // 多片段動畫 QR Code
                for _ in 0..<encoder.fragmentsCount {
                    if let part = encoder.nextPart() {
                        qrCodes.append(part)
                    }
                }
            } else {
                // 單一 QR Code
                if let singleQR = encoder.nextPart() {
                    qrCodes.append(singleQR)
                }
            }
            
            print("KeystoneSwiftBridge: Generated \(qrCodes.count) QR code segments")
            
            return KeystoneSignRequestData(
                requestId: requestId,
                qrCodeData: qrCodes,
                urString: qrCodes.first ?? ""
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
            // 創建 UR 解碼器
            let decoder = URDecoder()
            decoder.receivePart(urString)
            
            // 檢查是否完成
            guard decoder.isComplete else {
                print("KeystoneSwiftBridge: UR decoding incomplete")
                return nil
            }
            
            // 獲取結果
            guard let ur = try? decoder.result() else {
                print("KeystoneSwiftBridge: Failed to get UR result")
                return nil
            }
            
            // 解析 ETH 簽名
            if let ethSignature = try? EthSignature(ur: ur) {
                let signatureHex = ethSignature.signature.hexEncodedString()
                let requestIdString = String(data: ethSignature.requestId, encoding: .utf8) ?? ""
                
                print("KeystoneSwiftBridge: Successfully parsed signature")
                print("  Request ID: \(requestIdString)")
                print("  Signature: \(signatureHex.prefix(20))...")
                
                return KeystoneSignatureData(
                    signature: signatureHex,
                    requestId: requestIdString
                )
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
            decoder.receivePart(urString)
            
            guard decoder.isComplete else {
                print("KeystoneSwiftBridge: UR decoding incomplete")
                return nil
            }
            
            guard let ur = try? decoder.result() else {
                print("KeystoneSwiftBridge: Failed to get UR result")
                return nil
            }
            
            // 嘗試解析 Crypto HD Key
            if let cryptoHDKey = try? CryptoHDKey(ur: ur) {
                print("KeystoneSwiftBridge: Parsed CryptoHDKey")
                
                // 從 HD Key 導出帳戶
                var accounts: [KeystoneAccountData] = []
                
                // 預設導出前 5 個地址
                for i in 0..<5 {
                    let accountPath = "m/44'/60'/0'/0/\(i)"
                    let account = KeystoneAccountData(
                        path: accountPath,
                        xpub: cryptoHDKey.bip32Key ?? "",
                        address: deriveAddress(from: cryptoHDKey, index: i),
                        chainId: "1" // Ethereum mainnet
                    )
                    accounts.append(account)
                }
                
                return KeystoneHDKeyData(
                    name: cryptoHDKey.name ?? "Keystone Wallet",
                    masterFingerprint: cryptoHDKey.origin?.fingerprint.hexEncodedString() ?? "",
                    xpub: cryptoHDKey.bip32Key ?? "",
                    accounts: accounts
                )
            }
            
            // 嘗試解析 Crypto Multi-Accounts
            if let multiAccounts = try? CryptoMultiAccounts(ur: ur) {
                print("KeystoneSwiftBridge: Parsed CryptoMultiAccounts")
                
                var accounts: [KeystoneAccountData] = []
                
                for key in multiAccounts.keys {
                    let account = KeystoneAccountData(
                        path: key.chainAndPath?.path ?? "",
                        xpub: key.chainAndPath?.xpub ?? "",
                        address: deriveAddressFromKey(key),
                        chainId: "1"
                    )
                    accounts.append(account)
                }
                
                return KeystoneHDKeyData(
                    name: multiAccounts.device ?? "Keystone Wallet",
                    masterFingerprint: multiAccounts.masterFingerprint.hexEncodedString(),
                    xpub: "", // Multi-accounts 沒有單一 xpub
                    accounts: accounts
                )
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
            print("KeystoneSwiftBridge: Failed to parse HD Key")
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
        
        print("KeystoneSwiftBridge: Successfully imported wallet: \(wallet.name)")
        
        return wallet
    }
    
    // MARK: - Sync Request Generation
    
    @objc public static func generateSyncRequest() -> String? {
        print("KeystoneSwiftBridge: Generating sync request")
        
        do {
            // 創建同步請求
            let syncRequest = CryptoMultiAccounts(
                masterFingerprint: Data(hex: shared.currentWallet?.masterFingerprint ?? ""),
                keys: [],
                device: "WearWallet Watch",
                deviceId: UUID().uuidString
            )
            
            // 編碼為 UR
            let encoder = UREncoder(syncRequest, maxFragmentLen: 200)
            return encoder.nextPart()
            
        } catch {
            print("KeystoneSwiftBridge: Failed to generate sync request: \(error)")
            return nil
        }
    }
    
    // MARK: - Helper Methods
    
    private static func deriveAddress(from hdKey: CryptoHDKey, index: Int) -> String {
        // 實際實現需要使用加密庫來從公鑰導出地址
        // 這裡返回模擬地址用於測試
        let mockAddress = "0x" + String(format: "%040x", index + 1)
        return mockAddress
    }
    
    private static func deriveAddressFromKey(_ key: CryptoMultiAccountsKey) -> String {
        // 從帳戶信息導出地址
        if let address = key.address {
            return address
        }
        // 返回模擬地址
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
        super.init()
    }
}

@objc public class KeystoneSignatureData: NSObject {
    @objc public let signature: String
    @objc public let requestId: String
    
    @objc public init(signature: String, requestId: String) {
        self.signature = signature
        self.requestId = requestId
        super.init()
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
        super.init()
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
        super.init()
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
        super.init()
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
        super.init()
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

// MARK: - WatchConnectivity Support

#if os(watchOS)
import WatchConnectivity

extension KeystoneSwiftBridge: WCSessionDelegate {
    
    public func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {
        if let error = error {
            print("KeystoneSwiftBridge: WatchConnectivity activation failed: \(error)")
        } else {
            print("KeystoneSwiftBridge: WatchConnectivity activated")
        }
    }
    
    // 請求 iPhone 掃描 QR Code
    public static func requestQRScan(completion: @escaping (String?) -> Void) {
        guard WCSession.default.isReachable else {
            print("KeystoneSwiftBridge: iPhone not reachable")
            completion(nil)
            return
        }
        
        WCSession.default.sendMessage(
            ["action": "scanQR", "type": "keystone"],
            replyHandler: { response in
                if let qrData = response["qrData"] as? String {
                    completion(qrData)
                } else {
                    completion(nil)
                }
            },
            errorHandler: { error in
                print("KeystoneSwiftBridge: Failed to request QR scan: \(error)")
                completion(nil)
            }
        )
    }
    
    // 發送 QR Code 到 iPhone 顯示
    public static func sendQRToPhone(qrData: String, completion: @escaping (Bool) -> Void) {
        guard WCSession.default.isReachable else {
            print("KeystoneSwiftBridge: iPhone not reachable")
            completion(false)
            return
        }
        
        WCSession.default.sendMessage(
            ["action": "displayQR", "qrData": qrData],
            replyHandler: { _ in
                completion(true)
            },
            errorHandler: { error in
                print("KeystoneSwiftBridge: Failed to send QR to phone: \(error)")
                completion(false)
            }
        )
    }
}
#endif