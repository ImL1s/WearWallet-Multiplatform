package com.cbstudio.wearwallet.core.domain.model.context

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import kotlinx.serialization.Serializable

/**
 * Immutable domain representation of a user-selected blockchain network.
 *
 * Guarantees explicit typed differentiation between Mainnet and Testnet for all
 * supported multi-chain ecosystems (e.g. BSC 56 vs BSC 97, Arbitrum 42161 vs 421614).
 *
 * Invariants:
 * - [chainId] must be strictly positive (> 0).
 * - [canonicalContextId] must be non-blank and match canonical naming.
 */
@Serializable
data class ChainSelection(
    val multiChainType: MultiChainType,
    val networkType: NetworkType,
    val chainId: Long,
    val canonicalContextId: String
) {
    init {
        require(chainId > 0L) { "chainId must be positive: $chainId" }
        require(canonicalContextId.isNotBlank()) { "canonicalContextId must not be blank" }
    }

    /**
     * Resolves the canonical [ChainExecutionContext] associated with this selection.
     * Fails closed if the selection is not registered in [ChainExecutionContextRegistry].
     */
    fun toChainExecutionContext(): ChainExecutionContext {
        return ChainExecutionContextRegistry.resolveByChainId(chainId)
            ?: ChainExecutionContextRegistry.resolve(multiChainType, networkType)
    }

    /**
     * Helper alias for [toChainExecutionContext].
     */
    fun toExecutionContext(): ChainExecutionContext = toChainExecutionContext()

    /**
     * Whether this selection targets a testnet network.
     */
    fun isTestnet(): Boolean = networkType == NetworkType.TESTNET

    /**
     * Whether this selection targets an EVM-compatible execution environment.
     */
    fun isEvm(): Boolean = toChainExecutionContext().chain.isEVM()

    /**
     * Human-readable display name for UI presentation.
     */
    fun displayName(): String {
        return when (multiChainType) {
            MultiChainType.ETHEREUM -> when (networkType) {
                NetworkType.MAINNET -> "Ethereum"
                NetworkType.TESTNET -> if (chainId == 11155111L) "Sepolia" else if (chainId == 5L) "Goerli" else "Ethereum Testnet"
            }
            MultiChainType.BSC -> when (networkType) {
                NetworkType.MAINNET -> "BNB Smart Chain"
                NetworkType.TESTNET -> "BSC Testnet"
            }
            MultiChainType.POLYGON -> when (networkType) {
                NetworkType.MAINNET -> "Polygon"
                NetworkType.TESTNET -> if (chainId == 80002L) "Polygon Amoy" else if (chainId == 80001L) "Polygon Mumbai" else "Polygon Testnet"
            }
            MultiChainType.ARBITRUM -> when (networkType) {
                NetworkType.MAINNET -> "Arbitrum"
                NetworkType.TESTNET -> "Arbitrum Sepolia"
            }
            MultiChainType.OPTIMISM -> when (networkType) {
                NetworkType.MAINNET -> "Optimism"
                NetworkType.TESTNET -> "Optimism Sepolia"
            }
            MultiChainType.BASE -> when (networkType) {
                NetworkType.MAINNET -> "Base"
                NetworkType.TESTNET -> "Base Sepolia"
            }
            MultiChainType.AVALANCHE -> when (networkType) {
                NetworkType.MAINNET -> "Avalanche"
                NetworkType.TESTNET -> "Avalanche Fuji"
            }
            else -> if (isTestnet()) "${multiChainType.fullName} (Testnet)" else multiChainType.fullName
        }
    }

    /**
     * Native token symbol (e.g. "ETH", "BNB", "MATIC", "AVAX").
     */
    fun nativeSymbol(): String = when (multiChainType) {
        MultiChainType.POLYGON -> "POL"
        else -> toChainExecutionContext().chain.nativeToken
    }

    /**
     * Helper alias for [nativeSymbol].
     */
    fun symbol(): String = nativeSymbol()

    companion object {
        val ETHEREUM_MAINNET = ChainSelection(MultiChainType.ETHEREUM, NetworkType.MAINNET, 1L, "ETHEREUM_MAINNET_1")
        val ETHEREUM_SEPOLIA = ChainSelection(MultiChainType.ETHEREUM, NetworkType.TESTNET, 11155111L, "ETHEREUM_TESTNET_11155111")
        val ETHEREUM_GOERLI = ChainSelection(MultiChainType.ETHEREUM, NetworkType.TESTNET, 5L, "ETHEREUM_TESTNET_5")

        val BSC_MAINNET = ChainSelection(MultiChainType.BSC, NetworkType.MAINNET, 56L, "BSC_MAINNET_56")
        val BSC_TESTNET = ChainSelection(MultiChainType.BSC, NetworkType.TESTNET, 97L, "BSC_TESTNET_97")

        val POLYGON_MAINNET = ChainSelection(MultiChainType.POLYGON, NetworkType.MAINNET, 137L, "POLYGON_MAINNET_137")
        val POLYGON_AMOY = ChainSelection(MultiChainType.POLYGON, NetworkType.TESTNET, 80002L, "POLYGON_TESTNET_80002")
        val POLYGON_MUMBAI = ChainSelection(MultiChainType.POLYGON, NetworkType.TESTNET, 80001L, "POLYGON_TESTNET_80001")

        val ARBITRUM_MAINNET = ChainSelection(MultiChainType.ARBITRUM, NetworkType.MAINNET, 42161L, "ARBITRUM_MAINNET_42161")
        val ARBITRUM_SEPOLIA = ChainSelection(MultiChainType.ARBITRUM, NetworkType.TESTNET, 421614L, "ARBITRUM_TESTNET_421614")

        val OPTIMISM_MAINNET = ChainSelection(MultiChainType.OPTIMISM, NetworkType.MAINNET, 10L, "OPTIMISM_MAINNET_10")
        val OPTIMISM_SEPOLIA = ChainSelection(MultiChainType.OPTIMISM, NetworkType.TESTNET, 11155420L, "OPTIMISM_TESTNET_11155420")

        val BASE_MAINNET = ChainSelection(MultiChainType.BASE, NetworkType.MAINNET, 8453L, "BASE_MAINNET_8453")
        val BASE_SEPOLIA = ChainSelection(MultiChainType.BASE, NetworkType.TESTNET, 84532L, "BASE_TESTNET_84532")

        val AVALANCHE_MAINNET = ChainSelection(MultiChainType.AVALANCHE, NetworkType.MAINNET, 43114L, "AVALANCHE_MAINNET_43114")
        val AVALANCHE_FUJI = ChainSelection(MultiChainType.AVALANCHE, NetworkType.TESTNET, 43113L, "AVALANCHE_TESTNET_43113")

        /**
         * Creates a [ChainSelection] directly from a canonical [ChainExecutionContext].
         */
        fun from(context: ChainExecutionContext): ChainSelection = fromExecutionContext(context)

        /**
         * Creates a [ChainSelection] directly from a canonical [ChainExecutionContext].
         */
        fun fromExecutionContext(context: ChainExecutionContext): ChainSelection {
            return ChainSelection(
                multiChainType = context.multiChainType,
                networkType = context.networkType,
                chainId = context.chainId,
                canonicalContextId = "${context.multiChainType.name}_${context.networkType.name}_${context.chainId}"
            )
        }

        /**
         * Creates a [ChainSelection] from a [MultiChainType] and [NetworkType].
         */
        fun fromMultiChain(multiChainType: MultiChainType, networkType: NetworkType = NetworkType.MAINNET): ChainSelection {
            val context = ChainExecutionContextRegistry.resolve(multiChainType, networkType)
            return fromExecutionContext(context)
        }

        /**
         * Creates a [ChainSelection] from a legacy [ChainType].
         */
        fun fromChainType(chainType: ChainType, networkType: NetworkType? = null): ChainSelection {
            val resolvedNetwork = networkType ?: if (chainType.isTestnet()) NetworkType.TESTNET else NetworkType.MAINNET
            val context = ChainExecutionContextRegistry.resolve(chainType, resolvedNetwork)
            return fromExecutionContext(context)
        }

        /**
         * Looks up a [ChainSelection] by its numeric [chainId]. Returns null if unsupported.
         */
        fun fromChainId(chainId: Long): ChainSelection? {
            val context = ChainExecutionContextRegistry.resolveByChainId(chainId) ?: return null
            return fromExecutionContext(context)
        }

        /**
         * Default system selection: Ethereum Mainnet.
         */
        fun default(): ChainSelection = ETHEREUM_MAINNET

        /**
         * Returns all canonical selections available across the ecosystem (16 standard networks).
         */
        fun allCanonicalSelections(): List<ChainSelection> {
            return ChainExecutionContextRegistry.allCanonicalContexts.map { fromExecutionContext(it) }
        }

        /**
         * Returns all canonical Mainnet selections.
         */
        fun mainnetSelections(): List<ChainSelection> {
            return allCanonicalSelections().filter { !it.isTestnet() }
        }

        /**
         * Returns all canonical Testnet selections.
         */
        fun testnetSelections(): List<ChainSelection> {
            return allCanonicalSelections().filter { it.isTestnet() }
        }
    }
}
