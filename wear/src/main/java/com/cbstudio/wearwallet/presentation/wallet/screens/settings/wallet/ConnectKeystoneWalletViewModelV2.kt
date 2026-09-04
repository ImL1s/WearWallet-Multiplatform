package com.cbstudio.wearwallet.presentation.wallet.screens.settings.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.domain.model.keystone.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cbstudio.wearwallet.presentation.service.WearCommunicationRepository
import com.cbstudio.wearwallet.core.keystone.KeystoneManager
import com.cbstudio.wearwallet.core.keystone.ScanResult

/**
 * 連接 Keystone 錢包 ViewModel V2 - 整合 coreKmp
 * 使用 coreKmp 的 KeystoneManager 實現真正的硬體錢包連接
 */
class ConnectKeystoneWalletViewModelV2(
    private val keystoneManager: KeystoneManager
) : ViewModel() {
    
    // private val keystoneService = KeystoneService() // Legacy service removed
    private val communicationRepository = WearCommunicationRepository.getInstance()
    
    // UI 狀態
    private val _uiState = MutableStateFlow(KeystoneConnectUiState())
    val uiState: StateFlow<KeystoneConnectUiState> = _uiState.asStateFlow()
    
    // 連接狀態
    private val _connectionEvent = MutableSharedFlow<ConnectionEvent>()
    val connectionEvent: SharedFlow<ConnectionEvent> = _connectionEvent.asSharedFlow()
    
    init {
        initializeService()
        observeKeystoneConnectResults()
    }
    
    private fun initializeService() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // KeystoneManager 不需要顯式初始化，因為它是純邏輯組件
            // 實際的相機檢查由 UI 或手機端負責
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    isInitialized = true,
                    statusMessage = "Keystone 服務已就緒"
                )
            }
        }
    }
    
    /**
     * 監聽 Keystone 連接結果
     */
    private fun observeKeystoneConnectResults() {
        viewModelScope.launch {
            communicationRepository.keystoneConnectResults.collect { urData ->
                // 收到來自手機的 Keystone 掃描結果
                processQrCode(urData)
            }
        }
    }
    
    /**
     * 處理掃描到的 QR Code
     */
    fun processQrCode(qrData: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            // 1. 先處理掃描 (可能涉及多部分組裝)
            when (val scanResult = keystoneManager.handleScan(qrData)) {
                is com.cbstudio.wearwallet.core.common.Result.Success -> {
                    when (val resultData = scanResult.data) {
                        is ScanResult.Complete -> {
                            // 掃描完成，得到的完整 UR 數據
                            val completeUr = resultData.data
                            
                            // 優先使用 KeystoneService 判斷類型與解析
                            // 嘗試解析為 HD Key (導入錢包)
                            when (val hdResult = keystoneManager.handleSyncResponse(completeUr, "Keystone Wallet")) {
                                is com.cbstudio.wearwallet.core.common.Result.Success -> {
                                    val wallet = hdResult.data
                                    _uiState.update {
                                        it.copy(
                                            isLoading = false,
                                            connectedWallet = wallet.toLegacyKeystoneWallet(),
                                            statusMessage = "成功連接 ${wallet.name}"
                                        )
                                    }
                                    _connectionEvent.emit(ConnectionEvent.Success(wallet.toLegacyKeystoneWallet()))
                                    return@launch
                                }
                                else -> { /* 繼續嘗試解析為簽名 */ }
                            }

                            // 嘗試解析為簽名結果
                            when (val signResult = keystoneManager.handleSignResponse(completeUr)) {
                                is com.cbstudio.wearwallet.core.common.Result.Success -> {
                                    val response = signResult.data
                                    _connectionEvent.emit(
                                        ConnectionEvent.SignatureReceived(
                                            signature = response.signature,
                                            requestId = response.requestId
                                        )
                                    )
                                    _uiState.update { 
                                        it.copy(
                                            isLoading = false,
                                            statusMessage = "交易已簽名"
                                        )
                                    }
                                }
                                is com.cbstudio.wearwallet.core.common.Result.Failure -> {
                                    _uiState.update {
                                        it.copy(isLoading = false, errorMessage = "解析失敗: ${signResult.exception.message}")
                                    }
                                }
                                else -> {
                                    _uiState.update {
                                        it.copy(isLoading = false, errorMessage = "無法辨識的 QR 碼內容")
                                    }
                                }
                            }
                        }
                        is ScanResult.Progress -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    statusMessage = "掃描進度: ${resultData.current}/${resultData.total}"
                                )
                            }
                        }
                    }
                }
                is com.cbstudio.wearwallet.core.common.Result.Failure -> {
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = scanResult.exception.message)
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun importWallet(urData: String) {
        // 使用默認名稱，實際應用可能允許用戶輸入
        when (val result = keystoneManager.handleSyncResponse(urData, "Keystone Wallet")) {
            is com.cbstudio.wearwallet.core.common.Result.Success -> {
                 val wallet = result.data
                 _uiState.update {
                     it.copy(
                         isLoading = false,
                         connectedWallet = wallet.toLegacyKeystoneWallet(), // 需要轉換模型
                         statusMessage = "成功連接 ${wallet.name}"
                     )
                 }
                 _connectionEvent.emit(ConnectionEvent.Success(wallet.toLegacyKeystoneWallet()))
            }
             is com.cbstudio.wearwallet.core.common.Result.Failure -> {
                 _uiState.update {
                     it.copy(isLoading = false, errorMessage = result.exception.message)
                 }
             }
             else -> {}
        }
    }
    
    // 輔助轉換函數 (臨時)
    private fun com.cbstudio.wearwallet.core.domain.model.WalletAccount.toLegacyKeystoneWallet(): KeystoneWallet {
        return KeystoneWallet(
            id = this.id,
            name = this.name,
            masterFingerprint = this.masterFingerprint ?: "",
            addresses = listOf(KeystoneAddress(this.address, "1", this.derivationPath ?: "")),
            supportedChains = listOf("1")
        )
    }
    
    /**
     * 生成交易簽名請求
     */
    suspend fun generateSignRequest(
        unsignedTxHex: String,
        fromAddress: String,
        chainId: Long = 1L
    ): KeystoneSignRequest? {
        return try {
            _uiState.update { it.copy(isSigningTransaction = true) }
            
            // 構建 KeystoneTransaction
            val tx = KeystoneTransaction(
                to = "", // 需要從 unsignedTxHex 解析或傳入
                value = "",
                data = unsignedTxHex,
                chainId = chainId.toString()
            )
            
            // 使用 KeystoneManager 創建簽名請求
            // 需要 walletId，這裡假設已連接
            val walletId = _uiState.value.connectedWallet?.id ?: return null
            
            when (val result = keystoneManager.createSignRequest(walletId, tx, com.cbstudio.wearwallet.core.domain.model.ChainType.ETHEREUM)) {
                is com.cbstudio.wearwallet.core.common.Result.Success -> {
                    val qrCodeList = result.data
                    val request = KeystoneSignRequest(
                        requestId = "req_${System.currentTimeMillis()}",
                        qrCodeData = qrCodeList
                    )
                    
                    _uiState.update { 
                        it.copy(
                            isSigningTransaction = false,
                            currentSignRequest = request
                        )
                    }
                    request
                }
                else -> null
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isSigningTransaction = false,
                    errorMessage = "生成簽名請求失敗: ${e.message}"
                )
            }
            null
        }
    }
    
    /**
     * 處理簽名響應
     */
    fun processSignatureResponse(responseData: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            when (val result = keystoneManager.handleSignResponse(responseData)) {
                is com.cbstudio.wearwallet.core.common.Result.Success -> {
                    val signResponse = result.data
                    _connectionEvent.emit(
                        ConnectionEvent.SignatureReceived(
                            signature = signResponse.signature,
                            requestId = signResponse.requestId
                        )
                    )
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            statusMessage = "交易已簽名"
                        )
                    }
                }
                is com.cbstudio.wearwallet.core.common.Result.Failure -> {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            errorMessage = result.exception.message
                        )
                    }
                }
                else -> {}
            }
        }
    }
    
    /**
     * 重試連接
     */
    fun retry() {
        _uiState.update { 
            it.copy(
                errorMessage = null,
                statusMessage = null
            )
        }
        initializeService()
    }
    
    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    /**
     * 斷開連接
     */
    fun disconnect() {
        _uiState.update { 
            it.copy(
                connectedWallet = null,
                currentSignRequest = null,
                statusMessage = "已斷開連接"
            )
        }
    }
    
    // handleError removed or unused

}

/**
 * UI 狀態
 */
data class KeystoneConnectUiState(
    val isLoading: Boolean = false,
    val isInitialized: Boolean = false,
    val connectedWallet: KeystoneWallet? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val isSigningTransaction: Boolean = false,
    val currentSignRequest: KeystoneSignRequest? = null
)

/**
 * 連接事件
 */
sealed class ConnectionEvent {
    data class Success(val wallet: KeystoneWallet) : ConnectionEvent()
    data class SignatureReceived(val signature: String, val requestId: String) : ConnectionEvent()
    data class Error(val message: String) : ConnectionEvent()
}