package com.cbstudio.wearwallet.core.security

/**
 * 17 層刪除細項子步驟列舉 (17-Layer Deletion Step Breakdown)
 *
 * 涵蓋從資料庫墓碑、硬體安全晶片私鑰銷毀、各類業務關聯資料清理、
 * 平台層背景工作與 UI Complication / Tile 失效通知、到最終主記錄刪除的全鏈路。
 */
enum class DeletionStep {
    WALLET_TOMBSTONE,
    KEY_VAULT,
    NFT_ROWS,
    PUSH_SUBSCRIPTIONS,
    NOTIFICATION_HISTORY,
    NOTIFICATION_PREFERENCES,
    KEYSTONE_DATA,
    TOKEN_ROWS,
    TRANSACTION_ROWS,
    PRICE_ALERT_ROWS,
    WORK_MANAGER_JOBS,
    BACKGROUND_SYNC,
    TILES,
    COMPLICATIONS,
    CACHES,
    ACTIVE_POINTER,
    WALLET_DB_ROW
}

/**
 * 刪除子步驟狀態
 */
enum class DeletionStepStatus {
    PENDING,
    PASS,
    FAILED
}

/**
 * 刪除子步驟持久化記錄
 */
data class DeletionStepRecord(
    val walletId: Long,
    val step: DeletionStep,
    val status: DeletionStepStatus,
    val errorMessage: String? = null,
    val retryCount: Long = 0L,
    val updatedAt: Long
)
