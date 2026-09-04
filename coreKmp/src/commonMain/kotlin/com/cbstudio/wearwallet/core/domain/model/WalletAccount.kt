package com.cbstudio.wearwallet.core.domain.model

import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * 錢包帳戶領域模型
 */
@Serializable
data class WalletAccount(
    val id: String,
    val name: String,
    val address: String,
    val publicKey: String,
    val keyAlias: String? = null,
    val keyBackend: String? = null,
    val keyFormatVersion: Int = 1,
    val requiresAuth: Boolean = true,
    val chainType: ChainType,
    val walletType: WalletType = WalletType.HOT_WALLET,
    val isActive: Boolean = false,
    val isWatchOnly: Boolean = false,
    val derivationPath: String = "m/44'/60'/0'/0/0",
    val avatarId: String? = null,
    val metadata: String? = null,
    val isDeletionPending: Boolean = false,
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    // Keystone 硬體錢包相關
    val masterFingerprint: String? = null,
    val keystoneSignRequest: String? = null,
    val keystoneSyncData: String? = null
) {
    /**
     * 是否為硬體錢包
     */
    val isHardwareWallet: Boolean
        get() = walletType in listOf(
            WalletType.KEYSTONE,
            WalletType.KEYSTONE_COLD,
            WalletType.LEDGER,
            WalletType.TREZOR
        )
    
    /**
     * 是否為 Keystone 錢包
     */
    val isKeystoneWallet: Boolean
        get() = walletType in listOf(WalletType.KEYSTONE, WalletType.KEYSTONE_COLD)
    
    /**
     * 獲取顯示名稱（如果沒有名稱則顯示地址的簡寫）
     */
    val displayName: String
        get() = name.ifBlank { 
            "${address.take(6)}...${address.takeLast(4)}"
        }
    
    /**
     * 獲取簡短地址
     */
    val shortAddress: String
        get() = "${address.take(6)}...${address.takeLast(4)}"
}

