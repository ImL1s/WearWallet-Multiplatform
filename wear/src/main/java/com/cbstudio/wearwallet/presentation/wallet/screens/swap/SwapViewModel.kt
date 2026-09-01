package com.cbstudio.wearwallet.presentation.wallet.screens.swap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.Token
import com.cbstudio.wearwallet.core.domain.repository.WalletRepository
import com.cbstudio.wearwallet.core.domain.repository.TokenRepository
import com.cbstudio.wearwallet.core.rango.RangoMetadataRepository
import com.cbstudio.wearwallet.core.rango.RangoRepository
import com.cbstudio.wearwallet.core.rango.model.RangoBlockchain
import com.cbstudio.wearwallet.core.rango.model.RangoQuoteResponse
import com.cbstudio.wearwallet.core.rango.model.RangoStatusResponse
import com.cbstudio.wearwallet.core.rango.model.RangoSwapResponse
import com.cbstudio.wearwallet.core.rango.model.RangoTokenMeta
import com.cbstudio.wearwallet.core.swap.SwapExecutor
import com.cbstudio.wearwallet.core.domain.usecase.swap.GetSwapQuoteUseCase
import com.cbstudio.wearwallet.core.domain.usecase.swap.ExecuteSwapUseCase
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.security.CapabilityRequest
import com.cbstudio.wearwallet.core.security.Operation
import com.cbstudio.wearwallet.core.security.Platform
import com.cbstudio.wearwallet.core.security.BuildType
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import com.cbstudio.wearwallet.core.security.SignerImplementation
import com.cbstudio.wearwallet.core.security.WalletType
import com.cbstudio.wearwallet.core.security.BackendIdentity
import com.cbstudio.wearwallet.core.security.RuntimeCapabilityContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.math.BigDecimal
import android.util.Log
import com.cbstudio.wearwallet.core.swap.SwapError

/**
 * Swap UI State
 */
data class SwapUiState(
    // Token Selection
    val fromToken: RangoTokenMeta? = null,
    val toToken: RangoTokenMeta? = null,
    
    // Balance
    val fromTokenBalance: Double = 0.0,
    
    // Amount
    val amount: String = "",
    val amountUsd: Double? = null,
    
    // Quote
    val quote: RangoQuoteResponse? = null,
    
    // Status
    val status: SwapStatus = SwapStatus.IDLE,
    val error: String? = null,
    
    // Transaction
    val txHash: String? = null,
    val requestId: String? = null
)

enum class SwapStatus {
    IDLE,
    LOADING_METADATA,
    UNLOCKING,
    GETTING_QUOTE,
    QUOTE_READY,
    EXECUTING,
    WAITING_CONFIRMATION,
    SUCCESS,
    FAILED
}

/**
 * Swap ViewModel with Rango Metadata Integration
 */
class SwapViewModel : ViewModel(), KoinComponent {
    
    // Use lazy initialization to defer Koin dependency resolution
    // This prevents these heavy dependencies from being resolved during app startup
    private val rangoRepository: RangoRepository by lazy { getKoin().get() }
    private val metadataRepository: RangoMetadataRepository by lazy { getKoin().get() }
    // SwapExecutor and RangoRepository encapsulated in ExecuteSwapUseCase/GetSwapQuoteUseCase
    private val getSwapQuoteUseCase: GetSwapQuoteUseCase by lazy { getKoin().get() }
    private val executeSwapUseCase: ExecuteSwapUseCase? by lazy { getKoin().getOrNull() }
    private val walletRepository: WalletRepository by lazy { getKoin().get() }
    private val capabilityGate: com.cbstudio.wearwallet.core.security.CapabilityGate by lazy { getKoin().get() }
    private val platformProvider: com.cbstudio.wearwallet.core.security.PlatformProvider by lazy { getKoin().getOrNull() ?: com.cbstudio.wearwallet.core.security.TestPlatformProvider() }
    private val buildTypeProvider: com.cbstudio.wearwallet.core.security.BuildTypeProvider by lazy { getKoin().getOrNull() ?: com.cbstudio.wearwallet.core.security.TestBuildTypeProvider() }
    private val attestationProvider: com.cbstudio.wearwallet.core.security.BackendAttestationProvider by lazy { getKoin().getOrNull() ?: com.cbstudio.wearwallet.core.security.DefaultBackendAttestationProvider() }

    // Inject TokenRepository
    private val tokenRepository: TokenRepository by lazy { getKoin().get() }

    private val _uiState = MutableStateFlow(SwapUiState())
    val uiState: StateFlow<SwapUiState> = _uiState.asStateFlow()
    
    // Available chains and tokens from metadata
    private val _availableChains = MutableStateFlow<List<RangoBlockchain>>(emptyList())
    val availableChains: StateFlow<List<RangoBlockchain>> = _availableChains.asStateFlow()
    
    private val _availableTokens = MutableStateFlow<List<RangoTokenMeta>>(emptyList())
    val availableTokens: StateFlow<List<RangoTokenMeta>> = _availableTokens.asStateFlow()
    
    // Popular swap presets
    val popularSwapPairs = listOf(
        SwapPair("BNB", "BSC", "USDT", "BSC"),
        SwapPair("BNB", "BSC", "USDC", "POLYGON"),
        SwapPair("MATIC", "POLYGON", "USDC", "POLYGON"),
        SwapPair("ETH", "ETH", "USDC", "ETH")
    )
    // Lazy loading flag - metadata will be loaded when first needed
    private var isMetadataLoaded = false
    
    init {
        loadMetadata()
    }
    
    /**
     * Load blockchain and token metadata
     */
    fun loadMetadata() {
        // Skip if already loaded or loading
        if (isMetadataLoaded || _uiState.value.status == SwapStatus.LOADING_METADATA) return
        isMetadataLoaded = true
        
        viewModelScope.launch {
            Log.d("SwapViewModel", "loadMetadata: Starting...")
            _uiState.update { it.copy(status = SwapStatus.LOADING_METADATA) }
            
            Log.d("SwapViewModel", "loadMetadata: Calling metadataRepository.getMetadata()")
            metadataRepository.getMetadata()
                .onSuccess { metadata ->
                    Log.d("SwapViewModel", "loadMetadata: Success! Chains=${metadata.blockchains.size}")
                    _availableChains.value = metadata.blockchains.filter { it.enabled }
                    
                    // 合併 Rango 代幣與默認熱門代幣，確保永遠有代幣可選
                    val rangoTokens = metadata.tokens
                    val fallbackTokens = getDefaultFallbackTokens()
                    val existingKeys = rangoTokens.map { "${it.symbol}:${it.blockchain}" }.toSet()
                    val merged = rangoTokens + fallbackTokens.filter { 
                        "${it.symbol}:${it.blockchain}" !in existingKeys 
                    }
                    _availableTokens.value = merged
                    
                    // Set default tokens if available
                    val defaultFrom = _availableTokens.value.find { 
                        it.blockchain.equals("ETH", ignoreCase = true) && it.symbol == "ETH"
                    }
                    val defaultTo = _availableTokens.value.find { 
                        it.blockchain.equals("BSC", ignoreCase = true) && it.symbol == "BNB"
                    }
                    
                    _uiState.update { 
                        it.copy(
                            status = SwapStatus.IDLE,
                            fromToken = defaultFrom,
                            toToken = defaultTo
                        )
                    }
                }
                .onFailure { error ->
                    Log.e("SwapViewModel", "loadMetadata: Failed!", error)
                    // Fallback: load hardcoded popular tokens so Swap screen is not empty
                    Log.d("SwapViewModel", "loadMetadata: Loading fallback popular tokens")
                    _availableTokens.value = getDefaultFallbackTokens()
                    _uiState.update { 
                        it.copy(status = SwapStatus.IDLE, error = null)
                    }
                }
        }
    }
    
    /**
     * Get hardcoded popular tokens as fallback when Rango API is unavailable.
     * These tokens are for display purposes; actual swaps still require Rango API.
     */
    private fun getDefaultFallbackTokens(): List<RangoTokenMeta> = listOf(
        // ===== BSC =====
        RangoTokenMeta(blockchain = "BSC", symbol = "BNB", name = "BNB", decimals = 18, chainId = "56", isPopular = true),
        RangoTokenMeta(blockchain = "BSC", symbol = "USDT", name = "Tether USD", address = "0x55d398326f99059fF775485246999027B3197955", decimals = 18, chainId = "56", isPopular = true),
        RangoTokenMeta(blockchain = "BSC", symbol = "USDC", name = "USD Coin", address = "0x8AC76a51cc950d9822D68b83fE1Ad97B32Cd580d", decimals = 18, chainId = "56", isPopular = true),
        RangoTokenMeta(blockchain = "BSC", symbol = "BUSD", name = "Binance USD", address = "0xe9e7CEA3DedcA5984780Bafc599bD69ADd087D56", decimals = 18, chainId = "56", isPopular = true),
        // ===== Ethereum =====
        RangoTokenMeta(blockchain = "ETH", symbol = "ETH", name = "Ethereum", decimals = 18, chainId = "1", isPopular = true),
        RangoTokenMeta(blockchain = "ETH", symbol = "USDT", name = "Tether USD", address = "0xdAC17F958D2ee523a2206206994597C13D831ec7", decimals = 6, chainId = "1", isPopular = true),
        RangoTokenMeta(blockchain = "ETH", symbol = "USDC", name = "USD Coin", address = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", decimals = 6, chainId = "1", isPopular = true),
        RangoTokenMeta(blockchain = "ETH", symbol = "DAI", name = "Dai Stablecoin", address = "0x6B175474E89094C44Da98b954EedeAC495271d0F", decimals = 18, chainId = "1", isPopular = true),
        RangoTokenMeta(blockchain = "ETH", symbol = "WETH", name = "Wrapped Ether", address = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", decimals = 18, chainId = "1", isPopular = true),
        // ===== Polygon =====
        RangoTokenMeta(blockchain = "POLYGON", symbol = "MATIC", name = "Polygon", decimals = 18, chainId = "137", isPopular = true),
        RangoTokenMeta(blockchain = "POLYGON", symbol = "USDT", name = "Tether USD", address = "0xc2132D05D31c914a87C6611C10748AEb04B58e8F", decimals = 6, chainId = "137", isPopular = true),
        RangoTokenMeta(blockchain = "POLYGON", symbol = "USDC", name = "USD Coin", address = "0x2791Bca1f2de4661ED88A30C99A7a9449Aa84174", decimals = 6, chainId = "137", isPopular = true),
        // ===== Arbitrum =====
        RangoTokenMeta(blockchain = "ARBITRUM", symbol = "ETH", name = "Ethereum", decimals = 18, chainId = "42161", isPopular = true),
        RangoTokenMeta(blockchain = "ARBITRUM", symbol = "USDT", name = "Tether USD", address = "0xFd086bC7CD5C481DCC9C85ebE478A1C0b69FCbb9", decimals = 6, chainId = "42161", isPopular = true),
        RangoTokenMeta(blockchain = "ARBITRUM", symbol = "USDC", name = "USD Coin", address = "0xFF970A61A04b1cA14834A43f5dE4533eBDDB5CC8", decimals = 6, chainId = "42161", isPopular = true),
        // ===== Avalanche =====
        RangoTokenMeta(blockchain = "AVAX_CCHAIN", symbol = "AVAX", name = "Avalanche", decimals = 18, chainId = "43114", isPopular = true),
        RangoTokenMeta(blockchain = "AVAX_CCHAIN", symbol = "USDT", name = "Tether USD", address = "0x9702230A8Ea53601f5cD2dc00fDBc13d4dF4A8c7", decimals = 6, chainId = "43114", isPopular = true),
        RangoTokenMeta(blockchain = "AVAX_CCHAIN", symbol = "USDC", name = "USD Coin", address = "0xB97EF9Ef8734C71904D8002F8b6Bc66Dd9c48a6E", decimals = 6, chainId = "43114", isPopular = true),
    )
    
    /**
     * Get tokens for a specific blockchain
     */
    fun getTokensForChain(chain: String): List<RangoTokenMeta> {
        return _availableTokens.value.filter { 
            it.blockchain.equals(chain, ignoreCase = true) 
        }
    }
    
    /**
     * Set From Token and fetch its balance
     */
    fun setFromToken(token: RangoTokenMeta) {
        _uiState.update { 
            it.copy(fromToken = token, fromTokenBalance = 0.0, quote = null, status = SwapStatus.IDLE)
        }
        // Fetch balance asynchronously
        fetchTokenBalance(token)
    }
    
    /**
     * Fetch token balance for the selected fromToken
     */
    private fun fetchTokenBalance(token: RangoTokenMeta) {
        viewModelScope.launch {
            try {
                val activeWalletResult = walletRepository.getActiveWallet()
                val wallet = (activeWalletResult as? Result.Success)?.data ?: return@launch
                
                // Get chain type from token's blockchain
                val chainType = getChainType(token.blockchain)
                
                // If native token, get native balance; otherwise get token balance
                val balance = if (token.isNative) {
                    fetchNativeBalance(wallet.address, chainType)
                } else {
                    fetchErc20Balance(wallet.address, token.address ?: "", chainType, token.decimals)
                }
                
                _uiState.update { it.copy(fromTokenBalance = balance) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch token balance")
            }
        }
    }
    
    private suspend fun fetchNativeBalance(address: String, chainType: ChainType): Double {
        return try {
            val balanceStr = walletRepository.getNativeBalance(address, chainType)
            // Native balance usually returned as Double in our repo, but interface said String? 
            // Checking RealTokenRepository, getNativeBalance returns String (raw value or float string?)
            // Actually RealWalletRepository.getNativeBalance returns Double. TokenRepository.getNativeBalance returns String.
            // Using walletRepository methods for consistency with Dashboard
            walletRepository.getNativeBalance(address, chainType)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch native balance")
            0.0
        }
    }
    
    private suspend fun fetchErc20Balance(
        ownerAddress: String, 
        tokenAddress: String, 
        chainType: ChainType,
        decimals: Int
    ): Double {
        return try {
            val balanceStr = tokenRepository.getTokenBalance(ownerAddress, tokenAddress, chainType)
            if (balanceStr.isEmpty()) return 0.0
            
            // Scale balance: balance / 10^decimals
            val balanceBig = BigDecimal(balanceStr)
            val divisor = BigDecimal.TEN.pow(decimals)
            balanceBig.divide(divisor, 6, java.math.RoundingMode.HALF_UP).toDouble()
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch ERC20 balance for $tokenAddress")
            0.0
        }
    }
    
    /**
     * Set To Token
     */
    fun setToToken(token: RangoTokenMeta) {
        _uiState.update { 
            it.copy(toToken = token, quote = null, status = SwapStatus.IDLE)
        }
    }
    
    /**
     * Set Amount
     */
    fun setAmount(amount: String) {
        val fromToken = _uiState.value.fromToken
        val amountUsd = if (amount.isNotEmpty() && fromToken?.usdPrice != null) {
            try {
                amount.toDouble() * fromToken.usdPrice!!
            } catch (e: Exception) { null }
        } else null
        
        _uiState.update { 
            it.copy(amount = amount, amountUsd = amountUsd, quote = null)
        }
    }
    
    /**
     * Select a popular swap pair
     */
    fun selectPopularPair(pair: SwapPair) {
        val from = _availableTokens.value.find { 
            it.symbol == pair.fromSymbol && it.blockchain == pair.fromChain 
        }
        val to = _availableTokens.value.find { 
            it.symbol == pair.toSymbol && it.blockchain == pair.toChain 
        }
        
        _uiState.update { 
            it.copy(fromToken = from, toToken = to, quote = null)
        }
    }
    
    /**
     * Get Quote from Rango
     */
    /**
     * Get Quote from Rango
     */
    fun getQuote() {
        val state = _uiState.value
        val fromToken = state.fromToken ?: return
        val toToken = state.toToken ?: return
        val amount = state.amount
        
        if (amount.isEmpty() || amount == "0") return
        
        viewModelScope.launch {
            _uiState.update { it.copy(status = SwapStatus.GETTING_QUOTE, error = null) }
            
            // Convert to Wei/base units
            val amountInWei = convertToBaseUnits(amount, fromToken.decimals)
            
            val result = getSwapQuoteUseCase(
                GetSwapQuoteUseCase.Params(
                    fromToken = fromToken,
                    toToken = toToken,
                    amountInWei = amountInWei,
                    slippage = 1.0
                )
            )
            
            when (result) {
                is Result.Success -> {
                    val quote = result.data
                    if (quote.resultType == "OK" && quote.route != null) {
                        _uiState.update { 
                            it.copy(
                                status = SwapStatus.QUOTE_READY,
                                quote = quote,
                                requestId = quote.requestId
                            )
                        }
                    } else {
                        _uiState.update { 
                            it.copy(
                                status = SwapStatus.FAILED,
                                error = quote.error ?: "No route found"
                            )
                        }
                    }
                }
                is Result.Failure -> {
                    _uiState.update { 
                        it.copy(status = SwapStatus.FAILED, error = result.exception.message)
                    }
                }
                is Result.Loading -> {
                    // No-op
                }
            }
        }
    }
    
    /**
     * Unlock wallet and execute swap (M3: No raw private key export to presentation)
     */
    fun unlockWallet(password: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(status = SwapStatus.UNLOCKING, error = null) }
            
            val activeWalletResult = walletRepository.getActiveWallet()
            val wallet = (activeWalletResult as? Result.Success)?.data
            
            if (wallet == null) {
                _uiState.update { 
                    it.copy(status = SwapStatus.FAILED, error = "No active wallet found")
                }
                return@launch
            }
            
            val ctx = ChainExecutionContextRegistry.resolve(wallet.chainType)
            val attestation = attestationProvider.getAttestation(ctx)
            val runtimeContext = RuntimeCapabilityContext(
                platform = platformProvider.currentPlatform,
                buildType = buildTypeProvider.currentBuildType,
                chainContext = ctx,
                walletType = WalletType.SOFTWARE_MNEMONIC,
                envelopeType = EvmEnvelope.LEGACY,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL
            )
            val req = CapabilityRequest.fromRuntime(
                operation = Operation.SOFTWARE_SIGN,
                runtimeContext = runtimeContext,
                attestation = attestation
            )
            if (!capabilityGate.verifyCapability(req)) {
                _uiState.update { 
                    it.copy(
                        status = SwapStatus.FAILED, 
                        error = "Production capability gate fail-closed: swap disabled for ${wallet.chainType.displayName}"
                    ) 
                }
                return@launch
            }

            performSwapExecution(wallet.address)
        }
    }

    private fun performSwapExecution(walletAddress: String) {
        val state = _uiState.value
        val fromToken = state.fromToken ?: return
        val toToken = state.toToken ?: return
        val amount = state.amount
        
        if (amount.isEmpty()) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(status = SwapStatus.EXECUTING, error = null) }
            
            val useCase = executeSwapUseCase
            if (useCase == null) {
                _uiState.update { 
                    it.copy(
                        status = SwapStatus.FAILED,
                        error = "發行版本不支援直接執行 Swap 交易"
                    )
                }
                return@launch
            }
            
            val amountInWei = convertToBaseUnits(amount, fromToken.decimals)
            
            val txResult = useCase(
                ExecuteSwapUseCase.Params(
                    fromToken = fromToken,
                    toToken = toToken,
                    amountInWei = amountInWei,
                    walletAddress = walletAddress,
                    slippage = 1.0
                )
            )
            
            when (txResult) {
                is Result.Success<*> -> {
                    val success = txResult.data as? ExecuteSwapUseCase.Success
                    val txHash = success?.txHash ?: txResult.data.toString()
                    val requestId = success?.requestId
                    val isCrossChain = success?.isCrossChain == true
                    
                    if (isCrossChain && requestId != null) {
                        _uiState.update { 
                            it.copy(
                                status = SwapStatus.WAITING_CONFIRMATION, 
                                txHash = txHash,
                                requestId = requestId
                            )
                        }
                        startStatusPolling(requestId, txHash)
                    } else {
                        _uiState.update { 
                            it.copy(status = SwapStatus.SUCCESS, txHash = txHash)
                        }
                    }
                }
                is Result.Failure -> {
                    _uiState.update { 
                        it.copy(
                            status = SwapStatus.FAILED, 
                            error = formatErrorMessage(txResult.exception)
                        )
                    }
                }
                is Result.Loading<*> -> {
                    // No-op for loading state
                }
            }
        }
    }
    
    fun reset() {
        _uiState.update { 
            SwapUiState(
                fromToken = it.fromToken,
                toToken = it.toToken,
                status = SwapStatus.IDLE
            )
        }
    }
    
    private fun startStatusPolling(requestId: String, txHash: String) {
        viewModelScope.launch {
            var attempts = 0
            val maxAttempts = 60
            
            while (attempts < maxAttempts) {
                delay(5000)
                attempts++
                
                try {
                    val statusResult = rangoRepository.checkStatus(requestId, txHash)
                    statusResult.fold(
                        onSuccess = { status ->
                            when (status.status?.uppercase()) {
                                "SUCCESS", "COMPLETED" -> {
                                    _uiState.update { it.copy(status = SwapStatus.SUCCESS) }
                                    return@launch
                                }
                                "FAILED", "REFUNDED" -> {
                                    _uiState.update { 
                                        it.copy(status = SwapStatus.FAILED, error = status.error ?: "交易失敗")
                                    }
                                    return@launch
                                }
                            }
                        },
                        onFailure = {
                            Timber.w("Status check failed: ${it.message}")
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Status polling error")
                }
            }
            
            _uiState.update { 
                it.copy(status = SwapStatus.SUCCESS, error = "交易已提交，請稍後確認")
            }
        }
    }
    
    private fun formatErrorMessage(exception: Exception): String {
        return SwapError.fromException(exception).message
    }
    
    private fun convertToBaseUnits(amount: String, decimals: Int): String {
        return try {
            val value = amount.toBigDecimal()
            val multiplier = java.math.BigDecimal.TEN.pow(decimals)
            java.math.BigDecimal(value.toString()).multiply(multiplier).toBigInteger().toString()
        } catch (e: Exception) {
            "0"
        }
    }
    
    private fun getChainType(chain: String): ChainType {
        return ChainType.fromRangoChainName(chain) ?: ChainType.ETHEREUM
    }
    

    
    private fun getChainId(chain: String): Int {
        return getChainType(chain).getChainId().toInt()
    }
}

data class SwapPair(
    val fromSymbol: String,
    val fromChain: String,
    val toSymbol: String,
    val toChain: String
)
