package com.cbstudio.wearwallet.core.network

import com.cbstudio.wearwallet.core.BuildKonfig
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContext
import com.cbstudio.wearwallet.core.domain.model.context.ChainExecutionContextRegistry
import com.cbstudio.wearwallet.core.domain.model.context.ChainSelection
import com.cbstudio.wearwallet.core.domain.model.context.NetworkType
import com.cbstudio.wearwallet.core.multichain.MultiChainType

/**
 * API 配置管理
 *
 * ✅ 金鑰從 BuildKonfig 讀取（編譯時從 local.properties 注入）
 * ⚠️ 如果金鑰為空，部分功能可能無法使用
 */
object ApiConfig {

    // ==================== Infura Configuration ====================
    val infuraApiKey: String = BuildKonfig.INFURA_API_KEY
    val infuraHoleskyKey: String = BuildKonfig.INFURA_HOLESKY_KEY
    val infuraPolygonKey: String = BuildKonfig.INFURA_POLYGON_KEY

    // ==================== RPC URLs ====================
    // ✅ 優先使用公共 RPC（不需要 API key）
    // 如果 Infura key 有設定，可選擇使用 Infura（更穩定）
    private fun getRpcUrls(): Map<ChainType, String> = mapOf(
        // Mainnet RPCs - 使用公共免費 RPC
        ChainType.ETHEREUM to if (infuraApiKey.isNotEmpty())
            "https://mainnet.infura.io/v3/$infuraApiKey" else
            "https://ethereum-rpc.publicnode.com",
        ChainType.POLYGON to if (infuraPolygonKey.isNotEmpty())
            "https://polygon-mainnet.infura.io/v3/$infuraPolygonKey" else
            "https://polygon-rpc.com",
        ChainType.ARBITRUM to if (infuraApiKey.isNotEmpty())
            "https://arbitrum-mainnet.infura.io/v3/$infuraApiKey" else
            "https://arb1.arbitrum.io/rpc",
        ChainType.OPTIMISM to if (infuraApiKey.isNotEmpty())
            "https://optimism-mainnet.infura.io/v3/$infuraApiKey" else
            "https://mainnet.optimism.io",
        ChainType.BASE to if (infuraApiKey.isNotEmpty())
            "https://base-mainnet.infura.io/v3/$infuraApiKey" else
            "https://mainnet.base.org",
        ChainType.LINEA to if (infuraApiKey.isNotEmpty())
            "https://linea-mainnet.infura.io/v3/$infuraApiKey" else
            "https://linea-rpc.publicnode.com",

        // Public RPCs (no API key needed)
        ChainType.BSC to "https://bsc-dataseed.binance.org/",
        ChainType.AVALANCHE to "https://api.avax.network/ext/bc/C/rpc",
        ChainType.FANTOM to "https://rpc.ftm.tools/",
        ChainType.CRONOS to "https://evm.cronos.org",
        ChainType.ZKSYNC to "https://mainnet.era.zksync.io",
        ChainType.MOONBEAM to "https://rpc.api.moonbeam.network",
        ChainType.GNOSIS to "https://rpc.gnosischain.com",
        ChainType.CELO to "https://forno.celo.org",

        // Testnets - 使用公共 RPC
        ChainType.SEPOLIA to if (infuraApiKey.isNotEmpty())
            "https://sepolia.infura.io/v3/$infuraApiKey" else
            "https://rpc.sepolia.org"
    )

    // ==================== Block Explorer API Keys ====================
    private val explorerApiKeys: Map<ChainType, String> by lazy {
        mapOf(
            ChainType.ETHEREUM to BuildKonfig.ETHERSCAN_API_KEY,
            ChainType.POLYGON to BuildKonfig.POLYGONSCAN_API_KEY,
            ChainType.ARBITRUM to BuildKonfig.ARBISCAN_API_KEY,
            ChainType.BASE to BuildKonfig.BASESCAN_API_KEY,
            ChainType.OPTIMISM to BuildKonfig.OPTIMISM_API_KEY,
            ChainType.BSC to BuildKonfig.BSCSCAN_API_KEY
        )
    }

    // Block Explorer API URLs
    private val explorerApiUrls = mapOf(
        ChainType.ETHEREUM to "https://api.etherscan.io/api",
        ChainType.BSC to "https://api.bscscan.com/api",
        ChainType.POLYGON to "https://api.polygonscan.com/api",
        ChainType.ARBITRUM to "https://api.arbiscan.io/api",
        ChainType.OPTIMISM to "https://api-optimistic.etherscan.io/api",
        ChainType.AVALANCHE to "https://api.snowtrace.io/api",
        ChainType.FANTOM to "https://api.ftmscan.com/api",
        ChainType.CRONOS to "https://api.cronoscan.com/api",
        ChainType.BASE to "https://api.basescan.org/api",
        ChainType.MOONBEAM to "https://api-moonbeam.moonscan.io/api",
        ChainType.GNOSIS to "https://api.gnosisscan.io/api",
        ChainType.CELO to "https://api.celoscan.io/api",
        ChainType.LINEA to "https://api.lineascan.build/api"
    )

    // ==================== Chain IDs ====================
    private val chainIds = mapOf(
        ChainType.ETHEREUM to "1",
        ChainType.BSC to "56",
        ChainType.POLYGON to "137",
        ChainType.ARBITRUM to "42161",
        ChainType.OPTIMISM to "10",
        ChainType.AVALANCHE to "43114",
        ChainType.FANTOM to "250",
        ChainType.CRONOS to "25",
        ChainType.BASE to "8453",
        ChainType.ZKSYNC to "324",
        ChainType.MOONBEAM to "1284",
        ChainType.GNOSIS to "100",
        ChainType.CELO to "42220",
        ChainType.LINEA to "59144",
        ChainType.SEPOLIA to "11155111"
    )

    // ==================== Third-Party Services ====================

    // Rango Exchange
    const val RANGO_BASE_URL = "https://api.rango.exchange"
    val rangoApiKey: String = BuildKonfig.RANGO_API_KEY
    var rangoAffiliateRef: String = "cbstudio-affiliate"
    var rangoAffiliateFeePercent: Double = 0.5

    // 0x Aggregator
    const val ZEROX_BASE_URL = "https://api.0x.org"
    val zeroXApiKey: String = BuildKonfig.ZEROX_API_KEY
    var zeroXFeeRecipient: String = "0x889A5fDa61adA9E99f75A53c323e32430d1C34d8"
    var zeroXFeeBps: Int = 100

    // Moralis
    val moralisApiKey: String = BuildKonfig.MORALIS_API_KEY

    // Tron Network
    val tronApiKey: String = BuildKonfig.TRON_API_KEY

    // GetBlock WebSocket
    val getBlockApiKey: String = BuildKonfig.GETBLOCK_API_KEY

    // Legacy keys (for compatibility)
    val etherscanApiKey: String = BuildKonfig.ETHERSCAN_API_KEY
    var coinGeckoApiKey: String = ""

    // ==================== Canonical Context RPC URLs ====================
    private val canonicalContextRpcUrls: Map<String, String> by lazy {
        mapOf(
            // Ethereum
            "ETHEREUM_MAINNET_1" to if (infuraApiKey.isNotEmpty()) "https://mainnet.infura.io/v3/$infuraApiKey" else "https://ethereum-rpc.publicnode.com",
            "ETHEREUM_TESTNET_11155111" to if (infuraApiKey.isNotEmpty()) "https://sepolia.infura.io/v3/$infuraApiKey" else "https://rpc.sepolia.org",
            "ETHEREUM_TESTNET_5" to "https://rpc.ankr.com/eth_goerli",

            // BSC
            "BSC_MAINNET_56" to "https://bsc-dataseed.binance.org/",
            "BSC_TESTNET_97" to "https://data-seed-prebsc-1-s1.binance.org:8545/",

            // Polygon
            "POLYGON_MAINNET_137" to if (infuraPolygonKey.isNotEmpty()) "https://polygon-mainnet.infura.io/v3/$infuraPolygonKey" else "https://polygon-rpc.com",
            "POLYGON_TESTNET_80002" to "https://rpc-amoy.polygon.technology",
            "POLYGON_TESTNET_80001" to "https://rpc-mumbai.maticvigil.com",

            // Arbitrum
            "ARBITRUM_MAINNET_42161" to if (infuraApiKey.isNotEmpty()) "https://arbitrum-mainnet.infura.io/v3/$infuraApiKey" else "https://arb1.arbitrum.io/rpc",
            "ARBITRUM_TESTNET_421614" to "https://sepolia-rollup.arbitrum.io/rpc",

            // Optimism
            "OPTIMISM_MAINNET_10" to if (infuraApiKey.isNotEmpty()) "https://optimism-mainnet.infura.io/v3/$infuraApiKey" else "https://mainnet.optimism.io",
            "OPTIMISM_TESTNET_11155420" to "https://sepolia.optimism.io",

            // Base
            "BASE_MAINNET_8453" to if (infuraApiKey.isNotEmpty()) "https://base-mainnet.infura.io/v3/$infuraApiKey" else "https://mainnet.base.org",
            "BASE_TESTNET_84532" to "https://sepolia.base.org",

            // Avalanche
            "AVALANCHE_MAINNET_43114" to "https://api.avax.network/ext/bc/C/rpc",
            "AVALANCHE_TESTNET_43113" to "https://api.avax-test.network/ext/bc/C/rpc"
        )
    }

    // ==================== Helper Functions ====================

    fun getRpcUrl(context: ChainExecutionContext): String {
        val key = "${context.multiChainType.name}_${context.networkType.name}_${context.chainId}"
        return canonicalContextRpcUrls[key] ?: getRpcUrls()[context.chain] ?: "https://ethereum-rpc.publicnode.com"
    }

    fun getRpcUrl(multiChainType: MultiChainType, networkType: NetworkType): String {
        val context = ChainExecutionContextRegistry.resolve(multiChainType, networkType)
        return getRpcUrl(context)
    }

    fun getRpcUrl(selection: ChainSelection): String {
        return getRpcUrl(selection.toChainExecutionContext())
    }

    fun getRpcUrl(chainType: ChainType): String {
        return getRpcUrls()[chainType] ?: getRpcUrls()[ChainType.ETHEREUM]!!
    }

    fun getExplorerApiUrl(chainType: ChainType): String {
        return explorerApiUrls[chainType] ?: explorerApiUrls[ChainType.ETHEREUM]!!
    }

    fun getChainId(chainType: ChainType): String {
        return chainIds[chainType] ?: "1"
    }

    fun getExplorerApiKey(chainType: ChainType): String {
        return explorerApiKeys[chainType] ?: etherscanApiKey
    }

    // ==================== Validation ====================

    /**
     * 檢查必要的 API 金鑰是否已配置
     * @return 缺少的金鑰名稱列表
     */
    fun validateRequiredKeys(): List<String> {
        val missing = mutableListOf<String>()
        if (infuraApiKey.isEmpty()) missing.add("INFURA_API_KEY")
        if (etherscanApiKey.isEmpty()) missing.add("ETHERSCAN_API_KEY")
        return missing
    }

    /**
     * 檢查所有金鑰是否已配置
     * @return 缺少的金鑰名稱列表
     */
    fun validateAllKeys(): List<String> {
        val missing = mutableListOf<String>()
        if (infuraApiKey.isEmpty()) missing.add("INFURA_API_KEY")
        if (rangoApiKey.isEmpty()) missing.add("RANGO_API_KEY")
        if (zeroXApiKey.isEmpty()) missing.add("ZEROX_API_KEY")
        if (moralisApiKey.isEmpty()) missing.add("MORALIS_API_KEY")
        if (tronApiKey.isEmpty()) missing.add("TRON_API_KEY")
        if (getBlockApiKey.isEmpty()) missing.add("GETBLOCK_API_KEY")
        return missing
    }
}
