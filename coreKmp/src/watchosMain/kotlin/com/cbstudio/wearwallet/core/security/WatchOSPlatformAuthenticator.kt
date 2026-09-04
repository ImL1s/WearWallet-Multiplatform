package com.cbstudio.wearwallet.core.security

import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicy
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import kotlin.coroutines.resume

/**
 * watchOS 專屬認證器服務 (watchOS Platform Authenticator Service)
 * 嚴格由 Apple LocalAuthentication (LAContext) evaluatePolicy 真實回調簽發 PlatformAuthHandle。
 */
object WatchOSPlatformAuthenticator {

    /**
     * 執行真實的 LocalAuthentication Policy 評估並簽發 PlatformAuthHandle。
     *
     * 唯一合法之公開認證簽發入口：
     * 1. 內部執行 LAContext.canEvaluatePolicy 與 evaluatePolicy
     * 2. 僅在 success == true && error == null 時簽發 PlatformAuthHandle
     * 3. 註冊 invokeOnCancellation 以在協程取消時呼叫 laContext.invalidate()
     * 4. 回調內部檢查 continuation.isActive 防止重複或向已取消協程 resume
     */
    suspend fun evaluatePolicyAndIssueHandle(
        keyId: String,
        operation: AuthOperation,
        localizedReason: String,
        walletId: String,
        policy: LAPolicy = LAPolicyDeviceOwnerAuthentication,
        intentFingerprint: String = "",
        validityDurationMs: Long = 10_000L,
        laContext: LAContext = LAContext()
    ): Result<PlatformAuthHandle> = suspendCancellableCoroutine { continuation ->
        require(keyId.isNotBlank()) { "keyId must not be blank" }
        require(walletId.isNotBlank()) { "walletId must not be blank" }

        continuation.invokeOnCancellation {
            laContext.invalidate()
        }

        val canEvaluate = laContext.canEvaluatePolicy(policy, error = null)
        if (!canEvaluate) {
            if (continuation.isActive) {
                continuation.resume(
                    Result.Failure(
                        AuthenticationRequiredException("watchOS device does not support or has not enabled passcode/wrist detection")
                    )
                )
            }
            return@suspendCancellableCoroutine
        }

        laContext.evaluatePolicy(policy, localizedReason) { success, error ->
            if (continuation.isActive) {
                if (success && error == null) {
                    val handle = PlatformAuthHandle.createInternal(
                        keyId = keyId,
                        operation = operation,
                        intentFingerprint = intentFingerprint,
                        validityDurationMs = validityDurationMs,
                        walletId = walletId,
                        laContext = laContext,
                        authenticatorType = "WATCHOS_LOCAL_AUTHENTICATION"
                    )
                    continuation.resume(Result.Success(handle))
                } else {
                    val errorMsg = error?.localizedDescription ?: "LocalAuthentication failed or was cancelled by user on watchOS"
                    continuation.resume(
                        Result.Failure(AuthenticationRequiredException(errorMsg))
                    )
                }
            }
        }
    }
}
