package com.cbstudio.wearwallet.core.recovery

/**
 * 啟動狀態恢復狀態機之狀態定義
 *
 * 狀態流轉:
 * INITIALIZING -> RECONCILING -> READY (正常完成)
 *                             -> RECOVERY_REQUIRED (需要手動/維護恢復)
 *                             -> FAILED (致命錯誤/DB毀損/CAS不匹配)
 */
sealed class StartupRecoveryState {
    /** 初始狀態：應用剛啟動，尚未開始對帳 */
    object Initializing : StartupRecoveryState() {
        override fun toString(): String = "Initializing"
    }

    /** 對帳中：正在執行 Staging Journal、Deletion Journal 與墓碑對帳 */
    data class Reconciling(
        val stage: String = "Starting",
        val progress: Float = 0f
    ) : StartupRecoveryState()

    /** 就緒：所有對帳與清理保證 100% 完成，系統處於健康一致狀態 */
    object Ready : StartupRecoveryState() {
        override fun toString(): String = "Ready"
    }

    /** 需要修復：存在未完成的刪除日誌或孤兒項目，需手動確認或提示維護 */
    data class RecoveryRequired(
        val reason: String,
        val pendingDeletionsCount: Int = 0,
        val pendingStagingCount: Int = 0
    ) : StartupRecoveryState()

    /** 致命失敗：SQLite 損壞、Schema 不匹配、CAS 失敗、磁碟 I/O 錯誤等不可逆異常 */
    data class Failed(
        val error: Throwable,
        val message: String
    ) : StartupRecoveryState()
}
