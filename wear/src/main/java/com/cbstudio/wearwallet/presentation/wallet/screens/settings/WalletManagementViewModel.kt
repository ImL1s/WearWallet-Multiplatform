package com.cbstudio.wearwallet.presentation.wallet.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.wallet.CreateWalletUseCase
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * 錢包管理 ViewModel
 * 使用 coreKmp 的 WalletRepository
 */
class WalletManagementViewModel : ViewModel(), KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    private val createWalletUseCase: CreateWalletUseCase by inject()
    
    enum class DeleteStep {
        IDLE,
        CONFIRMATION,
        DELETE_AUTH_REQUIRED,
        DELETING
    }
    
    data class WalletManagementUiState(
        val wallets: List<WalletAccount> = emptyList(),
        val activeWallet: WalletAccount? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val showCreateDialog: Boolean = false,
        val deleteStep: DeleteStep = DeleteStep.IDLE,
        val walletToDelete: WalletAccount? = null,
        val operationSuccess: String? = null
    ) {
        val showDeleteDialog: Boolean get() = deleteStep == DeleteStep.CONFIRMATION || deleteStep == DeleteStep.DELETE_AUTH_REQUIRED
        val isDeleteAuthRequired: Boolean get() = deleteStep == DeleteStep.DELETE_AUTH_REQUIRED
    }
    
    private val _uiState = MutableStateFlow(WalletManagementUiState())
    val uiState: StateFlow<WalletManagementUiState> = _uiState.asStateFlow()
    
    init {
        loadWallets()
    }
    
    /**
     * 載入所有錢包
     */
    fun loadWallets() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                // 獲取所有錢包
                val walletsResult = walletRepository.getAllWallets()
                val activeWalletResult = walletRepository.getActiveWallet()
                
                when (walletsResult) {
                    is Result.Success -> {
                        val activeWallet = when (activeWalletResult) {
                            is Result.Success -> activeWalletResult.data
                            else -> null
                        }
                        
                        _uiState.update { 
                            it.copy(
                                wallets = walletsResult.data,
                                activeWallet = activeWallet,
                                isLoading = false,
                                error = null
                            )
                        }
                        Timber.d("載入錢包成功: ${walletsResult.data.size} 個錢包")
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(
                                isLoading = false,
                                error = "載入錢包失敗: ${walletsResult.exception.message}"
                            )
                        }
                        Timber.e(walletsResult.exception, "載入錢包失敗")
                    }
                    is Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "載入錢包時發生錯誤: ${e.message}"
                    )
                }
                Timber.e(e, "載入錢包異常")
            }
        }
    }
    
    /**
     * 切換活動錢包
     */
    fun switchWallet(wallet: WalletAccount) {
        viewModelScope.launch {
            try {
                val result = walletRepository.setActiveWallet(wallet.id)
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(activeWallet = wallet)
                        }
                        Timber.d("切換錢包成功: ${wallet.name}")
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "切換錢包失敗: ${result.exception.message}")
                        }
                        Timber.e(result.exception, "切換錢包失敗")
                    }
                    is Result.Loading -> {
                        // 保持狀態
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "切換錢包時發生錯誤: ${e.message}")
                }
                Timber.e(e, "切換錢包異常")
            }
        }
    }
    
    /**
     * 顯示創建錢包對話框
     */
    fun showCreateWalletDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }
    
    /**
     * 隱藏創建錢包對話框
     */
    fun hideCreateWalletDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }
    
    /**
     * 創建新錢包
     */
    fun createWallet(name: String, password: String = "", authContext: AuthenticationContext) {
        val pwChars = password.toCharArray()
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                
                createWalletUseCase(
                    name = name,
                    password = pwChars,
                    chainType = ChainType.ETHEREUM,
                    authContext = authContext
                ).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            _uiState.update { 
                                it.copy(
                                    showCreateDialog = false,
                                    isLoading = false
                                )
                            }
                            loadWallets()
                            Timber.d("創建錢包成功: $name, 地址: ${result.data.address}")
                        }
                        is Result.Failure -> {
                            _uiState.update { 
                                it.copy(
                                    isLoading = false,
                                    error = result.exception.message ?: "創建錢包失敗"
                                )
                            }
                            Timber.e(result.exception, "創建錢包失敗")
                        }
                        is Result.Loading -> {
                            _uiState.update { it.copy(isLoading = true) }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "創建錢包時發生錯誤"
                    )
                }
                Timber.e(e, "創建錢包異常")
            } finally {
                pwChars.fill('\u0000')
            }
        }
    }
    
    /**
     * 請求刪除錢包 (進入確認或認證狀態)
     */
    fun requestDeleteWallet(wallet: WalletAccount) {
        if (_uiState.value.wallets.size <= 1) {
            _uiState.update { 
                it.copy(
                    error = "不能刪除最後一個錢包 (Cannot delete last wallet)",
                    deleteStep = DeleteStep.IDLE,
                    walletToDelete = null
                )
            }
            return
        }
        if (wallet.isHardwareWallet) {
            _uiState.update { 
                it.copy(
                    deleteStep = DeleteStep.CONFIRMATION,
                    walletToDelete = wallet,
                    error = null
                )
            }
        } else {
            _uiState.update { 
                it.copy(
                    deleteStep = DeleteStep.DELETE_AUTH_REQUIRED,
                    walletToDelete = wallet,
                    error = null
                )
            }
        }
    }

    fun showDeleteWalletDialog(wallet: WalletAccount) {
        requestDeleteWallet(wallet)
    }

    /**
     * 隱藏刪除錢包對話框
     */
    fun hideDeleteWalletDialog() {
        _uiState.update { 
            it.copy(
                deleteStep = DeleteStep.IDLE,
                walletToDelete = null
            )
        }
    }

    /**
     * 確認刪除錢包 (帶 optional AuthenticationContext)
     */
    fun confirmDeleteWallet(authContext: AuthenticationContext? = null) {
        val wallet = _uiState.value.walletToDelete ?: return
        if (!wallet.isHardwareWallet && (authContext == null || authContext.authHandle == null)) {
            _uiState.update {
                it.copy(
                    deleteStep = DeleteStep.DELETE_AUTH_REQUIRED,
                    error = "刪除熱錢包需要生物識別或設備密碼認證"
                )
            }
            return
        }
        _uiState.update { it.copy(deleteStep = DeleteStep.DELETING, isLoading = true) }
        viewModelScope.launch {
            try {
                val result = walletRepository.deleteWallet(wallet.id, authContext)
                when (result) {
                    is Result.Success -> {
                        _uiState.update { 
                            it.copy(
                                deleteStep = DeleteStep.IDLE,
                                walletToDelete = null,
                                isLoading = false,
                                error = null,
                                operationSuccess = "刪除錢包成功"
                            )
                        }
                        authContext?.authHandle?.invalidate()
                        loadWallets()
                        Timber.d("刪除錢包成功: ${wallet.name}")
                    }
                    is Result.Failure -> {
                        authContext?.authHandle?.invalidate()
                        _uiState.update { 
                            it.copy(
                                deleteStep = DeleteStep.IDLE,
                                walletToDelete = null,
                                isLoading = false,
                                error = "刪除錢包失敗: ${result.exception.message ?: "未知錯誤"}"
                            )
                        }
                        Timber.e(result.exception, "刪除錢包失敗")
                    }
                    is Result.Loading -> {}
                }
            } catch (e: Exception) {
                authContext?.authHandle?.invalidate()
                _uiState.update { 
                    it.copy(
                        deleteStep = DeleteStep.IDLE,
                        walletToDelete = null,
                        isLoading = false,
                        error = "刪除錢包時發生錯誤: ${e.message}"
                    )
                }
                Timber.e(e, "刪除錢包異常")
            }
        }
    }

    fun onBiometricAuthSuccess(authContext: AuthenticationContext) {
        confirmDeleteWallet(authContext)
    }

    fun onDeleteAuthCancelled() {
        _uiState.update {
            it.copy(
                deleteStep = DeleteStep.IDLE,
                walletToDelete = null
            )
        }
    }

    fun onAuthCancel() {
        onDeleteAuthCancelled()
    }

    fun onDeleteAuthError(error: String) {
        _uiState.update {
            it.copy(
                deleteStep = DeleteStep.IDLE,
                walletToDelete = null,
                error = error
            )
        }
    }

    fun onAuthError(error: String) {
        onDeleteAuthError(error)
    }

    fun onAppBackgrounded() {
        if (_uiState.value.isDeleteAuthRequired || _uiState.value.deleteStep == DeleteStep.CONFIRMATION) {
            _uiState.update {
                it.copy(
                    deleteStep = DeleteStep.IDLE,
                    walletToDelete = null
                )
            }
        }
    }

    /**
     * 舊版相容接口：呼叫 requestDeleteWallet
     */
    fun deleteWallet(wallet: WalletAccount) {
        requestDeleteWallet(wallet)
    }
    
    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}