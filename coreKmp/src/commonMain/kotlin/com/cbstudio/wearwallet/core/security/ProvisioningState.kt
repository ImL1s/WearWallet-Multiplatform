package com.cbstudio.wearwallet.core.security

/**
 * 金鑰佈建狀態機 (Crash-Safe Provisioning State Machine)
 *
 * 狀態流轉：
 * PREPARED ➔ KEY_STAGED ➔ DB_WRITTEN ➔ COMMITTED
 *    │             │              │
 *    └─────────────┴──────────────┴──────➔ ROLLBACK_PENDING ➔ ROLLED_BACK
 */
enum class ProvisioningState {
    PREPARED,
    KEY_STAGED,
    DB_WRITTEN,
    COMMITTED,
    ROLLBACK_PENDING,
    ROLLED_BACK
}
