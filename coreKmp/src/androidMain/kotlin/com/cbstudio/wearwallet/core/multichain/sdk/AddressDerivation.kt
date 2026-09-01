package com.cbstudio.wearwallet.core.multichain.sdk

import com.cbstudio.wearwallet.core.multichain.MultiChainType
import com.cbstudio.wearwallet.core.multichain.monero.RealWallet2Wrapper
import wallet.core.jni.HDWallet
import wallet.core.jni.CoinType

/**
 * Android 實現 - 使用 TrustWallet Core 進行地址派生
 */
actual class AddressDerivation {
    
    init {
        // 確保 TrustWallet Core 已加載
        try {
            System.loadLibrary("TrustWalletCore")
        } catch (e: UnsatisfiedLinkError) {
            // 可能已經加載過了
        }
    }
    
    /**
     * 從助記詞派生指定鏈的地址
     */
    actual fun deriveAddress(mnemonic: String, chainType: MultiChainType): String {
        val wallet = HDWallet(mnemonic, "")
        
        return when (chainType) {
            // UTXO 鏈
            MultiChainType.BITCOIN -> {
                // BIP84 - Native SegWit (bc1...)
                wallet.getAddressForCoin(CoinType.BITCOIN)
            }
            MultiChainType.LITECOIN -> {
                wallet.getAddressForCoin(CoinType.LITECOIN)
            }
            MultiChainType.DOGECOIN -> {
                wallet.getAddressForCoin(CoinType.DOGECOIN)
            }
            MultiChainType.BITCOIN_CASH -> {
                wallet.getAddressForCoin(CoinType.BITCOINCASH)
            }
            
            // EVM 兼容鏈 - 全部使用 Ethereum 地址
            MultiChainType.ETHEREUM,
            MultiChainType.BSC,
            MultiChainType.POLYGON,
            MultiChainType.AVALANCHE,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.FANTOM,
            MultiChainType.CRONOS,
            MultiChainType.BASE,
            MultiChainType.CELO,
            MultiChainType.MOONBEAM -> {
                // 所有 EVM 鏈使用相同的地址派生
                wallet.getAddressForCoin(CoinType.ETHEREUM)
            }
            
            // 其他特殊鏈
            MultiChainType.SOLANA -> {
                wallet.getAddressForCoin(CoinType.SOLANA)
            }
            MultiChainType.TRON -> {
                wallet.getAddressForCoin(CoinType.TRON)
            }

            // Monero - 使用 RealWallet2Wrapper
            MultiChainType.MONERO -> {
                try {
                    // 確保 Native 庫已加載
                    if (!RealWallet2Wrapper.loadRealWallet2Library()) {
                        throw Exception("Failed to load Monero native library")
                    }

                    // 從助記詞創建錢包並獲取地址
                    val walletHandle = RealWallet2Wrapper.createRealWalletFromMnemonic(
                        mnemonic = mnemonic,
                        networkType = 2 // STAGENET for testing
                    )

                    if (walletHandle > 0) {
                        val address = RealWallet2Wrapper.getRealWalletAddress(walletHandle)
                        RealWallet2Wrapper.closeRealWallet(walletHandle)
                        address
                    } else {
                        throw Exception("Failed to create Monero wallet")
                    }
                } catch (e: Exception) {
                    // 如果無法生成地址，返回佔位符
                    // 實際地址將在 MoneroWalletManager 初始化時獲取
                    throw UnsupportedOperationException("Monero address derivation failed: ${e.message}")
                }
            }

            else -> throw IllegalArgumentException("Unsupported chain: $chainType")
        }
    }
    
    /**
     * 從助記詞派生指定鏈的私鑰
     */
    actual fun derivePrivateKey(mnemonic: String, chainType: MultiChainType): ByteArray {
        val wallet = HDWallet(mnemonic, "")
        
        val derivationPath = when (chainType) {
            // UTXO 鏈
            MultiChainType.BITCOIN -> "m/84'/0'/0'/0/0"  // BIP84 for native segwit
            MultiChainType.LITECOIN -> "m/84'/2'/0'/0/0"  // BIP84 for Litecoin
            MultiChainType.DOGECOIN -> "m/44'/3'/0'/0/0"  // BIP44 for Dogecoin
            MultiChainType.BITCOIN_CASH -> "m/44'/145'/0'/0/0"  // BIP44 for BCH
            
            // EVM 兼容鏈 - 全部使用 Ethereum 路徑
            MultiChainType.ETHEREUM,
            MultiChainType.BSC,
            MultiChainType.POLYGON,
            MultiChainType.AVALANCHE,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.FANTOM,
            MultiChainType.CRONOS,
            MultiChainType.BASE,
            MultiChainType.CELO,
            MultiChainType.MOONBEAM -> "m/44'/60'/0'/0/0"  // BIP44 for Ethereum
            
            // 其他特殊鏈
            MultiChainType.SOLANA -> "m/44'/501'/0'/0/0"  // BIP44 for Solana
            MultiChainType.TRON -> "m/44'/195'/0'/0/0"  // BIP44 for Tron
            MultiChainType.MONERO -> "m/44'/128'/0'/0/0"  // BIP44 for Monero (placeholder)

            else -> throw IllegalArgumentException("Unsupported chain: $chainType")
        }
        
        val coinType = when (chainType) {
            // UTXO 鏈
            MultiChainType.BITCOIN -> CoinType.BITCOIN
            MultiChainType.LITECOIN -> CoinType.LITECOIN
            MultiChainType.DOGECOIN -> CoinType.DOGECOIN
            MultiChainType.BITCOIN_CASH -> CoinType.BITCOINCASH
            
            // EVM 兼容鏈 - 全部使用 Ethereum CoinType
            MultiChainType.ETHEREUM,
            MultiChainType.BSC,
            MultiChainType.POLYGON,
            MultiChainType.AVALANCHE,
            MultiChainType.ARBITRUM,
            MultiChainType.OPTIMISM,
            MultiChainType.FANTOM,
            MultiChainType.CRONOS,
            MultiChainType.BASE,
            MultiChainType.CELO,
            MultiChainType.MOONBEAM -> CoinType.ETHEREUM
            
            // 其他特殊鏈
            MultiChainType.SOLANA -> CoinType.SOLANA
            MultiChainType.TRON -> CoinType.TRON

            // Monero 使用 RealWallet2Wrapper，不使用 TrustWallet Core
            MultiChainType.MONERO -> return ByteArray(0) // Monero keys managed separately

            else -> throw IllegalArgumentException("Unsupported chain: $chainType")
        }

        val privateKey = wallet.getKey(coinType, derivationPath)
        return privateKey.data()
    }
}