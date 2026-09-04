package com.cbstudio.wearwallet.domain.model

/**
 * ULTRATHINK - 加密貨幣枚舉
 * 
 * 定義支援的加密貨幣類型
 */
enum class Currency(
    val symbol: String,
    val displayName: String,
    val decimals: Int,
    val isStablecoin: Boolean = false
) {
    // 原生代幣
    ETH("ETH", "Ethereum", 18),
    BNB("BNB", "Binance Coin", 18),
    MATIC("MATIC", "Polygon", 18),
    CRO("CRO", "Cronos", 18),
    
    // 穩定幣
    USDC("USDC", "USD Coin", 6, true),
    USDT("USDT", "Tether", 6, true),
    DAI("DAI", "Dai Stablecoin", 18, true),
    BUSD("BUSD", "Binance USD", 18, true),
    GUSD("GUSD", "Gemini Dollar", 2, true),
    
    // 其他代幣
    WBTC("WBTC", "Wrapped Bitcoin", 8),
    LINK("LINK", "Chainlink", 18),
    UNI("UNI", "Uniswap", 18),
    AAVE("AAVE", "Aave", 18),
    SUSHI("SUSHI", "SushiSwap", 18);
    
    companion object {
        fun fromSymbol(symbol: String): Currency? {
            return values().find { it.symbol.equals(symbol, ignoreCase = true) }
        }
        
        /**
         * 獲取所有穩定幣
         */
        fun getStablecoins(): List<Currency> {
            return values().filter { it.isStablecoin }
        }
        
        /**
         * 獲取 NFC 支付支援的幣種
         */
        fun getNfcSupportedCurrencies(): List<Currency> {
            return listOf(USDC, USDT, DAI, ETH)
        }
    }
}
