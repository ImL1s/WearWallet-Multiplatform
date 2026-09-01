package com.cbstudio.wearwallet.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.config.PushProtocolConfig
import com.cbstudio.wearwallet.domain.service.WearWalletChannelManager
import com.cbstudio.wearwallet.shared.utils.Logger
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.tokens.TokenTransferManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Push Protocol 設置嚮導 ViewModel
 * 
 * 管理設置流程的狀態和邏輯
 */
// @HiltViewModel  // Removed Hilt
class PushProtocolSetupViewModel(
    private val pushProtocolConfig: PushProtocolConfig,
    private val channelManager: WearWalletChannelManager
) : ViewModel(), KoinComponent {
    
    companion object {
        private const val TAG = "PushProtocolSetupViewModel"
        // PUSH Token 合約地址 (Ethereum Mainnet)
        private const val PUSH_TOKEN_ADDRESS = "0xf418588522d5dd018b425E472991E52EBBeEEEEE"
    }
    
    // 注入依賴
    private val walletRepository: WalletRepository by inject()
    private val tokenManager = TokenTransferManager()
    
    private val _uiState = MutableStateFlow(PushProtocolSetupUiState())
    val uiState: StateFlow<PushProtocolSetupUiState> = _uiState.asStateFlow()
    
    init {
        // 初始化時檢查現有配置
        loadExistingConfiguration()
    }
    
    /**
     * 載入現有配置
     */
    private fun loadExistingConfiguration() {
        viewModelScope.launch {
            try {
                // 檢查是否已有頻道
                val channelAddress = pushProtocolConfig.getChannelAddress()
                if (!channelAddress.isNullOrBlank()) {
                    _uiState.update { state ->
                        state.copy(
                            channelAddress = channelAddress,
                            currentStep = SetupStep.CONFIGURE_NOTIFICATIONS
                        )
                    }
                }
                
                // 載入通知偏好設置
                _uiState.update { state ->
                    state.copy(
                        priceAlertsEnabled = pushProtocolConfig.isPriceAlertsEnabled(),
                        transactionAlertsEnabled = pushProtocolConfig.isTransactionAlertsEnabled(),
                        securityAlertsEnabled = pushProtocolConfig.isSecurityAlertsEnabled(),
                        defiAlertsEnabled = pushProtocolConfig.isDefiAlertsEnabled()
                    )
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to load configuration", e)
            }
        }
    }
    
    /**
     * 移動到下一步
     */
    fun moveToNextStep() {
        _uiState.update { state ->
            val nextStep = when (state.currentStep) {
                SetupStep.INTRODUCTION -> SetupStep.CHECK_BALANCE
                SetupStep.CHECK_BALANCE -> {
                    // 檢查是否有足夠的 tokens
                    val balance = state.pushTokenBalance.toDoubleOrNull() ?: 0.0
                    if (balance >= state.requiredTokenAmount) {
                        SetupStep.CREATE_CHANNEL
                    } else {
                        state.currentStep // 保持在當前步驟
                    }
                }
                SetupStep.CREATE_CHANNEL -> {
                    if (state.channelAddress != null) {
                        SetupStep.CONFIGURE_NOTIFICATIONS
                    } else {
                        state.currentStep
                    }
                }
                SetupStep.CONFIGURE_NOTIFICATIONS -> SetupStep.COMPLETED
                SetupStep.COMPLETED -> SetupStep.COMPLETED
            }
            state.copy(currentStep = nextStep)
        }
    }
    
    /**
     * 移動到上一步
     */
    fun moveToPreviousStep() {
        _uiState.update { state ->
            val previousStep = when (state.currentStep) {
                SetupStep.INTRODUCTION -> SetupStep.INTRODUCTION
                SetupStep.CHECK_BALANCE -> SetupStep.INTRODUCTION
                SetupStep.CREATE_CHANNEL -> SetupStep.CHECK_BALANCE
                SetupStep.CONFIGURE_NOTIFICATIONS -> {
                    if (state.channelAddress != null) {
                        SetupStep.CREATE_CHANNEL
                    } else {
                        SetupStep.CHECK_BALANCE
                    }
                }
                SetupStep.COMPLETED -> SetupStep.CONFIGURE_NOTIFICATIONS
            }
            state.copy(currentStep = previousStep)
        }
    }
    
    /**
     * 檢查 PUSH token 餘額
     * 使用真實的區塊鏈查詢獲取 PUSH token 餘額
     */
    fun checkPushTokenBalance() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isCheckingBalance = true) }
                
                // 獲取活躍錢包地址
                val walletResult = walletRepository.getActiveWallet()
                val walletAddress = when (walletResult) {
                    is Result.Success -> walletResult.data?.address
                    else -> null
                }
                
                if (walletAddress.isNullOrBlank()) {
                    _uiState.update { state ->
                        state.copy(
                            pushTokenBalance = "0",
                            isCheckingBalance = false,
                            errorMessage = "請先創建或導入錢包"
                        )
                    }
                    return@launch
                }
                
                // 從區塊鏈查詢真實的 PUSH token 餘額
                val balanceResult = tokenManager.getTokenBalance(
                    chainType = MultiChainType.ETHEREUM,
                    tokenAddress = PUSH_TOKEN_ADDRESS,
                    walletAddress = walletAddress
                )
                
                when (balanceResult) {
                    is Result.Success -> {
                        val balance = balanceResult.data
                        _uiState.update { state ->
                            state.copy(
                                pushTokenBalance = balance.formattedBalance,
                                isCheckingBalance = false
                            )
                        }
                        Logger.d(TAG, "PUSH token balance: ${balance.formattedBalance}")
                    }
                    is Result.Failure -> {
                        Logger.e(TAG, "Failed to get PUSH balance", balanceResult.exception)
                        _uiState.update { state ->
                            state.copy(
                                pushTokenBalance = "0",
                                isCheckingBalance = false,
                                errorMessage = "查詢餘額失敗: ${balanceResult.exception.message}"
                            )
                        }
                    }
                    is Result.Loading -> {
                        // 保持加載狀態
                    }
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to check PUSH balance", e)
                _uiState.update { state ->
                    state.copy(
                        pushTokenBalance = "0",
                        errorMessage = "檢查餘額失敗: ${e.message}",
                        isCheckingBalance = false
                    )
                }
            }
        }
    }
    
    /**
     * 創建 Push Protocol 頻道
     */
    fun createChannel(privateKey: String) {
        if (privateKey.isBlank()) {
            _uiState.update { it.copy(errorMessage = "請輸入有效的私鑰") }
            return
        }
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isCreatingChannel = true) }
                
                // 驗證私鑰格式 (基本的十六進制檢查)
                if (!isValidPrivateKey(privateKey)) {
                    _uiState.update { state ->
                        state.copy(
                            errorMessage = "無效的私鑰格式",
                            isCreatingChannel = false
                        )
                    }
                    return@launch
                }
                
                // 創建頻道
                val result = channelManager.createOfficialChannel()
                
                if (result.isSuccess) {
                    val channelAddress = result.getOrNull()
                    
                    // 保存配置
                    pushProtocolConfig.markChannelCreated(
                        channelAddress = channelAddress ?: "",
                        privateKey = privateKey
                    )
                    pushProtocolConfig.setPushProtocolEnabled(true)
                    
                    _uiState.update { state ->
                        state.copy(
                            channelAddress = channelAddress,
                            isCreatingChannel = false
                        )
                    }
                    
                    Logger.d(TAG, "Channel created successfully: $channelAddress")
                    
                } else {
                    result.exceptionOrNull()?.let { exception ->
                        _uiState.update { state ->
                            state.copy(
                                errorMessage = "創建頻道失敗: ${exception.message}",
                                isCreatingChannel = false
                            )
                        }
                    }
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to create channel", e)
                _uiState.update { state ->
                    state.copy(
                        errorMessage = "創建頻道失敗: ${e.message}",
                        isCreatingChannel = false
                    )
                }
            }
        }
    }
    
    /**
     * 切換價格提醒
     */
    fun togglePriceAlerts() {
        _uiState.update { state ->
            val newValue = !state.priceAlertsEnabled
            pushProtocolConfig.setPriceAlertsEnabled(newValue)
            state.copy(priceAlertsEnabled = newValue)
        }
    }
    
    /**
     * 切換交易提醒
     */
    fun toggleTransactionAlerts() {
        _uiState.update { state ->
            val newValue = !state.transactionAlertsEnabled
            pushProtocolConfig.setTransactionAlertsEnabled(newValue)
            state.copy(transactionAlertsEnabled = newValue)
        }
    }
    
    /**
     * 切換安全提醒
     */
    fun toggleSecurityAlerts() {
        _uiState.update { state ->
            val newValue = !state.securityAlertsEnabled
            pushProtocolConfig.setSecurityAlertsEnabled(newValue)
            state.copy(securityAlertsEnabled = newValue)
        }
    }
    
    /**
     * 切換 DeFi 提醒
     */
    fun toggleDefiAlerts() {
        _uiState.update { state ->
            val newValue = !state.defiAlertsEnabled
            pushProtocolConfig.setDefiAlertsEnabled(newValue)
            state.copy(defiAlertsEnabled = newValue)
        }
    }
    
    /**
     * 完成設置
     */
    suspend fun completeSetup() {
        try {
            // 確保所有配置已保存
            pushProtocolConfig.updateLastSyncTime()
            
            // 初始化頻道管理器
            channelManager.initialize()
            
            _uiState.update { it.copy(currentStep = SetupStep.COMPLETED) }
            
            Logger.d(TAG, "Push Protocol setup completed")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to complete setup", e)
            _uiState.update { state ->
                state.copy(errorMessage = "完成設置失敗: ${e.message}")
            }
        }
    }
    
    /**
     * 清除錯誤訊息
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    /**
     * 驗證私鑰格式
     * @param privateKey 私鑰字串
     * @return 是否為有效的私鑰格式
     */
    private fun isValidPrivateKey(privateKey: String): Boolean {
        // 移除可能的 "0x" 前綴
        val cleanPrivateKey = if (privateKey.startsWith("0x")) {
            privateKey.substring(2)
        } else {
            privateKey
        }
        
        // 檢查長度 (64 個十六進制字符 = 32 bytes)
        if (cleanPrivateKey.length != 64) {
            return false
        }
        
        // 檢查是否為有效的十六進制字符
        return cleanPrivateKey.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    }
}

/**
 * Push Protocol 設置 UI 狀態
 */
data class PushProtocolSetupUiState(
    val currentStep: SetupStep = SetupStep.INTRODUCTION,
    val pushTokenBalance: String = "0",
    val requiredTokenAmount: Int = 50,
    val isCheckingBalance: Boolean = false,
    val isCreatingChannel: Boolean = false,
    val channelAddress: String? = null,
    val priceAlertsEnabled: Boolean = true,
    val transactionAlertsEnabled: Boolean = true,
    val securityAlertsEnabled: Boolean = true,
    val defiAlertsEnabled: Boolean = true,
    val errorMessage: String? = null
)
