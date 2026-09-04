package com.cbstudio.wearwallet.core.security

import kotlinx.datetime.Clock

/**
 * 具有精確 Session 與 Staged Alias 綁定的金鑰佈建請求 (Exact Session Provisioning Request)
 * 徹底淘汰任何通配或魔法金鑰 (Magic Keys 如 IMPORT_PROVISIONING, wallet_creation, *, temp_*)。
 * 保證一個認證 Handle 僅能授權一把特定的 Staged Alias 金鑰，且僅在指定 Session 與時限內有效。
 */
data class ProvisioningRequest(
    val sessionId: String,
    val stagedAlias: String,
    val operation: AuthOperation = AuthOperation.IMPORT,
    val expiryEpochMs: Long = Clock.System.now().toEpochMilliseconds() + 60_000L
)
