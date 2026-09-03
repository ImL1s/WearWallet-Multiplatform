package com.cbstudio.wearwallet.core.platform

import com.cbstudio.wearwallet.core.common.Result

/**
 * 測試專用 No-Op 刪除清理勾子實作 (Test-only No-Op Platform Deletion Cleanup Hook)
 * 僅供單元測試與整合測試使用，嚴禁進入生產 source set。
 */
class NoOpPlatformDeletionCleanupHook : PlatformDeletionCleanupHook {
    override suspend fun cancelWorkManagerJobs(walletId: Long): Result<Unit> = Result.Success(Unit)
    override suspend fun cancelBackgroundSync(walletId: Long): Result<Unit> = Result.Success(Unit)
    override suspend fun invalidateTiles(): Result<Unit> = Result.Success(Unit)
    override suspend fun invalidateComplications(): Result<Unit> = Result.Success(Unit)
}
