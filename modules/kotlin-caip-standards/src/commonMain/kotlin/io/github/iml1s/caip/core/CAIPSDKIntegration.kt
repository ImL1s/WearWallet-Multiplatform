package io.github.iml1s.caip.core

import io.github.iml1s.caip.model.*

/**
 * CAIP Standard SDK Adapter Interface
 *
 * Defines the contract for blockchain SDK adapters that support CAIP standards.
 * Implementations should provide CAIP-compliant methods for blockchain operations.
 */
interface CAIPBlockchainSDKAdapter {

    /**
     * The chain type this adapter supports
     */
    val chainType: CAIPChainType

    /**
     * SDK version string
     */
    val sdkVersion: String

    /**
     * Supported CAIP namespaces
     */
    val supportedNamespaces: List<String>

    /**
     * Supported asset namespaces
     */
    val supportedAssetNamespaces: List<String>

    /**
     * SDK capabilities
     */
    val capabilities: Set<SDKCapability>

    /**
     * Initialize the SDK
     */
    suspend fun initialize(config: SDKConfig): CAIPResult<Unit>

    /**
     * Check if the SDK is initialized
     */
    fun isInitialized(): Boolean

    /**
     * Get account balance using CAIP address
     */
    suspend fun getAccountBalanceCAIP(caipAddress: CAIPAddress): CAIPResult<CAIPBalance>

    /**
     * Create a transaction using CAIP format
     */
    suspend fun createTransactionCAIP(request: CAIPTransactionRequest): CAIPResult<CAIPUnsignedTransaction>

    /**
     * Validate a CAIP address
     */
    fun validateAddressCAIP(caipAddress: CAIPAddress): CAIPResult<CAIPAddressValidation>

    /**
     * Broadcast a signed CAIP transaction
     */
    suspend fun broadcastTransactionCAIP(signedTransaction: CAIPSignedTransaction): CAIPResult<CAIPTransactionResult>

    /**
     * Get supported chain IDs
     */
    fun getSupportedChainIDs(): List<CAIPChainID>

    /**
     * Get supported assets for a chain
     */
    suspend fun getSupportedAssets(chainId: CAIPChainID): CAIPResult<List<CAIPAsset>>

    /**
     * Clean up resources
     */
    suspend fun cleanup()
}

/**
 * SDK Capability flags
 */
enum class SDKCapability {
    BALANCE_QUERY,
    TRANSACTION_CREATION,
    TRANSACTION_BROADCAST,
    ADDRESS_VALIDATION,
    TRANSACTION_HISTORY,
    NFT_OPERATIONS,
    DEFI_OPERATIONS,
    STAKING,
    GOVERNANCE
}

/**
 * SDK Configuration
 */
data class SDKConfig(
    val rpcUrl: String = "",
    val apiKey: String? = null,
    val timeout: Long = 30000L,
    val retryCount: Int = 3,
    val network: String = "mainnet",
    val options: Map<String, Any> = emptyMap()
)

/**
 * CAIP Balance Information
 */
data class CAIPBalance(
    val asset: CAIPAsset,
    val amount: String,
    val decimals: Int,
    val symbol: String,
    val usdValue: String? = null,
    val lastUpdated: Long = currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
) {
    /**
     * Get formatted display amount
     */
    fun getDisplayAmount(maxDecimals: Int = 8): String {
        val value = amount.toDoubleOrNull() ?: return amount
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            // Format with max decimals using Kotlin stdlib
            val formatted = value.toString()
            val parts = formatted.split('.')
            if (parts.size == 2) {
                val decimals = parts[1].take(maxDecimals).trimEnd('0')
                if (decimals.isEmpty()) parts[0] else "${parts[0]}.$decimals"
            } else {
                formatted
            }
        }
    }
}

/**
 * CAIP Unsigned Transaction
 */
data class CAIPUnsignedTransaction(
    val rawData: String,
    val chainId: CAIPChainID,
    val estimatedFee: CAIPTransactionFee,
    val expirationTime: Long? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * CAIP Signed Transaction
 */
data class CAIPSignedTransaction(
    val rawData: String,
    val signature: String,
    val chainId: CAIPChainID,
    val hash: String? = null
)

/**
 * CAIP Transaction Fee
 */
data class CAIPTransactionFee(
    val gasLimit: String,
    val gasPrice: String,
    val estimatedCost: String,
    val asset: CAIPAsset,
    val usdValue: String? = null,
    val priority: TransactionPriority = TransactionPriority.NORMAL
) {
    /**
     * Calculate total fee in smallest unit
     */
    fun getTotalFee(): String {
        val limit = gasLimit.toDoubleOrNull() ?: return "0"
        val price = gasPrice.toDoubleOrNull() ?: return "0"
        val result = limit * price
        // Return without scientific notation
        return if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            result.toString()
        }
    }
}

/**
 * Transaction Priority
 */
enum class TransactionPriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * CAIP Address Validation Result
 */
data class CAIPAddressValidation(
    val isValid: Boolean,
    val addressType: CAIPAddressType? = null,
    val networkMatches: Boolean = true,
    val message: String? = null,
    val supportedOperations: Set<String> = emptySet()
)

/**
 * SDK Exception types
 */
sealed class SDKException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    class InitializationException(
        val chainType: CAIPChainType,
        message: String,
        cause: Throwable? = null
    ) : SDKException("[$chainType] Initialization failed: $message", cause)

    class NetworkException(
        val chainType: CAIPChainType,
        message: String,
        cause: Throwable? = null
    ) : SDKException("[$chainType] Network error: $message", cause)

    class TransactionException(
        val chainType: CAIPChainType,
        message: String,
        cause: Throwable? = null
    ) : SDKException("[$chainType] Transaction error: $message", cause)

    class ConfigurationException(
        val chainType: CAIPChainType,
        message: String,
        cause: Throwable? = null
    ) : SDKException("[$chainType] Configuration error: $message", cause)

    class ValidationException(
        val chainType: CAIPChainType,
        message: String,
        cause: Throwable? = null
    ) : SDKException("[$chainType] Validation error: $message", cause)
}

/**
 * Abstract base implementation for CAIP SDK adapters
 *
 * Provides common functionality and default implementations
 * that can be overridden by specific chain adapters.
 */
abstract class AbstractCAIPSDKAdapter : CAIPBlockchainSDKAdapter {

    protected val caipService = CAIPService()

    override val supportedNamespaces: List<String>
        get() = listOf(chainType.namespace)

    override val supportedAssetNamespaces: List<String>
        get() = listOf("slip44", "erc20", "erc721", "erc1155")

    /**
     * Get the default network name
     */
    protected open fun getDefaultNetwork(): String = "mainnet"

    /**
     * Map chain type to CAIP namespace
     */
    protected fun chainTypeToNamespace(type: CAIPChainType): String = type.namespace

    override fun getSupportedChainIDs(): List<CAIPChainID> {
        return listOf(CAIPChainID.fromChainType(chainType, getDefaultNetwork()))
    }

    override suspend fun getSupportedAssets(chainId: CAIPChainID): CAIPResult<List<CAIPAsset>> {
        val nativeAsset = CAIPAsset.createNativeAsset(chainType, getDefaultNetwork())
        return CAIPResult.Success(listOf(nativeAsset))
    }

    /**
     * Validate that the address belongs to this adapter's chain
     */
    protected fun validateChainMatch(caipAddress: CAIPAddress): CAIPResult<Unit> {
        return if (caipAddress.chainId.namespace == chainType.namespace) {
            CAIPResult.Success(Unit)
        } else {
            CAIPResult.Failure(
                SDKException.ValidationException(
                    chainType,
                    "Address namespace '${caipAddress.chainId.namespace}' does not match expected '${chainType.namespace}'"
                )
            )
        }
    }
}

/**
 * Extension function to convert CAIPChainID to CAIPChainType
 */
fun CAIPChainID.toChainType(): CAIPChainType? {
    return when (namespace) {
        "eip155" -> CAIPChainType.ETHEREUM // Default to Ethereum for EVM chains
        "solana" -> CAIPChainType.SOLANA
        "bip122" -> CAIPChainType.BITCOIN // Default to Bitcoin for bip122
        "polkadot" -> CAIPChainType.POLKADOT
        "cardano" -> CAIPChainType.CARDANO
        "tron" -> CAIPChainType.TRON
        "monero" -> CAIPChainType.MONERO
        "cosmos" -> null // Not in CAIPChainType enum
        else -> null
    }
}
