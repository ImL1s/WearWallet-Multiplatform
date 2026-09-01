package com.cbstudio.wearwallet.core.multichain

import kotlinx.serialization.Serializable

/**
 * 支援的區塊鏈類型
 * 
 * 新增的五條鏈：
 * - SOLANA: 使用 Metaplex solana-kmp SDK
 * - TRON: 使用 JavaScript 橋接
 * - POLKADOT: 使用 expect/actual 混合模式
 * - CARDANO: 使用 Kogmios Kotlin 庫
 * - MONERO: 使用原生實現
 */
@Serializable
enum class MultiChainType(
    val symbol: String,
    val fullName: String,
    val decimals: Int,
    val isTestnetSupported: Boolean = true
) {
    // 現有支援的鏈
    BITCOIN("BTC", "Bitcoin", 8),
    ETHEREUM("ETH", "Ethereum", 18),
    BITCOIN_CASH("BCH", "Bitcoin Cash", 8),
    LITECOIN("LTC", "Litecoin", 8),
    DOGECOIN("DOGE", "Dogecoin", 8),
    
    // EVM 兼容鏈
    BSC("BNB", "Binance Smart Chain", 18),
    POLYGON("MATIC", "Polygon", 18),
    AVALANCHE("AVAX", "Avalanche", 18),
    ARBITRUM("ARB", "Arbitrum", 18),
    OPTIMISM("OP", "Optimism", 18),
    CRONOS("CRO", "Cronos", 18),
    BASE("BASE", "Base", 18),
    FANTOM("FTM", "Fantom", 18),
    CELO("CELO", "Celo", 18),
    MOONBEAM("GLMR", "Moonbeam", 18),
    LINEA("ETH", "Linea", 18),
    ZKSYNC("ETH", "zkSync", 18),
    
    // 新增的五條鏈
    SOLANA("SOL", "Solana", 9),
    TRON("TRX", "TRON", 6),
    POLKADOT("DOT", "Polkadot", 10),
    CARDANO("ADA", "Cardano", 6),
    MONERO("XMR", "Monero", 12);
    
    companion object {
        /**
         * 取得所有支援的鏈類型
         */
        fun getAllChains(): List<MultiChainType> = values().toList()
        
        /**
         * 取得新增的五條鏈
         */
        fun getNewChains(): List<MultiChainType> = listOf(
            SOLANA, TRON, POLKADOT, CARDANO, MONERO
        )
        
        /**
         * 根據符號查找鏈類型
         */
        fun fromSymbol(symbol: String): MultiChainType? {
            return values().find { it.symbol.equals(symbol, ignoreCase = true) }
        }
        
        /**
         * 檢查是否為 UTXO 模型的鏈
         */
        fun isUtxoChain(chainType: MultiChainType): Boolean {
            return when (chainType) {
                BITCOIN, BITCOIN_CASH, LITECOIN, DOGECOIN -> true
                else -> false
            }
        }
        
        /**
         * 檢查是否為帳戶模型的鏈
         */
        fun isAccountChain(chainType: MultiChainType): Boolean {
            return when (chainType) {
                ETHEREUM, SOLANA, TRON, POLKADOT, CARDANO -> true
                else -> false
            }
        }
        
        /**
         * 檢查是否為隱私鏈
         */
        fun isPrivacyChain(chainType: MultiChainType): Boolean {
            return when (chainType) {
                MONERO -> true
                else -> false
            }
        }
    }
}