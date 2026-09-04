package com.cbstudio.wearwallet.presentation.wallet.screens.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.usecase.wallet.ImportWalletUseCase
import com.cbstudio.wearwallet.core.security.CryptoProvider
import com.cbstudio.wearwallet.core.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import com.cbstudio.wearwallet.core.security.EphemeralMnemonicHolder

/**
 * ImportMnemonicViewModel - 專門處理助記詞導入的 ViewModel
 *
 * 負責管理 12 詞助記詞輸入流程：
 * 1. 輸入助記詞
 * 2. 設置密碼
 * 3. 導入錢包
 * 4. 完成
 */
class ImportMnemonicViewModel : ViewModel(), KoinComponent {

    private val importWalletUseCase: ImportWalletUseCase by inject()
    private val cryptoProvider: CryptoProvider by inject()

    private var ephemeralPasswordChars: CharArray? = null
    private var ephemeralConfirmPasswordChars: CharArray? = null
    private val ephemeralWords: MutableList<CharArray> = MutableList(12) { CharArray(0) }

    data class ImportMnemonicUiState(
        val mnemonicHolder: EphemeralMnemonicHolder? = null,
        val walletName: String = "",
        val currentStep: ImportStep = ImportStep.INPUT_MNEMONIC,
        val isLoading: Boolean = false,
        val error: String? = null
    ) {
        val mnemonicWords: List<String>
            get() = mnemonicHolder?.getWords() ?: List(12) { "" }
    }

    enum class ImportStep {
        INPUT_MNEMONIC,
        SET_PASSWORD,
        IMPORTING,
        COMPLETED
    }

    private val _uiState = MutableStateFlow(ImportMnemonicUiState())
    val uiState: StateFlow<ImportMnemonicUiState> = _uiState.asStateFlow()

    fun updateMnemonicWord(index: Int, word: String) {
        if (index in 0 until 12) {
            ephemeralWords[index].fill('\u0000')
            ephemeralWords[index] = word.lowercase().trim().toCharArray()
        }
        val holder = EphemeralMnemonicHolder(ephemeralWords.map { it.copyOf() })
        _uiState.update { it.copy(mnemonicHolder = holder, error = null) }
    }

    /**
     * 從貼上的文字解析並填入助記詞
     * 支持空格、換行、逗號分隔
     */
    fun setMnemonicFromPaste(text: String) {
        val cleanedText = text
            .replace("\n", " ")
            .replace(",", " ")
            .replace("\t", " ")
            .trim()
        
        val words = cleanedText
            .split("\\s+".toRegex())
            .map { it.lowercase().trim() }
            .filter { it.isNotEmpty() }
            .take(12)
        
        for (i in 0 until 12) {
            ephemeralWords[i].fill('\u0000')
            ephemeralWords[i] = if (i < words.size) words[i].toCharArray() else CharArray(0)
        }
        
        val holder = EphemeralMnemonicHolder(ephemeralWords.map { it.copyOf() })
        _uiState.update { 
            it.copy(
                mnemonicHolder = holder,
                error = if (words.size != 12) "已貼上 ${words.size} 個詞，需要 12 個" else null
            ) 
        }
        
        Logger.d("ImportMnemonic", "從貼上解析了 ${words.size} 個詞")
    }

    /**
     * 獲取 BIP39 單詞建議
     */
    fun getWordSuggestions(prefix: String): List<String> {
        return Bip39SuggestionProvider.getSuggestions(prefix, limit = 5)
    }

    /**
     * 預先準備金鑰佈建會話 (Exact Session Provisioning Request)
     */
    fun prepareProvisioning(
        onReady: (com.cbstudio.wearwallet.core.security.ProvisioningRequest) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = importWalletUseCase.prepareProvisioning()) {
                is Result.Success -> onReady(res.data)
                is Result.Failure -> onError(res.exception.message ?: "Failed to prepare provisioning")
                else -> onError("Failed to prepare provisioning")
            }
        }
    }


    fun validateAndProceed() {
        val words = _uiState.value.mnemonicWords
        Logger.d("ImportMnemonic", "validateAndProceed: Start call with ${words.size} words")

        // 檢查是否全部填寫
        if (words.any { it.isBlank() }) {
            Logger.w("ImportMnemonic", "單詞未填寫完整: $words")
            _uiState.update { it.copy(error = "請輸入所有 12 個助記詞") }
            return
        }

        // 驗證助記詞格式和 BIP39 合法性
        try {
            viewModelScope.launch {
                Logger.d("ImportMnemonic", "validateAndProceed: Coroutine Started")
                _uiState.update { it.copy(isLoading = true, error = null) }

                try {
                    val mnemonic = words.joinToString(" ")
                    val mnemonicChars = mnemonic.toCharArray()

                    Logger.d("ImportMnemonic", "CryptoProvider instance: ${cryptoProvider::class.simpleName}")
                    Logger.d("ImportMnemonic", "Calling validateMnemonic...")
                    
                    // 調用 CryptoProvider 驗證
                    val isValid = try {
                        cryptoProvider.validateMnemonic(mnemonicChars)
                    } finally {
                        mnemonicChars.fill('\u0000')
                    }
                    
                    Logger.d("ImportMnemonic", "validateMnemonic returned: $isValid")

                    if (isValid) {
                        _uiState.update {
                            it.copy(
                                currentStep = ImportStep.SET_PASSWORD,
                                isLoading = false,
                                error = null
                            )
                        }
                        Logger.d("ImportMnemonic", "助記詞驗證成功")
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "助記詞無效，請檢查是否輸入正確"
                            )
                        }
                        Logger.w("ImportMnemonic", "助記詞驗證失敗")
                    }
                } catch (t: Throwable) {
                    Logger.e("ImportMnemonic", "驗證過程發生異常 (Inner)", t)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "驗證助記詞時發生錯誤：${t.message}"
                        )
                    }
                }
            }
        } catch (t: Throwable) {
             Logger.e("ImportMnemonic", "validateAndProceed: Launch Failed (Outer)", t)
             _uiState.update { it.copy(error = "系统错误: ${t.message}") }
        }
    }

    fun setWalletName(name: String) {
        _uiState.update { it.copy(walletName = name) }
    }

    fun setPassword(password: CharArray) {
        ephemeralPasswordChars?.fill('\u0000')
        ephemeralPasswordChars = password.copyOf()
        _uiState.update { it.copy(error = null) }
    }

    fun setConfirmPassword(password: CharArray) {
        ephemeralConfirmPasswordChars?.fill('\u0000')
        ephemeralConfirmPasswordChars = password.copyOf()
        _uiState.update { it.copy(error = null) }
    }

    fun backToMnemonicInput() {
        _uiState.update {
            it.copy(
                currentStep = ImportStep.INPUT_MNEMONIC,
                error = null
            )
        }
    }

    fun importWallet(authContext: com.cbstudio.wearwallet.core.security.AuthenticationContext) {
        val state = _uiState.value
        val pwChars = ephemeralPasswordChars
        val confirmChars = ephemeralConfirmPasswordChars
        Logger.d("ImportMnemonic", "importWallet called. Pwd length: ${pwChars?.size ?: 0}")

        // 驗證密碼
        if (pwChars == null || pwChars.isEmpty()) {
            Logger.w("ImportMnemonic", "Password empty")
            _uiState.update { it.copy(error = "請輸入密碼") }
            return
        }

        if (confirmChars == null || !pwChars.contentEquals(confirmChars)) {
            Logger.w("ImportMnemonic", "Password mismatch")
            _uiState.update { it.copy(error = "密碼不一致") }
            return
        }

        if (pwChars.size < 6) {
            Logger.w("ImportMnemonic", "Password too short")
            _uiState.update { it.copy(error = "密碼至少需要 6 個字符") }
            return
        }

        val pwCopy = pwChars.copyOf()
        val mnemonicJoined = state.mnemonicWords.joinToString(" ").toCharArray()

        viewModelScope.launch {
            Logger.d("ImportMnemonic", "Starting import process...")
            _uiState.update {
                it.copy(
                    currentStep = ImportStep.IMPORTING,
                    isLoading = true,
                    error = null
                )
            }

            try {
                val name = state.walletName.ifBlank { "Wallet ${System.currentTimeMillis() / 1000}" }

                importWalletUseCase.importFromMnemonic(
                    name = name,
                    mnemonic = mnemonicJoined,
                    password = pwCopy,
                    chainType = ChainType.ETHEREUM,
                    authContext = authContext
                ).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            _uiState.update {
                                it.copy(
                                    currentStep = ImportStep.COMPLETED,
                                    isLoading = false,
                                    error = null
                                )
                            }
                        }
                        is Result.Failure -> {
                            Logger.e("ImportMnemonic", "Import failed", result.exception)
                            _uiState.update {
                                it.copy(
                                    currentStep = ImportStep.SET_PASSWORD,
                                    isLoading = false,
                                    error = result.exception.message ?: "導入錢包失敗"
                                )
                            }
                        }
                        is Result.Loading -> {
                            // Already loading
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e("ImportMnemonic", "Error in importWallet", e)
                _uiState.update {
                    it.copy(
                        currentStep = ImportStep.SET_PASSWORD,
                        isLoading = false,
                        error = e.message ?: "導入錢包時發生錯誤"
                    )
                }
            } finally {
                pwCopy.fill('\u0000')
                mnemonicJoined.fill('\u0000')
            }
        }
    }

    fun onAuthSuccess(handle: com.cbstudio.wearwallet.core.security.PlatformAuthHandle) {
        importWallet(com.cbstudio.wearwallet.core.security.AuthenticationContext(authHandle = handle, cryptoObject = handle.cryptoObject))
    }

    fun onAuthError(error: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = error,
                currentStep = ImportStep.SET_PASSWORD
            )
        }
    }

    /**
     * 取消導入並清零敏感數據
     */
    fun cancelImport() {
        Logger.d("ImportMnemonic", "取消導入，清零敏感數據")
        clearSensitiveData()
        _uiState.update {
            it.copy(
                currentStep = ImportStep.INPUT_MNEMONIC,
                error = null
            )
        }
    }

    /**
     * 清零/清除記憶體中保存的助記詞與密碼 (用於生命週期暫停、超時或背景化)
     */
    fun wipeEphemeralSecrets() {
        clearSensitiveData()
    }

    /**
     * 清零敏感數據
     */
    private fun clearSensitiveData() {
        ephemeralPasswordChars?.fill('\u0000')
        ephemeralPasswordChars = null
        ephemeralConfirmPasswordChars?.fill('\u0000')
        ephemeralConfirmPasswordChars = null
        for (i in 0 until ephemeralWords.size) {
            ephemeralWords[i].fill('\u0000')
            ephemeralWords[i] = CharArray(0)
        }
        _uiState.value.mnemonicHolder?.clear()
        _uiState.update {
            it.copy(
                mnemonicHolder = null,
                walletName = ""
            )
        }
    }

    /**
     * ViewModel 清除時清零敏感數據
     */
    override fun onCleared() {
        super.onCleared()
        Logger.d("ImportMnemonic", "ViewModel 清除，清零敏感數據")
        clearSensitiveData()
    }
}
