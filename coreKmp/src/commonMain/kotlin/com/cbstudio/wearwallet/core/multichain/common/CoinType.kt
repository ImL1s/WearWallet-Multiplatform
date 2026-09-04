package com.cbstudio.wearwallet.core.multichain.common

/**
 * 支援的區塊鏈類型
 */
enum class CoinType {
    // EVM 鏈
    ETHEREUM,
    BSC,
    POLYGON,
    ARBITRUM,
    OPTIMISM,
    AVALANCHE,
    FANTOM,
    CRONOS,
    
    // UTXO 鏈
    BITCOIN,
    DOGECOIN,
    LITECOIN,
    BITCOINCASH,
    DASH,
    ZCASH,
    
    // 隱私幣
    MONERO,
    
    // 其他
    SOLANA,
    COSMOS,
    APTOS,
    SUI,
    TON
}