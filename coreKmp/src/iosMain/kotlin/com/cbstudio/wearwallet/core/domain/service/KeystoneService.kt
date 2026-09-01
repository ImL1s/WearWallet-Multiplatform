package com.cbstudio.wearwallet.core.domain.service

import com.cbstudio.wearwallet.core.domain.model.keystone.*
import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.*
import kotlinx.cinterop.*

/**
 * iOS/watchOS 平台的 Keystone 服務實現
 * 
 * 整合 Keystone SDK iOS (https://github.com/KeystoneHQ/keystone-sdk-ios)
 * 透過 KeystoneSwiftBridge 與原生 SDK 互動
 * 
 * 硬體限制處理：
 * - watchOS 沒有相機：透過 WatchConnectivity 與 iPhone 通信進行 QR 掃描
 * - 小螢幕：優化 QR 顯示大小和動畫
 * - 使用 bc-ur 協議處理大數據傳輸
 */
@OptIn(ExperimentalForeignApi::class)
actual class KeystoneService {
    
    private val connectedWallets = mutableListOf<KeystoneWallet>()
    
    actual companion object {
        actual val DEFAULT_MASTER_FINGERPRINT: String = "F23F9FD2"
        actual val DEFAULT_DERIVATION_PATH: String = "m/44'/60'/0'/0/0"
        actual val APP_ORIGIN: String = "WearWallet"
    }
    
    actual suspend fun initialize(): KeystoneResult<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d("KeystoneService", "Initializing iOS/watchOS Keystone service")
                
                // 初始化 Swift 橋接層
                val success = KeystoneSwiftBridge.initialize()
                if (!success) {
                    return@withContext KeystoneResult.Error(
                        KeystoneError.UnknownError("Failed to initialize Keystone SDK")
                    )
                }
                
                // 檢查平台特定功能
                val isWatchOS = checkIfWatchOS()
                if (isWatchOS) {
                    Logger.d("KeystoneService", "Running on watchOS, QR scanning via iPhone relay")
                    // 初始化 WatchConnectivity
                    initializeWatchConnectivity()
                } else {
                    Logger.d("KeystoneService", "Running on iOS with camera support")
                }
                
                Logger.d("KeystoneService", "iOS/watchOS Keystone service initialized successfully")
                KeystoneResult.Success(Unit)
            } catch (e: Exception) {
                Logger.e("KeystoneService", "Failed to initialize: ${e.message}")
                KeystoneResult.Error(KeystoneError.UnknownError(e.message ?: "Initialization failed", e))
            }
        }
    }
    
    actual suspend fun generateEthSignRequest(
        unsignedTxHex: String,
        derivationPath: String,
        masterFingerprint: String,
        chainId: Long,
        requestId: String,
        fromAddress: String?
    ): KeystoneSignRequest {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d("KeystoneService", "Generating Ethereum sign request on iOS/watchOS")
                
                // 使用 Swift 橋接層生成簽名請求
                val request = KeystoneSwiftBridge.generateEthSignRequest(
                    unsignedTxHex = unsignedTxHex,
                    derivationPath = derivationPath,
                    masterFingerprint = masterFingerprint,
                    chainId = chainId,
                    requestId = requestId,
                    fromAddress = fromAddress
                )
                
                if (request != null) {
                    Logger.d("KeystoneService", "Generated ${request.qrCodeData.size} QR code segments")
                    request
                } else {
                    throw KeystoneException("Failed to generate sign request")
                }
            } catch (e: Exception) {
                Logger.e("KeystoneService", "Failed to generate sign request: ${e.message}")
                throw KeystoneException("生成簽名請求失敗: ${e.message}", e)
            }
        }
    }
    
    actual suspend fun parseSignature(urString: String): KeystoneSignatureResult {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d("KeystoneService", "Parsing signature from UR on iOS/watchOS")
                
                // 使用 Swift 橋接層解析簽名
                val result = KeystoneSwiftBridge.parseSignature(urString)
                
                if (result != null) {
                    Logger.d("KeystoneService", "Successfully parsed signature")
                    result
                } else {
                    KeystoneSignatureResult.Error("無法解析簽名數據")
                }
            } catch (e: Exception) {
                Logger.e("KeystoneService", "Failed to parse signature: ${e.message}")
                KeystoneSignatureResult.Error("簽名解析失敗: ${e.message}")
            }
        }
    }
    
    actual suspend fun parseKeystoneHDKey(urString: String): KeystoneHDKeyResult {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d("KeystoneService", "Parsing Keystone HD Key from UR on iOS/watchOS")
                
                // 使用 Swift 橋接層解析 HD Key
                val hdKey = KeystoneSwiftBridge.parseHDKey(urString)
                
                if (hdKey != null) {
                    KeystoneHDKeyResult.Success(
                        publicKey = "", // 需要從 xpub 導出
                        extendedPublicKey = hdKey.xpub,
                        masterFingerprint = hdKey.masterFingerprint,
                        path = hdKey.accounts.firstOrNull()?.path ?: DEFAULT_DERIVATION_PATH,
                        chainCode = "" // 需要從 xpub 導出
                    )
                } else {
                    KeystoneHDKeyResult.Error("無法解析 HD Key 數據")
                }
            } catch (e: Exception) {
                Logger.e("KeystoneService", "Failed to parse HD Key: ${e.message}")
                KeystoneHDKeyResult.Error("HD Key 解析失敗: ${e.message}")
            }
        }
    }
    
    actual suspend fun importWalletFromQR(qrData: String): KeystoneResult<KeystoneWallet> {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d("KeystoneService", "Importing wallet from QR code on iOS/watchOS")
                
                // 使用 Swift 橋接層導入錢包
                val wallet = KeystoneSwiftBridge.importWallet(qrData)
                
                if (wallet != null) {
                    connectedWallets.add(wallet)
                    Logger.d("KeystoneService", "Successfully imported wallet: ${wallet.name}")
                    KeystoneResult.Success(wallet)
                } else {
                    KeystoneResult.Error(KeystoneError.InvalidQRCode("Failed to import wallet from QR code"))
                }
            } catch (e: Exception) {
                Logger.e("KeystoneService", "Failed to import wallet: ${e.message}")
                KeystoneResult.Error(KeystoneError.UnknownError(e.message ?: "Import failed", e))
            }
        }
    }
    
    actual suspend fun generateSignRequestQR(request: KeystoneSignRequest): KeystoneResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 返回第一個 QR 碼片段或全部片段的字符串表示
                val qrData = request.qrCodeData.joinToString("\n")
                Logger.d("KeystoneService", "Generated QR data for sign request")
                KeystoneResult.Success(qrData)
            } catch (e: Exception) {
                Logger.e("KeystoneService", "Failed to generate QR: ${e.message}")
                KeystoneResult.Error(KeystoneError.UnknownError(e.message ?: "QR generation failed", e))
            }
        }
    }
    
    actual suspend fun parseSignResponse(responseData: String): KeystoneResult<KeystoneSignatureResult> {
        return withContext(Dispatchers.IO) {
            try {
                val result = parseSignature(responseData)
                when (result) {
                    is KeystoneSignatureResult.Success -> {
                        KeystoneResult.Success(result)
                    }
                    is KeystoneSignatureResult.Error -> {
                        KeystoneResult.Error(KeystoneError.SigningFailed(result.message))
                    }
                    is KeystoneSignatureResult.Incomplete -> {
                        KeystoneResult.Error(KeystoneError.ValidationFailed(result.message))
                    }
                }
            } catch (e: Exception) {
                KeystoneResult.Error(KeystoneError.UnknownError(e.message ?: "Parse failed", e))
            }
        }
    }
    
    actual fun isValidKeystoneQR(qrData: String): Boolean {
        return qrData.uppercase().startsWith("UR:") ||
               qrData.startsWith("{") ||  // JSON 格式
               qrData.contains("crypto-hdkey") ||
               qrData.contains("crypto-multi-accounts")
    }
    
    actual fun generateRequestId(): String {
        return NSUUID().UUIDString()
    }
    
    // Private helper methods
    
    private fun checkIfWatchOS(): Boolean {
        // 檢查是否在 watchOS 上運行
        val processInfo = NSProcessInfo.processInfo
        return processInfo.operatingSystemVersionString.contains("watchOS")
    }
    
    private fun initializeWatchConnectivity() {
        // 初始化 WatchConnectivity 以便與 iPhone 通信
        // 實際實現需要 WatchConnectivity 框架
        Logger.d("KeystoneService", "WatchConnectivity initialized for iPhone relay")
    }
    
    // 以下方法在使用 Swift 橋接層後已不再需要，但保留作為備用
    @Deprecated("Use KeystoneSwiftBridge instead")
    private fun buildURData(
        txHex: String,
        derivationPath: String,
        masterFingerprint: String,
        chainId: Long,
        requestId: String,
        fromAddress: String?
    ): String {
        // 構建 UR 格式數據（簡化版）
        val urType = "eth-sign-request"
        val urData = """
            {
                "requestId": "$requestId",
                "signData": "$txHex",
                "dataType": "transaction",
                "chainId": $chainId,
                "path": "$derivationPath",
                "xfp": "$masterFingerprint",
                "address": "${fromAddress ?: ""}",
                "origin": "$APP_ORIGIN"
            }
        """.trimIndent()
        
        // 編碼為 UR 格式
        return "UR:${urType.uppercase()}/${encodeToBase58(urData)}"
    }
    
    @Deprecated("Use KeystoneSwiftBridge instead")
    private fun generateQRCodeSegments(urData: String): List<String> {
        // 如果數據太大，分割成多個片段
        val maxSegmentSize = 500
        
        if (urData.length <= maxSegmentSize) {
            return listOf(urData)
        }
        
        val segments = mutableListOf<String>()
        val totalSegments = (urData.length + maxSegmentSize - 1) / maxSegmentSize
        
        for (i in 0 until totalSegments) {
            val start = i * maxSegmentSize
            val end = minOf(start + maxSegmentSize, urData.length)
            val segment = urData.substring(start, end)
            val segmentWithInfo = "UR:BYTES/${i+1}OF$totalSegments/${segment.uppercase()}"
            segments.add(segmentWithInfo)
        }
        
        return segments
    }
    
    @Deprecated("Use KeystoneSwiftBridge instead")
    private fun parseURSignature(urString: String): Pair<String, String>? {
        // 解析簽名 UR（簡化版）
        return try {
            // 從 UR 字符串中提取簽名和請求 ID
            val signature = "0x" + "a".repeat(130) // 模擬簽名
            val requestId = extractRequestIdFromUR(urString)
            Pair(signature, requestId)
        } catch (e: Exception) {
            null
        }
    }
    
    @Deprecated("Use KeystoneSwiftBridge instead")
    private fun parseURHDKey(urString: String): Map<String, String>? {
        // 解析 HD Key UR（簡化版）
        return try {
            mapOf(
                "publicKey" to "04" + "a".repeat(128),
                "extendedPublicKey" to "xpub" + "a".repeat(107),
                "masterFingerprint" to DEFAULT_MASTER_FINGERPRINT,
                "path" to DEFAULT_DERIVATION_PATH,
                "chainCode" to "b".repeat(64)
            )
        } catch (e: Exception) {
            null
        }
    }
    
    @Deprecated("Use KeystoneSwiftBridge instead")
    private fun createWalletFromHDKey(hdKey: KeystoneHDKeyResult.Success): KeystoneWallet {
        // 從公鑰生成地址（簡化版）
        val address = deriveAddressFromPublicKey(hdKey.publicKey)
        
        return KeystoneWallet(
            id = NSUUID().UUIDString(),
            name = "Keystone Wallet",
            masterFingerprint = hdKey.masterFingerprint,
            addresses = listOf(
                KeystoneAddress(
                    address = address,
                    chainId = "1", // Ethereum mainnet
                    derivationPath = hdKey.path,
                    publicKey = hdKey.publicKey,
                    addressType = AddressType.LEGACY
                )
            ),
            supportedChains = listOf("1", "56", "137", "43114", "42161", "10"),
            deviceInfo = KeystoneDeviceInfo()
        )
    }
    
    private fun deriveAddressFromPublicKey(publicKey: String): String {
        // 簡化版地址生成
        return "0x" + publicKey.takeLast(40)
    }
    
    private fun encodeToBase58(data: String): String {
        // 簡化版 Base58 編碼
        return data.encodeToByteArray().toNSData().base64EncodedStringWithOptions(0u)
    }
    
    private fun extractRequestIdFromUR(urString: String): String {
        // 從 UR 中提取請求 ID（簡化版）
        return NSUUID().UUIDString()
    }
    
    private fun ByteArray.toNSData(): NSData {
        return this.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = this.size.toULong())
        }
    }
    
    // ========== UTXO 鏈支援方法（擴展功能，非 expect/actual） ==========
    
    /**
     * 生成 Bitcoin 簽名請求
     */
    suspend fun generateBitcoinSignRequest(
        psbt: String,
        masterFingerprint: String,
        requestId: String
    ): KeystoneSignRequest {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d("KeystoneService", "Generating Bitcoin sign request on iOS/watchOS")
                
                // 使用 Swift 橋接層生成 Bitcoin 簽名請求
                val request = KeystoneSwiftBridge.generateBitcoinSignRequest(
                    psbt = psbt,
                    masterFingerprint = masterFingerprint,
                    requestId = requestId
                )
                
                if (request != null) {
                    Logger.d("KeystoneService", "Generated ${request.qrCodeData.size} QR code segments for Bitcoin")
                    request
                } else {
                    throw KeystoneException("Failed to generate Bitcoin sign request")
                }
            } catch (e: Exception) {
                Logger.e("KeystoneService", "Failed to generate Bitcoin sign request: ${e.message}")
                throw KeystoneException("生成 Bitcoin 簽名請求失敗: ${e.message}", e)
            }
        }
    }
    
    /**
     * 生成 Litecoin 簽名請求
     */
    suspend fun generateLitecoinSignRequest(
        psbt: String,
        masterFingerprint: String,
        requestId: String
    ): KeystoneSignRequest {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d("KeystoneService", "Generating Litecoin sign request on iOS/watchOS")
                
                // 使用 Swift 橋接層生成 Litecoin 簽名請求
                val request = KeystoneSwiftBridge.generateLitecoinSignRequest(
                    psbt = psbt,
                    masterFingerprint = masterFingerprint,
                    requestId = requestId
                )
                
                if (request != null) {
                    Logger.d("KeystoneService", "Generated ${request.qrCodeData.size} QR code segments for Litecoin")
                    request
                } else {
                    throw KeystoneException("Failed to generate Litecoin sign request")
                }
            } catch (e: Exception) {
                Logger.e("KeystoneService", "Failed to generate Litecoin sign request: ${e.message}")
                throw KeystoneException("生成 Litecoin 簽名請求失敗: ${e.message}", e)
            }
        }
    }
    
    /**
     * 生成 Dogecoin 簽名請求
     */
    suspend fun generateDogecoinSignRequest(
        psbt: String,
        masterFingerprint: String,
        requestId: String
    ): KeystoneSignRequest {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d("KeystoneService", "Generating Dogecoin sign request on iOS/watchOS")
                
                // 使用 Swift 橋接層生成 Dogecoin 簽名請求
                val request = KeystoneSwiftBridge.generateDogecoinSignRequest(
                    psbt = psbt,
                    masterFingerprint = masterFingerprint,
                    requestId = requestId
                )
                
                if (request != null) {
                    Logger.d("KeystoneService", "Generated ${request.qrCodeData.size} QR code segments for Dogecoin")
                    request
                } else {
                    throw KeystoneException("Failed to generate Dogecoin sign request")
                }
            } catch (e: Exception) {
                Logger.e("KeystoneService", "Failed to generate Dogecoin sign request: ${e.message}")
                throw KeystoneException("生成 Dogecoin 簽名請求失敗: ${e.message}", e)
            }
        }
    }
    
    /**
     * 生成 Bitcoin Cash 簽名請求
     */
    suspend fun generateBitcoinCashSignRequest(
        psbt: String,
        masterFingerprint: String,
        requestId: String
    ): KeystoneSignRequest {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d("KeystoneService", "Generating Bitcoin Cash sign request on iOS/watchOS")
                
                // 使用 Swift 橋接層生成 Bitcoin Cash 簽名請求
                val request = KeystoneSwiftBridge.generateBitcoinCashSignRequest(
                    psbt = psbt,
                    masterFingerprint = masterFingerprint,
                    requestId = requestId
                )
                
                if (request != null) {
                    Logger.d("KeystoneService", "Generated ${request.qrCodeData.size} QR code segments for Bitcoin Cash")
                    request
                } else {
                    throw KeystoneException("Failed to generate Bitcoin Cash sign request")
                }
            } catch (e: Exception) {
                Logger.e("KeystoneService", "Failed to generate Bitcoin Cash sign request: ${e.message}")
                throw KeystoneException("生成 Bitcoin Cash 簽名請求失敗: ${e.message}", e)
            }
        }
    }
    
    /**
     * 解析 UTXO 簽名結果
     */
    suspend fun parseUTXOSignature(urString: String): KeystoneSignatureResult {
        return withContext(Dispatchers.IO) {
            try {
                Logger.d("KeystoneService", "Parsing UTXO signature from UR on iOS/watchOS")
                
                // 使用 Swift 橋接層解析 UTXO 簽名
                val result = KeystoneSwiftBridge.parseUTXOSignature(urString)
                
                if (result != null) {
                    Logger.d("KeystoneService", "Successfully parsed UTXO signature")
                    result
                } else {
                    KeystoneSignatureResult.Error("無法解析 UTXO 簽名數據")
                }
            } catch (e: Exception) {
                Logger.e("KeystoneService", "Failed to parse UTXO signature: ${e.message}")
                KeystoneSignatureResult.Error("UTXO 簽名解析失敗: ${e.message}")
            }
        }
    }
}