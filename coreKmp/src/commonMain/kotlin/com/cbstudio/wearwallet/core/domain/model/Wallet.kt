package com.cbstudio.wearwallet.core.domain.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

@Serializable
enum class WalletType { 
    HOT_WALLET, 
    MNEMONIC,       // 助記詞錢包
    PRIVATE_KEY,    // 私鑰錢包
    KEYSTONE,       // Keystone 硬體錢包
    KEYSTONE_COLD,  // Keystone 冷錢包模式
    LEDGER, 
    TREZOR, 
    WATCH_ONLY,
    MULTI_SIG       // 多重簽名錢包
}

@Serializable
data class Wallet(
    val id: String,
    val name: String,
    val address: String,
    val publicKey: String,
    val encryptedPrivateKey: String,
    val encryptedMnemonic: String? = null,
    val derivationPath: String = "m/44'/60'/0'/0/0",
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val isActive: Boolean = true,
    val isWatchOnly: Boolean = false,
    val chainType: ChainType = ChainType.ETHEREUM,
    val metadata: Map<String, String> = emptyMap(),
    val masterFingerprint: String? = null,
    val walletType: WalletType = WalletType.HOT_WALLET
) {
    fun getShortAddress(): String = 
        if (address.length > 10) "${address.take(6)}...${address.takeLast(4)}" 
        else address
    
    fun isHardwareWallet(): Boolean = 
        walletType in listOf(WalletType.KEYSTONE, WalletType.KEYSTONE_COLD, WalletType.LEDGER, WalletType.TREZOR)
    
    fun requiresExternalSigning(): Boolean = 
        isHardwareWallet() || walletType == WalletType.WATCH_ONLY
}