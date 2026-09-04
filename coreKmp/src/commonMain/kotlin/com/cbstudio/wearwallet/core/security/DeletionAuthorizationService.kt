package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import kotlinx.datetime.Clock

/**
 * 錢包刪除授權憑證簽發服務 (Deletion Authorization Service)
 * 進程中唯一受信任簽發 DeletionAuthorizationGrant 的能力服務。
 *
 * 職責：
 * 1. 嚴格驗證 DELETE 授權 Handle 並於 AuthHandleRegistry 單一鎖內原子消費，簽發具備 HMAC-SHA256 簽名之 Grant。
 * 2. 針對無需生物認證的錢包，提供受控的 issueUnauthenticatedGrant 統一註冊受管 Grant。
 * 3. 杜絕外部或 Repository 自行構造 Grant。
 */
internal object DeletionAuthorizationService {

    /**
     * 憑經過生物識別/硬體認證的 PlatformAuthHandle 簽發一次性 DeletionAuthorizationGrant
     */
    internal fun issueDeletionGrant(
        handle: PlatformAuthHandle?,
        walletId: String,
        keyAlias: String,
        currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()
    ): Result<DeletionAuthorizationGrant> {
        if (walletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("walletId must not be blank"))
        }
        if (keyAlias.isBlank()) {
            return Result.Failure(IllegalArgumentException("keyAlias must not be blank"))
        }
        if (handle == null) {
            return Result.Failure(
                AuthenticationRequiredException("Authentication is required to delete key '$keyAlias' but authHandle is null")
            )
        }

        return AuthHandleRegistry.validateConsumeAndIssueGrant(
            handle = handle,
            walletId = walletId,
            expectedKeyId = keyAlias,
            currentTimeMs = currentTimeMs
        )
    }

    /**
     * 為未要求生物認證的錢包 (requires_auth == false) 簽發受管 DeletionAuthorizationGrant
     */
    internal fun issueUnauthenticatedGrant(
        walletId: String,
        keyAlias: String,
        currentTimeMs: Long = Clock.System.now().toEpochMilliseconds()
    ): Result<DeletionAuthorizationGrant> {
        if (walletId.isBlank()) {
            return Result.Failure(IllegalArgumentException("walletId must not be blank"))
        }
        if (keyAlias.isBlank()) {
            return Result.Failure(IllegalArgumentException("keyAlias must not be blank"))
        }

        val unauthSessionId = "unauth_${walletId}_${CryptoUtils.randomBytes(8).toHexString()}"
        val grantNonce = CryptoUtils.randomBytes(16).toHexString()
        val expiresAtMs = if (currentTimeMs > 0) currentTimeMs + 60_000L else 0L

        val grantHmac = DeletionGrantVerifier.sign(
            walletId = walletId,
            keyAlias = keyAlias,
            operation = AuthOperation.DELETE,
            originalSessionId = unauthSessionId,
            nonce = grantNonce,
            issuedAtMs = currentTimeMs,
            expiresAtMs = expiresAtMs
        )

        val grant = DeletionAuthorizationGrant(
            walletId = walletId,
            keyAlias = keyAlias,
            operation = AuthOperation.DELETE,
            originalAuthSessionId = unauthSessionId,
            issuedAtMs = currentTimeMs,
            expiresAtMs = expiresAtMs,
            nonce = grantNonce,
            proofToken = grantHmac
        )

        DeletionGrantRegistry.register(grant)
        return Result.Success(grant)
    }
}
