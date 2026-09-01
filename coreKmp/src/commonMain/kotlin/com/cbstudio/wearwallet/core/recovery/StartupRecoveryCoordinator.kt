package com.cbstudio.wearwallet.core.recovery

import com.cbstudio.wearwallet.core.common.Result
import kotlinx.coroutines.flow.StateFlow

/**
 * 阻塞式啟動對帳協調器介面
 *
 * 職責：
 * 1. 協調應用啟動時的 Staging Journal、Deletion Journal 與墓碑記錄對帳。
 * 2. 暴露 Observable 狀態 Flow (StateFlow<StartupRecoveryState>) 與錯誤狀態 (StateFlow<Throwable?>)。
 * 3. 提供 awaitReady(): Result<Unit> 阻塞掛起直到對帳就緒，杜絕 UI / ViewModels 搶先存取未對帳數據庫。
 * 4. 提供 retry() 支援在 FAILED 或 RECOVERY_REQUIRED 狀態下重新嘗試。
 */
interface StartupRecoveryCoordinator {
    /**
     * 當前啟動對帳狀態
     */
    val state: StateFlow<StartupRecoveryState>

    /**
     * 對帳過程發生的異常（若有）
     */
    val reconciliationError: StateFlow<Throwable?>

    /**
     * 發起啟動對帳（冪等且防併發重入）。
     *
     * @return 最終達到的 StartupRecoveryState
     */
    suspend fun startReconciliation(): StartupRecoveryState

    /**
     * 掛起直到狀態達到 READY。
     * 若處於 INITIALIZING 則自動觸發對帳；
     * 若對帳成功達到 READY 則回傳 Result.Success(Unit)；
     * 若對帳失敗達到 FAILED 或 RECOVERY_REQUIRED 則回傳 Result.Failure。
     */
    suspend fun awaitReady(): Result<Unit>

    /**
     * 重試啟動對帳流程
     */
    fun retry()
}
