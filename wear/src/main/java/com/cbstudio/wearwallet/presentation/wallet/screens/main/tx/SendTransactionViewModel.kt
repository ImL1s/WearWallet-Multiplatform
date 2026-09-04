package com.cbstudio.wearwallet.presentation.wallet.screens.main.tx

import androidx.biometric.BiometricPrompt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.address.EvmRecipientAddressPolicy
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.model.TransactionStatus
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.transaction.EvmBroadcastOutcome
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.domain.model.context.ChainSelection
import com.cbstudio.wearwallet.core.domain.model.intent.ConfirmedEvmTransactionIntent
import com.cbstudio.wearwallet.core.domain.model.quantities.*
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.domain.repository.TransactionRepository
import com.cbstudio.wearwallet.core.domain.usecase.transaction.SendTransactionUseCase
import com.cbstudio.wearwallet.core.domain.usecase.transaction.EstimateGasUseCase
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.GetAddressContactsUseCase
import com.cbstudio.wearwallet.core.domain.usecase.addressbook.SearchAddressBookUseCase
import com.cbstudio.wearwallet.core.domain.model.addressbook.AddressContact
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.presentation.wallet.ChainStateManager
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.math.BigDecimal

/**
 * 發送交易 ViewModel - M2 Immutable Intent, 10-State Machine & Fail-Closed Implementation
 */
class SendTransactionViewModel : ViewModel(), KoinComponent {
    
    private val walletRepository: WalletRepository by inject()
    private val tokenRepository: TokenRepository by inject()
    private val transactionRepository: TransactionRepository by inject()
    private val sendTransactionUseCase: SendTransactionUseCase by inject()
    private val estimateGasUseCase: EstimateGasUseCase by inject()
    private val secureKeyManager: com.cbstudio.wearwallet.core.security.SecureKeyManager by inject()
    
    // 地址簿相關 UseCase
    private val getAddressContactsUseCase: GetAddressContactsUseCase by inject()
    private val searchAddressBookUseCase: SearchAddressBookUseCase by inject()

    // 活躍授權 Handle
    private var activeAuthHandle: com.cbstudio.wearwallet.core.security.PlatformAuthHandle? = null

    // Single generation counter for async race protection (Directive R3)
    private var transactionIntentGeneration: Long = 0L

    // Re-entrancy and duplicate submission mutex
    private val submissionMutex = Mutex()

    /**
     * Increment generation counter and invalidate pending gas/nonce RPC requests and confirmed snapshot.
     */
    private fun onIntentFieldChanged() {
        transactionIntentGeneration++
        _uiState.update { 
            it.copy(
                confirmedSnapshot = null,
                authorizedFingerprint = null,
                estimatedGasPriceWei = null,
                estimatedGasLimitObj = null,
                estimatedTotalFee = null
            )
        }
    }

    /**
     * Builds full transaction intent fingerprint covering:
     * wallet, sender, chain, network, recipient, token, decimals, amount, gas/fee params.
     */
    fun buildIntentFingerprint(state: SendTransactionUiState = _uiState.value): String {
        val walletId = state.activeWallet?.id ?: ""
        val sender = state.activeWallet?.address?.lowercase() ?: ""
        val chain = state.currentChain.name
        val executionContext = try {
            state.currentSelection.toChainExecutionContext()
        } catch (e: Exception) {
            null
        }
        val recipient = state.recipientAddress.lowercase()
        val tokenContract = state.tokenAddress?.lowercase() ?: ""
        val tokenDecimals = state.tokenDecimals?.toString() ?: ""
        val amount = state.amount
        val gasPrice = state.estimatedGasPriceWei?.value?.toString(10) ?: ""
        val gasLimit = state.estimatedGasLimitObj?.value?.toString(10) ?: ""
        val fee = state.estimatedTotalFee ?: ""

        val chainName = executionContext?.multiChainType?.name ?: chain
        val netName = executionContext?.networkType?.name ?: "UNKNOWN"
        val chainIdStr = executionContext?.chainId?.toString() ?: "0"

        return "$walletId:$sender:$chainName:$netName:$chainIdStr:$recipient:$tokenContract:$tokenDecimals:$amount:$gasPrice:$gasLimit:$fee"
    }

    fun getTransactionIntentGeneration(): Long = transactionIntentGeneration
    
    /**
     * 交易狀態步驟 (10 狀態狀態機 + 輸入步驟)
     */
    enum class TransactionStep {
        INPUT_ADDRESS,      // 輸入地址
        INPUT_AMOUNT,       // 輸入金額
        CONFIRM,            // 相容原有 CONFIRM
        REVIEWED,           // 審核確認 (Intent 18-field Fingerprint Frozen)
        MIGRATION_REQUIRED, // 需要舊版金鑰遷移
        MIGRATING,          // 遷移進行中
        AUTH_REQUIRED,      // 需要授權 (Biometric/Password Required)
        AUTHENTICATING,     // 認證中 (Prompt/Dialog Active)
        AUTHORIZED,         // 授權成功 (Auth Handle Bound to Fingerprint)
        SIGNING,            // 簽名中
        BROADCASTING,       // 廣播中
        SENDING,            // 相容原有 SENDING
        BROADCASTED,        // hash returned; not chain confirmation
        SUCCESS,            // retained; send-hash path uses BROADCASTED
        AUTH_CANCELLED,     // 認證取消
        AUTH_EXPIRED,       // 認證過期
        FAILED              // 失敗
    }

    /**
     * UI 狀態
     */
    data class SendTransactionUiState(
        val currentStep: TransactionStep = TransactionStep.INPUT_ADDRESS,
        val activeWallet: WalletAccount? = null,
        val currentSelection: ChainSelection = ChainSelection.default(),
        val currentChain: ChainType = ChainType.ETHEREUM,
        val isUTXOChain: Boolean = false,
        val recipientAddress: String = "",
        val amount: String = "",
        val selectedToken: Token? = null, // null 表示發送原生代幣
        val estimatedGasPriceWei: Wei? = null,
        val estimatedGasLimitObj: GasLimit? = null,
        val estimatedTotalFee: String? = null,
        val confirmedSnapshot: ConfirmedEvmTransactionIntent? = null,
        val authorizedFingerprint: String? = null,
        val balance: BigDecimal = BigDecimal.ZERO,
        val balanceInUsd: String = "$0.00",
        val isLoading: Boolean = false,
        val isSubmitting: Boolean = false,
        val isEstimatingGas: Boolean = false,
        val error: String? = null,
        val txHash: String? = null,
        val broadcastStatus: TransactionStatus? = null,
        val addressError: String? = null,
        val amountError: String? = null,
        // 地址簿相關
        val showAddressBook: Boolean = false,
        val addressBookContacts: List<AddressContact> = emptyList(),
        val addressBookLoading: Boolean = false,
        // Keystone
        val keystoneUnsignedTx: String? = null
    ) {
        val tokenAddress: String? get() = selectedToken?.address
        val tokenDecimals: Int? get() = selectedToken?.decimals
        val estimatedGasPrice: String? get() = estimatedGasPriceWei?.toHex()
        val estimatedGasLimit: String? get() = estimatedGasLimitObj?.toLong()?.toString()
        val estimatedGasPriceGweiFormatted: String? get() = estimatedGasPriceWei?.toGweiString()?.let { "$it Gwei" }
        val isReadyToSign: Boolean get() = confirmedSnapshot != null && authorizedFingerprint != null && (authorizedFingerprint == confirmedSnapshot.signingDigestHex || authorizedFingerprint == confirmedSnapshot.canonicalFingerprint)
        val reviewFields: EvmSendReviewFields?
            get() {
                val snap = confirmedSnapshot ?: return null
                return EvmSendReviewFields(
                    toAddress = snap.recipient.value,
                    chainId = snap.executionContext.chainId,
                    nonce = snap.nonce.toLong(),
                    contractAddress = snap.tokenContract?.value,
                )
            }
    }

    data class EvmSendReviewFields(
        val toAddress: String,
        val chainId: Long,
        val nonce: Long,
        val contractAddress: String?,
    )
    
    private val _uiState = MutableStateFlow(SendTransactionUiState())
    val uiState: StateFlow<SendTransactionUiState> = _uiState.asStateFlow()
    
    init {
        loadActiveWallet()
        checkCurrentChain()
    }
    
    /**
     * 檢查當前鏈類型與網路選取狀態
     */
    private fun checkCurrentChain() {
        onIntentFieldChanged()
        viewModelScope.launch {
            val currentChain = ChainStateManager.getCurrentChain()
            val selection = try {
                ChainStateManager.getSelection()
            } catch (e: Exception) {
                ChainSelection.default()
            }
            val isUTXO = currentChain in listOf(
                ChainType.BITCOIN,
                ChainType.LITECOIN,
                ChainType.DOGECOIN,
                ChainType.BITCOIN_CASH
            )
            
            _uiState.update { 
                it.copy(
                    currentSelection = selection,
                    currentChain = currentChain,
                    isUTXOChain = isUTXO
                )
            }
            revalidateAddress()
        }
    }
    
    fun isLegacyWallet(wallet: WalletAccount?): Boolean {
        if (wallet == null || wallet.isHardwareWallet) return false
        return wallet.keyAlias.isNullOrBlank() || wallet.keyFormatVersion < 2
    }

    /**
     * 載入活動錢包
     */
    private fun loadActiveWallet() {
        onIntentFieldChanged()
        viewModelScope.launch {
            try {
                val result = walletRepository.getActiveWallet()
                when (result) {
                    is Result.Success -> {
                        result.data?.let { wallet ->
                            val isLegacy = isLegacyWallet(wallet)
                            _uiState.update { 
                                it.copy(
                                    activeWallet = wallet,
                                    balance = getWalletBalance(wallet),
                                    currentStep = if (isLegacy) TransactionStep.MIGRATION_REQUIRED else it.currentStep,
                                    error = if (isLegacy) "此錢包需要升級至安全 KeyVault 才能發送交易" else it.error
                                )
                            }
                            revalidateAddress()
                        } ?: run {
                            _uiState.update { 
                                it.copy(error = "沒有找到活動錢包")
                            }
                        }
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(error = "載入錢包失敗: ${result.exception.message}")
                        }
                    }
                    is Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(error = "載入錢包時發生錯誤: ${e.message}")
                }
            }
        }
    }
    
    /**
     * 獲取錢包餘額
     */
    private suspend fun getWalletBalance(wallet: WalletAccount): BigDecimal {
        return try {
            val balance = walletRepository.getNativeBalance(
                wallet.address, 
                ChainStateManager.getCurrentChain()
            )
            BigDecimal(balance.toString())
        } catch (e: Exception) {
            Timber.e(e, "獲取餘額失敗")
            BigDecimal.ZERO
        }
    }
    
    /**
     * 設置接收地址 (Clear intent & gas estimate on change)
     */
    fun setRecipientAddress(address: String) {
        onIntentFieldChanged()
        _uiState.update { 
            it.copy(
                recipientAddress = address,
                addressError = validateAddress(address),
                showAddressBook = false
            )
        }
        if (_uiState.value.addressError == null && _uiState.value.amountError == null && _uiState.value.amount.isNotEmpty()) {
            estimateGas()
        }
    }

    /**
     * 重新驗證地址
     */
    private fun revalidateAddress() {
        val currentAddress = _uiState.value.recipientAddress
        if (currentAddress.isNotEmpty()) {
            val error = validateAddress(currentAddress)
            if (_uiState.value.addressError != error) {
                _uiState.update { it.copy(addressError = error) }
            }
        }
    }
    
    /**
     * 驗證地址格式
     */
    private fun validateAddress(address: String): String? {
        if (address.isEmpty()) return "請輸入地址"
        
        val wallet = _uiState.value.activeWallet ?: return "錢包未載入"
        val chainType = ChainStateManager.getCurrentChain()
        
        val isUTXOChain = chainType in listOf(
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        )
        
        return if (isUTXOChain) {
            when (chainType) {
                ChainType.BITCOIN -> {
                    when {
                        address.matches(Regex("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$")) -> null
                        address.matches(Regex("^bc1[a-z0-9]{39,59}$")) -> null
                        else -> "無效的 Bitcoin 地址"
                    }
                }
                ChainType.LITECOIN -> {
                    when {
                        address.matches(Regex("^[LM][a-km-zA-HJ-NP-Z1-9]{25,34}$")) -> null
                        address.matches(Regex("^ltc1[a-z0-9]{39,59}$")) -> null
                        else -> "無效的 Litecoin 地址"
                    }
                }
                ChainType.DOGECOIN -> {
                    if (address.matches(Regex("^D[5-9A-HJ-NP-U][a-km-zA-HJ-NP-Z1-9]{31,33}$"))) {
                        null
                    } else {
                        "無效的 Dogecoin 地址"
                    }
                }
                ChainType.BITCOIN_CASH -> {
                    when {
                        address.matches(Regex("^bitcoincash:[a-z0-9]{42,}$")) -> null
                        address.matches(Regex("^[13][a-km-zA-HJ-NP-Z1-9]{25,34}$")) -> null
                        else -> "無效的 Bitcoin Cash 地址"
                    }
                }
                else -> "不支援的鏈類型"
            }
        } else {
            when {
                !address.startsWith("0x") -> "地址格式錯誤"
                address.length != 42 -> "地址長度錯誤"
                !EvmRecipientAddressPolicy.isValid(address) -> "地址校驗和錯誤"
                else -> null
            }
        }
    }
    
    /**
     * 顯示/隱藏地址簿
     */
    fun toggleAddressBook() {
        viewModelScope.launch {
            val show = !_uiState.value.showAddressBook
            _uiState.update { it.copy(showAddressBook = show) }
            
            if (show) {
                loadAddressBookContacts()
            }
        }
    }
    
    /**
     * 載入地址簿聯絡人
     */
    private suspend fun loadAddressBookContacts() {
        try {
            _uiState.update { it.copy(addressBookLoading = true) }
            val currentChain = ChainStateManager.getCurrentChain()
            
            val result = getAddressContactsUseCase.getContactsByChainType(currentChain)
            when (result) {
                is Result.Success -> {
                    _uiState.update { 
                        it.copy(
                            addressBookContacts = result.data,
                            addressBookLoading = false
                        )
                    }
                    Timber.d("載入地址簿成功: ${result.data.size} 個聯絡人")
                }
                is Result.Failure -> {
                    _uiState.update { 
                        it.copy(
                            addressBookLoading = false,
                            addressBookContacts = emptyList()
                        )
                    }
                    Timber.e(result.exception, "載入地址簿失敗")
                }
                is Result.Loading -> {}
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    addressBookLoading = false,
                    addressBookContacts = emptyList()
                )
            }
            Timber.e(e, "載入地址簿異常")
        }
    }
    
    /**
     * 搜尋地址簿
     */
    fun searchAddressBook(query: String) {
        viewModelScope.launch {
            try {
                if (query.isEmpty()) {
                    loadAddressBookContacts()
                    return@launch
                }
                
                val result = searchAddressBookUseCase.searchContacts(query)
                when (result) {
                    is Result.Success -> {
                        val filteredContacts = result.data.filter { 
                            it.chainType == ChainStateManager.getCurrentChain() 
                        }
                        _uiState.update { 
                            it.copy(addressBookContacts = filteredContacts)
                        }
                        Timber.d("搜尋地址簿成功: ${filteredContacts.size} 個結果")
                    }
                    is Result.Failure -> {
                        _uiState.update { 
                            it.copy(addressBookContacts = emptyList())
                        }
                        Timber.e(result.exception, "搜尋地址簿失敗")
                    }
                    is Result.Loading -> {}
                }
            } catch (e: Exception) {
                Timber.e(e, "搜尋地址簿異常")
            }
        }
    }
    
    /**
     * 選擇地址簿聯絡人
     */
    fun selectAddressContact(contact: AddressContact) {
        setRecipientAddress(contact.address)
        _uiState.update { 
            it.copy(showAddressBook = false)
        }
    }
    
    /**
     * 進入下一步
     */
    fun proceedToAmount() {
        if (_uiState.value.addressError != null) {
            _uiState.update { 
                it.copy(error = "請輸入有效的地址")
            }
            return
        }
        _uiState.update { 
            it.copy(currentStep = TransactionStep.INPUT_AMOUNT)
        }
    }
    
    /**
     * 設置金額 (Clear intent & gas estimate on change)
     */
    fun setAmount(amount: String) {
        onIntentFieldChanged()
        val cleanAmount = amount.replace(",", ".")
        _uiState.update { 
            it.copy(
                amount = cleanAmount,
                amountError = validateAmount(cleanAmount)
            )
        }
        
        if (_uiState.value.amountError == null && cleanAmount.isNotEmpty()) {
            estimateGas()
        }
    }

    /**
     * 手動更新 Gas/Fee 參數 (Clear intent & invalidate on change)
     */
    fun updateGasParameters(gasPriceWei: Wei?, gasLimitObj: GasLimit?) {
        onIntentFieldChanged()
        val feeStr = if (gasPriceWei != null && gasLimitObj != null) {
            val totalWei = gasPriceWei.value * BigInteger.fromLong(gasLimitObj.toLong())
            Wei.fromWei(totalWei).toEthString()
        } else null

        _uiState.update {
            it.copy(
                estimatedGasPriceWei = gasPriceWei,
                estimatedGasLimitObj = gasLimitObj,
                estimatedTotalFee = feeStr
            )
        }
    }
    
    /**
     * 驗證金額
     */
    private fun validateAmount(amount: String): String? {
        return try {
            val amountValue = BigDecimal(amount)
            when {
                amountValue <= BigDecimal.ZERO -> "金額必須大於 0"
                amountValue > _uiState.value.balance -> "餘額不足"
                else -> null
            }
        } catch (e: Exception) {
            "金額格式錯誤"
        }
    }
    
    /**
     * 估算 Gas 費用 - Captures generation counter & intent fingerprint to discard stale responses
     */
    private fun estimateGas() {
        val startGen = transactionIntentGeneration
        val startFingerprint = buildIntentFingerprint(_uiState.value)
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isEstimatingGas = true) }
                
                val wallet = _uiState.value.activeWallet ?: return@launch
                val currentChain = _uiState.value.currentChain
                
                estimateGasUseCase(
                    from = wallet.address,
                    to = _uiState.value.recipientAddress,
                    value = _uiState.value.amount,
                    chainType = currentChain,
                    tokenAddress = _uiState.value.tokenAddress,
                    tokenDecimals = _uiState.value.tokenDecimals
                ).collect { result ->
                    if (startGen != transactionIntentGeneration || startFingerprint != buildIntentFingerprint(_uiState.value)) {
                        Timber.d("Discarding stale gas estimation result for gen #$startGen vs current #$transactionIntentGeneration")
                        return@collect
                    }
                    when (result) {
                        is Result.Success -> {
                            val gasData = result.data
                            _uiState.update { 
                                it.copy(
                                    estimatedGasPriceWei = gasData.weiGasPrice,
                                    estimatedGasLimitObj = gasData.gasLimitObj,
                                    estimatedTotalFee = gasData.totalFee,
                                    isEstimatingGas = false
                                )
                            }
                        }
                        is Result.Failure -> {
                            _uiState.update { 
                                it.copy(
                                    estimatedGasPriceWei = null,
                                    estimatedGasLimitObj = null,
                                    estimatedTotalFee = null,
                                    isEstimatingGas = false,
                                    error = "Gas 估算失敗: ${result.exception.message}"
                                )
                            }
                            Timber.e(result.exception, "Gas 估算失敗")
                        }
                        is Result.Loading -> {}
                    }
                }
            } catch (e: Exception) {
                if (startGen == transactionIntentGeneration && startFingerprint == buildIntentFingerprint(_uiState.value)) {
                    _uiState.update { 
                        it.copy(
                            isEstimatingGas = false,
                            estimatedGasPriceWei = null,
                            estimatedGasLimitObj = null,
                            estimatedTotalFee = null,
                            error = "Gas 估算異常: ${e.message}"
                        )
                    }
                    Timber.e(e, "Gas 估算異常")
                }
            }
        }
    }
    
    /**
     * 選擇代幣
     */
    fun selectToken(token: Token?) {
        onIntentFieldChanged()
        _uiState.update { 
            it.copy(
                selectedToken = token
            )
        }
        if (_uiState.value.amountError == null && _uiState.value.amount.isNotEmpty()) {
            estimateGas()
        }
    }
    
    /**
     * 進入確認步驟 — 建立 18-field ConfirmedEvmTransactionIntent 快照
     */
    fun proceedToConfirm() {
        val state = _uiState.value
        val refreshedAddressError = validateAddress(state.recipientAddress)
        if (refreshedAddressError != null || state.addressError != null) {
            _uiState.update {
                it.copy(
                    addressError = refreshedAddressError ?: state.addressError,
                    error = "請輸入有效的地址"
                )
            }
            return
        }
        if (state.amountError != null) {
            _uiState.update { 
                it.copy(error = "請輸入有效的金額")
            }
            return
        }
        val gasPrice = state.estimatedGasPriceWei
        val gasLimit = state.estimatedGasLimitObj
        val feeStr = state.estimatedTotalFee
        if (gasPrice == null || gasLimit == null || feeStr == null) {
            _uiState.update { 
                it.copy(error = "Gas 估算未完成或失敗，無法進行交易")
            }
            return
        }
        val activeWallet = state.activeWallet ?: run {
            _uiState.update { it.copy(error = "沒有找到活動錢包") }
            return
        }
        if (!activeWallet.isHardwareWallet && (isLegacyWallet(activeWallet) || activeWallet.keyAlias.isNullOrBlank())) {
            _uiState.update {
                it.copy(
                    currentStep = TransactionStep.MIGRATION_REQUIRED,
                    error = "此錢包為舊版金鑰格式，需升級至安全 KeyVault 才能發送交易。"
                )
            }
            return
        }

        val startGen = ++transactionIntentGeneration
        val startFingerprint = buildIntentFingerprint(state)

        viewModelScope.launch {
            try {
                val executionContext = state.currentSelection.toChainExecutionContext()
                val fetchedNonce = try {
                    transactionRepository.getNonce(activeWallet.address, executionContext)
                } catch (e: Exception) {
                    if (startGen == transactionIntentGeneration && startFingerprint == buildIntentFingerprint(_uiState.value)) {
                        _uiState.update { 
                            it.copy(error = "Nonce 獲取失敗，無法進行交易: ${e.message}")
                        }
                    }
                    return@launch
                }

                if (startGen != transactionIntentGeneration || startFingerprint != buildIntentFingerprint(_uiState.value)) {
                    Timber.d("Discarding stale nonce response due to generation mismatch or intent change")
                    return@launch
                }

                val currentState = _uiState.value
                val isTokenTransfer = currentState.selectedToken != null
                val tokenContractAddr = if (isTokenTransfer) EvmAddress.fromString(currentState.selectedToken!!.address) else null
                val tokenSymbol = currentState.selectedToken?.symbol
                val tokenDecimals = currentState.selectedToken?.decimals

                val decimals = tokenDecimals ?: 18
                val baseUnitAmount = BaseUnitAmount.fromDecimalString(currentState.amount, decimals)
                val nativeValue = if (isTokenTransfer) Wei.ZERO else Wei.fromWei(baseUnitAmount.value)

                val calldata = if (isTokenTransfer) {
                    val cleanRecipient = currentState.recipientAddress.removePrefix("0x").removePrefix("0X").lowercase().padStart(64, '0')
                    val cleanAmount = baseUnitAmount.value.toString(16).lowercase().padStart(64, '0')
                    Calldata.fromHex("0xa9059cbb$cleanRecipient$cleanAmount")
                } else {
                    Calldata.EMPTY
                }

                val feeWei = Wei.fromWei(gasPrice.value * BigInteger.fromLong(gasLimit.toLong()))
                val multiChain = executionContext.multiChainType

                val senderAddr = EvmAddress.fromString(activeWallet.address)
                val recipientAddr = EvmAddress.fromString(currentState.recipientAddress)
                val nonceObj = Nonce(fetchedNonce)

                val keyAlias = if (activeWallet.isHardwareWallet) {
                    activeWallet.keyAlias?.takeIf { it.isNotBlank() } ?: activeWallet.id
                } else {
                    activeWallet.keyAlias?.takeIf { it.isNotBlank() } ?: run {
                        _uiState.update { it.copy(currentStep = TransactionStep.MIGRATION_REQUIRED, error = "缺少 KeyAlias，需要遷移") }
                        return@launch
                    }
                }
                val fingerprint = ConfirmedEvmTransactionIntent.createFingerprint(
                    walletId = activeWallet.id,
                    keyAlias = keyAlias,
                    sender = senderAddr,
                    chain = multiChain,
                    executionContext = executionContext,
                    envelopeType = EvmEnvelope.LEGACY,
                    recipient = recipientAddr,
                    tokenContract = tokenContractAddr,
                    tokenSymbol = tokenSymbol,
                    tokenDecimals = tokenDecimals,
                    humanAmount = currentState.amount,
                    baseUnitAmount = baseUnitAmount,
                    nativeValue = nativeValue,
                    calldata = calldata,
                    nonce = nonceObj,
                    gasPrice = gasPrice,
                    gasLimit = gasLimit,
                    fee = feeWei
                )

                val intent = ConfirmedEvmTransactionIntent(
                    walletId = activeWallet.id,
                    keyAlias = keyAlias,
                    sender = senderAddr,
                    chain = multiChain,
                    executionContext = executionContext,
                    envelopeType = EvmEnvelope.LEGACY,
                    recipient = recipientAddr,
                    tokenContract = tokenContractAddr,
                    tokenSymbol = tokenSymbol,
                    tokenDecimals = tokenDecimals,
                    humanAmount = currentState.amount,
                    baseUnitAmount = baseUnitAmount,
                    nativeValue = nativeValue,
                    calldata = calldata,
                    nonce = nonceObj,
                    gasPrice = gasPrice,
                    gasLimit = gasLimit,
                    fee = feeWei,
                    canonicalFingerprint = fingerprint
                )

                if (startGen == transactionIntentGeneration && startFingerprint == buildIntentFingerprint(_uiState.value)) {
                    _uiState.update { 
                        it.copy(
                            confirmedSnapshot = intent,
                            authorizedFingerprint = null,
                            currentStep = TransactionStep.REVIEWED,
                            isSubmitting = false,
                            error = null
                        )
                    }
                }
            } catch (e: Exception) {
                if (startGen == transactionIntentGeneration && startFingerprint == buildIntentFingerprint(_uiState.value)) {
                    _uiState.update { 
                        it.copy(error = "建立交易意圖失敗: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * 請求授權：若為硬錢包則直接發送，若為熱錢包則進入 AUTH_REQUIRED 狀態
     */
    fun proceedToAuthorize() {
        val state = _uiState.value
        val intent = state.confirmedSnapshot ?: run {
            _uiState.update { it.copy(error = "交易確認快照無效，請重新確認交易", currentStep = TransactionStep.FAILED) }
            return
        }
        val activeWallet = state.activeWallet ?: run {
            _uiState.update { it.copy(error = "沒有找到活動錢包", currentStep = TransactionStep.FAILED) }
            return
        }

        if (activeWallet.isHardwareWallet) {
            sendTransaction()
        } else {
            _uiState.update { it.copy(currentStep = TransactionStep.AUTH_REQUIRED, error = null) }
        }
    }

    /**
     * 獲取用於簽名認證的 CryptoObject
     */
    fun getCryptoObjectForSigning(keyId: String): androidx.biometric.BiometricPrompt.CryptoObject? {
        return try {
            val androidKeyManager = secureKeyManager as? com.cbstudio.wearwallet.core.security.AndroidSecureKeyManager
            androidKeyManager?.createCryptoObjectForDecryption(keyId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create CryptoObject for signing key: $keyId")
            null
        }
    }

    /**
     * 生物識別授權成功 (帶 typed PlatformAuthHandle)
     */
    fun onBiometricAuthSuccess(authHandle: com.cbstudio.wearwallet.core.security.PlatformAuthHandle) {
        this.activeAuthHandle = authHandle
        _uiState.update { it.copy(currentStep = TransactionStep.AUTHENTICATING) }
        val authContext = AuthenticationContext(
            authHandle = authHandle,
            cryptoObject = authHandle.cryptoObject
        )
        sendTransaction(authContext = authContext)
    }

    /**
     * 執行舊版錢包遷移至 KeyVault
     */
    fun onPerformLegacyMigration(password: CharArray, authContext: AuthenticationContext) {
        val state = _uiState.value
        val activeWallet = state.activeWallet ?: run {
            _uiState.update { it.copy(error = "沒有找到活動錢包", currentStep = TransactionStep.FAILED) }
            return
        }
        val pwCopy = password.copyOf()
        _uiState.update { it.copy(currentStep = TransactionStep.MIGRATING, isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = walletRepository.migrateLegacyWalletIfNeeded(
                    walletId = activeWallet.id,
                    password = pwCopy,
                    authContext = authContext
                )
                when (result) {
                    is Result.Success -> {
                        val migratedWallet = result.data
                        _uiState.update {
                            it.copy(
                                activeWallet = migratedWallet,
                                currentStep = TransactionStep.INPUT_ADDRESS,
                                isLoading = false,
                                error = null
                            )
                        }
                        Timber.d("Legacy wallet migration succeeded for wallet: ${migratedWallet.id}")
                    }
                    is Result.Failure -> {
                        val errMsg = result.exception.message ?: "舊版錢包遷移失敗，請檢查密碼"
                        _uiState.update {
                            it.copy(
                                currentStep = TransactionStep.FAILED,
                                isLoading = false,
                                error = "遷移失敗: $errMsg"
                            )
                        }
                        Timber.e(result.exception, "Legacy wallet migration failed")
                    }
                    is Result.Loading -> {}
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        currentStep = TransactionStep.FAILED,
                        isLoading = false,
                        error = "遷移異常: ${e.message}"
                    )
                }
                Timber.e(e, "Legacy wallet migration exception")
            } finally {
                pwCopy.fill('\u0000')
            }
        }
    }

    fun onPerformLegacyMigration(password: String, authContext: AuthenticationContext) {
        val pwChars = password.toCharArray()
        try {
            onPerformLegacyMigration(pwChars, authContext)
        } finally {
            pwChars.fill('\u0000')
        }
    }

    /**
     * 預先準備金鑰佈建會話 (Exact Session Provisioning Request)
     */
    fun prepareProvisioning(
        onReady: (com.cbstudio.wearwallet.core.security.ProvisioningRequest) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            when (val res = walletRepository.prepareProvisioning()) {
                is Result.Success -> onReady(res.data)
                is Result.Failure -> onError(res.exception.message ?: "Failed to prepare provisioning")
                else -> onError("Failed to prepare provisioning")
            }
        }
    }

    /**
     * 取消遷移
     */
    fun onCancelMigration() {
        _uiState.update {
            it.copy(
                currentStep = TransactionStep.INPUT_ADDRESS,
                error = null,
                isLoading = false
            )
        }
    }

    /**
     * 認證取消
     */
    fun onAuthCancel() {
        activeAuthHandle?.invalidate()
        activeAuthHandle = null
        _uiState.update {
            it.copy(
                currentStep = TransactionStep.AUTH_CANCELLED,
                authorizedFingerprint = null,
                isSubmitting = false
            )
        }
    }

    /**
     * 認證過期 / 逾時
     */
    fun onAuthExpired() {
        activeAuthHandle?.invalidate()
        activeAuthHandle = null
        _uiState.update {
            it.copy(
                currentStep = TransactionStep.AUTH_EXPIRED,
                authorizedFingerprint = null,
                confirmedSnapshot = null,
                isSubmitting = false
            )
        }
    }

    /**
     * 應用進入背景時取消授權
     */
    fun onAppBackgrounded() {
        activeAuthHandle?.invalidate()
        activeAuthHandle = null
        val currentStep = _uiState.value.currentStep
        if (currentStep == TransactionStep.AUTH_REQUIRED || currentStep == TransactionStep.AUTHENTICATING) {
            _uiState.update {
                it.copy(
                    currentStep = TransactionStep.AUTH_CANCELLED,
                    authorizedFingerprint = null,
                    isSubmitting = false
                )
            }
        }
    }

    /**
     * 認證錯誤
     */
    fun onAuthError(error: String) {
        activeAuthHandle?.invalidate()
        activeAuthHandle = null
        _uiState.update {
            it.copy(
                error = "認證錯誤: $error",
                currentStep = TransactionStep.FAILED,
                isSubmitting = false
            )
        }
    }
    
    /**
     * 發送交易 — 嚴格驗證 ConfirmedEvmTransactionIntent 與授權綁定
     */
    fun sendTransaction(authContext: AuthenticationContext? = null) {
        viewModelScope.launch {
            if (!submissionMutex.tryLock()) {
                Timber.w("sendTransaction ignored: duplicate submission locked")
                return@launch
            }
            try {
                val currentState = _uiState.value
                val intent = currentState.confirmedSnapshot ?: run {
                    val errMsg = if (currentState.estimatedGasPriceWei == null || currentState.estimatedGasLimitObj == null) {
                        "Gas 估算未完成或失敗，無法進行交易"
                    } else {
                        "交易確認快照無效，請重新確認交易"
                    }
                    _uiState.update { 
                        it.copy(
                            error = errMsg,
                            currentStep = TransactionStep.FAILED,
                            isLoading = false,
                            isSubmitting = false
                        )
                    }
                    return@launch
                }

                // 熱錢包若無授權，切換至 AUTH_REQUIRED
                if (authContext == null && activeAuthHandle == null && currentState.activeWallet?.isHardwareWallet == false) {
                    _uiState.update { 
                        it.copy(
                            currentStep = TransactionStep.AUTH_REQUIRED,
                            isLoading = false,
                            isSubmitting = false
                        )
                    }
                    return@launch
                }

                // 綁定授權指紋 (64-char lowercase hex signing Keccak digest)
                val currentFingerprint = intent.signingDigestHex
                _uiState.update { 
                    it.copy(
                        isLoading = true,
                        isSubmitting = true,
                        authorizedFingerprint = currentFingerprint,
                        currentStep = TransactionStep.AUTHORIZED,
                        error = null
                    )
                }
                
                if (currentState.activeWallet?.isHardwareWallet == true) {
                    _uiState.update { it.copy(currentStep = TransactionStep.SIGNING) }
                    val result = sendTransactionUseCase.createUnsignedTransaction(
                        toAddress = intent.recipient.value,
                        amount = intent.humanAmount,
                        tokenAddress = intent.tokenContract?.value,
                        tokenDecimals = intent.tokenDecimals,
                        gasPrice = intent.gasPrice.toHex(),
                        gasLimit = intent.gasLimit.toLong().toString()
                    )
                    
                    when (result) {
                        is Result.Success -> {
                            _uiState.update { 
                                it.copy(
                                    keystoneUnsignedTx = result.data,
                                    isLoading = false,
                                    isSubmitting = false
                                )
                            }
                            return@launch
                        }
                        is Result.Failure -> {
                            _uiState.update { 
                                it.copy(
                                    error = "建立交易失敗: ${result.exception.message}",
                                    currentStep = TransactionStep.FAILED,
                                    isLoading = false,
                                    isSubmitting = false
                                )
                            }
                            return@launch
                        }
                        else -> { return@launch }
                    }
                }
                
                // 熱錢包進入簽名中狀態
                _uiState.update { it.copy(currentStep = TransactionStep.SIGNING) }

                val resolvedAuthContext = authContext ?: activeAuthHandle?.let {
                    AuthenticationContext(authHandle = it, cryptoObject = it.cryptoObject)
                }

                // Directly pass ConfirmedEvmTransactionIntent to SendTransactionUseCase
                sendTransactionUseCase(
                    intent = intent,
                    authContext = resolvedAuthContext
                ).collect { result ->
                    when (result) {
                        is Result.Success -> {
                            _uiState.update { 
                                it.copy(
                                    txHash = result.data,
                                    currentStep = TransactionStep.BROADCASTED,
                                    broadcastStatus = EvmBroadcastOutcome.statusForSubmittedHash(),
                                    isLoading = false,
                                    isSubmitting = false
                                )
                            }
                            Timber.d("交易已送出（待鏈上確認）: ${result.data}")
                        }
                        is Result.Failure -> {
                            _uiState.update { 
                                it.copy(
                                    error = "交易失敗: ${result.exception.message}",
                                    currentStep = TransactionStep.FAILED,
                                    isLoading = false,
                                    isSubmitting = false
                                )
                            }
                            Timber.e(result.exception, "交易失敗")
                        }
                        is Result.Loading -> {
                            _uiState.update { it.copy(currentStep = TransactionStep.BROADCASTING) }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = "發送交易時發生錯誤: ${e.message}",
                        currentStep = TransactionStep.FAILED,
                        isLoading = false,
                        isSubmitting = false
                    )
                }
                Timber.e(e, "發送交易異常")
            } finally {
                activeAuthHandle?.invalidate()
                activeAuthHandle = null
                submissionMutex.unlock()
            }
        }
    }
    
    /**
     * 返回上一步
     */
    fun goBack() {
        val currentStep = _uiState.value.currentStep
        val newStep = when (currentStep) {
            TransactionStep.INPUT_AMOUNT -> TransactionStep.INPUT_ADDRESS
            TransactionStep.CONFIRM,
            TransactionStep.REVIEWED,
            TransactionStep.MIGRATION_REQUIRED,
            TransactionStep.MIGRATING,
            TransactionStep.AUTH_REQUIRED,
            TransactionStep.AUTH_CANCELLED,
            TransactionStep.AUTH_EXPIRED -> TransactionStep.INPUT_AMOUNT
            TransactionStep.FAILED -> if (_uiState.value.confirmedSnapshot != null) TransactionStep.REVIEWED else TransactionStep.INPUT_ADDRESS
            else -> currentStep
        }
        _uiState.update { 
            it.copy(
                currentStep = newStep,
                confirmedSnapshot = null,
                authorizedFingerprint = null,
                isSubmitting = false
            )
        }
    }
    
    /**
     * 重置交易
     */
    fun resetTransaction() {
        onIntentFieldChanged()
        val currentChain = ChainStateManager.getCurrentChain()
        val selection = try {
            ChainStateManager.getSelection()
        } catch (e: Exception) {
            ChainSelection.default()
        }
        val isUTXO = currentChain in listOf(
            ChainType.BITCOIN,
            ChainType.LITECOIN,
            ChainType.DOGECOIN,
            ChainType.BITCOIN_CASH
        )
        _uiState.update { 
            SendTransactionUiState(
                activeWallet = it.activeWallet,
                balance = it.balance,
                balanceInUsd = it.balanceInUsd,
                currentSelection = selection,
                currentChain = currentChain,
                isUTXOChain = isUTXO
            )
        }
    }
    
    /**
     * 清除錯誤
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * 清除 Keystone 導航狀態
     */
    fun resetKeystoneNavigation() {
        _uiState.update { it.copy(keystoneUnsignedTx = null, isLoading = false) }
    }
    
    /**
     * 設置最大金額
     */
    fun setMaxAmount() {
        val totalFee = _uiState.value.estimatedTotalFee
        if (totalFee == null) {
            _uiState.update { 
                it.copy(error = "Gas 估算未完成或失敗，無法計算最大金額")
            }
            return
        }
        val maxAmount = _uiState.value.balance.subtract(
            BigDecimal(totalFee)
        )
        
        if (maxAmount > BigDecimal.ZERO) {
            setAmount(maxAmount.toPlainString())
        } else {
            _uiState.update { 
                it.copy(error = "餘額不足以支付 Gas 費用")
            }
        }
    }
}