package com.cbstudio.wearwallet.presentation.wallet.screens.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
// Import from coreKmp
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.usecase.wallet.CreateWalletUseCase
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.utils.Logger

import com.cbstudio.wearwallet.core.security.EphemeralMnemonicHolder

/**
 * 創建錢包 ViewModel
 * 
 * 處理錢包創建流程，包括：
 * 1. 生成助記詞
 * 2. 創建錢包
 * 3. 顯示助記詞供用戶備份
 * 4. 完成流程後導航到主畫面
 */
class CreateWalletViewModel : ViewModel(), KoinComponent {
    
    private val createWalletUseCase: CreateWalletUseCase by inject()
    
    private var ephemeralPasswordChars: CharArray? = null
    private var ephemeralConfirmPasswordChars: CharArray? = null
    
    data class CreateWalletState(
        val isLoading: Boolean = false,
        val mnemonicHolder: EphemeralMnemonicHolder? = null,
        val showMnemonic: Boolean = false,
        val showSafetyWarning: Boolean = false,
        val walletCreated: Boolean = false,
        val error: String? = null,
        val currentStep: CreationStep = CreationStep.INITIAL,
        val walletName: String = "",
        val showPasswordInput: Boolean = false,
        val createdWalletId: String? = null
    )
    
    enum class CreationStep {
        INITIAL,           // 初始狀態
        PASSWORD_INPUT,    // 輸入密碼
        GENERATING,        // 生成中
        SHOW_WARNING,      // 顯示安全警告
        SHOW_MNEMONIC,     // 顯示助記詞
        CONFIRM_BACKUP,    // 確認已備份
        COMPLETED          // 完成
    }
    
    private val _uiState = MutableStateFlow(CreateWalletState())
    val uiState: StateFlow<CreateWalletState> = _uiState.asStateFlow()
    
    /**
     * 設置錢包名稱
     */
    fun setWalletName(name: String) {
        _uiState.update { it.copy(walletName = name) }
    }
    
    /**
     * 設置密碼 (暫態存儲於 CharArray，不在 StateFlow 中持久化)
     */
    fun setPassword(password: CharArray) {
        ephemeralPasswordChars?.fill('\u0000')
        ephemeralPasswordChars = password.copyOf()
    }
    
    /**
     * 設置確認密碼 (暫態存儲於 CharArray，不在 StateFlow 中持久化)
     */
    fun setConfirmPassword(password: CharArray) {
        ephemeralConfirmPasswordChars?.fill('\u0000')
        ephemeralConfirmPasswordChars = password.copyOf()
    }
    
    /**
     * 預先準備金鑰佈建會話 (Exact Session Provisioning Request)
     */
    fun prepareProvisioning(
        onReady: (com.cbstudio.wearwallet.core.security.ProvisioningRequest) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = createWalletUseCase.prepareProvisioning()) {
                is Result.Success -> onReady(res.data)
                is Result.Failure -> onError(res.exception.message ?: "Failed to prepare provisioning")
                else -> onError("Failed to prepare provisioning")
            }
        }
    }

    /**
     * 開始創建錢包流程 - 顯示密碼輸入
     * @param name 用戶輸入的錢包名稱，如果為空則使用隨機默認名稱
     */
    fun startWalletCreation(name: String = "") {
        val walletName = name.ifBlank { "錢包 ${System.currentTimeMillis() % 1000}" }
        _uiState.update { it.copy(
            currentStep = CreationStep.PASSWORD_INPUT,
            showPasswordInput = true,
            walletName = walletName,
            error = null
        )}
    }
    
    /**
     * 確認密碼並創建錢包
     */
    fun confirmPasswordAndCreate(authContext: AuthenticationContext) {
        Logger.d("WEAR_E2E", "confirmPasswordAndCreate called")
        val state = _uiState.value
        val pwChars = ephemeralPasswordChars
        val confirmChars = ephemeralConfirmPasswordChars
        
        // 驗證密碼
        if (pwChars == null || pwChars.isEmpty()) {
            _uiState.update { it.copy(error = "請輸入密碼") }
            return
        }
        
        if (confirmChars == null || !pwChars.contentEquals(confirmChars)) {
            _uiState.update { it.copy(error = "密碼不一致") }
            return
        }
        
        if (pwChars.size < 6) {
            _uiState.update { it.copy(error = "密碼至少需要6個字符") }
            return
        }
        
        val pwToUse = pwChars.copyOf()
        
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    isLoading = true,
                    error = null,
                    currentStep = CreationStep.GENERATING,
                    showPasswordInput = false
                )}
                
                // 創建錢包 (直接從創建結果獲取短暫助記詞供首次備份顯示，不使用 exportMnemonic)
                Logger.d("CreateWalletViewModel", "Creating wallet flow...")
                createWalletUseCase.createWithMnemonic(
                    name = state.walletName.ifEmpty { "錢包 ${System.currentTimeMillis() % 1000}" },
                    password = pwToUse,
                    chainType = ChainType.ETHEREUM,
                    authContext = authContext
                ).collect { result ->
                    Logger.d("CreateWalletViewModel", "Received result: ${result::class.simpleName}")
                    when (result) {
                        is Result.Loading -> {
                            // 繼續顯示載入狀態
                        }
                        is Result.Success -> {
                            val created = result.data
                            val wallet = created.wallet
                            val holder = created.mnemonicHolder
                            
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    mnemonicHolder = holder,
                                    currentStep = CreationStep.SHOW_WARNING,
                                    showSafetyWarning = true,
                                    createdWalletId = wallet.id
                                )
                            }
                            
                            println("✅ 成功創建錢包!")
                            println("   地址: ${wallet.address}")
                            println("   助記詞數量: ${holder.wordCount}")
                        }
                        is Result.Failure -> {
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    error = result.exception.message ?: "Unknown error",
                                    currentStep = CreationStep.INITIAL
                                )
                            }
                            println("❌ 創建錢包失敗: ${result.exception.message}")
                        }
                    }
                }
            } catch (e: Throwable) {
                Logger.e("CreateWalletViewModel", "Critical error during wallet creation", e)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "創建錢包時發生嚴重錯誤: ${e.message}",
                        currentStep = CreationStep.INITIAL
                    )
                }
            } finally {
                pwToUse.fill('\u0000')
            }
        }
    }
    
    /**
     * 認證成功並發起錢包創建
     */
    fun onAuthSuccess(handle: com.cbstudio.wearwallet.core.security.PlatformAuthHandle) {
        confirmPasswordAndCreate(
            com.cbstudio.wearwallet.core.security.AuthenticationContext(
                authHandle = handle,
                cryptoObject = handle.cryptoObject
            )
        )
    }

    /**
     * 認證失敗處理
     */
    fun onAuthError(error: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = error,
                currentStep = CreationStep.PASSWORD_INPUT,
                showPasswordInput = true
            )
        }
    }

    /**
     * 用戶確認已看到安全警告
     */
    fun acknowledgeWarning() {
        _uiState.update { 
            it.copy(
                showSafetyWarning = false,
                showMnemonic = true,
                currentStep = CreationStep.SHOW_MNEMONIC
            )
        }
    }
    
    /**
     * 用戶確認已備份助記詞，清零/清除記憶體中保存的助記詞與密碼
     */
    fun confirmBackup() {
        ephemeralPasswordChars?.fill('\u0000')
        ephemeralPasswordChars = null
        ephemeralConfirmPasswordChars?.fill('\u0000')
        ephemeralConfirmPasswordChars = null
        _uiState.value.mnemonicHolder?.clear()
        _uiState.update { 
            it.copy(
                mnemonicHolder = null,
                showMnemonic = false,
                walletCreated = true,
                currentStep = CreationStep.COMPLETED
            )
        }
    }

    /**
     * 清零/清除記憶體中保存的助記詞與密碼 (用於生命週期暫停、超時或背景化)
     */
    fun wipeEphemeralMnemonic() {
        ephemeralPasswordChars?.fill('\u0000')
        ephemeralPasswordChars = null
        ephemeralConfirmPasswordChars?.fill('\u0000')
        ephemeralConfirmPasswordChars = null
        _uiState.value.mnemonicHolder?.clear()
        _uiState.update { 
            it.copy(
                mnemonicHolder = null,
                showMnemonic = false,
                currentStep = if (it.createdWalletId != null) CreationStep.COMPLETED else CreationStep.INITIAL
            )
        }
    }
    
    /**
     * 清除錯誤訊息
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * 重置狀態
     */
    fun reset() {
        _uiState.value = CreateWalletState()
    }
    
    /**
     * 返回密碼輸入畫面
     */
    fun backToPasswordInput() {
        _uiState.update { it.copy(
            currentStep = CreationStep.PASSWORD_INPUT,
            showPasswordInput = true,
            showSafetyWarning = false,
            error = null
        )}
    }

    override fun onCleared() {
        super.onCleared()
        wipeEphemeralMnemonic()
    }
}