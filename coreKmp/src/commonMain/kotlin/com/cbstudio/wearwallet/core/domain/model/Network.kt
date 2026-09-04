package com.cbstudio.wearwallet.core.domain.model

import kotlinx.serialization.Serializable

/**
 * 網路類型
 */
@Serializable
sealed class Network {
    abstract val name: String
    abstract val chainId: Int
    abstract val symbol: String
    abstract val explorerBaseUrl: String
    
    @Serializable
    object Mainnet : Network() {
        override val name = "Mainnet"
        override val chainId = 1
        override val symbol = "ETH"
        override val explorerBaseUrl = "https://etherscan.io"
    }
    
    @Serializable
    object BITCOIN_MAINNET : Network() {
        override val name = "Bitcoin"
        override val chainId = 0 // Bitcoin doesn't use chainId
        override val symbol = "BTC"
        override val explorerBaseUrl = "https://blockstream.info"
    }
    
    @Serializable
    object BITCOIN_TESTNET : Network() {
        override val name = "Bitcoin Testnet"
        override val chainId = -1 // Bitcoin testnet doesn't use chainId
        override val symbol = "tBTC"
        override val explorerBaseUrl = "https://blockstream.info/testnet"
    }
    
    @Serializable
    object LITECOIN_MAINNET : Network() {
        override val name = "Litecoin"
        override val chainId = 0 // Litecoin doesn't use chainId
        override val symbol = "LTC"
        override val explorerBaseUrl = "https://blockchair.com/litecoin"
    }
    
    @Serializable
    object LITECOIN_TESTNET : Network() {
        override val name = "Litecoin Testnet"
        override val chainId = -1 // Litecoin testnet doesn't use chainId
        override val symbol = "tLTC"
        override val explorerBaseUrl = "https://blockexplorer.one/litecoin/testnet"
    }
    
    @Serializable
    object DOGECOIN_MAINNET : Network() {
        override val name = "Dogecoin"
        override val chainId = 0 // Dogecoin doesn't use chainId
        override val symbol = "DOGE"
        override val explorerBaseUrl = "https://blockchair.com/dogecoin"
    }
    
    @Serializable
    object DOGECOIN_TESTNET : Network() {
        override val name = "Dogecoin Testnet"
        override val chainId = -1 // Dogecoin testnet doesn't use chainId
        override val symbol = "tDOGE"
        override val explorerBaseUrl = "https://blockexplorer.one/dogecoin/testnet"
    }
    
    @Serializable
    object BCH_MAINNET : Network() {
        override val name = "Bitcoin Cash"
        override val chainId = 0 // BCH doesn't use chainId
        override val symbol = "BCH"
        override val explorerBaseUrl = "https://blockchair.com/bitcoin-cash"
    }
    
    @Serializable
    object BCH_TESTNET : Network() {
        override val name = "Bitcoin Cash Testnet"
        override val chainId = -1 // BCH testnet doesn't use chainId
        override val symbol = "tBCH"
        override val explorerBaseUrl = "https://www.blockchain.com/explorer/assets/bch-testnet"
    }
    
    // Alias for Bitcoin Cash networks (for compatibility)
    @Serializable
    object BITCOIN_CASH_MAINNET : Network() {
        override val name = "Bitcoin Cash"
        override val chainId = 0 // BCH doesn't use chainId
        override val symbol = "BCH"
        override val explorerBaseUrl = "https://blockchair.com/bitcoin-cash"
    }
    
    @Serializable
    object BITCOIN_CASH_TESTNET : Network() {
        override val name = "Bitcoin Cash Testnet"
        override val chainId = -1 // BCH testnet doesn't use chainId
        override val symbol = "tBCH"
        override val explorerBaseUrl = "https://www.blockchain.com/explorer/assets/bch-testnet"
    }
    
    @Serializable
    data class Testnet(
        override val name: String,
        override val chainId: Int,
        override val symbol: String,
        override val explorerBaseUrl: String
    ) : Network()
    
    @Serializable
    data class Custom(
        override val name: String,
        override val chainId: Int,
        override val symbol: String,
        override val explorerBaseUrl: String,
        val rpcUrl: String
    ) : Network()
}