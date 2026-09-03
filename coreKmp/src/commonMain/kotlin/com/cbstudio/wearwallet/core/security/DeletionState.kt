package com.cbstudio.wearwallet.core.security

/**
 * 真正持久化的 5 態刪除狀態機 (Persistent 5-State Deletion Machine)
 *
 * 狀態流轉：
 * DELETE_AUTHORIZED ➔ TOMBSTONED ➔ KEY_DELETED ➔ REFERENCES_CLEARED ➔ COMPLETED
 *                                                       │
 *                                                       └──────➔ RECOVERY_REQUIRED
 */
enum class DeletionState {
    DELETE_AUTHORIZED,
    TOMBSTONED,
    KEY_DELETED,
    REFERENCES_CLEARED,
    COMPLETED,
    RECOVERY_REQUIRED
}
