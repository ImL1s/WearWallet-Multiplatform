package com.cbstudio.wearwallet.presentation.util

import com.cbstudio.wearwallet.BuildConfig
import com.cbstudio.wearwallet.core.security.AuthOperation
import com.cbstudio.wearwallet.core.security.PlatformAuthHandle
import kotlinx.datetime.Clock
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug-only auth helper for AVD screenshot / QA flows where biometric enrollment is unavailable.
 * Never used on physical devices or release builds.
 */
internal const val DEBUG_EMULATOR_AUTH_TTL_MS = 10_000L

internal object DebugEmulatorAuth {
    private val sessionCounter = AtomicLong(0)

    fun canUse(): Boolean = try {
        BuildConfig.DEBUG && isEmulatorDevice()
    } catch (_: Throwable) {
        false
    }

    fun issueImportHandle(keyId: String, sessionId: String): PlatformAuthHandle? {
        if (!canUse()) return null
        return issueHandle(
            keyId = keyId,
            operation = AuthOperation.IMPORT,
            sessionId = sessionId,
            walletId = sessionId
        )
    }

    fun issueSignHandle(
        keyId: String,
        walletId: String,
        intentFingerprint: String
    ): PlatformAuthHandle? {
        if (!canUse()) return null
        return issueHandle(
            keyId = keyId,
            operation = AuthOperation.SIGN,
            sessionId = walletId,
            walletId = walletId,
            intentFingerprint = intentFingerprint
        )
    }

    private fun issueHandle(
        keyId: String,
        operation: AuthOperation,
        sessionId: String,
        walletId: String,
        intentFingerprint: String = "",
        validityDurationMs: Long = DEBUG_EMULATOR_AUTH_TTL_MS
    ): PlatformAuthHandle {
        val now = Clock.System.now().toEpochMilliseconds()
        val calculatedExpiresAt = now + validityDurationMs
        val nonce = "nonce_${UUID.randomUUID()}_${sessionCounter.incrementAndGet()}"

        val verifierClass = Class.forName("com.cbstudio.wearwallet.core.security.ProofTokenVerifier")
        val instanceField = verifierClass.getDeclaredField("INSTANCE")
        instanceField.isAccessible = true
        val verifierInstance = instanceField.get(null)

        val signMethod = verifierClass.declaredMethods.first {
            it.name.startsWith("sign") && (it.parameterCount == 8 || it.parameterCount == 9)
        }
        signMethod.isAccessible = true
        val token = if (signMethod.parameterCount == 9) {
            signMethod.invoke(
                verifierInstance,
                keyId,
                operation,
                intentFingerprint,
                sessionId,
                nonce,
                now,
                calculatedExpiresAt,
                walletId,
                "DEBUG_EMULATOR"
            ) as String
        } else {
            signMethod.invoke(
                verifierInstance,
                keyId,
                operation,
                intentFingerprint,
                sessionId,
                nonce,
                now,
                calculatedExpiresAt,
                walletId
            ) as String
        }

        val constructor = PlatformAuthHandle::class.java.getDeclaredConstructor(
            String::class.java,
            AuthOperation::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            String::class.java,
            String::class.java
        )
        constructor.isAccessible = true
        val handle = constructor.newInstance(
            keyId,
            operation,
            intentFingerprint,
            sessionId,
            nonce,
            now,
            calculatedExpiresAt,
            token,
            walletId
        ) as PlatformAuthHandle

        return handle
    }
}
