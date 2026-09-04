package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result

/**
 * 內部金鑰庫對帳能力介面 (Internal KeyVault Reconciliation Capability)
 *
 * 專為啟動狀態對帳 (Startup State Reconciliation) 與崩潰恢復設計的內部能力。
 * 嚴禁對外公開於 SecureKeyManager 介面。
 * 僅允許在完成 5 層防偽校驗 (5-Layer Anti-Spoofing Validation) 並持有單次使用 RecoveryGrant 後由受信任的對帳器呼叫。
 */
internal interface KeyVaultReconciliationCapability {
    /**
     * 清理經 5 層防偽驗證確認為孤兒或回滾中的未提交暫存金鑰。
     *
     * @param grant 經 5 層防偽驗證簽發且已註冊的單次使用 RecoveryGrant
     * @return 清理結果
     */
    suspend fun rollbackStagedKeyInternal(
        grant: RecoveryGrant
    ): Result<Unit>
}

