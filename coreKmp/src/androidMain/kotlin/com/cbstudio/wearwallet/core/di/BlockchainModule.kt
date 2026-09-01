package com.cbstudio.wearwallet.core.di

import com.cbstudio.wearwallet.core.blockchain.adapter.*
import com.cbstudio.wearwallet.core.blockchain.signer.*
import com.cbstudio.wearwallet.core.domain.model.Network
import org.koin.dsl.module

/**
 * 區塊鏈相關模組
 * 包含 UTXO 鏈和其他鏈的適配器和簽名器
 */
internal val androidBlockchainModule = module {
    // Bitcoin
    single { BitcoinPlatformAdapter(Network.BITCOIN_MAINNET) }
    single { BitcoinPlatformAdapter(Network.BITCOIN_TESTNET) }
    single { BitcoinSigner() }
    
    // Litecoin
    single { LitecoinPlatformAdapter(Network.LITECOIN_MAINNET) }
    single { LitecoinPlatformAdapter(Network.LITECOIN_TESTNET) }
    single { LitecoinSigner() }
    
    // Dogecoin
    single { DogecoinPlatformAdapter(Network.DOGECOIN_MAINNET) }
    single { DogecoinPlatformAdapter(Network.DOGECOIN_TESTNET) }
    single { DogecoinSigner() }
    
    // Bitcoin Cash
    single { BitcoinCashPlatformAdapter(Network.BCH_MAINNET) }
    single { BitcoinCashPlatformAdapter(Network.BCH_TESTNET) }
    single { BitcoinCashSigner() }
    
    // 通用鏈適配器工廠
    factory<ChainAdapter> { (network: Network) ->
        when (network) {
            Network.BITCOIN_MAINNET, Network.BITCOIN_TESTNET -> 
                BitcoinPlatformAdapter(network)
            Network.LITECOIN_MAINNET, Network.LITECOIN_TESTNET -> 
                LitecoinPlatformAdapter(network)
            Network.DOGECOIN_MAINNET, Network.DOGECOIN_TESTNET -> 
                DogecoinPlatformAdapter(network)
            Network.BCH_MAINNET, Network.BCH_TESTNET,
            Network.BITCOIN_CASH_MAINNET, Network.BITCOIN_CASH_TESTNET -> 
                BitcoinCashPlatformAdapter(network)
            else -> throw IllegalArgumentException("Unsupported network: $network")
        }
    }
}