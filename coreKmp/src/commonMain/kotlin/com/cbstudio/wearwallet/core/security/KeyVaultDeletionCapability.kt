package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result

/**
 * 內部金鑰庫刪除授權能力 (Internal KeyVault Deletion Capability)
 *
 * 僅接受持有一致且有效的一次回退/刪除授權憑證 (DeletionAuthorizationGrant) 進行私鑰物理刪除。
 * 杜絕二次消費或未經授權的直接底層刪除。
 */
internal interface KeyVaultDeletionCapability {
    /**
     * 憑 DeletionAuthorizationGrant 刪除金鑰
     */
    suspend fun deletePrivateKeyWithGrant(
        grant: DeletionAuthorizationGrant,
        expectedWalletId: String = ""
    ): Result<Unit>
}
