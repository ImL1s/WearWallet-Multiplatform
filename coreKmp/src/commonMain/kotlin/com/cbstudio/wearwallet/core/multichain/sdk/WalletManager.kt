package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.security.ApiKeyManager
import com.cbstudio.wearwallet.core.multichain.monero.MoneroWalletManager
import com.cbstudio.wearwallet.core.multichain.monero.BalanceInfo
import com.cbstudio.wearwallet.core.multichain.monero.AddressInfo
import com.cbstudio.wearwallet.core.multichain.monero.MoneroTransaction
import com.cbstudio.wearwallet.core.multichain.monero.MoneroTransactionResult
import com.cbstudio.wearwallet.core.multichain.monero.MoneroTransactionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.security.CapabilityGate
import com.cbstudio.wearwallet.core.security.CapabilityRequest
import com.cbstudio.wearwallet.core.security.Platform
import com.cbstudio.wearwallet.core.security.BuildType
import com.cbstudio.wearwallet.core.domain.model.quantities.EvmEnvelope
import com.cbstudio.wearwallet.core.security.SignerImplementation
import com.cbstudio.wearwallet.core.security.WalletType
import com.cbstudio.wearwallet.core.security.BackendIdentity
import com.cbstudio.wearwallet.core.security.Network
import com.cbstudio.wearwallet.core.security.Operation
import com.cbstudio.wearwallet.core.security.ReleaseProductionCapabilityGate
import com.cbstudio.wearwallet.core.security.PlatformProvider
import com.cbstudio.wearwallet.core.security.BuildTypeProvider
import com.cbstudio.wearwallet.core.security.BackendAttestationProvider
import com.cbstudio.wearwallet.core.security.TestPlatformProvider
import com.cbstudio.wearwallet.core.security.TestBuildTypeProvider
import com.cbstudio.wearwallet.core.security.DefaultBackendAttestationProvider
import com.cbstudio.wearwallet.core.security.RuntimeCapabilityContext
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException

/**
 * 錢包管理器
 * 整合助記詞、私鑰管理和區塊鏈 SDK
 * 
 * 新增: Monero 完整支援
 * - 持久化快取
 * - 後台同步
 * - 多賬戶管理
 */
class WalletManager @kotlin.jvm.JvmOverloads constructor(
    private val mnemonic: String,
    private val capabilityGate: CapabilityGate,
    private val platformProvider: PlatformProvider = TestPlatformProvider(),
    private val buildTypeProvider: BuildTypeProvider = TestBuildTypeProvider(),
    private val attestationProvider: BackendAttestationProvider = DefaultBackendAttestationProvider()
) {

    init {
        require(mnemonic.isNotBlank()) {
            "Mnemonic must be provided to initialize WalletManager. Hardcoded test mnemonics are strictly prohibited."
        }
    }
    private val sdkManager = RealSDKFactory.createRealManager()
    private val addressDerivation = AddressDerivation()
    
    // Monero 專用管理器
    private val moneroManager = MoneroWalletManager()
    
    // Monero 錢包 ID（以助記詞的哈希作為識別）
    private val moneroWalletId: String by lazy {
        "monero_" + activeMnemonic.hashCode().toString()
    }
    
    private val activeMnemonic = mnemonic
    
    // 動態派生的地址緩存
    private val derivedAddresses: Map<MultiChainType, String> by lazy {
        MultiChainType.values().associateWith { chainType ->
            val executionContext = ChainExecutionContextRegistry.resolve(chainType)
            val attestation = attestationProvider.getAttestationSync(executionContext)
            val runtimeContext = RuntimeCapabilityContext(
                platform = platformProvider.currentPlatform,
                buildType = buildTypeProvider.currentBuildType,
                chainContext = executionContext,
                walletType = WalletType.SOFTWARE_MNEMONIC,
                envelopeType = EvmEnvelope.LEGACY,
                signerImplementation = SignerImplementation.SOFTWARE_LOCAL
            )
            val req = CapabilityRequest.fromRuntime(
                operation = Operation.CREATE_WALLET,
                runtimeContext = runtimeContext,
                attestation = attestation
            )
            if (!capabilityGate.verifyCapability(req)) {
                ""
            } else {
                try {
                    addressDerivation.deriveAddress(activeMnemonic, chainType)
                } catch (e: Exception) {
                    println("⚠️ 無法派生 $chainType 地址: ${e.message}")
                    ""
                }
            }
        }
    }
    
    /**
     * 獲取指定鏈的 SDK
     */
    fun getSDK(chainType: MultiChainType): BlockchainSDKAdapter? {
        return sdkManager.getAdapter(chainType)
    }
    
    /**
     * 初始化所有 SDK
     */
    suspend fun initializeAll(): Result<Unit> {
        return try {
            val infuraApiKey = ApiKeyManager.getApiKey(ApiKeyManager.KEY_INFURA)
            val blockcypherKey = ApiKeyManager.getApiKey(ApiKeyManager.KEY_BLOCKCYPHER)
            
            // Ethereum
            val ethSDK = getSDK(MultiChainType.ETHEREUM)
            val ethRpcUrl = if (infuraApiKey != null && infuraApiKey != "null" && infuraApiKey.isNotBlank()) {
                "https://sepolia.infura.io/v3/$infuraApiKey"
            } else {
                println("⚠️ 沒有 Infura API Key，使用公共節點")
                "https://rpc.sepolia.org"
            }
            ethSDK?.initialize(SDKConfig(
                network = "sepolia",
                rpcUrl = ethRpcUrl,
                apiKey = infuraApiKey
            ))
            
            // Binance Smart Chain
            val bscSDK = getSDK(MultiChainType.BSC)
            bscSDK?.initialize(SDKConfig(
                network = "testnet",
                rpcUrl = "https://data-seed-prebsc-1-s1.binance.org:8545"
            ))
            
            // Polygon
            val polygonSDK = getSDK(MultiChainType.POLYGON)
            val polygonRpcUrl = if (infuraApiKey != null && infuraApiKey != "null" && infuraApiKey.isNotBlank()) {
                "https://polygon-mumbai.infura.io/v3/$infuraApiKey"
            } else {
                "https://rpc-mumbai.maticvigil.com"
            }
            polygonSDK?.initialize(SDKConfig(
                network = "mumbai",
                rpcUrl = polygonRpcUrl,
                apiKey = infuraApiKey
            ))
            
            // Avalanche
            val avalancheSDK = getSDK(MultiChainType.AVALANCHE)
            avalancheSDK?.initialize(SDKConfig(
                network = "fuji",
                rpcUrl = "https://api.avax-test.network/ext/bc/C/rpc"
            ))
            
            // Arbitrum
            val arbitrumSDK = getSDK(MultiChainType.ARBITRUM)
            val arbitrumRpcUrl = if (infuraApiKey != null && infuraApiKey != "null" && infuraApiKey.isNotBlank()) {
                "https://arbitrum-sepolia.infura.io/v3/$infuraApiKey"
            } else {
                "https://sepolia-rollup.arbitrum.io/rpc"
            }
            arbitrumSDK?.initialize(SDKConfig(
                network = "sepolia",
                rpcUrl = arbitrumRpcUrl,
                apiKey = infuraApiKey
            ))
            
            // Optimism
            val optimismSDK = getSDK(MultiChainType.OPTIMISM)
            val optimismRpcUrl = if (infuraApiKey != null && infuraApiKey != "null" && infuraApiKey.isNotBlank()) {
                "https://optimism-sepolia.infura.io/v3/$infuraApiKey"
            } else {
                "https://sepolia.optimism.io"
            }
            optimismSDK?.initialize(SDKConfig(
                network = "sepolia",
                rpcUrl = optimismRpcUrl,
                apiKey = infuraApiKey
            ))
            
            // Fantom
            val fantomSDK = getSDK(MultiChainType.FANTOM)
            fantomSDK?.initialize(SDKConfig(
                network = "testnet",
                rpcUrl = "https://rpc.testnet.fantom.network"
            ))
            
            // Cronos
            val cronosSDK = getSDK(MultiChainType.CRONOS)
            cronosSDK?.initialize(SDKConfig(
                network = "testnet",
                rpcUrl = "https://evm-t3.cronos.org"
            ))
            
            // Base
            val baseSDK = getSDK(MultiChainType.BASE)
            baseSDK?.initialize(SDKConfig(
                network = "sepolia",
                rpcUrl = "https://sepolia.base.org"
            ))
            
            // Celo
            val celoSDK = getSDK(MultiChainType.CELO)
            celoSDK?.initialize(SDKConfig(
                network = "alfajores",
                rpcUrl = "https://alfajores-forno.celo-testnet.org"
            ))
            
            // Moonbeam
            val moonbeamSDK = getSDK(MultiChainType.MOONBEAM)
            moonbeamSDK?.initialize(SDKConfig(
                network = "moonbase",
                rpcUrl = "https://rpc.api.moonbase.moonbeam.network"
            ))
            
            // Solana
            val solanaSDK = getSDK(MultiChainType.SOLANA)
            solanaSDK?.initialize(SDKConfig(
                network = "devnet",
                rpcUrl = "https://api.devnet.solana.com"
            ))
            
            // TRON
            val tronSDK = getSDK(MultiChainType.TRON)
            tronSDK?.initialize(SDKConfig(
                network = "shasta",
                rpcUrl = "https://api.shasta.trongrid.io"
            ))
            
            // Bitcoin
            val btcSDK = getSDK(MultiChainType.BITCOIN)
            btcSDK?.initialize(SDKConfig(
                network = "testnet",
                rpcUrl = "https://blockstream.info/testnet/api",
                apiKey = blockcypherKey
            ))
            
            // Litecoin
            val ltcSDK = getSDK(MultiChainType.LITECOIN)
            val ltcRpcUrl = if (blockcypherKey != null && blockcypherKey != "null" && blockcypherKey.isNotBlank()) {
                "https://api.blockcypher.com/v1/ltc/test3?token=$blockcypherKey"
            } else {
                "https://api.blockcypher.com/v1/ltc/test3"
            }
            ltcSDK?.initialize(SDKConfig(
                network = "testnet",
                rpcUrl = ltcRpcUrl,
                apiKey = blockcypherKey
            ))
            
            // Dogecoin
            val dogeSDK = getSDK(MultiChainType.DOGECOIN)
            val dogeRpcUrl = if (blockcypherKey != null && blockcypherKey != "null" && blockcypherKey.isNotBlank()) {
                "https://api.blockcypher.com/v1/doge/test?token=$blockcypherKey"
            } else {
                "https://api.blockcypher.com/v1/doge/test"
            }
            dogeSDK?.initialize(SDKConfig(
                network = "testnet",
                rpcUrl = dogeRpcUrl,
                apiKey = blockcypherKey
            ))
            
            // Bitcoin Cash
            val bchSDK = getSDK(MultiChainType.BITCOIN_CASH)
            bchSDK?.initialize(SDKConfig(
                network = "testnet",
                rpcUrl = "https://api.blockchair.com/bitcoin-cash/testnet",
                apiKey = blockcypherKey
            ))
            
            // Monero 初始化
            initializeMonero()
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }
    
    /**
     * 初始化 Monero 錢包 (Disabled in production release)
     */
    private suspend fun initializeMonero() {
        // Monero initialization disabled in production release
    }
    
    /**
     * 獲取地址（從助記詞派生）
     */
    fun getDerivedAddress(chainType: MultiChainType): String {
        val executionContext = ChainExecutionContextRegistry.resolve(chainType)
        val attestation = attestationProvider.getAttestationSync(executionContext)
        val runtimeContext = RuntimeCapabilityContext(
            platform = platformProvider.currentPlatform,
            buildType = buildTypeProvider.currentBuildType,
            chainContext = executionContext,
            walletType = WalletType.SOFTWARE_MNEMONIC,
            envelopeType = EvmEnvelope.LEGACY,
            signerImplementation = SignerImplementation.SOFTWARE_LOCAL
        )
        val req = CapabilityRequest.fromRuntime(
            operation = Operation.CREATE_WALLET,
            runtimeContext = runtimeContext,
            attestation = attestation
        )
        if (!capabilityGate.verifyCapability(req)) {
            return ""
        }
        return derivedAddresses[chainType] ?: ""
    }

    /**
     * 取得支援的區塊鏈類型 (過濾通過 CapabilityGate 的鏈)
     */
    fun getSupportedChains(): List<MultiChainType> {
        return MultiChainType.values().filter { chainType ->
            capabilityGate.isChainSupported(chainType)
        }
    }
    
    /**
     * 同步並獲取 Monero 餘額
     */
    suspend fun syncMoneroBalance(forceFullScan: Boolean = false): Result<BalanceInfo> {
        return moneroManager.syncAndGetBalance(moneroWalletId, forceFullScan)
    }
    
    /**
     * 獲取 Monero 快取餘額（不同步）
     */
    fun getMoneroCachedBalance(): Result<BalanceInfo> {
        val balance = moneroManager.getCachedBalance(moneroWalletId)
        return if (balance != null) {
            Result.Success(balance)
        } else {
            Result.Failure(Exception("No cached balance available"))
        }
    }
    
    /**
     * 創建 Monero 子地址
     */
    suspend fun createMoneroSubaddress(
        accountIndex: Int = 0,
        label: String? = null
    ): Result<AddressInfo> {
        return moneroManager.createSubaddress(moneroWalletId, accountIndex, label)
    }
    
    /**
     * 創建 Monero 交易
     */
    suspend fun createMoneroTransaction(
        toAddress: String,
        amount: Double
    ): Result<MoneroTransactionResult> {  // 修正：返回 MoneroTransactionResult 而不是 MoneroTransaction
        return moneroManager.createTransaction(moneroWalletId, toAddress, amount)
    }
    
    /**
     * 獲取 Monero 交易歷史
     */
    fun getMoneroTransactionHistory(limit: Int = 100): Result<List<MoneroTransactionInfo>> {  // 修正：返回 MoneroTransactionInfo 而不是 MoneroTransaction
        return moneroManager.getTransactionHistory(moneroWalletId, limit)
    }
    
    /**
     * 檢查所有鏈的餘額（包括 Monero）
     */
    suspend fun checkAllBalances(): Map<MultiChainType, String> {
        val balances = mutableMapOf<MultiChainType, String>()
        
        derivedAddresses.forEach { (chain, address) ->
            if (address.isNotEmpty()) {
                val sdk = getSDK(chain)
                val balanceResult = sdk?.getAccountBalance(address)
                if (balanceResult is Result.Success) {
                    balances[chain] = "${balanceResult.data.amount} ${balanceResult.data.symbol}"
                }
            }
        }
        
        // 添加 Monero 餘額
        try {
            val moneroBalance = getMoneroCachedBalance()
            if (moneroBalance is Result.Success) {
                balances[MultiChainType.MONERO] = "${moneroBalance.data.totalXmr} XMR"
            }
        } catch (e: Exception) {
            println("無法獲取 Monero 餘額: ${e.message}")
        }
        
        return balances
    }
    
    /**
     * 創建測試交易（極小金額）
     */
    @Deprecated("Diagnostic method disabled in production release source set.")
    suspend fun createTestTransaction(
        chainType: MultiChainType,
        amount: String = "0.0001"
    ): Result<UnsignedTransaction> {
        return Result.Failure(TypedUnsupportedTransactionException("createTestTransaction is disabled in production source set."))
    }
    
    /**
     * 創建代幣測試交易
     */
    @Deprecated("Diagnostic method disabled in production release source set.")
    suspend fun createTokenTestTransaction(
        chainType: MultiChainType,
        tokenType: TokenType,
        amount: String = "0.001"
    ): Result<UnsignedTransaction> {
        return Result.Failure(TypedUnsupportedTransactionException("createTokenTestTransaction is disabled in production source set."))
    }
    
    /**
     * 創建並簽名 UTXO 交易 (Disabled in production release)
     */
    @Deprecated("Diagnostic method disabled in production release source set.")
    suspend fun createAndSignUTXOTransaction(
        chainType: MultiChainType,
        toAddress: String? = null,
        amount: String = "0.0001"
    ): Result<SignedTransaction> {
        return Result.Failure(TypedUnsupportedTransactionException("createAndSignUTXOTransaction is disabled in production source set."))
    }
    
    /**
     * 從助記詞派生私鑰
     * 使用 AddressDerivation 進行真實派生
     */
    private fun derivePrivateKeyForChain(chainType: MultiChainType): String {
        val executionContext = ChainExecutionContextRegistry.resolve(chainType)
        val attestation = attestationProvider.getAttestationSync(executionContext)
        val runtimeContext = RuntimeCapabilityContext(
            platform = platformProvider.currentPlatform,
            buildType = buildTypeProvider.currentBuildType,
            chainContext = executionContext,
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
            throw TypedUnsupportedTransactionException("Private key derivation for $chainType is disabled by CapabilityGate")
        }
        return try {
            val privateKeyBytes = addressDerivation.derivePrivateKey(activeMnemonic, chainType)
            // 將 ByteArray 轉換為十六進制字符串
            privateKeyBytes.joinToString("") { byte -> 
                val value = byte.toInt() and 0xFF
                value.toString(16).padStart(2, '0')
            }
        } catch (e: Exception) {
            println("⚠️ 無法派生 $chainType 私鑰: ${e.message}")
            ""
        }
    }
    
    /**
     * 測試 UTXO 鏈功能 (Disabled in production release)
     */
    @Deprecated("Diagnostic method disabled in production release source set.")
    suspend fun testUTXOChainFunctions(chainType: MultiChainType): Result<Map<String, Any>> {
        return Result.Failure(TypedUnsupportedTransactionException("testUTXOChainFunctions is disabled in production source set."))
    }
    
    /**
     * 清理資源
     */
    fun dispose() {
        // 停止 Monero 後台同步
        moneroManager.stopBackgroundSync(moneroWalletId)
        
        // 釋放 Monero 資源
        moneroManager.dispose()
    }
}

/**
 * 代幣類型
 */
enum class TokenType {
    USDC,
    USDT,
    DAI
}

/**
 * 交易建構器 - 提供更簡單的 API
 */
class TransactionBuilder(
    private val walletManager: WalletManager
) {
    private var chainType: MultiChainType? = null
    private var amount: String = "0.0001"
    private var tokenType: TokenType? = null
    private var toAddress: String? = null
    
    fun chain(chain: MultiChainType) = apply {
        this.chainType = chain
    }
    
    fun amount(value: String) = apply {
        this.amount = value
    }
    
    fun token(type: TokenType) = apply {
        this.tokenType = type
    }
    
    fun to(address: String) = apply {
        this.toAddress = address
    }
    
    suspend fun build(): Result<UnsignedTransaction> {
        val chain = chainType ?: return Result.Failure(
            IllegalStateException("Chain type not specified")
        )
        
        return if (tokenType != null) {
            walletManager.createTokenTestTransaction(chain, tokenType!!, amount)
        } else {
            walletManager.createTestTransaction(chain, amount)
        }
    }
}

/**
 * 安全檢查器 - 確保不會意外花費大量資金
 */
object SafetyChecker {
    private const val MAX_NATIVE_AMOUNT = 0.01 // 最大原生幣金額
    private const val MAX_TOKEN_AMOUNT = 1.0   // 最大代幣金額
    
    fun checkAmount(amount: String, isToken: Boolean = false): Boolean {
        val value = amount.toDoubleOrNull() ?: return false
        if (value <= 0) return false // 拒絕負數和零
        val maxAmount = if (isToken) MAX_TOKEN_AMOUNT else MAX_NATIVE_AMOUNT
        return value <= maxAmount
    }
    
    fun validateTransaction(request: TransactionRequest): Result<Unit> {
        // 檢查金額
        if (!checkAmount(request.amount, request.tokenAddress != null)) {
            return Result.Failure(
                IllegalArgumentException("Amount too large for safety: ${request.amount}")
            )
        }
        
        // 檢查地址
        if (request.fromAddress.isBlank() || request.toAddress.isBlank()) {
            return Result.Failure(
                IllegalArgumentException("Invalid addresses")
            )
        }
        
        return Result.Success(Unit)
    }
}