package com.cbstudio.wearwallet.presentation.wallet.screens.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.usecase.wallet.ImportWalletUseCase
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * ViewModel for importing wallets
 * Connected to coreKmp through ImportWalletUseCase
 */
class ImportWalletViewModel : ViewModel(), KoinComponent {
    
    private val importWalletUseCase: ImportWalletUseCase by inject()
    
    private var ephemeralInputChars: CharArray? = null
    private var ephemeralPasswordChars: CharArray? = null
    private var ephemeralConfirmPasswordChars: CharArray? = null
    private val ephemeralMnemonicWords: MutableList<CharArray> = MutableList(12) { CharArray(0) }
    
    private val _uiState = MutableStateFlow(ImportWalletUiState())
    val uiState: StateFlow<ImportWalletUiState> = _uiState.asStateFlow()
    
    fun selectImportType(type: ImportType) {
        _uiState.update { it.copy(
            importType = type,
            currentStep = ImportStep.INPUT_DATA
        ) }
    }
    
    fun resetImportType() {
        wipeEphemeralSecrets()
        _uiState.update { it.copy(
            importType = null, 
            error = null,
            currentStep = ImportStep.SELECT_TYPE,
            showPasswordInput = false,
            inputValid = false
        ) }
    }
    
    fun updateInput(input: String) {
        ephemeralInputChars?.fill('\u0000')
        ephemeralInputChars = input.toCharArray()
        _uiState.update { currentState ->
            currentState.copy(
                inputValid = validateInput(input, currentState.importType),
                error = null
            )
        }
    }
    
    fun updateWalletName(name: String) {
        _uiState.update { it.copy(walletName = name) }
    }
    
    fun toggleChainSelection() {
        _uiState.update { currentState ->
            val chains = listOf(
                ChainType.ETHEREUM,
                ChainType.SEPOLIA,   // Testnet option so demo/import flows aren't blocked by the mainnet capability gate
                ChainType.BSC,
                ChainType.POLYGON,
                ChainType.ARBITRUM,
                ChainType.OPTIMISM,
                ChainType.AVALANCHE,
                ChainType.CRONOS,
                ChainType.FANTOM,
                ChainType.BITCOIN,
                ChainType.LITECOIN,   // NEW
                ChainType.DOGECOIN,   // NEW
                ChainType.TRON,       // NEW
                ChainType.SOLANA
            )
            val currentIndex = chains.indexOf(currentState.selectedChain)
            val nextIndex = (currentIndex + 1) % chains.size
            currentState.copy(selectedChain = chains[nextIndex])
        }
    }
    
    fun setPassword(password: CharArray) {
        ephemeralPasswordChars?.fill('\u0000')
        ephemeralPasswordChars = password.copyOf()
    }
    
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
            when (val res = importWalletUseCase.prepareProvisioning()) {
                is Result.Success -> onReady(res.data)
                is Result.Failure -> onError(res.exception.message ?: "Failed to prepare provisioning")
                else -> onError("Failed to prepare provisioning")
            }
        }
    }

    fun proceedToPasswordInput() {
        val state = _uiState.value
        if (!state.inputValid || state.importType == null) {
            Timber.d("ImportWalletViewModel: proceedToPasswordInput FAILED. inputValid=${state.inputValid}, type=${state.importType}")
            _uiState.update { it.copy(error = "請輸入有效的助記詞或私鑰") }
            return
        }
        Timber.d("ImportWalletViewModel: proceedToPasswordInput SUCCESS. Transitioning to PASSWORD_INPUT")
        _uiState.update { it.copy(
            currentStep = ImportStep.PASSWORD_INPUT,
            showPasswordInput = true,
            error = null
        ) }
    }
    
    fun importWallet(authContext: AuthenticationContext) {
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
        
        val inputChars = ephemeralInputChars
        if (inputChars == null || inputChars.isEmpty() || !state.inputValid || state.importType == null) return
        
        val pwCopy = pwChars.copyOf()
        val inputCopy = inputChars.copyOf()
        
        viewModelScope.launch {
            _uiState.update { it.copy(
                isLoading = true, 
                error = null,
                currentStep = ImportStep.IMPORTING,
                showPasswordInput = false
            ) }
            
            try {
                val name = state.walletName.ifBlank { 
                    "Wallet ${System.currentTimeMillis() / 1000}"
                }
                
                // 判斷輸入類型並調用對應的導入方法
                if (state.importType == ImportType.MNEMONIC) {
                    // 助記詞導入
                    importWalletUseCase.importFromMnemonic(
                        name = name,
                        mnemonic = inputCopy,
                        password = pwCopy,
                        chainType = state.selectedChain,
                        authContext = authContext
                    ).collect { result ->
                        when (result) {
                            is Result.Success -> {
                                _uiState.update { 
                                    it.copy(
                                        isLoading = false, 
                                        walletImported = true,
                                        importCompleted = true,
                                        error = null,
                                        currentStep = ImportStep.COMPLETED
                                    )
                                }
                            }
                            is Result.Failure -> {
                                _uiState.update { 
                                    it.copy(
                                        isLoading = false, 
                                        error = result.exception.message ?: "導入錢包失敗"
                                    )
                                }
                            }
                            is Result.Loading -> {
                                // 繼續顯示載入狀態
                            }
                        }
                    }
                } else {
                    // 私鑰導入
                    importWalletUseCase.importFromPrivateKey(
                        name = name,
                        privateKey = inputCopy,
                        password = pwCopy,
                        chainType = state.selectedChain,
                        authContext = authContext
                    ).collect { result ->
                        when (result) {
                            is Result.Success -> {
                                _uiState.update { 
                                    it.copy(
                                        isLoading = false, 
                                        walletImported = true,
                                        importCompleted = true,
                                        error = null,
                                        currentStep = ImportStep.COMPLETED
                                    )
                                }
                            }
                            is Result.Failure -> {
                                _uiState.update { 
                                    it.copy(
                                        isLoading = false, 
                                        error = result.exception.message ?: "導入錢包失敗"
                                    )
                                }
                            }
                            is Result.Loading -> {
                                // 繼續顯示載入狀態
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = e.message ?: "導入錢包時發生錯誤"
                    )
                }
            } finally {
                pwCopy.fill('\u0000')
                inputCopy.fill('\u0000')
            }
        }
    }

    fun onAuthSuccess(handle: com.cbstudio.wearwallet.core.security.PlatformAuthHandle) {
        importWallet(AuthenticationContext(authHandle = handle, cryptoObject = handle.cryptoObject))
    }

    fun onAuthError(error: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                error = error,
                currentStep = ImportStep.PASSWORD_INPUT,
                showPasswordInput = true
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    private fun validateInput(input: String, importType: ImportType?): Boolean {
        if (input.isBlank()) return false
        
        return when (importType) {
            ImportType.MNEMONIC -> {
                val words = input.trim().split(" ")
                words.size == 12 || words.size == 24
            }
            ImportType.PRIVATE_KEY -> {
                val cleanInput = input.trim().removePrefix("0x")
                cleanInput.length == 64 && cleanInput.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            }
            null -> false
        }
    }
    
    enum class ImportType {
        MNEMONIC,
        PRIVATE_KEY
    }
    
    data class ImportWalletUiState(
        val importType: ImportType? = null,
        val inputValid: Boolean = false,
        val walletName: String = "",
        val selectedChain: ChainType = ChainType.ETHEREUM,
        val isLoading: Boolean = false,
        val walletImported: Boolean = false,
        val importCompleted: Boolean = false,
        val error: String? = null,
        val showPasswordInput: Boolean = false,
        val currentStep: ImportStep = ImportStep.SELECT_TYPE
    )
    
    enum class ImportStep {
        SELECT_TYPE,       // 選擇導入類型
        INPUT_DATA,        // 輸入助記詞或私鑰
        INPUT_MNEMONIC,    // 輸入助記詞
        SET_PASSWORD,      // 設置密碼
        PASSWORD_INPUT,    // 輸入密碼
        IMPORTING,         // 導入中
        COMPLETED          // 完成
    }
    
    fun updateMnemonicWord(index: Int, word: String) {
        if (index in 0 until 12) {
            ephemeralMnemonicWords[index].fill('\u0000')
            ephemeralMnemonicWords[index] = word.toCharArray()
        }
    }
    
    fun validateAndProceed() {
        val words = ephemeralMnemonicWords.map { String(it) }
        if (words.all { it.isNotBlank() }) {
            val joined = words.joinToString(" ")
            updateInput(joined)
            _uiState.update { it.copy(
                currentStep = ImportStep.SET_PASSWORD,
                error = null
            )}
        } else {
            _uiState.update { it.copy(error = "請輸入所有助記詞") }
        }
    }
    
    fun backToMnemonicInput() {
        _uiState.update { it.copy(
            currentStep = ImportStep.INPUT_MNEMONIC,
            error = null
        )}
    }
    
    fun setWalletName(name: String) {
        _uiState.update { it.copy(walletName = name) }
    }

    /**
     * 清零/清除記憶體中保存的助記詞、私鑰與密碼 (用於生命週期暫停、超時或背景化)
     */
    fun wipeEphemeralSecrets() {
        ephemeralInputChars?.fill('\u0000')
        ephemeralInputChars = null
        ephemeralPasswordChars?.fill('\u0000')
        ephemeralPasswordChars = null
        ephemeralConfirmPasswordChars?.fill('\u0000')
        ephemeralConfirmPasswordChars = null
        for (i in 0 until ephemeralMnemonicWords.size) {
            ephemeralMnemonicWords[i].fill('\u0000')
            ephemeralMnemonicWords[i] = CharArray(0)
        }
        _uiState.update {
            it.copy(
                error = null
            )
        }
    }

    /**
     * ViewModel 銷毀時清零敏感數據
     */
    override fun onCleared() {
        super.onCleared()
        wipeEphemeralSecrets()
    }
}