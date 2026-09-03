package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.flow.Flow

/**
 * 安全私鑰管理器介面
 * 提供跨平台的私鑰安全存儲和管理功能
 * 
 * 安全級別：
 * - Level 1: 基本加密存儲
 * - Level 2: 使用平台 Keystore/Keychain
 * - Level 3: 硬體支援的安全存儲（TEE/Secure Element）
 * - Level 4: 生物識別認證保護
 */
interface SecureKeyManager {
    
    /**
     * 存儲私鑰
     * @param keyId 唯一標識符
     * @param privateKey 私鑰（十六進制字符串）
     * @param requireAuth 是否需要生物識別認證
     * @param authContext 認證上下文（可選）
     * @return 存儲結果
     */
    suspend fun storePrivateKey(
        keyId: String,
        privateKey: ByteArray,
        requireAuth: Boolean = true,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<Unit>
    
    
    /**
     * 刪除私鑰
     * @param keyId 唯一標識符
     * @param authContext 認證上下文
     * @param expectedWalletId 預期所屬錢包 ID
     * @return 刪除結果
     */
    suspend fun deletePrivateKey(
        keyId: String,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<Unit>
    
    /**
     * 開啟一個新的金鑰佈建會話 (Bounded Provisioning Session)
     */
    suspend fun startProvisioningSession(): ProvisioningSession

    /**
     * 獲取目前活躍的佈建會話 (若存在)
     */
    suspend fun getActiveProvisioningSession(sessionId: String): ProvisioningSession? = null

    /**
     * 於會話中存儲暫存私鑰 (Staged Private Key) - 精確 Session 綁定
     * @param sessionId 佈建會話 ID
     * @param stagedKeyAlias 暫存金鑰別名
     * @param privateKey 私鑰（字節陣列）
     * @param requireAuth 是否需要生物識別認證
     * @param authContext 認證上下文
     * @return 存儲結果
     */
    suspend fun storeStagedPrivateKey(
        sessionId: String,
        stagedKeyAlias: String,
        privateKey: ByteArray,
        requireAuth: Boolean = true,
        authContext: AuthenticationContext?
    ): Result<Unit>

    /**
     * 於會話中存儲暫存私鑰 (Staged Private Key)
     * @param session 佈建會話
     * @param privateKey 私鑰（字節陣列）
     * @param requireAuth 是否需要生物識別認證
     * @param authContext 認證上下文
     * @return 存儲結果
     */
    suspend fun storeStagedPrivateKey(
        session: ProvisioningSession,
        privateKey: ByteArray,
        requireAuth: Boolean = true,
        authContext: AuthenticationContext?
    ): Result<Unit> = storeStagedPrivateKey(
        sessionId = session.sessionId,
        stagedKeyAlias = session.stagedKeyAlias,
        privateKey = privateKey,
        requireAuth = requireAuth,
        authContext = authContext
    )

    /**
     * 提交金鑰佈建會話
     * 標記會話已完成持久化，使其轉為正式 Committed 狀態，並立即使 Session Token 失效以防止回滾。
     * @param session 佈建會話
     * @return 提交結果
     */
    suspend fun commitProvisioningSession(session: ProvisioningSession): Result<Unit>

    /**
     * 回滾未提交的金鑰佈建會話
     * 僅允許在未提交且有效的 session 上執行，自動抹除暫存金鑰，防止孤兒金鑰留下。
     * @param session 佈建會話
     * @return 回滾結果
     */
    suspend fun rollbackProvisioningSession(session: ProvisioningSession): Result<Unit>
    
    /**
     * 檢查私鑰三態存在狀態 (KeyPresence Tri-State Abstraction)
     * 分辨 Present (存在), Absent (確定不存在), Unavailable (硬體/IO異常不可判定)
     * @param keyId 唯一標識符
     * @return KeyPresence 三態結果
     */
    suspend fun checkKeyPresence(keyId: String): KeyPresence

    /**
     * 檢查私鑰是否存在
     * @param keyId 唯一標識符
     * @return 是否存在 (僅當 Present 時為 true)
     */
    suspend fun hasPrivateKey(keyId: String): Boolean = checkKeyPresence(keyId) is KeyPresence.Present
    
    /**
     * 列出所有私鑰 ID
     * @return 私鑰 ID 列表
     */
    suspend fun listKeyIds(): List<String>
    
    /**
     * 使用私鑰簽名
     * 私鑰不會離開安全區域
     * @param keyId 唯一標識符
     * @param data 要簽名的數據
     * @param authContext 認證上下文
     * @return 簽名結果
     */
    suspend fun signWithKey(
        keyId: String,
        data: ByteArray,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<ByteArray>

    /**
     * 揭露助記詞 (僅在需要時暴露)
     * @param keyId 唯一標識符
     * @param authContext 認證上下文
     * @param expectedWalletId 預期所屬錢包 ID
     */
    suspend fun revealMnemonic(
        keyId: String,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<ScopedMnemonic> = Result.Failure(UnsupportedOperationException("revealMnemonic not supported directly on KeyManager"))
    
    /**
     * 檢查硬體安全支援
     * @return 安全級別資訊
     */
    suspend fun getSecurityLevel(): SecurityLevel
    
    /**
     * 備份私鑰（加密後）
     * @param keyId 唯一標識符
     * @param backupPassword 備份密碼 (CharArray)
     * @param authContext 認證上下文
     * @param expectedWalletId 預期所屬錢包 ID
     * @return 加密的備份數據
     */
    suspend fun exportEncryptedKey(
        keyId: String,
        backupPassword: CharArray,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<EncryptedBackup>

    suspend fun exportEncryptedKey(
        keyId: String,
        backupPassword: ScopedPassword,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<EncryptedBackup> = backupPassword.use { exportEncryptedKey(keyId, it, authContext, expectedWalletId) }
    
    /**
     * 恢復私鑰
     * @param keyId 唯一標識符
     * @param encryptedBackup 加密的備份數據
     * @param backupPassword 備份密碼 (CharArray)
     * @param authContext 認證上下文
     * @param expectedWalletId 預期所屬錢包 ID
     * @return 恢復結果
     */
    suspend fun importEncryptedKey(
        keyId: String,
        encryptedBackup: EncryptedBackup,
        backupPassword: CharArray,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<Unit>

    suspend fun importEncryptedKey(
        keyId: String,
        encryptedBackup: EncryptedBackup,
        backupPassword: ScopedPassword,
        authContext: AuthenticationContext?,
        expectedWalletId: String
    ): Result<Unit> = backupPassword.use { importEncryptedKey(keyId, encryptedBackup, it, authContext, expectedWalletId) }
    
    /**
     * 監聽安全事件
     * @return 安全事件流
     */
    fun observeSecurityEvents(): Flow<SecurityEvent>
}

/**
 * 認證上下文 (Platform-Specific Typed Authorization Context)
 */
data class AuthenticationContext(
    val biometricPrompt: String? = null,
    val useDeviceCredential: Boolean = false,
    val validityDuration: Int = 0, // 秒
    val strongBoxBacked: Boolean = true,
    val authHandle: PlatformAuthHandle? = null,
    val cryptoObject: PlatformCryptoObject? = null // 平台特定認證物件 (例如 Android BiometricPrompt.CryptoObject、Apple LAContext)
)

/**
 * 安全級別
 */
data class SecurityLevel(
    val level: Level,
    val hasHardwareBacking: Boolean,
    val hasStrongBox: Boolean,
    val hasBiometricSupport: Boolean,
    val isRooted: Boolean
) {
    enum class Level {
        BASIC,        // 軟體加密
        KEYSTORE,     // 平台 Keystore
        HARDWARE,     // TEE 支援
        STRONGBOX     // Secure Element
    }
}

/**
 * 安全事件
 */
sealed class SecurityEvent {
    data class KeyAccessed(val keyId: String, val timestamp: Long) : SecurityEvent()
    data class KeyCreated(val keyId: String, val timestamp: Long) : SecurityEvent()
    data class KeyDeleted(val keyId: String, val timestamp: Long) : SecurityEvent()
    data class AuthenticationFailed(val keyId: String, val timestamp: Long) : SecurityEvent()
    data class RootDetected(val timestamp: Long) : SecurityEvent()
    data class TamperingDetected(val details: String, val timestamp: Long) : SecurityEvent()
}

/**
 * 私鑰元數據
 */
data class KeyMetadata(
    val keyId: String,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val requiresAuth: Boolean,
    val isHardwareBacked: Boolean,
    val algorithm: String,
    val keySize: Int
)

/**
 * 安全存儲配置
 */
data class SecureStorageConfig(
    val useStrongBox: Boolean = true,
    val requireUserPresence: Boolean = true,
    val userAuthenticationValidityDuration: Int = 10, // 秒
    val invalidateOnBiometricEnrollment: Boolean = true,
    val enableRootDetection: Boolean = true,
    val enableAntiTampering: Boolean = true
)

/**
 * 工廠方法創建平台特定的實現
 */
expect class SecureKeyManagerFactory {
    companion object {
        fun create(config: SecureStorageConfig = SecureStorageConfig()): SecureKeyManager
    }
}