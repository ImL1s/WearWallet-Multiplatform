package com.cbstudio.wearwallet.core.multichain.config

import com.cbstudio.wearwallet.core.multichain.MultiChainType

/**
 * 默認 RPC 配置
 *
 * 提供所有支援區塊鏈的默認 RPC URL
 * 使用公共 RPC 節點，建議在生產環境替換為專用節點
 */
object DefaultRPCConfig {

    // ============ Ethereum 和 EVM 兼容鏈 ============

    /** Ethereum Mainnet */
    const val ETHEREUM_MAINNET = "https://eth-mainnet.g.alchemy.com/v2/demo"

    /** Ethereum Sepolia Testnet */
    const val ETHEREUM_SEPOLIA = "https://eth-sepolia.g.alchemy.com/v2/demo"

    /** Ethereum Goerli Testnet */
    const val ETHEREUM_GOERLI = "https://eth-goerli.g.alchemy.com/v2/demo"

    /** Binance Smart Chain Mainnet */
    const val BSC_MAINNET = "https://bsc-dataseed1.binance.org"

    /** BSC Testnet */
    const val BSC_TESTNET = "https://data-seed-prebsc-1-s1.binance.org:8545"

    /** Polygon Mainnet */
    const val POLYGON_MAINNET = "https://polygon-rpc.com"

    /** Polygon Amoy Testnet */
    const val POLYGON_AMOY = "https://rpc-amoy.polygon.technology"

    /** Polygon Mumbai Testnet (legacy) */
    const val POLYGON_MUMBAI = "https://rpc-mumbai.maticvigil.com"

    /** Arbitrum Mainnet */
    const val ARBITRUM_MAINNET = "https://arb1.arbitrum.io/rpc"

    /** Arbitrum Sepolia Testnet */
    const val ARBITRUM_SEPOLIA = "https://sepolia-rollup.arbitrum.io/rpc"

    /** Arbitrum Goerli Testnet (legacy) */
    const val ARBITRUM_GOERLI = "https://goerli-rollup.arbitrum.io/rpc"

    /** Optimism Mainnet */
    const val OPTIMISM_MAINNET = "https://mainnet.optimism.io"

    /** Optimism Sepolia Testnet */
    const val OPTIMISM_SEPOLIA = "https://sepolia.optimism.io"

    /** Optimism Goerli Testnet (legacy) */
    const val OPTIMISM_GOERLI = "https://goerli.optimism.io"

    /** Avalanche C-Chain Mainnet */
    const val AVALANCHE_MAINNET = "https://api.avax.network/ext/bc/C/rpc"

    /** Avalanche Fuji Testnet */
    const val AVALANCHE_FUJI = "https://api.avax-test.network/ext/bc/C/rpc"

    /** Fantom Opera Mainnet */
    const val FANTOM_MAINNET = "https://rpc.ftm.tools"

    /** Fantom Testnet */
    const val FANTOM_TESTNET = "https://rpc.testnet.fantom.network"

    /** Cronos Mainnet */
    const val CRONOS_MAINNET = "https://evm.cronos.org"

    /** Cronos Testnet */
    const val CRONOS_TESTNET = "https://evm-t3.cronos.org"

    /** Base Mainnet */
    const val BASE_MAINNET = "https://mainnet.base.org"

    /** Base Sepolia Testnet */
    const val BASE_SEPOLIA = "https://sepolia.base.org"

    /** Base Goerli Testnet (legacy) */
    const val BASE_GOERLI = "https://goerli.base.org"

    /** Celo Mainnet */
    const val CELO_MAINNET = "https://forno.celo.org"

    /** Celo Alfajores Testnet */
    const val CELO_ALFAJORES = "https://alfajores-forno.celo-testnet.org"

    /** Moonbeam Mainnet */
    const val MOONBEAM_MAINNET = "https://rpc.api.moonbeam.network"

    /** Moonbeam Moonbase Alpha Testnet */
    const val MOONBEAM_MOONBASE = "https://rpc.api.moonbase.moonbeam.network"

    // ============ 非 EVM 鏈 ============

    /** Solana Mainnet Beta */
    const val SOLANA_MAINNET = "https://api.mainnet-beta.solana.com"

    /** Solana Devnet */
    const val SOLANA_DEVNET = "https://api.devnet.solana.com"

    /** Solana Testnet */
    const val SOLANA_TESTNET = "https://api.testnet.solana.com"

    /** TRON Mainnet */
    const val TRON_MAINNET = "https://api.trongrid.io"

    /** TRON Shasta Testnet */
    const val TRON_SHASTA = "https://api.shasta.trongrid.io"

    /** TRON Nile Testnet */
    const val TRON_NILE = "https://nile.trongrid.io"

    /** Cardano Mainnet (Blockfrost) */
    const val CARDANO_MAINNET = "https://cardano-mainnet.blockfrost.io/api/v0"

    /** Cardano Preprod Testnet */
    const val CARDANO_PREPROD = "https://cardano-preprod.blockfrost.io/api/v0"

    /** Cardano Preview Testnet */
    const val CARDANO_PREVIEW = "https://cardano-preview.blockfrost.io/api/v0"

    /** Polkadot Mainnet (WebSocket) */
    const val POLKADOT_MAINNET = "wss://rpc.polkadot.io"

    /** Polkadot Westend Testnet */
    const val POLKADOT_WESTEND = "wss://westend-rpc.polkadot.io"

    /** Polkadot Kusama */
    const val POLKADOT_KUSAMA = "wss://kusama-rpc.polkadot.io"

    /** Monero Mainnet */
    const val MONERO_MAINNET = "https://xmr-node.cakewallet.com:18081"

    /** Monero Stagenet */
    const val MONERO_STAGENET = "https://stagenet.xmr-node.cakewallet.com:38081"

    /** Monero Testnet */
    const val MONERO_TESTNET = "https://testnet.xmr-node.cakewallet.com:28081"

    // ============ UTXO 鏈 (通常不需要 RPC URL，但提供區塊瀏覽器 API) ============

    /** Bitcoin Mainnet (Blockstream API) */
    const val BITCOIN_MAINNET = "https://blockstream.info/api"

    /** Bitcoin Testnet */
    const val BITCOIN_TESTNET = "https://blockstream.info/testnet/api"

    /** Litecoin Mainnet */
    const val LITECOIN_MAINNET = "https://api.blockcypher.com/v1/ltc/main"

    /** Litecoin Testnet */
    const val LITECOIN_TESTNET = "https://api.blockcypher.com/v1/ltc/test3"

    /** Dogecoin Mainnet */
    const val DOGECOIN_MAINNET = "https://api.blockcypher.com/v1/doge/main"

    /** Dogecoin Testnet */
    const val DOGECOIN_TESTNET = "https://api.blockcypher.com/v1/doge/test3"

    /** Bitcoin Cash Mainnet */
    const val BITCOIN_CASH_MAINNET = "https://api.fullstack.cash/v5"

    /** Bitcoin Cash Testnet */
    const val BITCOIN_CASH_TESTNET = "https://api.fullstack.cash/v5"

    // ============ 輔助方法 ============

    /**
     * 根據鏈類型和網路獲取默認 RPC URL
     *
     * @param chainType 區塊鏈類型
     * @param network 網路類型 (mainnet, testnet, devnet, stagenet 等)
     * @return 默認 RPC URL
     */
    fun getDefaultRpcUrl(chainType: MultiChainType, network: String = "mainnet"): String {
        val normalizedNetwork = network.lowercase().trim()

        return when (chainType) {
            // EVM 兼容鏈
            MultiChainType.ETHEREUM -> when (normalizedNetwork) {
                "sepolia" -> ETHEREUM_SEPOLIA
                "goerli" -> ETHEREUM_GOERLI
                "testnet" -> ETHEREUM_SEPOLIA
                else -> ETHEREUM_MAINNET
            }

            MultiChainType.BSC -> when (normalizedNetwork) {
                "testnet" -> BSC_TESTNET
                else -> BSC_MAINNET
            }

            MultiChainType.POLYGON -> when (normalizedNetwork) {
                "amoy" -> POLYGON_AMOY
                "mumbai" -> POLYGON_MUMBAI
                "testnet" -> POLYGON_AMOY
                else -> POLYGON_MAINNET
            }

            MultiChainType.ARBITRUM -> when (normalizedNetwork) {
                "sepolia", "testnet" -> ARBITRUM_SEPOLIA
                "goerli" -> ARBITRUM_GOERLI
                else -> ARBITRUM_MAINNET
            }

            MultiChainType.OPTIMISM -> when (normalizedNetwork) {
                "sepolia", "testnet" -> OPTIMISM_SEPOLIA
                "goerli" -> OPTIMISM_GOERLI
                else -> OPTIMISM_MAINNET
            }

            MultiChainType.AVALANCHE -> when (normalizedNetwork) {
                "fuji", "testnet" -> AVALANCHE_FUJI
                else -> AVALANCHE_MAINNET
            }

            MultiChainType.FANTOM -> when (normalizedNetwork) {
                "testnet" -> FANTOM_TESTNET
                else -> FANTOM_MAINNET
            }

            MultiChainType.CRONOS -> when (normalizedNetwork) {
                "testnet" -> CRONOS_TESTNET
                else -> CRONOS_MAINNET
            }

            MultiChainType.BASE -> when (normalizedNetwork) {
                "sepolia", "testnet" -> BASE_SEPOLIA
                "goerli" -> BASE_GOERLI
                else -> BASE_MAINNET
            }

            MultiChainType.CELO -> when (normalizedNetwork) {
                "alfajores", "testnet" -> CELO_ALFAJORES
                else -> CELO_MAINNET
            }

            MultiChainType.MOONBEAM -> when (normalizedNetwork) {
                "moonbase", "testnet" -> MOONBEAM_MOONBASE
                else -> MOONBEAM_MAINNET
            }

            // 非 EVM 鏈
            MultiChainType.SOLANA -> when (normalizedNetwork) {
                "devnet" -> SOLANA_DEVNET
                "testnet" -> SOLANA_TESTNET
                else -> SOLANA_MAINNET
            }

            MultiChainType.TRON -> when (normalizedNetwork) {
                "shasta", "testnet" -> TRON_SHASTA
                "nile" -> TRON_NILE
                else -> TRON_MAINNET
            }

            MultiChainType.CARDANO -> when (normalizedNetwork) {
                "preprod", "testnet" -> CARDANO_PREPROD
                "preview" -> CARDANO_PREVIEW
                else -> CARDANO_MAINNET
            }

            MultiChainType.POLKADOT -> when (normalizedNetwork) {
                "westend", "testnet" -> POLKADOT_WESTEND
                "kusama" -> POLKADOT_KUSAMA
                else -> POLKADOT_MAINNET
            }

            MultiChainType.MONERO -> when (normalizedNetwork) {
                "stagenet" -> MONERO_STAGENET
                "testnet" -> MONERO_TESTNET
                else -> MONERO_MAINNET
            }

            // UTXO 鏈
            MultiChainType.BITCOIN -> when (normalizedNetwork) {
                "testnet" -> BITCOIN_TESTNET
                else -> BITCOIN_MAINNET
            }

            MultiChainType.LITECOIN -> when (normalizedNetwork) {
                "testnet" -> LITECOIN_TESTNET
                else -> LITECOIN_MAINNET
            }

            MultiChainType.DOGECOIN -> when (normalizedNetwork) {
                "testnet" -> DOGECOIN_TESTNET
                else -> DOGECOIN_MAINNET
            }

            MultiChainType.BITCOIN_CASH -> when (normalizedNetwork) {
                "testnet" -> BITCOIN_CASH_TESTNET
                else -> BITCOIN_CASH_MAINNET
            }

            // 不支援或未配置的鏈
            else -> ""
        }
    }

    /**
     * 獲取所有支援的網路類型
     *
     * @param chainType 區塊鏈類型
     * @return 支援的網路類型列表
     */
    fun getSupportedNetworks(chainType: MultiChainType): List<String> {
        return when (chainType) {
            MultiChainType.ETHEREUM -> listOf("mainnet", "sepolia", "goerli")
            MultiChainType.SOLANA -> listOf("mainnet", "devnet", "testnet")
            MultiChainType.TRON -> listOf("mainnet", "shasta", "nile")
            MultiChainType.CARDANO -> listOf("mainnet", "preprod", "preview")
            MultiChainType.POLKADOT -> listOf("mainnet", "westend", "kusama")
            MultiChainType.MONERO -> listOf("mainnet", "stagenet", "testnet")

            // EVM 鏈通常有 mainnet 和 testnet
            MultiChainType.BSC,
            MultiChainType.POLYGON,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.AVALANCHE,
            MultiChainType.FANTOM,
            MultiChainType.CRONOS,
            MultiChainType.BASE,
            MultiChainType.CELO,
            MultiChainType.MOONBEAM -> listOf("mainnet", "testnet")

            // UTXO 鏈
            MultiChainType.BITCOIN,
            MultiChainType.LITECOIN,
            MultiChainType.DOGECOIN,
            MultiChainType.BITCOIN_CASH -> listOf("mainnet", "testnet")

            else -> listOf("mainnet")
        }
    }

    /**
     * 檢查給定的網路是否為測試網路
     */
    fun isTestnet(network: String): Boolean {
        val normalizedNetwork = network.lowercase().trim()
        return normalizedNetwork in listOf(
            "testnet", "devnet", "stagenet",
            "sepolia", "goerli", "amoy", "mumbai", "fuji",
            "shasta", "nile", "westend", "preprod", "preview",
            "alfajores", "moonbase"
        )
    }
}
