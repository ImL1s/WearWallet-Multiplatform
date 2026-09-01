package com.cbstudio.wearwallet.core.domain.model.context

import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.usecase.transaction.TypedUnsupportedTransactionException
import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.security.Network as CapabilityNetwork
import kotlinx.serialization.Serializable

@Serializable
enum class NetworkType {
    MAINNET,
    TESTNET
}

@Serializable
data class ChainExecutionContext internal constructor(
    val chain: ChainType,
    val multiChainType: MultiChainType,
    val networkType: NetworkType,
    val chainId: Long,
    val rpcBackendIdentity: String,
    val capabilityNetwork: CapabilityNetwork
) {
    init {
        require(chainId > 0L) { "chainId must be positive: $chainId" }
        require(rpcBackendIdentity.isNotBlank()) { "rpcBackendIdentity must not be blank" }
    }
}

object ChainExecutionContextRegistry {

    private val canonicalList = listOf(
        // 1. Ethereum Mainnet
        ChainExecutionContext(
            chain = ChainType.ETHEREUM,
            multiChainType = MultiChainType.ETHEREUM,
            networkType = NetworkType.MAINNET,
            chainId = 1L,
            rpcBackendIdentity = "ethereum-mainnet-rpc",
            capabilityNetwork = CapabilityNetwork.MAINNET
        ),
        // 2. Ethereum Sepolia
        ChainExecutionContext(
            chain = ChainType.SEPOLIA,
            multiChainType = MultiChainType.ETHEREUM,
            networkType = NetworkType.TESTNET,
            chainId = 11155111L,
            rpcBackendIdentity = "ethereum-sepolia-rpc",
            capabilityNetwork = CapabilityNetwork.TESTNET
        ),
        // 3. Ethereum Goerli
        ChainExecutionContext(
            chain = ChainType.GOERLI,
            multiChainType = MultiChainType.ETHEREUM,
            networkType = NetworkType.TESTNET,
            chainId = 5L,
            rpcBackendIdentity = "ethereum-goerli-rpc",
            capabilityNetwork = CapabilityNetwork.TESTNET
        ),
        // 4. BSC Mainnet
        ChainExecutionContext(
            chain = ChainType.BSC,
            multiChainType = MultiChainType.BSC,
            networkType = NetworkType.MAINNET,
            chainId = 56L,
            rpcBackendIdentity = "bsc-mainnet-rpc",
            capabilityNetwork = CapabilityNetwork.MAINNET
        ),
        // 5. BSC Testnet
        ChainExecutionContext(
            chain = ChainType.BSC,
            multiChainType = MultiChainType.BSC,
            networkType = NetworkType.TESTNET,
            chainId = 97L,
            rpcBackendIdentity = "bsc-testnet-rpc",
            capabilityNetwork = CapabilityNetwork.TESTNET
        ),
        // 6. Polygon Mainnet
        ChainExecutionContext(
            chain = ChainType.POLYGON,
            multiChainType = MultiChainType.POLYGON,
            networkType = NetworkType.MAINNET,
            chainId = 137L,
            rpcBackendIdentity = "polygon-mainnet-rpc",
            capabilityNetwork = CapabilityNetwork.MAINNET
        ),
        // 7. Polygon Amoy
        ChainExecutionContext(
            chain = ChainType.POLYGON,
            multiChainType = MultiChainType.POLYGON,
            networkType = NetworkType.TESTNET,
            chainId = 80002L,
            rpcBackendIdentity = "polygon-amoy-rpc",
            capabilityNetwork = CapabilityNetwork.TESTNET
        ),
        // 8. Polygon Mumbai (legacy)
        ChainExecutionContext(
            chain = ChainType.MUMBAI,
            multiChainType = MultiChainType.POLYGON,
            networkType = NetworkType.TESTNET,
            chainId = 80001L,
            rpcBackendIdentity = "polygon-mumbai-rpc",
            capabilityNetwork = CapabilityNetwork.TESTNET
        ),
        // 9. Arbitrum Mainnet
        ChainExecutionContext(
            chain = ChainType.ARBITRUM,
            multiChainType = MultiChainType.ARBITRUM,
            networkType = NetworkType.MAINNET,
            chainId = 42161L,
            rpcBackendIdentity = "arbitrum-mainnet-rpc",
            capabilityNetwork = CapabilityNetwork.MAINNET
        ),
        // 10. Arbitrum Sepolia
        ChainExecutionContext(
            chain = ChainType.ARBITRUM,
            multiChainType = MultiChainType.ARBITRUM,
            networkType = NetworkType.TESTNET,
            chainId = 421614L,
            rpcBackendIdentity = "arbitrum-sepolia-rpc",
            capabilityNetwork = CapabilityNetwork.TESTNET
        ),
        // 11. Optimism Mainnet
        ChainExecutionContext(
            chain = ChainType.OPTIMISM,
            multiChainType = MultiChainType.OPTIMISM,
            networkType = NetworkType.MAINNET,
            chainId = 10L,
            rpcBackendIdentity = "optimism-mainnet-rpc",
            capabilityNetwork = CapabilityNetwork.MAINNET
        ),
        // 12. Optimism Sepolia
        ChainExecutionContext(
            chain = ChainType.OPTIMISM,
            multiChainType = MultiChainType.OPTIMISM,
            networkType = NetworkType.TESTNET,
            chainId = 11155420L,
            rpcBackendIdentity = "optimism-sepolia-rpc",
            capabilityNetwork = CapabilityNetwork.TESTNET
        ),
        // 13. Base Mainnet
        ChainExecutionContext(
            chain = ChainType.BASE,
            multiChainType = MultiChainType.BASE,
            networkType = NetworkType.MAINNET,
            chainId = 8453L,
            rpcBackendIdentity = "base-mainnet-rpc",
            capabilityNetwork = CapabilityNetwork.MAINNET
        ),
        // 14. Base Sepolia
        ChainExecutionContext(
            chain = ChainType.BASE,
            multiChainType = MultiChainType.BASE,
            networkType = NetworkType.TESTNET,
            chainId = 84532L,
            rpcBackendIdentity = "base-sepolia-rpc",
            capabilityNetwork = CapabilityNetwork.TESTNET
        ),
        // 15. Avalanche Mainnet
        ChainExecutionContext(
            chain = ChainType.AVALANCHE,
            multiChainType = MultiChainType.AVALANCHE,
            networkType = NetworkType.MAINNET,
            chainId = 43114L,
            rpcBackendIdentity = "avalanche-mainnet-rpc",
            capabilityNetwork = CapabilityNetwork.MAINNET
        ),
        // 16. Avalanche Fuji
        ChainExecutionContext(
            chain = ChainType.AVALANCHE,
            multiChainType = MultiChainType.AVALANCHE,
            networkType = NetworkType.TESTNET,
            chainId = 43113L,
            rpcBackendIdentity = "avalanche-fuji-rpc",
            capabilityNetwork = CapabilityNetwork.TESTNET
        )
    )

    val allCanonicalContexts: List<ChainExecutionContext> get() = canonicalList
    fun getCanonicalContexts(): List<ChainExecutionContext> = canonicalList

    fun resolve(selection: ChainSelection): ChainExecutionContext {
        return resolveByChainId(selection.chainId)
            ?: resolve(selection.multiChainType, selection.networkType)
    }

    fun resolve(multiChainType: MultiChainType, isTestnet: Boolean = false): ChainExecutionContext {
        return resolve(multiChainType, if (isTestnet) NetworkType.TESTNET else NetworkType.MAINNET)
    }

    fun resolve(multiChainType: MultiChainType, networkType: NetworkType): ChainExecutionContext {
        return canonicalList.find { it.multiChainType == multiChainType && it.networkType == networkType }
            ?: throw TypedUnsupportedTransactionException("Unsupported chain and network combination: multiChainType=$multiChainType, networkType=$networkType")
    }

    fun resolve(chainType: ChainType, networkType: NetworkType = if (chainType.isTestnet()) NetworkType.TESTNET else NetworkType.MAINNET): ChainExecutionContext {
        return canonicalList.find { it.chain == chainType && it.networkType == networkType }
            ?: throw TypedUnsupportedTransactionException("Unsupported chainType: $chainType with networkType=$networkType")
    }

    fun resolveByChainId(chainId: Long): ChainExecutionContext? {
        return canonicalList.find { it.chainId == chainId }
    }

    fun isSupported(multiChainType: MultiChainType, networkType: NetworkType): Boolean {
        return canonicalList.any { it.multiChainType == multiChainType && it.networkType == networkType }
    }

    fun isSupportedChainId(chainId: Long): Boolean {
        return canonicalList.any { it.chainId == chainId }
    }
}
