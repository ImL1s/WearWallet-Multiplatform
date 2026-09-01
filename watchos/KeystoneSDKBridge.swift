import Foundation
import KeystoneSDK
import URRegistryFFI

/**
 * KeystoneSDK Swift Bridge
 * 
 * 這個類提供 Kotlin/Native 可以調用的 Swift 接口
 * 用於與 Keystone 硬體錢包進行真實的通信
 * 
 * 依賴：
 * - KeystoneSDK: https://github.com/KeystoneHQ/keystone-sdk-ios
 * - URRegistryFFI: https://github.com/KeystoneHQ/keystone-sdk-ios-deps
 */
@objc public class KeystoneSDKBridge: NSObject {
    
    // MARK: - UR 解碼
    
    /**
     * 解碼 UR 字符串
     * 
     * @param urString UR 格式的字符串 (例如: "ur:crypto-hdkey/...")
     * @return 包含 type 和 cbor 數據的字典，失敗返回 nil
     */
    @objc public static func decodeUR(_ urString: String) -> NSDictionary? {
        do {
            let ur = try UR(urString: urString)
            return [
                "type": ur.type,
                "cbor": ur.cbor
            ]
        } catch {
            print("KeystoneSDKBridge: Failed to decode UR - \(error)")
            return nil
        }
    }
    
    // MARK: - 以太坊簽名請求
    
    /**
     * 編碼以太坊簽名請求
     * 
     * @param requestId 請求 ID (hex string)
     * @param signData 要簽名的數據 (hex string)
     * @param dataType 數據類型 (1=transaction, 2=typedData, 3=personalMessage)
     * @param chainId 鏈 ID
     * @param path 派生路徑 (例如: "m/44'/60'/0'/0/0")
     * @param xfp 主指紋 (hex string)
     * @param origin 請求來源標識
     * @return UR 編碼的字符串，失敗返回 nil
     */
    @objc public static func encodeEthSignRequestWithRequestId(
        _ requestId: String,
        signData: String,
        dataType: Int,
        chainId: Int,
        path: String,
        xfp: String,
        origin: String
    ) -> String? {
        do {
            // 轉換 hex string 到 Data
            let requestIdData = Data(hexString: requestId) ?? Data()
            let signDataBytes = Data(hexString: signData) ?? Data()
            
            // 確定數據類型
            let ethDataType: DataType
            switch dataType {
            case 1:
                ethDataType = .transaction
            case 2:
                ethDataType = .typedData
            case 3:
                ethDataType = .personalMessage
            default:
                ethDataType = .transaction
            }
            
            // 創建以太坊簽名請求
            let ethSignRequest = EthSignRequest(
                requestId: requestIdData,
                signData: signDataBytes,
                dataType: ethDataType,
                chainId: chainId,
                path: path,
                xfp: xfp,
                origin: origin,
                timestamp: Int64(Date().timeIntervalSince1970 * 1000)
            )
            
            // 轉換為 UR
            let ur = try ethSignRequest.toUR()
            return ur.string
            
        } catch {
            print("KeystoneSDKBridge: Failed to encode ETH sign request - \(error)")
            return nil
        }
    }
    
    // MARK: - 以太坊簽名解析
    
    /**
     * 解析以太坊簽名
     * 
     * @param urString 包含簽名的 UR 字符串
     * @return 包含 requestId, signature, origin 的字典，失敗返回 nil
     */
    @objc public static func parseEthSignature(_ urString: String) -> NSDictionary? {
        do {
            let ur = try UR(urString: urString)
            let ethSignature = try EthSignature(ur: ur)
            
            // 從簽名數據中提取 r, s, v 值
            let signature = ethSignature.signature
            let sigHex = signature.hexString
            
            // 標準 ECDSA 簽名長度為 65 字節 (r:32, s:32, v:1)
            var r = ""
            var s = ""
            var v = ""
            
            if sigHex.count >= 130 { // 65 bytes * 2 (hex)
                let startIndex = sigHex.startIndex
                r = String(sigHex[startIndex..<sigHex.index(startIndex, offsetBy: 64)])
                s = String(sigHex[sigHex.index(startIndex, offsetBy: 64)..<sigHex.index(startIndex, offsetBy: 128)])
                if sigHex.count >= 130 {
                    v = String(sigHex[sigHex.index(startIndex, offsetBy: 128)..<sigHex.index(startIndex, offsetBy: 130)])
                }
            }
            
            return [
                "requestId": ethSignature.requestId.hexString,
                "signature": [
                    "r": "0x\(r)",
                    "s": "0x\(s)",
                    "v": "0x\(v)"
                ],
                "origin": ethSignature.origin ?? ""
            ]
        } catch {
            print("KeystoneSDKBridge: Failed to parse ETH signature - \(error)")
            return nil
        }
    }
    
    // MARK: - HD Key 解析
    
    /**
     * 解析 HD Key
     * 
     * @param urString 包含 HD Key 的 UR 字符串
     * @return 包含密鑰信息的字典，失敗返回 nil
     */
    @objc public static func parseHDKey(_ urString: String) -> NSDictionary? {
        do {
            let ur = try UR(urString: urString)
            let hdKey = try CryptoHDKey(ur: ur)
            
            var result: [String: Any] = [:]
            
            // 主指紋
            if let fingerprint = hdKey.parentFingerprint {
                result["fingerprint"] = fingerprint.hexString
            }
            
            // 公鑰
            if let publicKey = hdKey.publicKey {
                result["publicKey"] = publicKey.hexString
            }
            
            // 鏈碼
            if let chainCode = hdKey.chainCode {
                result["chainCode"] = chainCode.hexString
            }
            
            // 派生路徑
            if let components = hdKey.derivationPath?.components {
                let path = components.map { component in
                    if component.hardened {
                        return "\(component.index)'"
                    } else {
                        return "\(component.index)"
                    }
                }.joined(separator: "/")
                result["derivationPath"] = "m/\(path)"
            }
            
            // 名稱和備註
            result["name"] = hdKey.name ?? ""
            result["note"] = hdKey.note ?? ""
            
            return result as NSDictionary
            
        } catch {
            print("KeystoneSDKBridge: Failed to parse HD Key - \(error)")
            return nil
        }
    }
    
    // MARK: - Fountain Code 支援
    
    /**
     * 創建多部分 UR 編碼器
     * 
     * @param type UR 類型
     * @param data CBOR 數據
     * @param maxFragmentLen 最大片段長度
     * @return 編碼器實例
     */
    @objc public static func createUREncoder(
        type: String,
        data: Data,
        maxFragmentLen: Int
    ) -> UREncoder? {
        do {
            let ur = try UR(type: type, cbor: data)
            return UREncoder(ur: ur, maxFragmentLen: maxFragmentLen)
        } catch {
            print("KeystoneSDKBridge: Failed to create UR encoder - \(error)")
            return nil
        }
    }
    
    /**
     * 創建多部分 UR 解碼器
     * 
     * @return 解碼器實例
     */
    @objc public static func createURDecoder() -> URDecoder {
        return URDecoder()
    }
}

// MARK: - 輔助擴展

extension Data {
    /**
     * 從十六進制字符串創建 Data
     */
    init?(hexString: String) {
        let hex = hexString.replacingOccurrences(of: "0x", with: "")
        let len = hex.count / 2
        var data = Data(capacity: len)
        var index = hex.startIndex
        
        for _ in 0..<len {
            let nextIndex = hex.index(index, offsetBy: 2)
            guard let byte = UInt8(hex[index..<nextIndex], radix: 16) else {
                return nil
            }
            data.append(byte)
            index = nextIndex
        }
        
        self = data
    }
    
    /**
     * 轉換為十六進制字符串
     */
    var hexString: String {
        return map { String(format: "%02x", $0) }.joined()
    }
}

// MARK: - UR 編碼器擴展

extension UREncoder {
    /**
     * 獲取下一個片段
     */
    @objc public func nextPart() -> String {
        return self.next()
    }
    
    /**
     * 獲取總片段數
     */
    @objc public var fragmentsCount: Int {
        return self.fragmentsCount
    }
}

// MARK: - UR 解碼器擴展

extension URDecoder {
    /**
     * 接收片段
     */
    @objc public func receivePart(_ part: String) -> Bool {
        do {
            try self.receive(part)
            return self.isComplete
        } catch {
            print("KeystoneSDKBridge: Failed to receive UR part - \(error)")
            return false
        }
    }
    
    /**
     * 檢查是否完成
     */
    @objc public var isComplete: Bool {
        return self.isComplete
    }
    
    /**
     * 獲取進度
     */
    @objc public var progress: Float {
        return Float(self.progress)
    }
    
    /**
     * 獲取結果
     */
    @objc public func getResult() -> UR? {
        return self.result
    }
}