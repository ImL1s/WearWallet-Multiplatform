package com.cbstudio.wearwallet.core.domain.repository

import com.cbstudio.wearwallet.core.common.Result
import com.cbstudio.wearwallet.core.domain.model.WalletAccount
import com.cbstudio.wearwallet.core.domain.model.ChainType
import com.cbstudio.wearwallet.core.security.KeyPair
import com.cbstudio.wearwallet.core.security.AuthenticationContext
import com.cbstudio.wearwallet.core.security.ProvisioningRequest
import kotlinx.coroutines.flow.Flow

import com.cbstudio.wearwallet.core.security.ScopedPrivateKey

interface WalletRepository {
    /**
     * 預先準備金鑰佈建會話 (Exact Session Provisioning Request)
     * 回傳具有精確 Session ID 與 Staged Alias 的請求物件供 UI 發起精確生物識別認證。
     */
    suspend fun prepareProvisioning(): Result<ProvisioningRequest>

    // 創建和導入
    suspend fun createWallet(
        name: String,
        mnemonic: CharArray,
        password: CharArray,
        chainType: ChainType = ChainType.ETHEREUM,
        authContext: AuthenticationContext
    ): Result<WalletAccount>

    suspend fun importFromMnemonic(
        name: String,
        mnemonic: CharArray,
        password: CharArray,
        chainType: ChainType = ChainType.ETHEREUM,
        authContext: AuthenticationContext
    ): Result<WalletAccount>
    
    /**
     * 使用預先計算的 KeyPair 導入錢包（優化版本，避免重複計算）
     */
    suspend fun importFromMnemonicWithKeyPair(
        name: String,
        mnemonic: CharArray,
        password: CharArray,
        chainType: ChainType,
        keyPair: KeyPair,
        address: String,
        authContext: AuthenticationContext
    ): Result<WalletAccount>
    
    suspend fun importFromPrivateKey(
        name: String,
        privateKey: ScopedPrivateKey,
        password: CharArray,
        chainType: ChainType = ChainType.ETHEREUM,
        authContext: AuthenticationContext
    ): Result<WalletAccount>

    suspend fun importKeystoneWallet(
        name: String,
        xpub: String,
        derivationPath: String,
        masterFingerprint: String,
        chainType: ChainType = ChainType.ETHEREUM,
        policy: com.cbstudio.wearwallet.core.security.ExtendedPublicKeyPolicy = com.cbstudio.wearwallet.core.security.ExtendedPublicKeyPolicy.STRICT_DEFAULT
    ): Result<WalletAccount>

    /**
     * 將舊版 (v1/Legacy) 加密錢包遷移至 v2 KeyVault 託管架構。
     *
     * @param walletId 錢包唯一識別碼
     * @param password 用於解密舊版密文之密碼
     * @param authContext 認證上下文
     * @return 遷移後之 [WalletAccount]
     */
    suspend fun migrateLegacyWallet(walletId: String, password: CharArray, authContext: AuthenticationContext): Result<WalletAccount> =
        Result.Failure(UnsupportedOperationException("migrateLegacyWallet not supported by default implementation"))

    /**
     * 若錢包尚未完成 KeyVault 遷移 (key_format_version < 2 或缺少 key_alias) 則執行遷移；若已遷移則保證冪等。
     */
    suspend fun migrateLegacyWalletIfNeeded(walletId: String, password: CharArray, authContext: AuthenticationContext): Result<WalletAccount> =
        Result.Failure(UnsupportedOperationException("migrateLegacyWalletIfNeeded not supported by default implementation"))
    
    // 查詢
    suspend fun getAllWallets(): Result<List<WalletAccount>>
    suspend fun getWallet(id: String): Result<WalletAccount?>
    suspend fun getWalletByAddress(address: String): Result<WalletAccount?>
    suspend fun getActiveWallet(): Result<WalletAccount?>
    suspend fun getKeystoneWallets(): Result<List<WalletAccount>>
    
    // 更新和刪除
    suspend fun updateWallet(wallet: WalletAccount): Result<Unit>
    suspend fun deleteWallet(id: String, authContext: AuthenticationContext?): Result<Unit>
    suspend fun setActiveWallet(walletId: String): Result<Unit>
    
    // Keystone 相關
    suspend fun updateKeystoneData(
        walletId: String,
        signRequest: String?,
        syncData: String?
    ): Result<Unit>
    
    // 觀察
    fun observeWallets(): Flow<List<WalletAccount>>
    fun observeActiveWallet(): Flow<WalletAccount?>
    
    // 重啟狀態對帳 (P0-3 / P1-1 / M2: Crash-Safe Reconciliation)
    /**
     * 於 App 啟動時執行狀態對帳：
     * 1. 處理未提交的 Staging Journal（孤兒金鑰清理或遺漏 DB 提交恢復）
     * 2. 處理處於 DELETION_PENDING 墓碑狀態的錢包（已刪除 KeyVault 金鑰者清理 DB 記錄）
     */
    suspend fun reconcileStartupState(): Result<Unit> = Result.Success(Unit)

    // 餘額相關 (保留向後兼容)
    suspend fun getNativeBalance(address: String, chainType: ChainType): Double = 0.0
}