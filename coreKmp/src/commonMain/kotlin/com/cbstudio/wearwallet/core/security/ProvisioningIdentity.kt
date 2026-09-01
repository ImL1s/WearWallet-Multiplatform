package com.cbstudio.wearwallet.core.security

/**
 * 類型化錢包佈建身分 (Typed Provisioning Identity)
 * 分辨新錢包建立階段 (以 SessionId 綁定) 與既有錢包 (以正式 WalletId 綁定)。
 */
sealed interface ProvisioningIdentity {
    val identifier: String

    data class NewWallet(val sessionId: String) : ProvisioningIdentity {
        override val identifier: String get() = sessionId
    }

    data class ExistingWallet(val walletId: String) : ProvisioningIdentity {
        override val identifier: String get() = walletId
    }

    companion object {
        fun fromWalletId(walletId: String): ProvisioningIdentity = ExistingWallet(walletId)
        fun fromSessionId(sessionId: String): ProvisioningIdentity = NewWallet(sessionId)
    }
}
