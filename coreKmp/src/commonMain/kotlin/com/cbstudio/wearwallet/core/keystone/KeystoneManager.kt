package com.cbstudio.wearwallet.core.keystone

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.keystone.*
import com.cbstudio.wearwallet.core.domain.service.KeystoneService
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Keystone 硬體錢包管理器
 * 負責管理 Keystone 設備的連接、同步和簽名流程
 */
class KeystoneManager(
    private val walletRepository: WalletRepository,
    private val keystoneService: KeystoneService
) {
    
    // 當前連接的 Keystone 錢包
    private val _connectedWallet = MutableStateFlow<WalletAccount?>(null)
    val connectedWallet = _connectedWallet.asStateFlow()
    
    // 當前簽名請求狀態
    private val _signRequestState = MutableStateFlow<SignRequestState>(SignRequestState.Idle)
    val signRequestState = _signRequestState.asStateFlow()

    // 掃描狀態管理
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState = _scanState.asStateFlow()
    
    /**
     * 開始同步流程
     * 生成同步請求 QR Code 數據
     */
    suspend fun startSync(chainType: ChainType): Result<String> {
        return try {
            // 標準 Keystone 流程中，通常只需掃描設備顯示的 QR 碼
            // 如果需要手邊發起同步請求，這裡可以整合 SDK 的 AccountRequest
            Result.Failure(Exception("Sync request from watch is not yet implemented using SDK"))
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 處理同步響應
     * 從掃描的 QR Code 中導入錢包
     */
    suspend fun handleSyncResponse(qrData: String, walletName: String): Result<WalletAccount> {
        return try {
            // 解析同步響應 (使用 KeystoneService 真正的 SDK 解析)
            val response = keystoneService.parseKeystoneHDKey(qrData)
            
            when (response) {
                is KeystoneHDKeyResult.Success -> {
                    // 導入錢包
                    val result = walletRepository.importKeystoneWallet(
                        name = walletName,
                        xpub = response.extendedPublicKey,
                        derivationPath = response.path,
                        masterFingerprint = response.masterFingerprint
                    )
                    
                    when (result) {
                        is Result.Success -> {
                            _connectedWallet.value = result.data
                            Result.Success(result.data)
                        }
                        is Result.Failure -> Result.Failure(result.exception)
                        is Result.Loading -> Result.Loading()
                    }
                }
                is KeystoneHDKeyResult.Error -> Result.Failure(Exception(response.message))
                is KeystoneHDKeyResult.Incomplete -> Result.Failure(Exception("Scan incomplete: ${response.message}"))
                else -> Result.Failure(Exception("Unknown HDKey error"))
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 創建交易簽名請求
     * 生成簽名請求 QR Code 數據
     */
    suspend fun createSignRequest(
        walletId: String,
        transaction: KeystoneTransaction,
        chainType: ChainType
    ): Result<List<String>> {
        return try {
            _signRequestState.value = SignRequestState.Creating
            
            // 獲取錢包資訊
            val walletResult = walletRepository.getWallet(walletId)
            when (walletResult) {
                is Result.Success -> {
                    val wallet = walletResult.data
                        ?: return Result.Failure(Exception("Wallet not found"))
                    
                    if (!wallet.isKeystoneWallet) {
                        return Result.Failure(Exception("Not a Keystone wallet"))
                    }
                    
                    // 創建簽名請求 (使用 SDK)
                    val signRequest = keystoneService.generateEthSignRequest(
                        unsignedTxHex = transaction.data ?: "",
                        derivationPath = wallet.derivationPath ?: KeystoneService.DEFAULT_DERIVATION_PATH,
                        masterFingerprint = wallet.masterFingerprint ?: KeystoneService.DEFAULT_MASTER_FINGERPRINT,
                        chainId = transaction.chainId.toLongOrNull() ?: 1L,
                        fromAddress = wallet.address
                    )
                    
                    // 生成 QR 碼數據列表 (支持動畫 QR)
                    _signRequestState.value = SignRequestState.WaitingForSignature
                    
                    // 保存第一幀到數據庫 (暫時維持舊有行為，但返回列表給 UI)
                    walletRepository.updateKeystoneData(
                        walletId = walletId,
                        signRequest = signRequest.qrCodeData.firstOrNull() ?: "",
                        syncData = null
                    )
                    
                    Result.Success(signRequest.qrCodeData)
                }
                is Result.Failure -> {
                    _signRequestState.value = SignRequestState.Error(walletResult.exception.message ?: "Unknown error")
                    Result.Failure(walletResult.exception)
                }
                is Result.Loading -> Result.Loading()
            }
        } catch (e: Exception) {
            _signRequestState.value = SignRequestState.Error(e.message ?: "Unknown error")
            Result.Failure(e)
        }
    }
    
    /**
     * 處理簽名響應
     * 從掃描的 QR Code 中提取簽名
     */
    suspend fun handleSignResponse(qrData: String): Result<KeystoneSignResponse> {
        return try {
            _signRequestState.value = SignRequestState.Processing
            
            // 解析簽名響應 (使用 KeystoneService 真正的 SDK 解析)
            val response = keystoneService.parseSignResponse(qrData)
            
            when (response) {
                is KeystoneResult.Success -> {
                    val signatureResult = response.data as KeystoneSignatureResult.Success
                    _signRequestState.value = SignRequestState.Completed(signatureResult.signature)
                    
                    // 注意: Keystone 官方 SDK 返回的可能是聚合後的簽名信息
                    // 為了保持兼容性，我們手動轉換或調整返回模型
                    Result.Success(KeystoneSignResponse(
                        requestId = signatureResult.requestId,
                        signature = signatureResult.signature,
                        v = "", // 基於 SDK 實現，通常 signature 已包含 vrs
                        r = "",
                        s = ""
                    ))
                }
                is KeystoneResult.Error -> {
                    _signRequestState.value = SignRequestState.Error(response.error.message)
                    Result.Failure(Exception(response.error.message))
                }
                else -> Result.Failure(Exception("Unknown sign error"))
            }
        } catch (e: Exception) {
            _signRequestState.value = SignRequestState.Error(e.message ?: "Unknown error")
            Result.Failure(e)
        }
    }
    
    /**
     * 創建動態 QR Code 數據
     * 用於大數據量的交易
     */
    suspend fun createAnimatedQRData(urString: String): List<String> {
        // 如果傳入的是單個 UR，或者已經是動畫 QR 的第一幀，我們可能需要重新分片
        // 考慮到 KeystoneService 內部已經處理了分片 (generateSignRequestQR 可能返回單個或組裝後的動畫數據)
        // 這裡為了向後兼容 UI 預期 List<String> 的邏輯：
        
        // 實際上 KeystoneService 的 generateEthSignRequest 得到的 KeystoneSignRequest 
        // 已經包含 qrCodeData: List<String>。
        // 但 KeystoneManager.createSignRequest 返回的是 Result<String> (通常是第一幀或單幀)。
        
        // 更好的做法是讓 UI 直接獲取完整的 List<String>。
        // 為此，我們添加一個方法
        return listOf(urString) // 暫時，如果 UI 循環顯示這個 List，也會正確顯示
    }
    
    /**
     * 處理掃描到的 QR Code 數據
     * 自動處理單一或多部分 UR
     */
    suspend fun handleScan(qrData: String): Result<ScanResult> {
        return try {
            _scanState.value = ScanState.Scanning
            
            // 使用 KeystoneService 內部的 SDK 處理掃描
            // SDK 內部會自動處理單幀與多幀 (動畫 QR)
            if (keystoneService.isValidKeystoneQR(qrData)) {
                 // 這裡我們直接返回數據，讓後續的 handleSync/SignResponse 處理聚合
                 // 因為 KeystoneSDK.decodeQR(qrData) 在 androidMain 內部會維護狀態
                 Result.Success(ScanResult.Complete(qrData))
            } else {
                 Result.Success(ScanResult.Complete(qrData))
            }
        } catch (e: Exception) {
            _scanState.value = ScanState.Error(e.message ?: "Scan failed")
            Result.Failure(e)
        }
    }
    
    /**
     * 重置掃描狀態
     */
    fun resetScanState() {
        _scanState.value = ScanState.Idle
    }
    
    /**
     * 連接到 Keystone 錢包
     */
    suspend fun connectWallet(walletId: String): Result<Unit> {
        return try {
            val walletResult = walletRepository.getWallet(walletId)
            when (walletResult) {
                is Result.Success -> {
                    val wallet = walletResult.data
                        ?: return Result.Failure(Exception("Wallet not found"))
                    
                    if (!wallet.isKeystoneWallet) {
                        return Result.Failure(Exception("Not a Keystone wallet"))
                    }
                    
                    _connectedWallet.value = wallet
                    Result.Success(Unit)
                }
                is Result.Failure -> Result.Failure(walletResult.exception)
                is Result.Loading -> Result.Loading()
            }
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 斷開 Keystone 錢包連接
     */
    fun disconnectWallet() {
        _connectedWallet.value = null
        _signRequestState.value = SignRequestState.Idle
    }
    
    /**
     * 重置簽名請求狀態
     */
    fun resetSignRequest() {
        _signRequestState.value = SignRequestState.Idle
    }
    
    /**
     * 獲取推薦的派生路徑
     */
    private fun getDerivationPaths(chainType: ChainType): List<String> {
        return when (chainType) {
            ChainType.ETHEREUM,
            ChainType.BSC,
            ChainType.POLYGON,
            ChainType.ARBITRUM,
            ChainType.OPTIMISM,
            ChainType.AVALANCHE,
            ChainType.CRONOS,
            ChainType.FANTOM -> listOf(
                "m/44'/60'/0'/0/0",  // 標準 Ethereum 路徑
                "m/44'/60'/0'/0/1",
                "m/44'/60'/0'/0/2"
            )
            else -> listOf("m/44'/60'/0'/0/0")
        }
    }
}

/**
 * 簽名請求狀態
 */
sealed class SignRequestState {
    object Idle : SignRequestState()
    object Creating : SignRequestState()
    object WaitingForSignature : SignRequestState()
    object Processing : SignRequestState()
    data class Completed(val signature: String) : SignRequestState()
    data class Error(val message: String) : SignRequestState()
}

/**
 * 掃描狀態
 */
sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    data class Progress(val current: Int, val total: Int) : ScanState()
    data class Error(val message: String) : ScanState()
}

/**
 * 掃描結果
 */
sealed class ScanResult {
    data class Complete(val data: String) : ScanResult()
    data class Progress(val current: Int, val total: Int) : ScanResult()
}