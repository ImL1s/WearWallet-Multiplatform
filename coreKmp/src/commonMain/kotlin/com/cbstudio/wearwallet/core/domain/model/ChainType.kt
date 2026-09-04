package com.cbstudio.wearwallet.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ChainType {
    // EVM 兼容鏈 - 主網
    ETHEREUM,
    BSC,
    POLYGON,
    ARBITRUM,
    OPTIMISM,
    AVALANCHE,
    FANTOM,
    CRONOS,
    CRONOSZVM,
    BASE,
    ZKSYNC,
    MOONBEAM,
    GNOSIS,
    CELO,
    LINEA,
    
    // EVM 兼容鏈 - 測試網
    SEPOLIA,
    GOERLI,
    MUMBAI,
    
    // 非 EVM 鏈
    BITCOIN,
    LITECOIN,
    DOGECOIN,
    BITCOIN_CASH,
    SOLANA,
    APTOS,
    SUI,
    COSMOS,
    POLKADOT,
    CARDANO,
    NEAR,
    TRON,
    TEZOS,
    MONERO;
    
    /**
     * 獲取鏈的預設衍生路徑
     */
    fun getDefaultDerivationPath(): String {
        return when (this) {
            // EVM 兼容鏈使用 BIP44 ETH 路徑
            ETHEREUM, BSC, POLYGON, ARBITRUM, OPTIMISM, 
            AVALANCHE, FANTOM, CRONOS, CRONOSZVM, BASE, 
            ZKSYNC, MOONBEAM, GNOSIS, CELO, LINEA,
            SEPOLIA, GOERLI, MUMBAI -> "m/44'/60'/0'/0/0"
            
            // 非 EVM 鏈使用各自的標準路徑
            BITCOIN -> "m/44'/0'/0'/0/0"
            LITECOIN -> "m/44'/2'/0'/0/0"
            DOGECOIN -> "m/44'/3'/0'/0/0"
            BITCOIN_CASH -> "m/44'/145'/0'/0/0"
            SOLANA -> "m/44'/501'/0'/0'"
            APTOS -> "m/44'/637'/0'/0'/0'"
            SUI -> "m/44'/784'/0'/0'/0'"
            COSMOS -> "m/44'/118'/0'/0/0"
            POLKADOT -> "m/44'/354'/0'/0'/0'"
            CARDANO -> "m/1852'/1815'/0'/0/0"
            NEAR -> "m/44'/397'/0'"
            TRON -> "m/44'/195'/0'/0/0"
            TEZOS -> "m/44'/1729'/0'/0'"
            MONERO -> "m/44'/128'/0'/0/0"
        }
    }
    
    /**
     * 是否為 EVM 兼容鏈
     */
    fun isEVM(): Boolean {
        return this in listOf(
            ETHEREUM, BSC, POLYGON, ARBITRUM, OPTIMISM,
            AVALANCHE, FANTOM, CRONOS, CRONOSZVM, BASE,
            ZKSYNC, MOONBEAM, GNOSIS, CELO, LINEA,
            SEPOLIA, GOERLI, MUMBAI
        )
    }
    
    /**
     * 是否為測試網
     */
    fun isTestnet(): Boolean {
        return this in listOf(SEPOLIA, GOERLI, MUMBAI)
    }
    
    /**
     * 獲取鏈的 Chain ID
     */
    fun getChainId(): Long {
        return when (this) {
            ETHEREUM -> 1L
            BSC -> 56L
            POLYGON -> 137L
            ARBITRUM -> 42161L
            OPTIMISM -> 10L
            AVALANCHE -> 43114L
            FANTOM -> 250L
            CRONOS -> 25L
            CRONOSZVM -> 388L
            BASE -> 8453L
            ZKSYNC -> 324L
            MOONBEAM -> 1284L
            GNOSIS -> 100L
            CELO -> 42220L
            LINEA -> 59144L
            SEPOLIA -> 11155111L
            GOERLI -> 5L
            MUMBAI -> 80001L
            // UTXO 鏈沒有 EVM chain ID，使用 BIP44 coin type 作為標識
            BITCOIN -> 0L
            LITECOIN -> 2L
            DOGECOIN -> 3L
            BITCOIN_CASH -> 145L
            // 其他鏈使用各自的標識
            SOLANA -> 501L
            APTOS -> 637L
            SUI -> 784L
            COSMOS -> 118L
            POLKADOT -> 354L
            CARDANO -> 1815L
            NEAR -> 397L
            TRON -> 195L
            TEZOS -> 1729L
            MONERO -> 128L
        }
    }
    
    /**
     * 獲取原生代幣符號
     */
    val nativeToken: String
        get() = when (this) {
            ETHEREUM -> "ETH"
            BSC -> "BNB"
            POLYGON -> "MATIC"
            CRONOS, CRONOSZVM -> "CRO"
            AVALANCHE -> "AVAX"
            ARBITRUM -> "ETH"
            OPTIMISM -> "ETH"
            FANTOM -> "FTM"
            BASE -> "ETH"
            ZKSYNC -> "ETH"
            MOONBEAM -> "GLMR"
            GNOSIS -> "xDAI"
            CELO -> "CELO"
            LINEA -> "ETH"
            SEPOLIA -> "SepoliaETH"
            GOERLI -> "GoerliETH"
            MUMBAI -> "testMATIC"
            BITCOIN -> "BTC"
            LITECOIN -> "LTC"
            DOGECOIN -> "DOGE"
            BITCOIN_CASH -> "BCH"
            SOLANA -> "SOL"
            APTOS -> "APT"
            SUI -> "SUI"
            COSMOS -> "ATOM"
            POLKADOT -> "DOT"
            CARDANO -> "ADA"
            NEAR -> "NEAR"
            TRON -> "TRX"
            TEZOS -> "XTZ"
            MONERO -> "XMR"
        }
    
    /**
     * 獲取鏈的顯示名稱
     */
    val displayName: String
        get() = when (this) {
            ETHEREUM -> "Ethereum"
            BSC -> "BNB Smart Chain"
            POLYGON -> "Polygon"
            ARBITRUM -> "Arbitrum"
            OPTIMISM -> "Optimism"
            AVALANCHE -> "Avalanche"
            FANTOM -> "Fantom"
            CRONOS -> "Cronos"
            CRONOSZVM -> "Cronos zkEVM"
            BASE -> "Base"
            ZKSYNC -> "zkSync"
            MOONBEAM -> "Moonbeam"
            GNOSIS -> "Gnosis"
            CELO -> "Celo"
            LINEA -> "Linea"
            SEPOLIA -> "Sepolia Testnet"
            GOERLI -> "Goerli Testnet"
            MUMBAI -> "Mumbai Testnet"
            BITCOIN -> "Bitcoin"
            LITECOIN -> "Litecoin"
            DOGECOIN -> "Dogecoin"
            BITCOIN_CASH -> "Bitcoin Cash"
            SOLANA -> "Solana"
            APTOS -> "Aptos"
            SUI -> "Sui"
            COSMOS -> "Cosmos"
            POLKADOT -> "Polkadot"
            CARDANO -> "Cardano"
            NEAR -> "NEAR"
            TRON -> "TRON"
            TEZOS -> "Tezos"
            MONERO -> "Monero"
        }

    /**
     * 獲取 BIP44 Coin Type (SLIP-0044)
     */
    fun getCoinType(): Int {
        return when (this) {
            ETHEREUM, BSC, POLYGON, ARBITRUM, OPTIMISM,
            AVALANCHE, FANTOM, CRONOS, CRONOSZVM, BASE,
            ZKSYNC, MOONBEAM, GNOSIS, CELO, LINEA,
            SEPOLIA, GOERLI, MUMBAI -> 60
            
            BITCOIN -> 0
            LITECOIN -> 2
            DOGECOIN -> 3
            BITCOIN_CASH -> 145
            SOLANA -> 501
            APTOS -> 637
            SUI -> 784
            COSMOS -> 118
            POLKADOT -> 354
            CARDANO -> 1815
            NEAR -> 397
            TRON -> 195
            TEZOS -> 1729
            MONERO -> 128
        }
    }
    
    /**
     * 獲取 Rango API 使用的鏈名稱
     */
    val rangoChainName: String
        get() = when (this) {
            ETHEREUM -> "ETH"
            BSC -> "BSC"
            POLYGON -> "POLYGON"
            ARBITRUM -> "ARBITRUM"
            OPTIMISM -> "OPTIMISM"
            AVALANCHE -> "AVAX_CCHAIN"
            FANTOM -> "FANTOM"
            BASE -> "BASE"
            LINEA -> "LINEA"
            ZKSYNC -> "ZKSYNC"
            else -> name
        }
    
    companion object {
        /**
         * 從 Rango 鏈名稱轉換為 ChainType
         */
        fun fromRangoChainName(name: String): ChainType? {
            return when (name.uppercase()) {
                "ETH", "ETHEREUM" -> ETHEREUM
                "BSC" -> BSC
                "POLYGON" -> POLYGON
                "ARBITRUM" -> ARBITRUM
                "OPTIMISM" -> OPTIMISM
                "AVAX_CCHAIN", "AVALANCHE" -> AVALANCHE
                "FANTOM" -> FANTOM
                "BASE" -> BASE
                "LINEA" -> LINEA
                "ZKSYNC" -> ZKSYNC
                "CRONOS" -> CRONOS
                "MOONBEAM" -> MOONBEAM
                "GNOSIS" -> GNOSIS
                "CELO" -> CELO
                else -> null
            }
        }
        
        /**
         * 從 Chain ID 轉換為 ChainType
         */
        fun fromChainId(chainId: Long): ChainType? {
            return entries.find { it.getChainId() == chainId }
        }
    }
}