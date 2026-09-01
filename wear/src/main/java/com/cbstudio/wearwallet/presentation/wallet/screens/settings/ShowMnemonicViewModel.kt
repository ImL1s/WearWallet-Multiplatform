package com.cbstudio.wearwallet.presentation.wallet.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.usecase.wallet.RevealMnemonicUseCase
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.security.PlatformAuthHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

enum class RevealStatus {
    LOCKED,
    AUTHENTICATING,
    REVEALED,
    EXPIRED,
    ERROR
}

typealias EphemeralMnemonicHolder = com.cbstudio.wearwallet.core.security.EphemeralMnemonicHolder

/**
 * 顯示助記詞 ViewModel
 * 安全地顯示錢包助記詞 (Milestone 4 / P1-5 Presentation Hardening)
 */
class ShowMnemonicViewModel : ViewModel(), KoinComponent {

    private val walletRepository: WalletRepository by inject()
    private val revealMnemonicUseCase: RevealMnemonicUseCase by inject()

    data class ShowMnemonicUiState(
        val activeWallet: WalletAccount? = null,
        val mnemonicHolder: EphemeralMnemonicHolder? = null,
        val isLoading: Boolean = false,
        val error: String? = null,
        val showWarning: Boolean = true,
        val requiresPassword: Boolean = false,
        val isRevealed: Boolean = false,
        val remainingSeconds: Int = 0,
        val status: RevealStatus = RevealStatus.LOCKED
    ) {
        val mnemonicWords: List<String>?
            get() = mnemonicHolder?.getWords()

        val mnemonic: List<String>
            get() = mnemonicHolder?.getWords() ?: emptyList()
    }

    private val _uiState = MutableStateFlow(ShowMnemonicUiState())
    val uiState: StateFlow<ShowMnemonicUiState> = _uiState.asStateFlow()

    private var autoClearJob: Job? = null
    private var currentAuthHandle: PlatformAuthHandle? = null

    init {
        loadActiveWallet()
    }

    /**
     * 載入活動錢包
     */
    private fun loadActiveWallet() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val result = walletRepository.getActiveWallet()
                when (result) {
                    is Result.Success -> {
                        val wallet = result.data
                        if (wallet != null) {
                            _uiState.update {
                                it.copy(
                                    activeWallet = wallet,
                                    isLoading = false,
                                    requiresPassword = !wallet.isHardwareWallet,
                                    status = RevealStatus.LOCKED
                                )
                            }

                            // 硬體錢包不顯示助記詞
                            if (wallet.isHardwareWallet) {
                                _uiState.update {
                                    it.copy(
                                        error = "硬體錢包無法顯示助記詞",
                                        status = RevealStatus.ERROR
                                    )
                                }
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "沒有找到活動錢包",
                                    status = RevealStatus.ERROR
                                )
                            }
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "載入錢包失敗",
                                status = RevealStatus.ERROR
                            )
                        }
                        Timber.e(result.exception, "載入錢包失敗")
                    }
                    is Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "載入錢包時發生錯誤",
                        status = RevealStatus.ERROR
                    )
                }
                Timber.e(e, "載入錢包異常")
            }
        }
    }

    /**
     * 接受警告並繼續
     */
    fun acceptWarning() {
        _uiState.update { it.copy(showWarning = false) }
    }

    /**
     * 認證成功處理入口 (支援直接傳入 PlatformAuthHandle)
     */
    fun onAuthSuccess(handle: PlatformAuthHandle, password: String = "") {
        revealMnemonic(
            password = password,
            authContext = AuthenticationContext(authHandle = handle, cryptoObject = handle.cryptoObject)
        )
    }

    /**
     * 認證成功處理入口 (傳入 AuthenticationContext)
     */
    fun onAuthSuccess(authContext: AuthenticationContext, password: String = "") {
        revealMnemonic(password = password, authContext = authContext)
    }

    /**
     * 使用者取消認證
     */
    fun onAuthCancelled() {
        _uiState.value.mnemonicHolder?.clear()
        _uiState.update {
            it.copy(
                isLoading = false,
                isRevealed = false,
                mnemonicHolder = null,
                status = RevealStatus.LOCKED
            )
        }
    }

    /**
     * 認證錯誤處理
     */
    fun onAuthError(errorMessage: String) {
        _uiState.value.mnemonicHolder?.clear()
        _uiState.update {
            it.copy(
                isLoading = false,
                isRevealed = false,
                mnemonicHolder = null,
                error = errorMessage,
                status = RevealStatus.ERROR
            )
        }
    }

    /**
     * 顯示助記詞（需要經過 UI 生物識別/設備憑證驗證所簽發的 AuthenticationContext 與 PlatformAuthHandle）
     */
    fun revealMnemonic(
        password: String? = null,
        authContext: AuthenticationContext? = null
    ) {
        viewModelScope.launch {
            try {
                val wallet = _uiState.value.activeWallet ?: return@launch

                if (wallet.isHardwareWallet) {
                    _uiState.value.mnemonicHolder?.clear()
                    _uiState.update {
                        it.copy(
                            error = "硬體錢包無法顯示助記詞",
                            isRevealed = false,
                            mnemonicHolder = null,
                            isLoading = false,
                            status = RevealStatus.ERROR
                        )
                    }
                    return@launch
                }

                val context = authContext ?: run {
                    _uiState.value.mnemonicHolder?.clear()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "需要生物識別或設備憑證認證",
                            isRevealed = false,
                            mnemonicHolder = null,
                            status = RevealStatus.LOCKED
                        )
                    }
                    return@launch
                }

                val handle = context.authHandle ?: run {
                    _uiState.value.mnemonicHolder?.clear()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "需要生物識別或設備憑證認證",
                            isRevealed = false,
                            mnemonicHolder = null,
                            status = RevealStatus.LOCKED
                        )
                    }
                    return@launch
                }

                // 1. 嚴格拒絕空白 keyId
                if (handle.keyId.isBlank()) {
                    _uiState.value.mnemonicHolder?.clear()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "認證金鑰識別碼不可為空",
                            isRevealed = false,
                            mnemonicHolder = null,
                            status = RevealStatus.LOCKED
                        )
                    }
                    return@launch
                }

                // 2. 嚴格限定操作為 REVEAL (拒絕 EXPORT 或 SIGN)
                if (handle.operation != AuthOperation.REVEAL) {
                    _uiState.value.mnemonicHolder?.clear()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "認證操作類型不符: ${handle.operation} (僅支援 REVEAL)",
                            isRevealed = false,
                            mnemonicHolder = null,
                            status = RevealStatus.LOCKED
                        )
                    }
                    return@launch
                }

                // 3. 嚴格比對 keyId
                val expectedKeyId = wallet.keyAlias ?: wallet.address
                if (handle.keyId != expectedKeyId &&
                    handle.keyId != wallet.id &&
                    handle.keyId != wallet.address
                ) {
                    _uiState.value.mnemonicHolder?.clear()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "跨金鑰認證被拒絕",
                            isRevealed = false,
                            mnemonicHolder = null,
                            status = RevealStatus.LOCKED
                        )
                    }
                    return@launch
                }

                if (handle.isExpired() || handle.isInvalidated) {
                    _uiState.value.mnemonicHolder?.clear()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "認證憑證已失效或過期",
                            isRevealed = false,
                            mnemonicHolder = null,
                            status = RevealStatus.LOCKED
                        )
                    }
                    return@launch
                }

                _uiState.update { it.copy(isLoading = true) }

                val mnemonicResult = revealMnemonicUseCase.executeWithMnemonic(
                    walletId = wallet.id,
                    password = password ?: "",
                    authContext = context
                ) { chars ->
                    val wordCharsList = mutableListOf<CharArray>()
                    val currentWord = mutableListOf<Char>()
                    for (c in chars) {
                        if (c == ' ' || c == '\n' || c == '\t') {
                            if (currentWord.isNotEmpty()) {
                                wordCharsList.add(currentWord.toCharArray())
                                currentWord.clear()
                            }
                        } else {
                            currentWord.add(c)
                        }
                    }
                    if (currentWord.isNotEmpty()) {
                        wordCharsList.add(currentWord.toCharArray())
                        currentWord.clear()
                    }
                    EphemeralMnemonicHolder(wordCharsList)
                }

                when (mnemonicResult) {
                    is Result.Success -> {
                        val holder = mnemonicResult.data
                        currentAuthHandle = handle
                        _uiState.value.mnemonicHolder?.clear()
                        _uiState.update {
                            it.copy(
                                mnemonicHolder = holder,
                                isRevealed = true,
                                isLoading = false,
                                error = null,
                                remainingSeconds = 30,
                                status = RevealStatus.REVEALED
                            )
                        }
                        startAutoClearTimer()
                        Timber.d("成功顯示助記詞")
                    }
                    is Result.Failure -> {
                        clearMnemonic(status = RevealStatus.ERROR)
                        _uiState.update {
                            it.copy(
                                error = mnemonicResult.exception.message ?: "密碼錯誤或無法獲取助記詞",
                                isLoading = false
                            )
                        }
                        Timber.e(mnemonicResult.exception, "獲取助記詞失敗")
                    }
                    is Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            } catch (e: Exception) {
                clearMnemonic(status = RevealStatus.ERROR)
                _uiState.update {
                    it.copy(
                        error = "顯示助記詞時發生錯誤: ${e.message}",
                        isLoading = false
                    )
                }
                Timber.e(e, "顯示助記詞異常")
            }
        }
    }

    private fun startAutoClearTimer() {
        autoClearJob?.cancel()
        autoClearJob = viewModelScope.launch {
            for (sec in 30 downTo 1) {
                _uiState.update { it.copy(remainingSeconds = sec) }
                delay(1000L)
            }
            // 30 seconds timer expired -> clear mnemonic and set status to EXPIRED
            clearMnemonic(status = RevealStatus.EXPIRED)
        }
    }

    /**
     * 清除助記詞狀態（當離開畫面、退到背景、逾時或鎖屏時）
     */
    fun clearMnemonic(status: RevealStatus = RevealStatus.LOCKED) {
        autoClearJob?.cancel()
        autoClearJob = null
        currentAuthHandle?.invalidate()
        currentAuthHandle = null
        _uiState.value.mnemonicHolder?.clear()
        _uiState.update {
            it.copy(
                mnemonicHolder = null,
                isRevealed = false,
                isLoading = false,
                remainingSeconds = 0,
                status = status
            )
        }
    }

    /**
     * 應用進入背景時立即清除助記詞
     */
    fun onAppBackgrounded() {
        autoClearJob?.cancel()
        autoClearJob = null
        currentAuthHandle?.invalidate()
        currentAuthHandle = null
        clearMnemonic(status = RevealStatus.LOCKED)
    }

    override fun onCleared() {
        super.onCleared()
        autoClearJob?.cancel()
        autoClearJob = null
        clearMnemonic(status = RevealStatus.LOCKED)
    }

    /**
     * 隱藏助記詞
     */
    fun hideMnemonic() {
        clearMnemonic(status = RevealStatus.LOCKED)
    }

    /**
     * 設定錯誤訊息
     */
    fun setError(error: String) {
        _uiState.update { it.copy(error = error, isLoading = false, status = RevealStatus.ERROR) }
    }

    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}