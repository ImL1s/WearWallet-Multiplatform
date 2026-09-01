package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.sdk.impl.*

/**
 * Real SDK Factory
 * 創建真實的區塊鏈 SDK 實例
 */
object RealSDKFactory {
    
    /**
     * 創建 SDK 管理器並註冊所有支援的 SDK
     */
    fun createRealManager(): SDKAdapterManager {
        val manager = SDKAdapterManager()
        
        // 註冊 EVM 鏈 SDK
        registerEVMChains(manager)
        
        // 註冊特殊鏈 SDK
        registerSpecialChains(manager)
        
        return manager
    }
    
    /**
     * 註冊所有 EVM 兼容鏈
     */
    private fun registerEVMChains(manager: SDKAdapterManager) {
        val evmChains = listOf(
            MultiChainType.ETHEREUM,
            MultiChainType.BSC,
            MultiChainType.POLYGON,
            MultiChainType.AVALANCHE,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.BASE,
            MultiChainType.CRONOS,
            MultiChainType.FANTOM,
            MultiChainType.CELO,
            MultiChainType.MOONBEAM
        )
        
        evmChains.forEach { chain ->
            manager.registerAdapter(RealEthereumSDK(chain))
        }
    }
    
    /**
     * 註冊特殊鏈
     */
    private fun registerSpecialChains(manager: SDKAdapterManager) {
        // Solana
        manager.registerAdapter(RealSolanaSDK())
        
        // TRON
        manager.registerAdapter(RealTronSDK())
        
        // UTXO 鏈
        manager.registerAdapter(RealBitcoinSDK())
        manager.registerAdapter(RealLitecoinSDK())
        manager.registerAdapter(RealDogecoinSDK())
        manager.registerAdapter(RealBitcoinCashSDK())
        
        // Cardano
        manager.registerAdapter(CardanoRealSDK())

        // Polkadot
        manager.registerAdapter(RealPolkadotSDK())

        // Monero - 使用提供者模式
        val provider = com.cbstudio.wearwallet.core.multichain.monero.crypto.getMoneroCryptoProvider()
        manager.registerAdapter(com.cbstudio.wearwallet.core.multichain.monero.sdk.MoneroSDK(provider))
    }
}